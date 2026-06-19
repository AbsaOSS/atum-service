# Authentication & Authorization Options for atum-service

**Prepared:** 2026-06-19  
**Context:** atum-service has zero authentication today. All endpoints are public.  
**Scope:** Server (Tapir + http4s + ZIO 2), Agent (sttp/OkHttp3, Java 8), Reader (sttp + Cats/Future)  
**Goal:** Restrict write operations (POST/PATCH) to authorised users; allow all authenticated identities to read (GET)

---

## Current State

| Component | Auth Status |
|-----------|-------------|
| Server | ❌ No auth — all endpoints public |
| Agent (HttpDispatcher) | ❌ Sends bare HTTP — no token, no cert |
| Reader | ❌ Sends bare HTTP — no token, no cert |
| SSL/TLS | ✅ Supported (keystore-based, config-driven) |
| HTTPS | ✅ Supported via `SSL.scala` |

**Note on the "author" field:** The existing `author` header is explicitly documented as *not for authorization* — it can be spoofed and is only an audit trail.

---

## Role Model (Common to All Options)

Regardless of which authentication mechanism is chosen, the access model is:

| Role | Who | Allowed Operations |
|------|-----|--------------------|
| `WRITER` | AD group members (e.g. `atum-writers`) | GET + POST + PATCH (all endpoints) |
| `READER` | Any authenticated identity | GET endpoints only |
| *(unauthenticated)* | No valid credential | ❌ 401 Unauthorized |

**Protected write endpoints:**
- `POST /api/v2/partitionings`
- `POST /api/v2/partitionings/{id}/checkpoints`
- `PATCH /api/v2/partitionings/{id}/additional-data`
- `PATCH /api/v2/partitionings/{id}/ancestors`
- `POST /api/v1/checkpoint`, `POST /api/v1/partitioning` (legacy)

**Public endpoints (always accessible):**
- `GET /health`, `GET /health/readiness`, `GET /health/liveness`
- `GET /buildinfo`, `GET /metrics/*`
- Swagger UI

---

## Option 1 — Azure AD / Microsoft Entra ID with JWT (OIDC)

> **Best for:** Enterprises already on Microsoft 365 / Azure AD. The most common pattern in modern enterprise Java/Scala services.

### How it works

1. Azure AD is the **identity provider**. Your company's LDAP/AD is already synced to it.
2. Clients (Agent, Reader) obtain a **JWT access token** via OAuth2 using one of two flows:
   - **Client Credentials Flow** — for automated services (Agent, Aqueduct pipelines). No user interaction. Service principal authenticates with a client ID + secret or certificate.
   - **Authorization Code / Device Flow** — for interactive tools (Reader used from notebooks or scripts by a human).
3. The JWT contains a `groups` claim listing the user's AD group Object IDs (e.g. `atum-writers`).
4. The server validates the JWT signature against Azure AD's **public JWKS endpoint** (`https://login.microsoftonline.com/{tenant}/discovery/v2.0/keys`) — no LDAP calls at request time.
5. After validation, the server checks: does this token's `groups` claim contain the `atum-writers` group ID? If yes → WRITER; if not → READER.

### Token flow diagram

```
Agent (Aqueduct job)
  │
  ├─► Azure AD (OAuth2 Client Credentials)
  │     client_id: <service-principal>
  │     client_secret: <from AWS Secrets Manager>
  │     ──► returns: JWT access token (expires ~1h)
  │
  └─► POST /api/v2/partitionings
        Authorization: Bearer <JWT>
        ──► Server validates JWT signature
        ──► Checks groups claim → WRITER ✓
        ──► 201 Created
```

### Server implementation (ZIO + Tapir + http4s)

```scala
// Tapir security input — reusable across all protected endpoints
val bearerAuth: EndpointInput[JwtClaims] =
  auth.bearer[String]().mapDecode(token => JwtValidator.validate(token))

// Endpoints gain a security layer
val createPartitioningEndpoint =
  endpoint.securityIn(bearerAuth)
          .in("api" / "v2" / "partitionings")
          .post
          ...

// Middleware: validate JWT on every request
// Library: com.github.jwt-scala (jwt-circe) or nimbus-jose-jwt
// JWKS fetched once at startup, refreshed periodically
```

**Recommended libraries:**
- [`com.github.jwt-scala:jwt-circe_2.13`](https://github.com/jwt-scala/jwt-scala) — pure Scala, Circe integration, ZIO-friendly
- Or [`com.nimbusds:nimbus-jose-jwt`](https://connect2id.com/products/nimbus-jose-jwt) — battle-hardened Java library, widely used in enterprise

### Agent changes

```hocon
# reference.conf additions
atum.dispatcher.http.auth {
  type = "oauth2-client-credentials"
  token-url = "https://login.microsoftonline.com/${TENANT_ID}/oauth2/v2.0/token"
  client-id = ${?ATUM_CLIENT_ID}
  client-secret = ${?ATUM_CLIENT_SECRET}  # or load from AWS Secrets Manager
  scope = "api://<server-app-id>/.default"
}
```

The Agent's `HttpDispatcher` would:
1. Obtain a token at startup (or lazily on first request)
2. Cache it with a TTL matching the token's `exp` claim
3. Inject `Authorization: Bearer <token>` on every request
4. Re-acquire on 401 responses

### Reader changes

Same pattern. Readers run as a specific service principal OR accept a user-provided token at construction:
```scala
AtumReader(serverConfig, bearerToken = sys.env.get("ATUM_TOKEN"))
```

### Pros

- ✅ **Industry standard** — OAuth2/OIDC is the de-facto enterprise auth protocol
- ✅ **No LDAP calls at request time** — JWT is self-contained, validated offline via JWKS
- ✅ **Group membership from LDAP is automatic** — Azure AD syncs from your LDAP; no custom group management
- ✅ **Token expiry + rotation** handled by Azure AD — no manual secret rotation
- ✅ **Audit trail** — Azure AD logs all token issuances
- ✅ AWS Secrets Manager already in the server's dependency tree — client secrets can be stored there
- ✅ Java 8 compatible JWT libraries available for the Agent

### Cons

- ⚠️ **Azure AD dependency** — if Azure AD is unavailable, auth fails (mitigated: JWKS can be cached)
- ⚠️ **Token caching complexity** in Agent — need to handle expiry and refresh
- ⚠️ **Group ID vs name** — Azure AD `groups` claim contains Object IDs (GUIDs), not friendly names. Requires mapping config or the `groupMembershipClaims: "SecurityGroup"` + optional claims setup in Azure AD app registration
- ⚠️ **App Registration required** — an Azure AD admin must register the server app and grant permissions

### Effort estimate

| Component | Effort |
|-----------|--------|
| Server (JWT middleware + RBAC) | Medium (1–2 weeks) |
| Agent (token acquisition + caching) | Medium (1 week) |
| Reader (token injection) | Small (days) |
| Azure AD app registration | Small (hours, admin task) |
| **Total** | **~3–4 weeks** |

---

## Option 2 — Server-side API Key Registry (Static Tokens)

> **Best for:** Simple service-to-service auth where Azure AD integration is not feasible or desired. Fast to implement, no external IdP dependency.

### How it works

1. The server maintains an **API key registry** in its PostgreSQL database (table: `api_keys`).
2. Each key is associated with a **role** (`WRITER` or `READER`) and a **description** (e.g. "Aqueduct production pipeline").
3. Clients include their key in every request via the `Authorization: Bearer <api-key>` header (or a custom `X-Api-Key` header).
4. The server validates the key against the DB on each request (with in-memory cache + TTL to avoid per-request DB hits).
5. Key management is done via a privileged admin endpoint or directly via DB scripts.

### Schema

```sql
CREATE TABLE api_keys (
    key_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash     TEXT NOT NULL UNIQUE,  -- bcrypt or SHA-256 of the raw key
    role         TEXT NOT NULL CHECK (role IN ('WRITER', 'READER')),
    description  TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ,           -- NULL = no expiry
    revoked      BOOLEAN NOT NULL DEFAULT false
);
```

Raw keys are **never stored** — only a SHA-256 (or bcrypt) hash. The actual key is shown to the operator once at creation.

### Request flow

```
Agent (Aqueduct job)
  │
  └─► POST /api/v2/partitionings
        X-Api-Key: atm_abc123xyz...
        ──► Server: hash(key) → lookup in api_keys cache
        ──► role = WRITER, not revoked, not expired → allow
        ──► 201 Created
```

### Server implementation

```scala
// Tapir security input
val apiKeyAuth: EndpointInput[ApiKeyPrincipal] =
  auth.apiKey(header[String]("X-Api-Key")).mapDecode(key =>
    apiKeyService.validate(key)  // ZIO effect: cache lookup → DB fallback
  )
```

### Key management (admin endpoint)

```
POST /admin/api-keys        { "role": "WRITER", "description": "Aqueduct prod" }
  → { "key": "atm_<random>", "keyId": "<uuid>" }   # shown ONCE

DELETE /admin/api-keys/{keyId}    # revoke
GET    /admin/api-keys            # list (no raw keys shown)
```

The admin endpoint is protected by a separate **admin secret** (env var or AWS Secrets Manager), never exposed in Swagger.

### Agent changes

```hocon
atum.dispatcher.http.auth {
  type = "api-key"
  api-key = ${?ATUM_API_KEY}  # inject from env or Secrets Manager
}
```

Agent's `HttpDispatcher` injects `X-Api-Key: <value>` on every request. No token refresh needed — keys are long-lived.

### Reader changes

```scala
AtumReader(serverConfig, apiKey = sys.env.get("ATUM_API_KEY"))
```

### Pros

- ✅ **Simplest implementation** — no external IdP, no OAuth2 flows
- ✅ **No Azure AD / LDAP dependency** — fully self-contained
- ✅ **Easy to rotate** — revoke old key, issue new one, update env var
- ✅ **Java 8 compatible** — just HTTP headers
- ✅ **Deterministic** — no token expiry surprises in production at 2am
- ✅ **Works offline** — no JWKS endpoint or internet access needed
- ✅ Fast to implement (~1–2 weeks total)

### Cons

- ⚠️ **No SSO / identity federation** — keys are not linked to AD user identity; who is using a key is tracked by description only
- ⚠️ **Manual key management** — keys don't expire automatically; rotation is a manual process
- ⚠️ **No fine-grained user attribution** — all requests from Aqueduct look the same (mitigated partially by the existing `author` header)
- ⚠️ **Key leakage risk** — if a key appears in logs or is committed to git, it must be immediately revoked
- ⚠️ **Not compliant** with enterprise policies that require federated identity (check with your security team)

### Effort estimate

| Component | Effort |
|-----------|--------|
| Server (API key middleware + DB schema + cache) | Small–Medium (1 week) |
| Admin endpoint | Small (days) |
| Agent (header injection) | Small (days) |
| Reader (header injection) | Small (days) |
| **Total** | **~1–2 weeks** |

---

## Option 3 — Mutual TLS (mTLS / Client Certificates)

> **Best for:** Strictly controlled service-to-service communication where strong cryptographic identity is required and PKI infrastructure exists (or can be set up).

### How it works

1. A **Certificate Authority (CA)** issues client certificates to each service (Agent, Reader).
2. The server's TLS layer is configured to **require a valid client certificate** (`clientAuth = require` in SSLContext).
3. On connection, the server extracts the certificate's Subject DN (e.g. `CN=atum-agent, OU=atum-writers`) to determine role.
4. No token headers needed — identity is established at the TLS handshake level.

### Architecture

```
Agent (Aqueduct job)
  │  TLS handshake:
  │    → presents: client cert (CN=atum-agent, OU=atum-writers)
  │    ← server cert validated by agent's truststore
  │    → server validates client cert against CA truststore
  │
  └─► POST /api/v2/partitionings  (no auth headers — identity from cert)
        ──► Server: extract CN/OU from client cert
        ──► OU=atum-writers → WRITER role
        ──► 201 Created
```

### Server changes

The existing `SSL.scala` already handles a keystore. It needs to be extended:

```hocon
# application.conf
atum.server.ssl {
  key-store-path     = "server-keystore.jks"
  key-store-password = "..."
  trust-store-path   = "ca-truststore.jks"   # NEW: CA cert for validating clients
  trust-store-password = "..."
  client-auth        = "require"              # NEW: enforce client cert
}
```

```scala
// Role extraction from certificate (in Routes or Tapir middleware)
def roleFromCert(cert: X509Certificate): Role = {
  val ou = extractOU(cert.getSubjectDN.getName)
  if (ou == "atum-writers") Role.Writer else Role.Reader
}
```

### Agent changes

```hocon
atum.dispatcher.http {
  ssl {
    key-store-path     = "agent-keystore.jks"   # client cert + private key
    key-store-password = ${?ATUM_KEYSTORE_PASS}
    trust-store-path   = "ca-truststore.jks"    # CA cert to trust server
    trust-store-password = ${?ATUM_TRUSTSTORE_PASS}
  }
}
```

OkHttp3 supports custom SSLContext — the agent configures it via `OkHttpClient.Builder.sslSocketFactory()`.

### Reader changes

Same keystore configuration passed to sttp's backend.

### Certificate management

```
CA (your PKI team or internal CA)
├── server.atum.company.com (server cert + key)
├── atum-agent-writer.crt (CN=atum-agent, OU=atum-writers)
└── atum-reader.crt        (CN=atum-reader, OU=atum-readers)

Cert renewal: typically 1–2 years
Distribution: via Kubernetes secrets, HashiCorp Vault, or AWS Certificate Manager Private CA
```

### Pros

- ✅ **Strongest security model** — cryptographic identity, not a shareable secret
- ✅ **No token management** — no expiry surprises, no refresh flows
- ✅ **No external IdP dependency** — works fully offline
- ✅ **Tamper-proof identity** — private key never leaves the client; cert cannot be forged
- ✅ **Zero trust compatible** — well-suited for service mesh / zero-trust architectures
- ✅ Java 8 compatible — TLS/cert support is in the JVM itself

### Cons

- ⚠️ **PKI infrastructure required** — needs an internal CA (or AWS Private CA, HashiCorp Vault PKI); complex to set up if not already in place
- ⚠️ **Certificate distribution and rotation** — deploying new certs to all Aqueduct jobs is operationally heavy
- ⚠️ **No human identity** — does not integrate with LDAP/AD user identity (identifies services, not users)
- ⚠️ **Not user-facing** — unsuitable if you ever need a human to authenticate via a browser or notebook
- ⚠️ **Revocation complexity** — requires CRL/OCSP infrastructure for immediate revocation
- ⚠️ **Debugging is harder** — TLS issues at handshake level are less visible than HTTP 401 responses

### Effort estimate

| Component | Effort |
|-----------|--------|
| Server (mTLS SSLContext + cert extraction middleware) | Medium (1 week) |
| Agent (SSLContext with client keystore) | Medium (1 week) |
| Reader (SSLContext with client keystore) | Small (days) |
| PKI setup (CA, cert issuance, distribution) | Medium–Large (depends on existing infra) |
| **Total** | **~2–4 weeks (excl. PKI setup)** |

---

## Option 4 — Keycloak + Direct LDAP Federation (Self-hosted OIDC)

> **Best for:** Teams that want LDAP/AD group integration but cannot (or do not want to) use Azure AD. Full ownership of the identity provider.

### How it works

[Keycloak](https://www.keycloak.org/) is an open-source identity provider that connects **directly to your corporate LDAP/AD** — no Azure AD required. It issues standard OAuth2/OIDC JWTs with group claims sourced from LDAP group memberships.

The key insight: **the server-side code is identical to Option 1.** JWT validation against a JWKS endpoint works the same way regardless of whether the issuer is Azure AD or Keycloak. The only difference is in the JWKS URL and the token issuer (`iss` claim).

### Architecture

```
Agent
  │
  ├─► Keycloak (OAuth2 Client Credentials)
  │     → Keycloak reads group membership from LDAP
  │     ← returns JWT with groups: ["atum-writers"]
  │
  └─► POST /api/v2/partitionings
        Authorization: Bearer <JWT>
        ──► Server validates JWT against Keycloak JWKS
        ──► groups → WRITER role ✓
```

### What changes vs Option 1

| Aspect | Option 1 (Azure AD) | Option 4 (Keycloak) |
|--------|---------------------|---------------------|
| IdP location | Azure (Microsoft-managed) | Self-hosted (your infra) |
| LDAP integration | Via Azure AD Connect sync | Direct LDAP user federation |
| App registration | Azure portal | Keycloak admin console |
| JWKS URL | `login.microsoftonline.com` | `keycloak.yourcompany.com/realms/{realm}/protocol/openid-connect/certs` |
| Server-side JWT code | Same | Same |
| Agent/Reader OAuth2 code | Same | Same |
| Infra to manage | None | Keycloak cluster + HA + upgrades |

### Pros

- ✅ **Direct LDAP connection** — no Azure AD, no sync delays, no dependency on Microsoft
- ✅ **Full control** — own your token policies, lifetimes, group mappings
- ✅ **Open source, battle-hardened** — used by Red Hat, large enterprises; CNCF graduated
- ✅ **Server-side implementation identical to Option 1** — easy swap
- ✅ **Multi-protocol** — supports SAML, OIDC, OAuth2, LDAP passthrough

### Cons

- ⚠️ **You operate the IdP** — Keycloak is HA-critical infrastructure; downtime = no auth
- ⚠️ **Cluster management** — Keycloak HA requires PostgreSQL backend + multiple nodes
- ⚠️ **Upgrade path** — Keycloak has had breaking changes between major versions
- ⚠️ **Initial setup complexity** — realm configuration, client setup, LDAP federation mapping

### Effort estimate

| Component | Effort |
|-----------|--------|
| Keycloak deployment (Kubernetes/Docker) | Medium (1 week, mostly ops) |
| Realm + client + LDAP federation setup | Small (days) |
| Server (JWT middleware) — identical to Option 1 | Medium (1–2 weeks) |
| Agent + Reader — identical to Option 1 | Medium (1 week) |
| **Total** | **~3–4 weeks + ops setup** |

---

## Option 5 — Hybrid: API Keys for Machines, JWT for Humans

> **Best for:** Pragmatic deployments where automated services (pipelines) and human operators have different auth needs. Combines the simplicity of Option 2 with the identity federation of Option 1 or 4.

### How it works

The server accepts **two credential types** on all protected endpoints:

| Client Type | Credential | Auth Flow |
|-------------|-----------|-----------|
| Aqueduct pipeline jobs | `X-Api-Key: atm_<key>` | DB lookup (Option 2) |
| Human operators / Reader notebooks | `Authorization: Bearer <JWT>` | JWKS validation (Option 1 or 4) |
| Health/metrics endpoints | None | Always public |

Role is derived from whichever credential is present:
- API key → role from DB row
- JWT → role from `groups` claim

### Why this is common in practice

Automated services like Aqueduct jobs have predictable, machine-controlled environments. They don't benefit from OAuth2 token flows — a static API key stored in Secrets Manager is simpler, more reliable (no refresh at 2am), and easier to debug. Human users, on the other hand, benefit greatly from SSO (they don't manage secrets) and proper identity attribution.

### Server implementation

```scala
// Tapir: try JWT first, fall back to API key
val combinedAuth: EndpointInput[Principal] =
  auth.bearer[String]().mapDecode(JwtValidator.validate)
    .orElse(auth.apiKey(header[String]("X-Api-Key")).mapDecode(ApiKeyValidator.validate))
```

Or more cleanly, implement as http4s middleware that runs before Tapir:

```scala
def authMiddleware: HttpMiddleware[Task] = routes =>
  Kleisli { req =>
    resolveCredential(req) match {  // checks both Bearer and X-Api-Key
      case Some(principal) => routes(req.withAttribute(principalKey, principal))
      case None            => Forbidden()
    }
  }
```

### Pros

- ✅ **Best fit for atum-service's actual usage pattern** — pipelines + human readers are different use cases
- ✅ **Incremental migration path** — deploy API keys first (1–2 weeks), add JWT later
- ✅ **No token refresh complexity in Agent** — API keys don't expire on 1h TTL
- ✅ **Humans get SSO** — Reader users authenticate via browser without managing secrets
- ✅ **Audit trail**: API key logs show `source: aqueduct-prod-key-abc`, JWT logs show `user: ab@company.com`

### Cons

- ⚠️ **Two auth paths to maintain** — slightly more server-side complexity
- ⚠️ **Still needs an IdP** for the JWT path (Azure AD or Keycloak)
- ⚠️ **API key discipline still required** — key rotation, leakage scanning, etc.

### Effort estimate

| Component | Effort |
|-----------|--------|
| API key path (Option 2) | ~1–2 weeks |
| JWT path (Option 1 or 4) | ~2–3 weeks (can be added later) |
| Combined auth middleware | Small addition (days) |
| **Total (both paths)** | **~3–4 weeks** |

---

## Refinements to Options 1–3

### Option 1 (Azure AD + JWT) — adjustments

- **Use App Roles instead of `groups` claim:** Azure AD app roles are purpose-built for API authorization. The `groups` claim can hit a 200-group limit in large orgs (token becomes truncated). App roles are explicit, scoped to your application, and not subject to the limit. Example: define `atum.writer` and `atum.reader` app roles in the Azure AD app registration.

- **Validate `tid` claim:** Always check the tenant ID claim in addition to the JWT signature. This prevents tokens issued to other Azure AD tenants from being accepted (relevant in multi-tenant or partner environments).

- **Token caching in Agent:** Cache tokens with `exp - 60s` TTL (60 second buffer before expiry). On 401 response, immediately re-acquire — do not just retry with the cached token.

### Option 2 (API Keys) — adjustments

- **Prefix-based key format:** Use `atm_<32-random-bytes-base62>`. The `atm_` prefix enables secret scanning tools (GitHub, GitLab, AWS) to auto-detect leaked keys in commits, CI logs, and issue trackers.

- **HMAC-signed tokens (optional upgrade):** Structure keys as `atm_<key_id>.<hmac>`. The server can validate the HMAC without a DB lookup (stateless fast path), with DB used only for revocation checks. This adds security without sacrificing simplicity.

- **Per-key scoping:** Add a `permissions` column to `api_keys` (e.g. `JSONB` or a bitmask). A key for read-only integrations can be `READER`; Aqueduct gets `WRITER`. Future: scope to specific endpoints if needed.

### Option 3 (mTLS) — adjustments

- **HashiCorp Vault PKI or AWS Private CA:** Eliminates the main operational burden. Both support short-lived certificates (24h–7d), automated renewal, and online revocation via OCSP. Vault PKI in particular integrates with Kubernetes service accounts for zero-touch cert issuance.

- **SPIFFE/SPIRE on Kubernetes:** If atum-service runs on Kubernetes, SPIFFE/SPIRE provides automated workload identity (X.509 SVIDs) that are transparent to the application. The server's TLS stack just validates the cert; SPIRE handles all issuance and rotation. Eliminates cert distribution entirely.

- **Short-lived certs (24h):** Rather than 1–2 year certs that need manual rotation, use 24h certs auto-renewed by the PKI. This removes the revocation problem entirely — a compromised cert expires within a day.

---

## Comparison Matrix (All Options)

| Criterion | Opt 1: Azure AD JWT | Opt 2: API Keys | Opt 3: mTLS | Opt 4: Keycloak | Opt 5: Hybrid |
|-----------|---------------------|-----------------|-------------|-----------------|---------------|
| **Complexity** | Medium | Low | High | Medium-High | Medium |
| **LDAP/AD group integration** | ✅ Native | ❌ Manual | ❌ OU-based | ✅ Direct LDAP | ✅ (via JWT path) |
| **User identity in token** | ✅ Full | ⚠️ Description | ⚠️ Cert CN | ✅ Full | ✅ Full (humans) |
| **No external IdP needed** | ❌ Azure AD | ✅ | ✅ | ⚠️ Self-hosted | ⚠️ Partial |
| **Works fully offline** | ⚠️ JWKS cached | ✅ | ✅ | ⚠️ JWKS cached | ⚠️ API key path |
| **Automatic expiry/rotation** | ✅ | ❌ Manual | ⚠️ Cert TTL | ✅ | ✅ (JWT path) |
| **Java 8 compatible (Agent)** | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Human user auth (future)** | ✅ | ⚠️ Awkward | ❌ | ✅ | ✅ |
| **Enterprise policy compliance** | ✅ | ⚠️ Check policy | ✅ Zero-trust | ✅ | ✅ |
| **Operational overhead** | Low | Medium | High | High | Medium |
| **Implementation effort** | ~3–4w | ~1–2w | ~2–4w + PKI | ~3–4w + ops | ~3–4w |
| **Best for** | Standard enterprise | Fastest / PoC | Zero-trust K8s | No Azure AD | Mixed machine+human clients |

---

## Recommendation

> ⭐ **Option 1 (Azure AD + JWT)** remains the recommended path for a standard enterprise environment.

> 🔄 **Option 5 (Hybrid)** is the most pragmatic production pattern: start with API keys (Option 2) for the Agent in week 1–2, then layer in JWT (Option 1 or 4) for human-facing Reader auth. The incremental nature reduces risk.

> 🏠 **Option 4 (Keycloak)** if your organisation cannot use Azure AD — the server-side implementation is identical to Option 1, so the choice is purely an infrastructure/organisational one.

> 🔒 **Option 3 (mTLS)** only if your security team has an explicit zero-trust mandate and PKI infrastructure (or Vault/SPIRE) is already in place.

---

## Open Questions for Architects

1. **Azure AD availability:** Is registering an Azure AD application feasible? Who is the AAD admin contact?
2. **Group naming:** What AD group(s) map to the `WRITER` role? (e.g. `atum-writers`, `dq-platform-users`)
3. **Service principals:** Should each Aqueduct pipeline have its own service principal, or share one? (Shared is simpler; per-pipeline gives better audit trail.)
4. **Token audience (`aud` claim):** Should the server app registration be scoped to atum-service only, or a broader platform scope?
5. **Offline validation:** Are there network policies that block access to `login.microsoftonline.com` (Azure AD) or require a self-hosted IdP (→ Option 4)?
6. **App Roles vs Groups:** Does the org prefer Azure AD App Roles (explicit, no 200-group limit) or the `groups` claim? Check with the AAD admin.
7. **Machine identity policy:** Does InfoSec require all service-to-service calls to use federated identity (Azure AD service principals), or are API keys acceptable for pipeline jobs?
8. **Reader auth model:** Should the Reader library require auth configuration at construction time, or fall back to unauthenticated (for read-only public deployments)?
9. **v2 backlog bundling:** OBS-02 (retry logging) and RES-01 (circuit breaker) — bundle into this milestone or keep separate?

---

*Generated from codebase analysis of atum-service @ commit HEAD — 2026-06-19*  
*Stack: ZIO 2.0.19 · Tapir 1.9.6 · http4s-blaze 0.23.16 · sttp 3.5.2 · Scala 2.13*  
*Updated: 2026-06-19 — added Option 4 (Keycloak), Option 5 (Hybrid), and per-option refinements*

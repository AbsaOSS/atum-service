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

## Comparison Matrix

| Criterion | Option 1: Azure AD + JWT | Option 2: API Keys | Option 3: mTLS |
|-----------|--------------------------|-------------------|----------------|
| **Complexity** | Medium | Low | High |
| **LDAP/AD group integration** | ✅ Native (via Azure AD sync) | ❌ Manual role assignment | ❌ OU-based, not LDAP groups |
| **User identity in token** | ✅ Full (UPN, display name, groups) | ⚠️ Description only | ⚠️ Certificate CN only |
| **No external IdP needed** | ❌ Requires Azure AD | ✅ Self-contained | ✅ Self-contained |
| **Works offline** | ⚠️ JWKS cached | ✅ | ✅ |
| **Automatic expiry/rotation** | ✅ (token TTL) | ❌ Manual | ⚠️ Cert lifetime (1–2y) |
| **Java 8 compatible (Agent)** | ✅ | ✅ | ✅ |
| **Human user auth (future)** | ✅ Authorization Code flow | ⚠️ Possible but awkward | ❌ Not designed for humans |
| **Enterprise policy compliance** | ✅ Preferred by most InfoSec | ⚠️ Depends on policy | ✅ Zero-trust friendly |
| **Operational overhead** | Low (Azure handles IdP) | Medium (key rotation) | High (PKI management) |
| **Implementation effort** | ~3–4 weeks | ~1–2 weeks | ~2–4 weeks + PKI |
| **Recommended for** | Greenfield enterprise | Fastest path / PoC | Strict zero-trust environments |

---

## Recommendation

> ⭐ **Option 1 (Azure AD + JWT)** is the recommended path for a production enterprise system.

**Rationale:**
- Your company already uses Active Directory — Azure AD (Entra ID) syncs those groups automatically
- JWT is the industry standard for REST API auth; Tapir has first-class support for `auth.bearer`
- The Agent runs as service principals (Aqueduct pipelines), which map directly to OAuth2 Client Credentials
- The AWS Secrets Manager SDK is already in the server's dependency tree — client secrets can be stored there securely
- Scales naturally: adding human users (e.g. `AtumReader` from a notebook) is just adding the Authorization Code flow

**If you need the fastest path** (e.g. to unblock a security audit): implement **Option 2** first as a stepping stone. API keys can be live in 1–2 weeks and give you real authentication with minimal architectural change. Migrate to Option 1 in a subsequent milestone.

**Option 3** is worth considering only if your security team has an existing PKI and a zero-trust mandate for all internal services.

---

## Open Questions for Architects

1. **Azure AD App Registration:** Does the company allow registering an Azure AD application for atum-service? Who is the AAD admin contact?
2. **Group naming:** What AD group(s) should map to the `WRITER` role? (e.g. `atum-writers`, `dq-platform-users`)
3. **Service principals:** Should each Aqueduct pipeline have its own service principal, or share one? (Shared is simpler; per-pipeline gives better audit trail.)
4. **Token audience (`aud` claim):** The server app registration defines the audience. Should this be scoped to atum-service only, or a broader platform scope?
5. **Offline validation:** Are there network policies that would prevent the server from reaching `login.microsoftonline.com` for JWKS? If yes, JWKS must be pre-fetched and cached at deploy time.
6. **v2 requirements:** OBS-02 (retry logging) and RES-01 (circuit breaker) in the backlog — should those be bundled into this milestone or stay separate?
7. **Reader auth model:** Should the Reader library require auth configuration at construction time, or fall back to unauthenticated (for read-only public deployments)?

---

*Generated from codebase analysis of atum-service @ commit HEAD — 2026-06-19*  
*Stack: ZIO 2.0.19 · Tapir 1.9.6 · http4s-blaze 0.23.16 · sttp 3.5.2 · Scala 2.13*

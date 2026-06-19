# Authentication & Authorization Design — atum-service

**Prepared:** 2026-06-19  
**Status:** ✅ Decision made by architect (2026-06-19)  
**Scope:** Server (Tapir + http4s + ZIO 2) · Agent (sttp/OkHttp3, Java 8) · Reader (sttp + Cats/Future)

---

## Decision

> **Static Bearer Token (Pluggable provider)** — Option 6 below.

The Atum server accepts a predefined token in HTTP headers and validates it before processing any request. Two tokens: **WRITE** (Agent) and **READ** (Reader). Token retrieval is pluggable — config file, env var, AWS Secrets Manager, or custom implementation. The Agent and Reader inject the token automatically; application code never touches it.

---

## Current State

| Component | Auth Status |
|-----------|-------------|
| Server | ❌ No auth — all endpoints public |
| Agent (HttpDispatcher) | ❌ Sends bare HTTP — no token |
| Reader | ❌ Sends bare HTTP — no token |
| SSL/TLS | ✅ Supported (keystore-based, config-driven) |

> The existing `author` header is **not for authorization** — it can be spoofed and is audit-only.

---

## Role Model

| Role | Token | Allowed operations |
|------|-------|--------------------|
| `WRITER` | WRITE token | GET + POST + PATCH (all endpoints) |
| `READER` | READ token | GET endpoints only |
| *(none)* | Missing / invalid | ❌ 401 Unauthorized |

**Write endpoints:** `POST /api/v2/partitionings`, `POST /api/v2/partitionings/{id}/checkpoints`, `PATCH /api/v2/partitionings/{id}/additional-data`, `PATCH /api/v2/partitionings/{id}/ancestors`, `POST /api/v1/checkpoint`, `POST /api/v1/partitioning`

**Always public:** `/health/*`, `/buildinfo`, `/metrics/*`, Swagger UI

---

## Selected Approach: Static Bearer Token (Pluggable)

### How it works

1. The server holds one or two configured tokens (WRITE + optional READ).
2. Every request includes `Authorization: Bearer <token>`.
3. Server does a **constant-time string comparison** — no DB lookup, no crypto, no external calls.
4. Agent injects WRITE token automatically. Reader injects READ token automatically. Application code never sees either.

### Token retrieval — pluggable provider

Follows the same pattern as DB credential retrieval:

```
Priority:  Custom plugin  →  AWS Secrets Manager  →  Env var  →  Config file
```

```scala
trait TokenProvider {
  def getToken: Task[String]
}

object TokenProvider {
  def fromConfig(key: String): TokenProvider
  def fromEnv(varName: String): TokenProvider
  def fromSecretsManager(secretName: String, fieldName: String): TokenProvider
  def custom(f: Task[String]): TokenProvider
}
```

### Server configuration

```hocon
atum.server.auth {
  enabled = true

  # Option A: config / env var
  write-token = ${?ATUM_WRITE_TOKEN}
  read-token  = ${?ATUM_READ_TOKEN}   # optional; if absent, write-token grants all access

  # Option B: AWS Secrets Manager
  # aws-secret-name      = "atum-service-tokens"
  # aws-write-token-field = "writeToken"
  # aws-read-token-field  = "readToken"
  # aws-region            = "eu-west-1"
}
```

### Server implementation (ZIO + Tapir + http4s)

```scala
// Tapir security input
val tokenAuth: EndpointInput[Role] =
  auth.bearer[String]().mapDecode { token =>
    tokenAuthService.validate(token).map {
      case TokenMatch.Write => DecodeResult.Value(Role.Writer)
      case TokenMatch.Read  => DecodeResult.Value(Role.Reader)
      case TokenMatch.None  => DecodeResult.Error(token, UnauthorizedException)
    }
  }

// Constant-time comparison (prevents timing attacks)
class TokenAuthService(writeToken: String, readToken: Option[String]) {
  def validate(presented: String): UIO[TokenMatch] = ZIO.succeed {
    if (MessageDigest.isEqual(presented.getBytes, writeToken.getBytes)) TokenMatch.Write
    else if (readToken.exists(rt => MessageDigest.isEqual(presented.getBytes, rt.getBytes))) TokenMatch.Read
    else TokenMatch.None
  }
}
```

> ⚠️ Always use `MessageDigest.isEqual()` — not `==`. Naive equality is vulnerable to timing attacks.

### Agent configuration

```hocon
# agent reference.conf
atum.dispatcher.http.auth {
  type           = "bearer-token"
  token-provider = "config"           # or "env", "aws-secrets-manager", "custom"
  token          = ${?ATUM_AUTH_TOKEN}

  # AWS variant:
  # token-provider    = "aws-secrets-manager"
  # aws-secret-name   = "atum-agent-token"
  # aws-token-field   = "token"
  # aws-region        = "eu-west-1"
}
```

```scala
// AtumAgent public API — token invisible to application code
AtumAgent.builder()
  .serverUrl("https://atum.company.com")
  .authToken(TokenProvider.fromEnv("ATUM_AUTH_TOKEN"))                        // A
  .authToken(TokenProvider.fromSecretsManager("atum-tokens", "writeToken"))   // B
  .authToken(myCustomProvider)                                                 // C
  .build()
```

Agent's `HttpDispatcher` injects `Authorization: Bearer <token>` on every outbound request. No expiry, no refresh.

### Reader configuration

```scala
AtumReader.builder()
  .serverUrl("https://atum.company.com")
  .authToken(TokenProvider.fromEnv("ATUM_READ_TOKEN"))
  .build()
```

### Token generation

```bash
openssl rand -base64 32          # generates a 32-byte random token
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

Use ≥ 32 random bytes. Never commit tokens to source code.

### Token rotation (zero-downtime)

For seamless rotation, the server temporarily accepts **both old and new token** (configured as a list). Once all clients are updated, remove the old token.

```hocon
atum.server.auth.write-tokens = ["<new-token>", "<old-token>"]  # transition window
```

### Pros

- ✅ Simplest possible — string comparison, no external services
- ✅ No external IdP — fully self-contained, works offline
- ✅ Easy to set up — one config value per component; app code is unaffected
- ✅ Pluggable — same pattern as DB credential retrieval; custom providers supported
- ✅ AWS Secrets Manager — already in server's dependency tree
- ✅ Java 8 compatible — just HTTP headers
- ✅ No token expiry surprises — static tokens don't expire at 2am
- ✅ Two-token RBAC — WRITE vs READ separation is simple and explicit

### Cons

- ⚠️ Static tokens are long-lived — rotate immediately if leaked; use Secrets Manager
- ⚠️ No user identity — token identifies a role, not a person (audit trail via existing `author` header)
- ⚠️ No automatic rotation — manual or Secrets Manager rotation lambda
- ⚠️ Brute-force risk — mitigated by ≥ 32 random bytes + rate limiting (future)
- ⚠️ Not scalable to per-user authz — explicitly deferred by architect

### Effort estimate

| Component | Effort |
|-----------|--------|
| Server: bearer token middleware + role enforcement | Small–Medium (1 week) |
| Server: pluggable TokenProvider + Secrets Manager | Small (days) |
| Agent: token injection + pluggable provider | Small (days) |
| Reader: token injection + pluggable provider | Small (days) |
| Config + docs | Small (days) |
| **Total** | **~1–2 weeks** |

---

## Options Considered (Reference)

The following options were researched before the architect's decision. Kept for reference and future milestones.

| # | Option | Why not selected |
|---|--------|-----------------|
| 0 | **AbsaOSS login-service** (internal JWT gateway, direct LDAP) | More complex than needed for current scope; good candidate for future milestone if per-user identity is required |
| 1 | **Azure AD / MS Entra + JWT (OIDC)** | Requires Azure AD app registration; adds external IdP dependency; over-engineered for the stated goal |
| 2 | **Server-side API Key Registry** (DB-managed keys) | DB overhead and admin endpoints add complexity beyond what's needed; static token is simpler and sufficient |
| 3 | **mTLS (Mutual TLS / Client Certificates)** | Requires PKI infrastructure; operationally heavy; over-engineered |
| 4 | **Keycloak + Direct LDAP** (self-hosted OIDC) | Self-hosted IdP adds infra overhead; not justified for current scope |
| 5 | **Hybrid (API Keys + JWT)** | Combining two auth paths adds complexity; deferred until per-user identity is needed |

### When to revisit these options

- **Option 0 (login-service) or Option 1 (Azure AD):** When per-user identity, SSO, or fine-grained authorization is needed. The server-side JWT validation code is the same regardless of IdP.
- **Option 2 (API Keys with DB):** If multiple teams need independent tokens with individual rotation — static tokens become unwieldy above ~5 clients.
- **Option 3 (mTLS):** If a zero-trust mandate is issued by InfoSec.

---

## Open Questions

1. **Group naming for RBAC:** Which LDAP groups should map to WRITE access? (For future JWT migration — architect deferred detailed authz to the consuming application.)
2. **Single token or two?** Architect noted "perhaps two tokens" as an N.B. — confirm whether WRITE + READ split is desired for v1, or a single token for simplicity.
3. **Token distribution to Aqueduct:** How will the WRITE token be distributed to Aqueduct pipeline jobs? (Recommendation: AWS Secrets Manager, already in server's dependency tree.)
4. **Public endpoints policy:** Should health/metrics/Swagger remain public even when auth is enabled? (Recommendation: yes — consistent with standard practice.)
5. **v2 backlog:** OBS-02 (retry logging) and RES-01 (circuit breaker) — bundle into this milestone or keep separate?

---

*Generated from codebase analysis of atum-service @ HEAD — 2026-06-19*  
*Stack: ZIO 2.0.19 · Tapir 1.9.6 · http4s-blaze 0.23.16 · sttp 3.5.2 · Scala 2.13*  
*Decision recorded: 2026-06-19 — Static Bearer Token (Pluggable), architect-selected*

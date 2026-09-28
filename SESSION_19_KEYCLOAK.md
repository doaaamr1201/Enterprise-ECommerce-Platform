# Session 19 — Keycloak Identity Provider

## What is in the repository
- `services/docker-compose.yml`: `keycloak` (Keycloak 24, `start-dev --import-realm`, Admin Console on http://localhost:8090, admin/admin).
- `services/keycloak/ecommerce-platform-realm.json`: realm `ecommerce-platform`, confidential client `gateway` (secret `gateway-dev-secret`, redirect `http://localhost:8080/login/oauth2/code/keycloak`), realm roles `CUSTOMER` and `ADMIN`, user `customer1` / `customer1` with role `CUSTOMER`. Dev values only.
- `KC_HOSTNAME_URL=http://localhost:8090` makes every token carry the same `iss`, whether it was requested from the browser or from a container.

The gateway still validates the Session 3 HS256 tokens. Session 20 switches it to Keycloak.

## Keycloak token vs Session 3 token
The Session 3 token was signed with a shared HMAC secret and carried only `sub`, a hand-written `role` claim and `exp`. The Keycloak access token is signed with the realm's RSA private key (`alg: RS256`, a `kid` in the header), so services verify it with the public key from the realm's JWKS endpoint and never hold a signing secret. It adds `iss` (the realm URL), `aud`, `azp` (the client that requested it), `iat`, `jti`, `session_state`, `scope`, `preferred_username` and `email`, and the roles live in `realm_access.roles` instead of a single `role` string.

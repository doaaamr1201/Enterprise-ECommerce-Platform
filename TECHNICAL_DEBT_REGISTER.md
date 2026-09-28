# Technical Debt Register v2

Session 24 review of the Session 8 register (items 1-9) plus debt that surfaced in Sessions 9-23.

## Resolved
| # | Item | Logged | Resolved |
|---|------|--------|----------|
| 3 | PUBLIC_ROUTES hardcoded in JwtAuthFilter | S8 | S20 (c63a34d): JwtAuthFilter and the shared HS256 secret are gone; the gateway is a Keycloak resource server. Public routes are still declared in code, in `SecurityConfig` |
| 6 | Plain-text logs | S8 | S17 (668aa13): JSON logs with `traceId`/`spanId` in product-service and order-service; the other services still log plain text |
| 7 | No API versioning | S8 | S22 (de3716e): `/api/v1/products`, gateway rewrite of `/api/products/**` with `Deprecation` and `Sunset: Thu, 31 Dec 2026`. Order endpoints are still unversioned |
| 1a | In-memory product store | S8 | S10 (c3287a9): product-service on PostgreSQL through JPA |

## Still open
| # | Item | Logged | Priority note |
|---|------|--------|---------------|
| 9 | Saga dual write -- order saved and `OrderPlaced` sent without a transactional outbox | S8 | First: a crash between the two leaves an order no service will ever process |
| 10 | In-memory `SagaState` map in `OrderSagaOrchestrator` -- lost on restart | S12 | Second: same failure class as #9, fixed by persisting state next to the outbox table |
| 1b | In-memory stores in inventory-service and order-service | S8 | Needed before #9 and #10 can be fixed properly, because the outbox has to share a transaction with the order row |
| 2 | No idempotency key on payment calls | S8 | After the outbox: retries happen inside one request today, so a duplicate charge needs a client retry that nothing performs yet |
| 4 | Dead-letter handling -- only notification-service has `@RetryableTopic` + DLT (S13, 288754a); saga consumers log and skip a failing event | S8 | Medium: a skipped saga event leaves an order stuck in PENDING |
| 8 | No database migrations -- `ddl-auto=update` | S8 | Medium: becomes urgent with the first column rename |
| 5 | `payment.failure-rate` test setting shipped in payment-service configuration (`application.yml` 50, `application.properties` 0) | S8 | Low: compose and k8s override it, but one file should hold one value |

## Newly surfaced
| # | Item | Found | Priority note |
|---|------|-------|---------------|
| 11 | Dev secrets in Git: Keycloak client secrets in the realm file, `product-secrets` base64 in `k8s/`, DB password defaults in properties | S15, S19 | High before any shared environment: move to Sealed Secrets or Vault |
| 12 | inventory-service does not validate the client-credentials token order-service sends | S20 | Medium: the mesh's STRICT mTLS (S21) covers transport, not caller identity |
| 13 | Event records duplicated in every service; the `__TypeId__` names are the only contract | S7 | Medium: a renamed field fails silently at runtime, because the S11 Pact test guards only the HTTP API |
| 14 | Helm chart and raw manifests describe the same product-service resources | S16 | Low: pick one source for ArgoCD |
| 15 | A 4 GB Docker VM needs `-Xmx200m -XX:TieredStopAtLevel=1` on every JVM to run the stack, which also lowers load-test throughput | S9, S23 | Low for development, but k6 numbers from this setup are not production numbers |

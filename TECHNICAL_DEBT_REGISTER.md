# Technical Debt Register

| # | Item | Logged | Status |
|---|------|--------|--------|
| 1 | In-memory stores in Inventory, Product and Order services -- data lost on restart | S8 | OPEN |
| 2 | No idempotency on payment retry -- duplicate charges possible | S8 | OPEN |
| 3 | PUBLIC_ROUTES hardcoded in JwtAuthFilter instead of configuration | S8 | OPEN |
| 4 | No dead-letter topic on Kafka consumers -- failed events are only logged | S8 | OPEN |
| 5 | `payment.failure-rate` test setting shipped in payment-service configuration | S8 | OPEN |
| 6 | Plain-text logs -- hard to search across services | S8 | OPEN |
| 7 | No API versioning -- `/api/products` has no version prefix | S8 | OPEN |
| 8 | No database migrations (Flyway/Liquibase) | S8 | OPEN |
| 9 | Saga dual write -- order saved and event sent without a transactional outbox | S8 | OPEN |

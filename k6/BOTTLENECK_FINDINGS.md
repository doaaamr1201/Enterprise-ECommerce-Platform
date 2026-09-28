# Session 23 — Bottleneck Findings

## Scripts
| Script | Target | Purpose |
|--------|--------|---------|
| `smoke-test.js` | `GET /api/v1/products` | 1 VU for 10s, baseline latency |
| `order-load-test.js` | `POST /api/orders` | 20 VUs sustained for 2 minutes |
| `checkout-stress-test.js` | `POST /api/orders` (Gateway -> Order -> Inventory -> Payment) | ramps to 150 VUs, far above the bulkhead limit of 10 |

`checkout-stress-test.js` counts every response in the `order_outcomes` metric, tagged by what answered it:

| Outcome | Pattern that answered |
|---------|-----------------------|
| `CONFIRMED` | none -- the whole chain succeeded |
| `QUEUED` | Bulkhead (`bulkheadFallback`) |
| `PENDING_TIMEOUT` | TimeLimiter (`timeoutFallback`, "Payment is taking too long") |
| `PENDING` | CircuitBreaker (`paymentFallback`, "Will retry payment later") |
| `HTTP_401` | Gateway -- `TEST_JWT` missing or expired |

## Run settings
- payment-service: `PAYMENT_FAILURE_RATE=50` and `PAYMENT_DELAY_MS=500`, so the CircuitBreaker sees real failures and calls stay open long enough to fill the bulkhead.
- `TEST_JWT`: a `customer1` access token from Keycloak.

## Theoretical order
Session 5 slide: Bulkhead -> TimeLimiter -> CircuitBreaker -> Retry, with the Bulkhead rejecting first.

This platform's configured order (order-service `application.properties`, Session 4): `circuit-breaker-aspect-order=1`, `retry-aspect-order=2`, so a call passes CircuitBreaker -> Retry -> TimeLimiter -> Bulkhead. The bulkhead and timeout fallbacks return a normal `QUEUED` / `PENDING` response, so the CircuitBreaker never counts them as failures. It opens only on payment errors. Expected sequence under load: `QUEUED` answers appear first, then `PENDING` answers once the payment failure rate passes 50% of the last 10 calls.

## Baseline
| Test | req/s | P95 | P99 | failed |
|------|-------|-----|-----|--------|
| smoke-test.js | | | | |
| order-load-test.js | | | | |

## Observed firing order
| Time | `/actuator/bulkheads` available calls | `/actuator/circuitbreakers` state | First outcome seen |
|------|---------------------------------------|-----------------------------------|--------------------|
| | | | |

Comparison with the theoretical order:

## Zipkin correlation
| Failed request (time, outcome) | Trace ID | Span that failed or took longest | Duration |
|--------------------------------|----------|-----------------------------------|----------|
| | | | |

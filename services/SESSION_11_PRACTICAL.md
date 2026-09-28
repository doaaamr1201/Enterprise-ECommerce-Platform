# Session 11 Practical — Contract Testing + WireMock

## 1) Pact Consumer
From `services/order-service`:

```powershell
mvn test -Dtest=OrderServiceInventoryContractTest
```

Expected:
`target/pacts/order-service-inventory-service.json`

## 2) Pact Provider
After the consumer test generates the pact, from `services/inventory-service`:

```powershell
mvn test -Dtest=InventoryServicePactVerificationTest
```

The provider test reads:
`../order-service/target/pacts`

## 3) WireMock
From `services/order-service`:

```powershell
mvn test -Dtest=OrderServicePaymentWireMockTest
```

It checks:
- payment 200 -> CONFIRMED
- payment 503 -> PENDING (Circuit Breaker fallback)
- correct POST payload -> verified by WireMock

The payment URL was made configurable with:
`payment.service.url`

The test overrides it to WireMock's random port.

## 4) Chaos
`docker-compose.yml` now runs every service (Session 9), and the gateway routes `/api/orders/**`.
`OrderServicePaymentWireMockTest.createOrder_returnsPending_whenInventoryServiceIsDown` checks the same hypothesis in code.

From the `services` folder, with the platform running (`docker compose up -d`):

```powershell
docker compose stop inventory-service
```

`POST http://localhost:8080/api/orders` (with a Bearer token) now returns `PENDING`, not 500, and
`GET http://localhost:8082/actuator/circuitbreakers` shows `paymentService` OPEN after repeated failures.

```powershell
docker compose start inventory-service
```

After the circuit breaker closes again, the same order returns `CONFIRMED`.

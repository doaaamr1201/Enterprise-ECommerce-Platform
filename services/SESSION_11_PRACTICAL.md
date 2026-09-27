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

## 4) Important note about Chaos
The supplied docker-compose currently starts Kafka and Redis only.
It does not contain `inventory-service`, `order-service`, or `payment-service`.
So the PDF's exact command:

```powershell
docker compose stop inventory-service
```

cannot work with the supplied compose file until those services are containerized and added to compose.

For the code-level Session 11 lab, Pact + WireMock are the implemented parts in this package.

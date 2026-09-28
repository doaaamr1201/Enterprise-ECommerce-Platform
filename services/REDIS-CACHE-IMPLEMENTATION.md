# Redis Caching - Session 08 Implementation

Implemented in `product-service`:

- Added `spring-boot-starter-cache`
- Enabled caching with `@EnableCaching`
- Added Redis host/port configuration
- Configured Redis as Spring Cache with 5-minute TTL
- Added `@Cacheable("products")` to `getProductById` and `getAllProducts` (key `'all'`)
- Session 18: writes publish `ProductChangedEvent`; `ProductCacheEvictionListener` evicts the product and `'all'` after the transaction commits
- Added PUT `/api/v1/products/{id}` update endpoint
- Added Redis service to the root `docker-compose.yml`

## Run Redis

From the `services` folder:

```bash
docker compose up -d redis
```

Verify:

```bash
docker ps
docker exec -it redis redis-cli ping
```

Expected: `PONG`

## Test cache

1. Create a product with POST `/api/v1/products`.
2. Call GET `/api/v1/products/{id}` once. The console prints `[CACHE MISS] Loading product ... from repository`.
3. Call the same GET again. The repository method should not execute again because Spring Cache returns the cached value.
4. Inspect Redis:

```bash
docker exec -it redis redis-cli KEYS "*"
docker exec -it redis redis-cli TTL "products::1"
```

5. Update the product with PUT `/api/v1/products/{id}`. The console prints `[CQRS] Cache evicted for product ... due to UPDATED` and the product and `'all'` entries are evicted.
6. Call GET again. It loads from the repository and puts the fresh result back into Redis.

# Kips Shop

Kips Shop is a Spring Boot e-commerce REST backend. It provides JWT authentication, a product catalog, category trees, carts, stock-aware checkout, and orders with local test payments.

## Stack

Java 25, Spring Boot 4.1.2, Gradle 9.8.1, PostgreSQL 15, Flyway, Redis 7, and SpringDoc OpenAPI.

## Run locally

Create a local environment file from the template.

```bash
cp -n .env.example .env
```

Set `POSTGRES_PASSWORD` in `.env`. Generate a JWT key with `openssl rand -base64 32` and set it as `JWT_SECRET_KEY`. For an existing PostgreSQL volume, keep using that database user's current password.

```bash
docker compose up --build -d --wait
```

The API runs at [localhost:8080](http://localhost:8080), with [Swagger UI](http://localhost:8080/swagger-ui.html) and the [OpenAPI specification](http://localhost:8080/v3/api-docs). Set `POSTGRES_HOST_PORT=5433` in `.env` if port `5432` is occupied. PostgreSQL data persists in the `postgres_data` volume.

## API

| Method and path | Purpose |
| --- | --- |
| `POST /api/v1/auth/register` | Create a standard user using `username`, `email`, and `password` |
| `POST /api/v1/auth/authenticate` | Sign in with `username` and `password` |
| `POST /api/v1/auth/refresh` | Issue a new access token from the `refresh_token` field |
| `GET /api/v1/products` | List products with optional `page`, `size`, `sort`, and `categoryId` parameters |
| `GET /api/v1/products/{id}` | Read a product |
| `POST /api/v1/products`, `PUT /api/v1/products/{id}`, `DELETE /api/v1/products/{id}` | Manage products as an administrator |
| `GET /api/v1/product-categories` | Read the cached category tree |
| `POST /api/v1/product-categories` | Create a category tree as an administrator |
| `GET /api/v1/cart` | Read the signed-in user's cart |
| `PUT /api/v1/cart/items/{productId}`, `DELETE /api/v1/cart/items/{productId}` | Set a positive `quantity` or remove an item |
| `POST /api/v1/orders/checkout` | Check stock, record prices, and create a local test order |
| `GET /api/v1/orders`, `GET /api/v1/orders/{id}` | List and read the signed-in user's orders |
| `POST /api/v1/orders/{id}/cancel` | Cancel an unshipped order and restore stock |
| `POST /api/v1/admin/orders/{id}/ship` | Mark a paid order as shipped as an administrator |

Send `Authorization: Bearer <access_token>` for catalog, cart, and order requests. A refresh token is accepted only by the refresh endpoint and retains its original expiry. Tokens from before the token format change require signing in again. Set a stable JWT key to keep tokens valid across restarts.

Product lists default to 20 rows, accept up to 100 per page, and include an `X-Total-Count` response header. The supported sort fields are `id`, `name`, `price`, and `stock`. For example, `?page=0&size=20&sort=price,asc&categoryId=2`. Product writes require `name`, `price`, `stock`, and an existing `categoryId`. Category writes take a `categories` array whose entries may specify a `parentId` or contain nested `subCategories`.

Public registration assigns `ROLE_USER`. A trusted database operator can grant `ROLE_ADMIN` to a known account in a local environment using the following SQL, after replacing the username.

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM app_user u CROSS JOIN app_user_roles r
WHERE u.username = 'your-local-account' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;
```

Checkout requires an `Idempotency-Key` header of up to 128 characters. Retries with the same key for the same account return the same order. The cart shows current prices. Checkout records prices and product names in the order and adjusts stock in one transaction. The test payment uses TRY and is enabled by default in local Compose. Outside Compose, set `CHECKOUT_LOCAL_TEST_ENABLED=true` to enable it. The test flow requires no payment details.

Validation failures return `400`, missing or invalid authentication returns `401`, insufficient privileges returns `403`, and missing records return `404`. Conflicts such as duplicate registration and insufficient stock return `409`.

## Tests

Run `./gradlew test` with Java 25, an empty PostgreSQL test database, and Redis. Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` for the test database. Redis defaults to localhost and the tests use logical database 15. CI provisions both services before running the suite.

## License

[MIT](LICENSE)

# Kips Shop

**Kips Shop** is a monolithic e-commerce backend application built with **Spring Boot**. It follows clean architecture principles and aims to provide a scalable and maintainable foundation for e-commerce platforms.

---

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sametakbal_kips-shop&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sametakbal_kips-shop)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sametakbal_kips-shop&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sametakbal_kips-shop)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=sametakbal_kips-shop&metric=bugs)](https://sonarcloud.io/summary/new_code?id=sametakbal_kips-shop)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=sametakbal_kips-shop&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=sametakbal_kips-shop)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=sametakbal_kips-shop&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=sametakbal_kips-shop)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=sametakbal_kips-shop&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=sametakbal_kips-shop)




## 🚀 Features

- 📦 Product & Category Management  
- 🛒 Cart Operations with Redis Caching  
- 🧾 Order Management (WIP)  
- 🗃️ PostgreSQL + Flyway for database versioning  
- ⚙️ RESTful API design with Spring Boot  
- 📄 Auto-generated API documentation with Swagger (SpringDoc)

---

## 🧱 Tech Stack

- Java 21  
- Spring Boot 3.4.x  
- Spring Data JPA  
- Redis (for caching)  
- PostgreSQL  
- Flyway (for DB migrations)  
- SpringDoc OpenAPI (Swagger UI)

---

## 📦 Modules (within monolith)

- `product` – product & category entities and logic  
- `cart` – Redis-based cart management  
- `order` – order and checkout (in progress)  
- `common` – shared DTOs, configs, and utilities

---

## ⚙️ Getting Started

### Prerequisites

- Java 21  
- Docker (for PostgreSQL and Redis)
- Gradle (wrapper included)

### Clone the repository

```bash
git clone https://github.com/sametakbal/kips-shop.git
cd kips-shop

---

## 📦 Product CRUD API

Full description of the **Product** module: create, read, update and delete products through a RESTful API protected by JWT authentication.

### Overview

Products are managed under the `/api/v1/products` resource. Each product belongs to exactly one category (`categoryId` references an entry in `product_category`, seeded by Flyway migration `V2`). All endpoints require a valid JWT bearer token; without one Spring Security returns `403 Forbidden`.

### Code structure

```
src/main/java/com/akbal/kips/be/
├── web/controller/ProductController.java      # REST endpoints
├── service/product/                           # service layer
│   ├── ProductService.java                    # interface
│   └── impl/ProductServiceImpl.java           # implementation
├── service/product/mapper/ProductMapper.java  # entity <-> DTO mapping
├── domain/product/Product.java                # JPA entity
├── repository/ProductRepository.java          # Spring Data repository
└── dto/product/
    ├── request/ProductRequest.java            # input payload
    └── response/ProductResponse.java          # output payload
```

### Endpoints

| Method   | Path                  | Description              |
|----------|-----------------------|--------------------------|
| `POST`   | `/api/v1/products`      | Create a new product     |
| `GET`    | `/api/v1/products`      | List all products        |
| `GET`    | `/api/v1/products/{id}` | Get one product by id    |
| `PUT`    | `/api/v1/products/{id}` | Update an existing product |
| `DELETE` | `/api/v1/products/{id}` | Delete a product         |

### Authentication

Every request must include the header:

```
Authorization: Bearer <access_token>
```

Get a token by registering or authenticating at `/api/v1/auth/**` (see JWT flow).

### Request payload (`ProductRequest`)

| Field         | Type      | Validation      |
|---------------|-----------|-----------------|
| `name`        | String    | required (`@NotBlank`) |
| `description` | String    | optional         |
| `price`       | BigDecimal| required (`@NotNull`)    |
| `stock`       | Integer   | required (`@NotNull`)    |
| `categoryId`  | Long      | required (`@NotNull`), must reference an existing `product_category.id` |

### Response payload (`ProductResponse`)

```json
{
  "id": 1,
  "name": "Rolex Submariner",
  "description": "Luxury dive watch",
  "price": 15999.99,
  "stock": 5,
  "categoryId": 2
}
```

All responses are wrapped in a standard envelope:

```json
{
  "data": { ... },
  "success": true,
  "status": "OK"
}
```

### Example requests (curl)

**Create a product**

```bash
curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{"name":"Rolex Submariner","description":"Luxury dive watch","price":15999.99,"stock":5,"categoryId":2}'
```

**Get all products**

```bash
curl -s http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer <access_token>"
```

**Get one product**

```bash
curl -s http://localhost:8080/api/v1/products/1 \
  -H "Authorization: Bearer <access_token>"
```

**Update a product**

```bash
curl -s -X PUT http://localhost:8080/api/v1/products/1 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{"name":"Rolex Submariner Date","description":"Updated luxury dive watch","price":17500.00,"stock":3,"categoryId":2}'
```

**Delete a product**

```bash
curl -s -X DELETE http://localhost:8080/api/v1/products/1 \
  -H "Authorization: Bearer <access_token>"
```

### Error cases

| Scenario                          | Result |
|-----------------------------------|--------|
| Missing / invalid JWT token       | `403 Forbidden` |
| `categoryId` does not exist       | `500` (thrown by `orElseThrow()`) |
| Missing required field (`name`, `price`, `stock`, `categoryId`) | `400` with field error message |
| Product id not found on GET/PUT   | `500` |

> Note: the service currently uses `.orElseThrow()` and validation is not mapped to clean `400` responses for missing resources — a catch-all `RuntimeException` handler returns an `ApiResponse` with the failure message.

# Contributing to Kips Shop

First of all, thank you for considering contributing to **Kips Shop**! Your help is greatly appreciated. 🙌

This guide provides you with the guidelines for contributing effectively.

---

## 🧰 Prerequisites

- Docker with Compose
- JDK 25 for running Gradle directly on the host

Make sure you can build and run the project locally:

```bash
docker compose up --build -d --wait
```

To run the API on the host while keeping its dependencies in Docker, start `postgres` and `redis` with `docker compose up -d --wait postgres redis`, then run `./gradlew bootRun --args='--redis.hostname=localhost --redis.password='`.

---

## 🧑‍💻 How to Contribute

### 1. Fork the Repository

Click the **Fork** button on the top right of the repo page to create your own copy.

### 2. Clone Your Fork

```bash
git clone https://github.com/YOUR_USERNAME/kips-shop.git
cd kips-shop
```

### 3. Create a New Branch

```bash
git checkout -b feature/your-feature-name
```

Use meaningful branch names, such as:
- `feature/cart-redis-integration`
- `fix/typo-in-readme`

### 4. Commit Your Changes

```bash
git add .
git commit -m "Add support for X feature"
```

Make sure your commit messages are clear and concise.

### 5. Push Your Changes

```bash
git push origin feature/your-feature-name
```

### 6. Create a Pull Request

Go to your forked repo on GitHub and click **Compare & Pull Request**. Include a description of the changes and link any relevant issues.

---

## ✅ Coding Guidelines

- Follow the existing architecture and conventions
- Use meaningful names for variables, methods, and classes
- Use `@Slf4j` for logging, and avoid `System.out.println`
- Avoid pushing commented-out code
- Write Javadoc/KDoc when necessary

---

## 🧪 Tests

If you're adding new features or fixing bugs, please include tests:
```bash
./gradlew test
```

---

## 🗣️ Need Help?

Feel free to open an issue or reach out via [GitHub Discussions](https://github.com/sametakbal/kips-shop/discussions) if you’re not sure how to start.

---

Thanks again, and happy coding! 🎉


# Session 13 Practical — Completed

Implemented from the Session 13 lab:
- GitHub Actions workflow at `.github/workflows/product-service-ci.yml`
- Product-service test/quality gate
- Java 21 + Temurin + Maven cache
- Docker Buildx
- GHCR authentication with `GITHUB_TOKEN`
- SHA-based image tags plus `latest` on `main`
- Docker build/push job gated by `needs: test`
- Product-service Dockerfile

Important project/source alignment:
- The supplied `product-service/pom.xml` declares Java 17, while Session 13 specifies Java 21 for CI. The workflow follows the lecture (Java 21); the Dockerfile uses a Java 17 runtime to match the supplied project configuration.
- Session 13 stops after pushing the image to GHCR; it does not deploy to Kubernetes.

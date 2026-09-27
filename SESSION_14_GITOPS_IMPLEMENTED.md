# Session 14 — GitOps Implementation

Implemented the Session 14 Lab 11B pattern for `product-service`.

## Added
- `services/k8s/product-service/deployment.yaml` — 8 stable replicas
- `services/k8s/product-service/service.yaml` — ClusterIP on port 8081
- `services/k8s/product-service/configmap.yaml` — Redis/Eureka configuration
- `services/k8s/product-service/deployment-canary.yaml` — 2 canary replicas (20%)
- `services/k8s/argocd/product-service-app.yaml` — ArgoCD Application with automated sync, prune and selfHeal

## Alignment fix
- Product service is aligned to port 8081 to match the Session 14 lab manifests.
- Dockerfile now exposes 8081 and Spring Boot is configured with `server.port=8081`.

## Before applying ArgoCD
Replace `YOUR_GITHUB_OWNER/YOUR_REPOSITORY` and the image placeholders with the real GitHub repository/image.

For a private GHCR image, configure an imagePullSecret in the `ecommerce` namespace (Secrets are intentionally deferred in Session 14).

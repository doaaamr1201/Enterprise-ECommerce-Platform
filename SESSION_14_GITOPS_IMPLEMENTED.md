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

## Repository and image
- ArgoCD watches `https://github.com/doaaamr1201/Enterprise-ECommerce-Platform.git`, path `services/k8s/product-service`.
- Both deployments use `ghcr.io/doaaamr1201/enterprise-ecommerce-platform/product-service:latest`, the image the CI workflow pushes. Set the canary to a `sha-` tag when a new version is rolled out.
- The deployments read `SPRING_DATASOURCE_URL` from the ConfigMap.

## Infrastructure in the cluster
`services/k8s/infrastructure/` holds PostgreSQL (`productdb`) and Redis for the `ecommerce` namespace. Apply it once before ArgoCD syncs product-service:

```powershell
kubectl apply -f services/k8s/infrastructure/
```

For a private GHCR image, configure an imagePullSecret in the `ecommerce` namespace (Secrets are intentionally deferred in Session 14).

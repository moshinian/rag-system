# 本地 Kubernetes 基线

V1 首先以 `rag-backend replicas=1` 验证镜像、Service Discovery、Secret、PVC 和探针；完成分布式任务改造后才扩为四副本。

```bash
docker build -f rag-backend/Dockerfile -t rag-backend:local .
docker build -f rag-ai-service/Dockerfile -t rag-ai-service:local rag-ai-service
docker build -f rag-frontend/Dockerfile -t rag-frontend:local .

cp k8s/base/secret.example.yaml /tmp/rag-secret.yaml
# 编辑 /tmp/rag-secret.yaml，禁止把真实凭证提交到 Git。
kubectl apply -f k8s/base/namespace.yaml
kubectl apply -f /tmp/rag-secret.yaml
kubectl apply -k k8s/infra
kubectl apply -k k8s/base
```

在 kind/minikube 中需把三个 `:local` 镜像加载进集群。将 `rag.local` 指向 Ingress 地址后访问前端。

资源预算以四副本总量为准：Backend 每 Pod JVM `128m/384m`、内存 request/limit `384Mi/640Mi`，Indexing 与 Agent 并发各为 1，Hikari 最大 5。四副本上限约为 20 条应用数据库连接、4 个 Indexing 和 4 个 Agent 在途执行。

完成单副本功能验证和 Lease 故障测试后，再启用同构四副本：

```bash
kubectl apply -k k8s/overlays/multi-instance
kubectl -n rag-system rollout status deployment/rag-backend
kubectl -n rag-system get pods -l app=rag-backend -o wide
```

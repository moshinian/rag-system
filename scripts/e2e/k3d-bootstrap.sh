#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
E2E_BIN_DIR="${E2E_BIN_DIR:-/tmp/rag-e2e-bin}"
K3D="${E2E_BIN_DIR}/k3d"
KUBECTL="${E2E_BIN_DIR}/kubectl"
CLUSTER_NAME="${RAG_E2E_CLUSTER_NAME:-rag-e2e}"
SECRET_FILE="${RAG_E2E_SECRET_FILE:-/tmp/rag-e2e-secret.yaml}"

for command_path in "${K3D}" "${KUBECTL}"; do
  if [[ ! -x "${command_path}" ]]; then
    echo "missing executable: ${command_path}" >&2
    exit 1
  fi
done

dashscope_key="$(jq -er '.configurations[] | select(.name == "Spring Boot-RagApplication<rag-service>") | .env.DASHSCOPE_API_KEY' "${ROOT_DIR}/.vscode/launch.json")"
deepseek_key="$(jq -er '.configurations[] | select(.name == "Spring Boot-RagApplication<rag-service>") | .env.DEEPSEEK_API_KEY' "${ROOT_DIR}/.vscode/launch.json")"
db_password="$(openssl rand -hex 16)"
redis_password="$(openssl rand -hex 16)"
minio_access_key="rage2e$(openssl rand -hex 6)"
minio_secret_key="$(openssl rand -hex 20)"
mcp_token="$(openssl rand -hex 24)"

umask 077
"${KUBECTL}" create secret generic rag-secrets \
  --namespace rag-system \
  --from-literal=DB_PASSWORD="${db_password}" \
  --from-literal=REDIS_PASSWORD="${redis_password}" \
  --from-literal=MINIO_ACCESS_KEY="${minio_access_key}" \
  --from-literal=MINIO_SECRET_KEY="${minio_secret_key}" \
  --from-literal=DASHSCOPE_API_KEY="${dashscope_key}" \
  --from-literal=DEEPSEEK_API_KEY="${deepseek_key}" \
  --from-literal=RAG_AGENT_TOOL_TOKEN="${mcp_token}" \
  --dry-run=client -o yaml > "${SECRET_FILE}"
unset dashscope_key deepseek_key db_password redis_password minio_access_key minio_secret_key mcp_token

if ! "${K3D}" cluster list --no-headers | awk '{print $1}' | grep -Fxq "${CLUSTER_NAME}"; then
  "${K3D}" cluster create "${CLUSTER_NAME}" \
    --servers 1 \
    --agents 1 \
    --port "18080:80@loadbalancer" \
    --wait
fi

export KUBECONFIG
KUBECONFIG="$(${K3D} kubeconfig write "${CLUSTER_NAME}")"

"${K3D}" image import -c "${CLUSTER_NAME}" \
  rag-backend:local rag-ai-service:local rag-frontend:local
"${KUBECTL}" apply -f "${ROOT_DIR}/k8s/base/namespace.yaml"
"${KUBECTL}" apply -f "${SECRET_FILE}"
"${KUBECTL}" apply -k "${ROOT_DIR}/k8s/infra"
"${KUBECTL}" rollout status statefulset/postgres -n rag-system --timeout=180s
"${KUBECTL}" rollout status statefulset/redis -n rag-system --timeout=180s
"${KUBECTL}" rollout status statefulset/minio -n rag-system --timeout=180s
"${KUBECTL}" apply -k "${ROOT_DIR}/k8s/base"
"${KUBECTL}" rollout status deployment/rag-ai-service -n rag-system --timeout=240s
"${KUBECTL}" rollout status deployment/rag-backend -n rag-system --timeout=240s
"${KUBECTL}" rollout status deployment/rag-frontend -n rag-system --timeout=180s

echo "KUBECONFIG=${KUBECONFIG}"
echo "Ingress: curl -H 'Host: rag.local' http://127.0.0.1:18080/api/health"

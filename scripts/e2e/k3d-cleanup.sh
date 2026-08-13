#!/usr/bin/env bash
set -euo pipefail

E2E_BIN_DIR="${E2E_BIN_DIR:-/tmp/rag-e2e-bin}"
CLUSTER_NAME="${RAG_E2E_CLUSTER_NAME:-rag-e2e}"
SECRET_FILE="${RAG_E2E_SECRET_FILE:-/tmp/rag-e2e-secret.yaml}"

if [[ -x "${E2E_BIN_DIR}/k3d" ]]; then
  "${E2E_BIN_DIR}/k3d" cluster delete "${CLUSTER_NAME}" || true
fi
if [[ -f "${SECRET_FILE}" ]]; then
  chmod 600 "${SECRET_FILE}" || true
  rm -f -- "${SECRET_FILE}"
fi

#!/usr/bin/env bash
# 导出 Admin OpenAPI 3 JSON（Admin 须已启动）
# 用法：bash scripts/docs/export-openapi.sh
# 环境变量：BASE_URL（默认 http://localhost:8080）、OUTPUT（默认 docs/openapi/admin-api.json）

set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
OUTPUT="${OUTPUT:-docs/openapi/admin-api.json}"
DOCS_URL="${BASE_URL}/v3/api-docs"

mkdir -p "$(dirname "${ROOT}/${OUTPUT}")"
echo "Fetching OpenAPI from ${DOCS_URL} ..."

if ! curl -fsSL "${DOCS_URL}" -o "${ROOT}/${OUTPUT}.tmp"; then
  echo "ERROR: 无法获取 OpenAPI。请先启动 Admin（local profile）。" >&2
  exit 1
fi

python3 -m json.tool "${ROOT}/${OUTPUT}.tmp" > "${ROOT}/${OUTPUT}" 2>/dev/null \
  || mv "${ROOT}/${OUTPUT}.tmp" "${ROOT}/${OUTPUT}"
rm -f "${ROOT}/${OUTPUT}.tmp"

echo "Written: ${OUTPUT}"
echo "Swagger UI: ${BASE_URL}/swagger-ui.html"

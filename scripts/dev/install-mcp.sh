#!/usr/bin/env bash
# 构建 zestflow-mcp 并安装到 ~/.zestflow/tools/（平台级，全项目共用）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS_DIR="${HOME}/.zestflow/tools"
SKIP_BUILD=false
SET_USER_ENV=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build) SKIP_BUILD=true; shift ;;
    --set-user-env) SET_USER_ENV=true; shift ;;
    *) echo "Unknown arg: $1"; exit 1 ;;
  esac
done

read_version() {
  VER=$(sed -n 's:.*<zestflow-mcp.version>\([^<]*\)</zestflow-mcp.version>.*:\1:p' "$ROOT/pom.xml" | head -1)
  if [[ -z "$VER" ]]; then
    VER=$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' "$ROOT/pom.xml" | head -1)
  fi
  echo "$VER"
}

VER="$(read_version)"
JAR_NAME="zestflow-mcp-${VER}-all.jar"
SRC_JAR="$ROOT/zestflow-mcp/target/$JAR_NAME"
STABLE_JAR="$TOOLS_DIR/zestflow-mcp.jar"

if [[ "$SKIP_BUILD" != true ]]; then
  echo "--- Build zestflow-mcp (profile dev-mcp) ---"
  (cd "$ROOT" && mvn -Pdev-mcp -pl zestflow-mcp package -DskipTests)
fi

[[ -f "$SRC_JAR" ]] || { echo "Jar not found: $SRC_JAR"; exit 1; }

mkdir -p "$TOOLS_DIR"
cp -f "$SRC_JAR" "$TOOLS_DIR/$JAR_NAME"
cp -f "$SRC_JAR" "$STABLE_JAR"
echo "$VER" > "$TOOLS_DIR/zestflow-mcp.version"

if [[ "$SET_USER_ENV" == true ]]; then
  echo "export ZESTFLOW_MCP_JAR=$STABLE_JAR" >> "${HOME}/.zestflow/tools/env.sh"
  echo "[OK] Append ZESTFLOW_MCP_JAR to ~/.zestflow/tools/env.sh (source it in your shell)"
fi

echo "[OK] Installed $STABLE_JAR ($VER)"
echo "Copy scripts/dev/mcp/project.cursor.mcp.json.example to your-project/.cursor/mcp.json"

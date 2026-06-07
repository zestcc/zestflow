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

REQUIRED_TEMPLATES=(
  "META-INF/zestflow/dev-templates/rules/architecture.md.template"
  "META-INF/zestflow/dev-templates/rules/project.md.template"
  "META-INF/zestflow/dev-templates/ide/cursor-rules.md.template"
  "META-INF/zestflow/dev-templates/ide/copilot-instructions.md.template"
  "META-INF/zestflow/dev-templates/ide/claude.md.template"
)

assert_mcp_jar_dev_templates() {
  local jar_path="$1"
  [[ -f "$jar_path" ]] || { echo "JAR not found: $jar_path"; exit 1; }
  local listing missing=()
  listing="$(jar tf "$jar_path")" || { echo "Cannot read JAR: $jar_path"; exit 1; }
  for entry in "${REQUIRED_TEMPLATES[@]}"; do
    if ! echo "$listing" | grep -Fxq "$entry"; then
      missing+=("$entry")
    fi
  done
  if ((${#missing[@]} > 0)); then
    echo "MCP JAR outdated or incomplete; missing --init-dev templates:"
    printf '  - %s\n' "${missing[@]}"
    echo ""
    echo "Rebuild from zestflow repo root:"
    echo "  cd $ROOT && mvn -pl zestflow-mcp -am package -DskipTests"
    echo "  bash scripts/dev/install-mcp.sh"
    echo ""
    echo "JAR: $jar_path"
    exit 1
  fi
}

if [[ "$SKIP_BUILD" != true ]]; then
  echo "--- Build zestflow-mcp ---"
  (cd "$ROOT" && mvn -pl zestflow-mcp -am package -DskipTests)
fi

[[ -f "$SRC_JAR" ]] || { echo "Jar not found: $SRC_JAR"; exit 1; }

assert_mcp_jar_dev_templates "$SRC_JAR"

mkdir -p "$TOOLS_DIR"
cp -f "$SRC_JAR" "$TOOLS_DIR/$JAR_NAME"
cp -f "$SRC_JAR" "$STABLE_JAR"
echo "$VER" > "$TOOLS_DIR/zestflow-mcp.version"
assert_mcp_jar_dev_templates "$STABLE_JAR"
echo "[OK] dev-templates verified (architecture + cross-IDE rules)"

DEV_INIT_SRC="$ROOT/zestflow-dev-init/target/zestflow-dev-init-0.1.0-all.jar"
DEV_INIT_STABLE="$TOOLS_DIR/zestflow-dev-init.jar"
[[ -f "$DEV_INIT_SRC" ]] || { echo "Dev-init JAR not found: $DEV_INIT_SRC"; exit 1; }
cp -f "$DEV_INIT_SRC" "$DEV_INIT_STABLE"
echo "[OK] Installed dev-init CLI (Java 8+): $DEV_INIT_STABLE"

if [[ "$SET_USER_ENV" == true ]]; then
  echo "export ZESTFLOW_MCP_JAR=$STABLE_JAR" >> "${HOME}/.zestflow/tools/env.sh"
  echo "[OK] Append ZESTFLOW_MCP_JAR to ~/.zestflow/tools/env.sh (source it in your shell)"
fi

echo "[OK] Installed $STABLE_JAR ($VER)"
echo "Copy scripts/dev/mcp/project.cursor.mcp.json.example to your-project/.cursor/mcp.json"

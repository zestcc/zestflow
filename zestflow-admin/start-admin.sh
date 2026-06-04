#!/bin/bash
# ==================================
# ZestFlow Admin — 启动脚本
# 可调参数见 config/start-admin.env
#
#   ./start-admin.sh
#   ./config/application.yml
#   ./config/start-admin.env
#   ./zestflow-admin-0.1.0.jar   （多版本时取版本号最大）
#   ./log/
# ==================================

# Windows 上传的脚本若含 CRLF，自动去 \r 后重新执行
if [ -z "${__ZF_ADMIN_NOCR:-}" ] && grep -q $'\r' "${BASH_SOURCE[0]}" 2>/dev/null; then
  export __ZF_ADMIN_NOCR=1
  exec bash <(sed 's/\r$//' "${BASH_SOURCE[0]}") "$@"
fi

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="$SCRIPT_DIR/config"
CONFIG_FILE="$CONFIG_DIR/application.yml"
ENV_FILE="$CONFIG_DIR/start-admin.env"
LOG_DIR="$SCRIPT_DIR/log"

APP_NAME="zestflow-admin"
APP_LOG_FILE="$LOG_DIR/${APP_NAME}.log"

# ==================================
# 加载配置（config/start-admin.env）
# ==================================
_load_config() {
  JVM_XMS=1g
  JVM_XMX=1g
  JVM_GC=zgc
  JVM_METASPACE=256m
  JVM_DIRECT_MEMORY=256m
  JVM_ZGC_CONC_THREADS=2
  JVM_ZGC_INTERVAL=5
  JVM_G1_MAX_PAUSE_MS=200
  JVM_EXTRA_OPTS=""
  STOP_TIMEOUT_SEC=15
  HEALTH_CHECK_RETRIES=30
  HEALTH_CHECK_INTERVAL_SEC=2
  APP_TIMEZONE=Asia/Shanghai

  if [ -f "$ENV_FILE" ]; then
    set -a
    # 兼容 Windows 编辑产生的 CRLF 换行
    if grep -q $'\r' "$ENV_FILE" 2>/dev/null; then
      # shellcheck disable=SC1090
      source <(sed 's/\r$//' "$ENV_FILE")
    else
      # shellcheck disable=SC1090
      source "$ENV_FILE"
    fi
    set +a
  fi

  _resolve_java
}

_resolve_java() {
  # 1. env 中显式指定 JAVA_BIN
  if [ -n "${JAVA_BIN:-}" ] && [ -x "$JAVA_BIN" ]; then
    [ -z "${JAVA_HOME:-}" ] && JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    return 0
  fi

  # 2. env 或环境中的 JAVA_HOME
  if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    JAVA_BIN="${JAVA_HOME}/bin/java"
    return 0
  fi

  # 3. PATH 中的 java
  if command -v java &>/dev/null; then
    JAVA_BIN="$(command -v java)"
    [ -z "${JAVA_HOME:-}" ] && JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    return 0
  fi

  # 4. RHEL/CentOS alternatives
  if [ -e /etc/alternatives/java ]; then
    local alt
    alt="$(readlink -f /etc/alternatives/java 2>/dev/null || true)"
    if [ -n "$alt" ] && [ -x "$alt" ]; then
      JAVA_BIN="$alt"
      JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
      return 0
    fi
  fi

  # 5. 扫描常见 JDK 安装目录（取版本号最大的 java）
  local java_path best=""
  while IFS= read -r java_path; do
    [ -x "$java_path" ] || continue
    best="$java_path"
  done < <(find /usr/lib/jvm /usr/java /usr/local/java /opt/java /opt/jdk \
    -maxdepth 3 -type f -path '*/bin/java' 2>/dev/null | sort -V)

  if [ -n "$best" ]; then
    JAVA_BIN="$best"
    JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    return 0
  fi

  JAVA_BIN="java"
  return 1
}

_build_jvm_opts() {
  local -a opts=(
    -Xms"${JVM_XMS}"
    -Xmx"${JVM_XMX}"
    -XX:+AlwaysPreTouch
    -XX:MaxMetaspaceSize="${JVM_METASPACE}"
    -XX:MaxDirectMemorySize="${JVM_DIRECT_MEMORY}"
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath="${LOG_DIR}/heap_dump.hprof"
    -XX:+ExitOnOutOfMemoryError
    -Xlog:gc*=info:file="${LOG_DIR}/gc.log":time,level,tags:filecount=5,filesize=20M
    -Dspring.application.name="${APP_NAME}"
    -Dfile.encoding=UTF-8
    -Duser.timezone="${APP_TIMEZONE}"
    -Djava.security.egd=file:/dev/./urandom
  )

  case "$(echo "$JVM_GC" | tr '[:upper:]' '[:lower:]')" in
    g1)
      opts+=(
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis="${JVM_G1_MAX_PAUSE_MS}"
      )
      ;;
    *)
      opts+=(
        -XX:+UseZGC
        -XX:ConcGCThreads="${JVM_ZGC_CONC_THREADS}"
        -XX:ZCollectionInterval="${JVM_ZGC_INTERVAL}"
      )
      ;;
  esac

  if [ -n "${JVM_EXTRA_OPTS:-}" ]; then
    # shellcheck disable=SC2206
    opts+=(${JVM_EXTRA_OPTS})
  fi

  _JVM_OPTS=("${opts[@]}")
}

# ==================================
# 工具
# ==================================
_get_time() { date '+%Y-%m-%d %H:%M:%S'; }
_log()      { echo "[$(_get_time)] $*"; }

_get_pid() {
  pgrep -f "${SCRIPT_DIR}/${APP_NAME}-" 2>/dev/null || true
}

_running_jar() {
  local pid=$(_get_pid)
  [ -z "$pid" ] && return 0
  ps -p "$pid" -o args= 2>/dev/null | grep -oE "${APP_NAME}-[^ ]+\.jar" | head -n 1 || true
}

_jar_version() {
  basename "$1" .jar | sed "s/^${APP_NAME}-//"
}

_health_check() {
  local port=$1
  [ -z "$port" ] && return 1
  local i=1
  while [ "$i" -le "${HEALTH_CHECK_RETRIES}" ]; do
    curl -sf -o /dev/null "http://127.0.0.1:${port}/actuator/health/liveness" 2>/dev/null && return 0
    curl -sf -o /dev/null "http://127.0.0.1:${port}/actuator/health" 2>/dev/null && return 0
    sleep "${HEALTH_CHECK_INTERVAL_SEC}"
    i=$((i + 1))
  done
  return 1
}

_graceful_stop() {
  local pid=$1
  _log "SIGTERM → PID=$pid"
  kill "$pid" 2>/dev/null || return 0
  local i=1
  while [ "$i" -le "${STOP_TIMEOUT_SEC}" ]; do
    kill -0 "$pid" 2>/dev/null || { _log "已退出"; return 0; }
    sleep 1
    i=$((i + 1))
  done
  _log "超时 SIGKILL → PID=$pid"
  kill -9 "$pid" 2>/dev/null || true
  sleep 1
}

# ==================================
# 同目录下取版本号最大的 jar
# ==================================
_find_jar() {
  local jar
  jar=$(find "$SCRIPT_DIR" -maxdepth 1 -type f -name "${APP_NAME}-*.jar" 2>/dev/null \
    | sort -V | tail -n 1)
  [ ! -f "$jar" ] && { _log "[FAIL] 未找到 ${APP_NAME}-*.jar（请放在脚本同目录）"; exit 1; }
  echo "$jar"
}

_read_server_port() {
  if [ -n "${SERVER_PORT:-}" ]; then
    echo "$SERVER_PORT"
    return 0
  fi
  if [ -f "$CONFIG_FILE" ]; then
    local port
    port=$(awk '
      /^server:/ { in_server=1; next }
      in_server && /^[^[:space:]]/ { in_server=0 }
      in_server && /^[[:space:]]+port:[[:space:]]*/ {
        gsub(/[^0-9]/, "", $2); print $2; exit
      }
    ' "$CONFIG_FILE")
    [ -n "$port" ] && echo "$port" && return 0
  fi
  echo "8080"
}

# ==================================
# 启动前检测
# ==================================
_preflight() {
  local err=0

  _log ">>> 启动前检测 <<<"

  if [ -x "$JAVA_BIN" ]; then
    _log "  [OK] ${JAVA_BIN}"
    _log "       $("$JAVA_BIN" -version 2>&1 | head -1)"
    [ -n "${JAVA_HOME:-}" ] && _log "       JAVA_HOME=${JAVA_HOME}"
  else
    _log "  [FAIL] 未找到 java"
    _log "         1) 在 config/start-admin.env 设置："
    _log "            JAVA_HOME=/usr/lib/jvm/java-17-openjdk"
    _log "         2) 或安装 JDK 17+：yum install java-17-openjdk"
    _log "         3) 查找路径：find /usr/lib/jvm -name java 2>/dev/null"
    err=1
  fi

  [ -f "$ENV_FILE" ] \
    && _log "  [OK] 启动配置: $ENV_FILE" \
    || _log "  [WARN] 未找到 $ENV_FILE，使用内置默认值"

  [ -f "$CONFIG_FILE" ] \
    && _log "  [OK] 应用配置: $CONFIG_FILE" \
    || { _log "  [FAIL] 缺少: $CONFIG_FILE"; err=1; }

  local jar=$(_find_jar)
  _log "  [OK] jar: $(basename "$jar") (v$(_jar_version "$jar"))"
  _log "  [OK] JVM: -Xms${JVM_XMS} -Xmx${JVM_XMX} GC=${JVM_GC}"

  mkdir -p "$LOG_DIR"
  _log "  [OK] 日志: $LOG_DIR"

  local port=$(_read_server_port)
  if command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | grep -q ":${port} " \
      && _log "  [WARN] 端口 $port 被占用" \
      || _log "  [OK] 端口 $port 可用"
  elif command -v netstat &>/dev/null; then
    netstat -tlnp 2>/dev/null | grep -q ":${port} " \
      && _log "  [WARN] 端口 $port 被占用" \
      || _log "  [OK] 端口 $port 可用"
  fi

  _log ">>> 检测完成 <<<"
  echo ""
  if [ "$err" -eq 1 ]; then
    return 1
  fi
}

# ==================================
# start
# ==================================
start() {
  _load_config
  _build_jvm_opts

  local old_pid=$(_get_pid)
  [ -n "$old_pid" ] && { _log "停旧进程 PID=$old_pid"; _graceful_stop "$old_pid"; }

  _preflight || exit 1

  local jar
  jar=$(_find_jar)
  local version=$(_jar_version "$jar")
  local port=$(_read_server_port)
  local -a app_args=(
    --logging.file.path="$LOG_DIR"
    --spring.config.additional-location="file:${CONFIG_DIR}/"
  )

  [ -n "${SPRING_PROFILE:-}" ] && app_args+=(--spring.profiles.active="$SPRING_PROFILE")
  [ -n "${SERVER_PORT:-}" ] && app_args+=(--server.port="$SERVER_PORT")

  _log ">>> 启动 v${version}  port=${port}  jar=$(basename "$jar") <<<"
  echo ""

  touch "$LOG_DIR/stdout.log"

  nohup "$JAVA_BIN" "${_JVM_OPTS[@]}" -jar "$jar" "${app_args[@]}" \
    >> "$LOG_DIR/stdout.log" 2>&1 &
  local java_pid=$!

  sleep 1
  if ! kill -0 "$java_pid" 2>/dev/null; then
    echo ""
    _log ">>> [FAIL] Java 进程启动后立即退出 <<<"
    tail -50 "$LOG_DIR/stdout.log" 2>/dev/null || true
    exit 1
  fi

  tail -F "$LOG_DIR/stdout.log" &
  local tail_pid=$!

  if _health_check "$port"; then
    kill "$tail_pid" 2>/dev/null || true
    wait "$tail_pid" 2>/dev/null || true
    local new_pid=$(_get_pid)
    echo ""
    _log ">>> 启动成功  PID=$new_pid  v${version}  http://127.0.0.1:${port} <<<"

    case "${1:-}" in
      -l|-log) shift; logs "$@"; return 0 ;;
    esac
  else
    kill "$tail_pid" 2>/dev/null || true
    wait "$tail_pid" 2>/dev/null || true
    echo ""
    _log ">>> 启动超时，最近日志 <<<"
    tail -30 "$LOG_DIR/stdout.log" 2>/dev/null || true
    exit 1
  fi
}

# ==================================
# stop
# ==================================
stop() {
  _load_config
  local pid=$(_get_pid)
  [ -z "$pid" ] && { _log "未运行"; return 0; }
  _graceful_stop "$pid"
}

# ==================================
# status
# ==================================
status() {
  _load_config
  local pid=$(_get_pid)
  [ -z "$pid" ] && { _log "已停止"; return 1; }

  local port=$(_read_server_port)
  local running_jar=$(_running_jar)
  local latest_jar=$(_find_jar 2>/dev/null || true)

  _log "运行中  PID=$pid  port=$port"
  [ -n "$running_jar" ] && _log "运行 jar: $running_jar"
  if [ -n "$latest_jar" ] && [ "$(basename "$latest_jar")" != "$running_jar" ]; then
    _log "[WARN] 目录最新 jar 为 $(basename "$latest_jar")，restart 后才会切换"
  fi
  echo ""
  echo "--- JVM ---"
  echo "  堆: ${JVM_XMS} / ${JVM_XMX}  GC: ${JVM_GC}"
  echo ""
  echo "--- 内存 ---"
  free -h 2>/dev/null || true
  echo ""
  echo "--- JVM 堆使用率 ---"
  jstat -gcutil "$pid" 2>/dev/null | tail -1 | awk '{printf "Eden:%s%% Old:%s%% Meta:%s%% YGC:%s CGC:%s\n",$3,$4,$5,$7,$9}' || echo "  jstat 不可用"
  echo ""
  echo "--- 端口 ---"
  if command -v ss &>/dev/null; then
    ss -tlnp 2>/dev/null | grep ":$port" || echo "  未监听"
  else
    netstat -tlnp 2>/dev/null | grep ":$port" || echo "  未监听"
  fi
  echo ""
  echo "--- 健康检查 ---"
  curl -sf "http://127.0.0.1:${port}/actuator/health" 2>/dev/null || echo "  不可达"
}

# ==================================
# logs
# ==================================
logs() {
  local target="$APP_LOG_FILE"
  case "${1:-}" in
    -s|--stdout) target="$LOG_DIR/stdout.log" ;;
    -e|--error)  target="$APP_LOG_FILE" ;;
  esac

  [ ! -f "$target" ] && { _log "不存在: $target"; return 1; }

  case "${1:-}" in
    -e|--error)
      _log "tail -f $target (ERROR/WARN)"
      tail -f "$target" | grep --line-buffered -E 'ERROR|WARN'
      ;;
    *)
      _log "tail -f $target"
      tail -f "$target"
      ;;
  esac
}

# ==================================
# version / config
# ==================================
version() {
  local jar=$(_find_jar)
  _log "$(basename "$jar")  v$(_jar_version "$jar")"
}

show_config() {
  _load_config
  _build_jvm_opts
  echo "ENV_FILE=$ENV_FILE"
  echo "CONFIG_FILE=$CONFIG_FILE"
  echo "JAVA_BIN=$JAVA_BIN"
  echo "JAVA_HOME=${JAVA_HOME:-<未设置>}"
  echo "JVM_XMS=$JVM_XMS JVM_XMX=$JVM_XMX JVM_GC=$JVM_GC"
  echo "JVM_METASPACE=$JVM_METASPACE JVM_DIRECT_MEMORY=$JVM_DIRECT_MEMORY"
  echo "STOP_TIMEOUT_SEC=$STOP_TIMEOUT_SEC"
  echo "HEALTH_CHECK_RETRIES=$HEALTH_CHECK_RETRIES HEALTH_CHECK_INTERVAL_SEC=$HEALTH_CHECK_INTERVAL_SEC"
  echo "SPRING_PROFILE=${SPRING_PROFILE:-<未设置>}"
  echo "SERVER_PORT=${SERVER_PORT:-<读 application.yml>}"
  echo "JVM_OPTS=${_JVM_OPTS[*]}"
}

# ==================================
# 入口
# ==================================
_load_config

case "${1:-}" in
  start)   start "${@:2}" ;;
  stop)    stop ;;
  restart) stop; start "${@:2}" ;;
  status)  status ;;
  version) version ;;
  config)  show_config ;;
  -l|-log) logs "${@:2}" ;;
  *)
    cat << EOF
${APP_NAME} {start|stop|restart|status|version|config|-l}

目录（相对脚本位置）:
  ./start-admin.sh
  ./config/application.yml       应用配置
  ./config/start-admin.env       启动/JVM 配置（改这里）
  ./${APP_NAME}-<version>.jar    多版本时自动取最大版本号
  ./log/

  start          启动
  start -l       启动后继续 tail 业务日志
  start -l -s    启动后继续 tail stdout.log
  stop           优雅停止
  restart        重启
  status         状态（含运行 jar 与最新 jar 对比）
  version        将使用的 jar 版本
  config         打印当前生效的启动配置
  -l             tail 业务日志
  -l -s          tail stdout.log
  -l -e          tail ERROR/WARN

JVM 默认（config/start-admin.env）:
  堆 1g/1g + ZGC，适合 8G 独占 Admin
  4G 同机 MySQL 建议改为 JVM_XMS=512m JVM_XMX=768m
  兼容性问题可将 JVM_GC 改为 g1
EOF
    ;;
esac

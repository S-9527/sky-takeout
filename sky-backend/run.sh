#!/usr/bin/env bash
# 苍穹外卖后端一键启动脚本
# 用法: ./run.sh start|stop|restart|status
#
# 本地开发默认即 mock 支付 + 本地文件存储(无需阿里云/微信商户账号),
# 生产环境通过环境变量或命令行覆盖:
#   PAY_GATEWAY=wechat OSS_STORAGE=aliyun ./run.sh start
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/sky-server/target/sky-server-1.0-SNAPSHOT.jar"
LOG=/tmp/sky-server.log
PID_FILE=/tmp/sky-server.pid
APP_NAME="sky-server-1.0-SNAPSHOT.jar"

log()  { echo "[run.sh] $*"; }
fail() { log "ERROR: $*"; exit 1; }

# 依赖进程:默认从配置读取(application-dev.yml),可经环境变量覆盖
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
DB_NAME="${DB_NAME:-sky_take_out}"
# 校验用账号密码,仅作用在本次调用的子命令,不导出到应用进程
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PWD="${MYSQL_PASSWORD:-root}"

tcp_ok() { (exec 3<>"/dev/tcp/$1/$2") 2>/dev/null && { exec 3>&- 3<&-; return 0; } || return 1; }

check_dependencies() {
  if ! command -v java >/dev/null 2>&1; then
    fail "未找到 java,请先安装 JDK 17+"
  fi
  if ! tcp_ok "$MYSQL_HOST" "$MYSQL_PORT"; then
    fail "MySQL 不可达($MYSQL_HOST:$MYSQL_PORT),请先启动数据库"
  fi
  if ! tcp_ok "$REDIS_HOST" "$REDIS_PORT"; then
    fail "Redis 不可达($REDIS_HOST:$REDIS_PORT),请先启动 Redis"
  fi
  # 装了 mysql 客户端时进一步校验账号与库是否存在,否则仅做端口连通性检查
  if command -v mysqladmin >/dev/null 2>&1; then
    MYSQL_PWD="$MYSQL_PWD" mysqladmin --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" ping >/dev/null 2>&1 \
      || fail "MySQL 账号 $MYSQL_USER 无法连接(可设 MYSQL_PASSWORD 指定密码)"
    if command -v mysql >/dev/null 2>&1; then
      MYSQL_PWD="$MYSQL_PWD" mysql --host="$MYSQL_HOST" --port="$MYSQL_PORT" --user="$MYSQL_USER" \
        -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DB_NAME';" \
        | grep -q "$DB_NAME" || fail "数据库 $DB_NAME 不存在,请先导入初始化脚本"
    fi
  else
    log "未安装 mysql 客户端,跳过账号/库名校验(仅做端口连通性检查)"
  fi
}

start() {
  check_dependencies
  [ -f "$JAR" ] || fail "未找到 Jar 包,请先执行: mvn -q package -DskipTests"
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    log "服务已在运行 (PID $(cat "$PID_FILE"))"
    return 0
  fi
  mkdir -p "$SCRIPT_DIR/data/upload"
  nohup java -Xmx512m -jar "$JAR" > "$LOG" 2>&1 &
  echo $! > "$PID_FILE"
  log "启动中... PID $(cat "$PID_FILE"),日志: $LOG"
  for _ in $(seq 1 60); do
    if grep -qE "Started Sky[A-Za-z]*Application" "$LOG" 2>/dev/null; then
      log "启动成功,端口 8080"
      return 0
    fi
    if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
      fail "启动失败,查看日志: tail -100 $LOG"
    fi
    sleep 1
  done
  fail "启动超时,查看日志: tail -100 $LOG"
}

stop() {
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    kill "$(cat "$PID_FILE")"
    log "已发送停止信号 (PID $(cat "$PID_FILE"))"
    rm -f "$PID_FILE"
  else
    PIDS="$(pgrep -f "$APP_NAME" || true)"
    if [ -n "$PIDS" ]; then
      kill $PIDS
      log "已停止遗留进程: $PIDS"
    else
      log "服务未在运行"
    fi
  fi
}

status() {
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    log "运行中 (PID $(cat "$PID_FILE"))"
  elif pgrep -f "$APP_NAME" >/dev/null 2>&1; then
    log "运行中(无 PID 文件) PID $(pgrep -f "$APP_NAME" | tr '\n' ' ')"
  else
    log "未运行"
  fi
}

case "${1:-}" in
  start)   start ;;
  stop)    stop ;;
  restart) stop; sleep 1; start ;;
  status)  status ;;
  *) echo "用法: $0 {start|stop|restart|status}"; exit 1 ;;
esac
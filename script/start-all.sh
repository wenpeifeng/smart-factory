#!/bin/bash
# Smart Factory 套件一键启动（重启语义）：先确保 Docker 可用，再停止旧进程，
# 按 认证中心 -> ERP -> MES 顺序拉起。
# 各系统独立部署，本脚本负责：环境预检 -> 自动启动 Docker Desktop -> 统一停止
# -> 依次调起各项目启动脚本 -> 最后健康检查汇总。
# 基础设施容器由各项目 env-init.sh 管理（运行中跳过/已停止 docker start/不存在才创建），停止脚本默认保留数据库容器。
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 项目名|显示名|前端端口|后端端口
PROJECTS=("sf-auth|认证中心|5174|8082" "sf-erp|ERP 系统|5172|8080" "sf-mes|MES 系统|5173|8081")

log()  { echo "[SUITE] $1"; }
die()  { echo "[SUITE][错误] $1" >&2; exit 1; }

# ---------- 0. 环境预检：自动启动 Docker Desktop ----------
ensure_docker() {
  command -v docker >/dev/null 2>&1 \
    || die "未安装 Docker，请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop/"

  if docker info >/dev/null 2>&1; then
    log "Docker daemon 已就绪"
    return 0
  fi

  case "$(uname -s)" in
    Darwin)
      if ! open -Ra "Docker" >/dev/null 2>&1; then
        die "已找到 docker 命令，但未找到 Docker Desktop 应用"
      fi
      log "Docker daemon 未运行，正在启动 Docker Desktop..."
      open -a "Docker" >/dev/null 2>&1 \
        || die "无法启动 Docker Desktop，请手动打开后重试"
      ;;
    Linux)
      log "Docker daemon 未运行，尝试启动本机 Docker 服务..."
      if command -v systemctl >/dev/null 2>&1; then
        systemctl --user start docker-desktop >/dev/null 2>&1 \
          || systemctl start docker >/dev/null 2>&1 \
          || true
      fi
      ;;
    *)
      die "Docker daemon 未运行，且当前系统不支持自动启动，请手动启动 Docker"
      ;;
  esac

  # Docker Desktop 首次启动或升级后可能需要较长时间；最多等待 120 秒。
  for i in $(seq 1 60); do
    if docker info >/dev/null 2>&1; then
      log "Docker daemon 已就绪（等待 $((i * 2)) 秒）"
      return 0
    fi
    if [ $((i % 5)) -eq 0 ]; then
      log "等待 Docker daemon 就绪... $((i * 2))/120 秒"
    fi
    sleep 2
  done

  docker info 2>&1 | tail -8 >&2 || true
  die "Docker Desktop 已启动，但 daemon 在 120 秒内未就绪"
}

ensure_docker

# ---------- 1. 重启语义：环境可用后再停止旧的应用进程（数据库容器保留） ----------
if [ -f "$ROOT/script/stop-all.sh" ]; then
  echo "==== 一键启动 = 重启：先停止旧进程 ===="
  bash "$ROOT/script/stop-all.sh" || echo "[警告] 停止阶段有异常，继续启动"
  echo ""
fi

# ---------- 2. 依次启动三套系统 ----------
for entry in "${PROJECTS[@]}"; do
  p="${entry%%|*}"
  rest="${entry#*|}"
  dir="$ROOT/$p"
  echo "==== 启动 $p ===="
  if [ ! -d "$dir" ]; then
    echo "[跳过] $dir 不存在"
    continue
  fi

  # 应用启动脚本（各项目自带，内部含环境初始化与基础设施管理）
  if [ -f "$dir/script/sh/start-all.sh" ]; then
    (cd "$dir" && bash script/sh/start-all.sh) || echo "[警告] $p 应用启动失败，继续"
  else
    # 兜底（内层脚本缺失时才碰基础设施）：
    #   运行中 -> 跳过；已停止 -> docker start；不存在 -> docker compose 创建
    prefix="${p#sf-}"   # auth / erp / mes
    if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${prefix}-"; then
      echo "[基础设施] ${prefix}-mysql / ${prefix}-redis 已在运行，跳过"
    elif docker ps -a --format '{{.Names}}' 2>/dev/null | grep -q "^${prefix}-"; then
      echo "[基础设施] 启动已停止的 ${prefix}- 容器..."
      docker start "${prefix}-mysql" "${prefix}-redis" 2>/dev/null \
        || echo "[警告] $p 基础设施启动失败，继续"
    elif [ -f "$dir/docker-compose.yml" ]; then
      (cd "$dir" && docker compose up -d) || echo "[警告] $p 基础设施启动失败，继续"
    fi
    echo "[提示] $p 暂无 script/sh/start-all.sh，仅启动了基础设施"
  fi
done

# ---------- 3. 首次启动身份数据收敛 ----------
# 用户种子只存在 sf-auth（唯一事实源），ERP/MES 只保存可重建的授权副本。
# 子系统 ApplicationReady 会自动同步；套件启动脚本在三端就绪后再触发一次，
# 保证全新 clone + 空数据库的首次启动返回前已经能看到全部分配人员。
IDENTITY_SYNC_TOKEN="${IDENTITY_SYNC_TOKEN:-identity-sync-token}"
converge_identity() {
  local name="$1"
  local url="$2"
  if curl -fsS --max-time 20 -X POST \
      -H "X-Identity-Sync-Token: ${IDENTITY_SYNC_TOKEN}" \
      "${url}?source=GROUP&reason=SUITE_BOOTSTRAP" >/dev/null; then
    log "${name} 认证中心人员副本已收敛"
  else
    echo "[警告] ${name} 首次人员收敛未完成，后台每分钟会自动重试" >&2
  fi
}

converge_identity "ERP" "http://localhost:8080/api/auth/sso/internal/sync"
converge_identity "MES" "http://localhost:8081/api/integration/auth/internal/sync"

# ---------- 最终健康检查汇总 ----------
wait_http() {
  curl -sf -o /dev/null --max-time 2 "$1"
}

# ERP/MES 的内部同步端点用错误令牌探活：401 表示应用、Security 与控制器均已就绪，
# 且不会触发任何数据库写入或全量对账。
wait_sync_api() {
  local url="$1"
  local status
  status="$(curl -s -o /dev/null --max-time 2 -w '%{http_code}' -X POST \
    -H 'X-Identity-Sync-Token: __suite_health_probe__' "$url")"
  [ "$status" = "401" ]
}

echo ""
echo "======================================"
echo "            启动结果汇总"
echo "======================================"
declare -a FIELDS
FAIL=0
for entry in "${PROJECTS[@]}"; do
  IFS='|' read -r p disp_name web_port api_port <<< "$entry" || true

  web_ok="✗ 未就绪"
  api_ok="✗ 未就绪"
  wait_http "http://localhost:${web_port}" && web_ok="✓"
  case "$p" in
    sf-auth)
      wait_http "http://localhost:${api_port}/api/.well-known/openid-configuration" && api_ok="✓"
      ;;
    sf-erp)
      wait_sync_api "http://localhost:${api_port}/api/auth/sso/internal/sync" && api_ok="✓"
      ;;
    sf-mes)
      wait_sync_api "http://localhost:${api_port}/api/integration/auth/internal/sync" && api_ok="✓"
      ;;
  esac

  if [ "$web_ok" = "✓" ] && [ "$api_ok" = "✓" ]; then
    status="✅ 全部就绪"
  else
    status="⚠️  有异常"
    FAIL=1
  fi
  echo "$status  $p $disp_name"
  echo "         前端页面: http://localhost:${web_port}   [${web_ok}]"
  echo "         后端 API: http://localhost:${api_port}/api   [${api_ok}]"
done
echo "======================================"
if [ "$FAIL" -eq 0 ]; then
  echo "全部启动成功 🎉  演示账号见套件 README.md"
else
  echo "部分服务异常，可查看各项目 script/logs/ 下的日志排查"
fi

exit "$FAIL"

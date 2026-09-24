#!/bin/bash
# Smart Factory 套件一键启动（重启语义）：先一键停止旧进程，再按 认证中心 -> ERP -> MES 顺序拉起
# 各系统独立部署，本脚本负责：统一停止 -> 依次调起各项目启动脚本 -> 最后健康检查汇总。
# 基础设施容器由各项目 env-init.sh 管理（运行中跳过/已停止 docker start/不存在才创建），停止脚本默认保留数据库容器。
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# 项目名|显示名|前端端口|后端端口
PROJECTS=("sf-auth|认证中心|5174|8082" "sf-erp|ERP 系统|5172|8080" "sf-mes|MES 系统|5173|8081")

# ---------- 0. 重启语义：先一键停止旧的应用进程（数据库容器保留） ----------
if [ -f "$ROOT/script/stop-all.sh" ]; then
  echo "==== 一键启动 = 重启：先停止旧进程 ===="
  bash "$ROOT/script/stop-all.sh" || echo "[警告] 停止阶段有异常，继续启动"
  echo ""
fi

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

# ---------- 最终健康检查汇总 ----------
wait_http() {
  curl -sf -o /dev/null --max-time 2 "$1"
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
  if wait_http "http://localhost:${api_port}/api" \
     || wait_http "http://localhost:${api_port}/api/ping" \
     || wait_http "http://localhost:${api_port}/api/actuator/health"; then
    api_ok="✓"
  fi

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

#!/bin/bash
# Smart Factory 套件一键停止
# 停止各系统应用进程（后端 + 前端 dev server）；数据库容器默认保留。
# 说明：基础设施容器是各项目 env-init.sh 用 docker run 创建的，
#       不能用 docker compose down（同名冲突且可能误删 volume）。
#       如需连数据库一起停：./script/stop-all.sh --with-db
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

WITH_DB=0
[ "${1:-}" == "--with-db" ] && WITH_DB=1

# 项目名|前端端口|后端端口|容器前缀
PROJECTS=("sf-mes|MES|5173|8081|mes" "sf-erp|ERP|5172|8080|erp" "sf-auth|认证中心|5174|8082|auth")

for entry in "${PROJECTS[@]}"; do
  IFS='|' read -r p disp web_port api_port prefix <<< "$entry" || true
  [ -n "$p" ] || continue
  echo "==== 停止 ${p}（${disp}） ===="

  # 优先用各项目自带的停止脚本
  if [ -f "$ROOT/$p/script/sh/stop-all.sh" ]; then
    if [ "$WITH_DB" -eq 1 ]; then
      (cd "$ROOT/$p" && bash script/sh/stop-all.sh --with-db) || true
    else
      (cd "$ROOT/$p" && bash script/sh/stop-all.sh) || true
    fi
    continue
  fi

  # 无自带脚本（如 sf-auth）：按端口停止后端与前端
  lsof -t -i :"${api_port}" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null \
    && echo "后端 (${api_port}) 已停止" || echo "后端 (${api_port}) 未在运行"
  lsof -t -i :"${web_port}" -sTCP:LISTEN 2>/dev/null | xargs kill 2>/dev/null \
    && echo "前端 (${web_port}) 已停止" || echo "前端 (${web_port}) 未在运行"

  # 可选：连数据库容器一起停（docker stop，数据保留在 volume）
  if [ "$WITH_DB" -eq 1 ]; then
    docker stop "${prefix}-mysql" "${prefix}-redis" >/dev/null 2>&1 \
      && echo "${prefix}-mysql / ${prefix}-redis 已停止（数据保留）" || true
  fi
done

echo ""
echo "==== 全部停止 ✓（数据库容器仍在运行，如需停止: ./script/stop-all.sh --with-db） ===="

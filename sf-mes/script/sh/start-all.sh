#!/bin/bash
# MES 项目一键启动脚本
# 流程：环境检查 -> 数据库容器 -> 后端(编译+后台启动) -> 前端(dev server)
# 用法：./script/sh/start-all.sh  [--backend-only | --web-only]

set -e
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${PROJECT_ROOT}"
mkdir -p script/logs

# 环境自包含：脚本内直接定位 JDK21 与 Maven（不依赖用户 shell 的 PATH 配置）
export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export PATH="${JAVA_HOME}/bin:${HOME}/tools/maven/bin:/opt/homebrew/bin:${PATH}"

log()  { echo -e "\033[36m[START]\033[0m $1"; }
warn() { echo -e "\033[33m[START][警告]\033[0m $1"; }
die()  { echo -e "\033[31m[START][错误]\033[0m $1"; exit 1; }

MODE="all"
[ "$1" == "--backend-only" ] && MODE="backend"
[ "$1" == "--web-only" ] && MODE="web"

# ---------- 1. 环境 & 数据库 ----------
if [ "${MODE}" != "web" ]; then
    ./script/sh/env-init.sh || die "环境初始化失败"
fi

# ---------- 2. 后端 ----------
if [ "${MODE}" != "web" ]; then
    log "编译后端 (跳过测试)..."
    (cd mes-backend && mvn -q clean package -DskipTests) || die "后端编译失败"

    # 若已有进程在跑，先停掉
    if lsof -i :8081 -sTCP:LISTEN >/dev/null 2>&1; then
        log "端口 8081 已被占用，先停止旧进程..."
        kill $(lsof -t -i :8081 -sTCP:LISTEN) 2>/dev/null || true
        sleep 2
    fi

    log "启动后端 (8081)..."
    JAR=$(ls mes-backend/mes-bootstrap/target/*.jar | head -1)
    nohup java -jar "${JAR}" \
        --spring.profiles.active=dev \
        > "${PROJECT_ROOT}/script/logs/backend.log" 2>&1 &
    echo $! > "${PROJECT_ROOT}/script/logs/backend.pid"

    # 等待健康检查通过
    log "等待后端就绪..."
    for i in $(seq 1 30); do
        if curl -sf http://localhost:8081/api/ping >/dev/null 2>&1; then
            log "后端就绪: http://localhost:8081/api  (日志: script/logs/backend.log)"
            break
        fi
        [ "$i" -eq 30 ] && { tail -30 "${PROJECT_ROOT}/script/logs/backend.log"; die "后端 60 秒内未就绪，以上是日志尾部"; }
        sleep 2
    done
fi

# ---------- 3. 前端 ----------
if [ "${MODE}" != "backend" ]; then
    log "安装前端依赖（首次较慢）..."
    (cd mes-web && npm install --silent) || die "前端依赖安装失败"

    if lsof -i :5173 -sTCP:LISTEN >/dev/null 2>&1; then
        log "端口 5173 已被占用，先停止旧进程..."
        kill $(lsof -t -i :5173 -sTCP:LISTEN) 2>/dev/null || true
        sleep 1
    fi

    log "启动前端 dev server (5173)..."
    nohup bash -c 'cd mes-web && npm run dev' \
        > "${PROJECT_ROOT}/script/logs/frontend.log" 2>&1 &

    for i in $(seq 1 20); do
        if curl -sf http://localhost:5173 >/dev/null 2>&1; then
            log "前端就绪: http://localhost:5173  (日志: script/logs/frontend.log)"
            break
        fi
        [ "$i" -eq 20 ] && warn "前端 20 秒内未响应，可查看 script/logs/frontend.log"
        sleep 1
    done
fi

log "全部启动完成 ✓  前端: http://localhost:5173   后端: http://localhost:8081/api"

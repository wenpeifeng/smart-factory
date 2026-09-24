# ERP 项目一键环境初始化脚本
# 职责：检查依赖 -> 启动 MySQL/Redis 容器 -> 等待就绪
# 用法：./script/sh/env-init.sh

#!/bin/bash
set -e

# ---------- 常量 ----------
MYSQL_CONTAINER="erp-mysql"
REDIS_CONTAINER="erp-redis"
MYSQL_ROOT_PASSWORD="erp123456"
MYSQL_PORT="3306"
REDIS_PORT="6379"
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# ---------- 工具函数 ----------
log()  { echo -e "\033[32m[ENV-INIT]\033[0m $1"; }
warn() { echo -e "\033[33m[ENV-INIT][警告]\033[0m $1"; }
die()  { echo -e "\033[31m[ENV-INIT][错误]\033[0m $1"; exit 1; }

# ---------- 1. 依赖检查 ----------
log "检查依赖..."

command -v docker >/dev/null 2>&1 || die "未安装 Docker，请先安装 Docker Desktop: https://www.docker.com/products/docker-desktop/"
docker info >/dev/null 2>&1 || die "Docker daemon 未运行，请先打开 Docker Desktop 应用"
log "Docker 正常 (v$(docker --version 2>/dev/null | sed 's/[^0-9.]*//g' || echo '?'))"

command -v java >/dev/null 2>&1 || die "未找到 java，请确认 JDK 21 已安装并配置 JAVA_HOME"
JAVA_VER=$(java -version 2>&1 | head -1)
log "Java: $JAVA_VER"

command -v mvn >/dev/null 2>&1 || die "未找到 mvn，请安装 Maven: brew install maven"
log "Maven: $(mvn -v 2>/dev/null | head -1 | cut -d' ' -f3)"

command -v node >/dev/null 2>&1 || die "未找到 node，请安装 Node.js 18+"
log "Node: $(node -v)"

# ---------- 2. 启动 MySQL ----------
if docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
    log "MySQL 容器已在运行"
else
    if docker ps -a --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
        log "启动已停止的 MySQL 容器..."
        docker start "${MYSQL_CONTAINER}"
    else
        log "首次创建 MySQL 8 容器（数据持久化到 docker volume: erp-mysql-data）..."
        docker run -d --name "${MYSQL_CONTAINER}" \
          -p ${MYSQL_PORT}:3306 \
          -e MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD} \
          -e MYSQL_DATABASE=erp_db \
          -e TZ=Asia/Shanghai \
          -v erp-mysql-data:/var/lib/mysql \
          --restart unless-stopped \
          mysql:8 \
          --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null
    fi
fi

# 等待 MySQL 完全就绪（首次初始化需要 20~60 秒）
log "等待 MySQL 就绪..."
for i in $(seq 1 40); do
    if docker exec "${MYSQL_CONTAINER}" mysqladmin ping -uroot -p${MYSQL_ROOT_PASSWORD} --silent >/dev/null 2>&1; then
        log "MySQL 就绪 (${MYSQL_PORT})"
        break
    fi
    [ "$i" -eq 40 ] && die "MySQL 60 秒内未就绪，请检查: docker logs ${MYSQL_CONTAINER}"
    sleep 3
done

# ---------- 3. 启动 Redis ----------
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
    log "Redis 容器已在运行"
else
    if docker ps -a --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
        log "启动已停止的 Redis 容器..."
        docker start "${REDIS_CONTAINER}"
    else
        log "首次创建 Redis 7 容器（AOF 持久化）..."
        docker run -d --name "${REDIS_CONTAINER}" \
          -p ${REDIS_PORT}:6379 \
          -v erp-redis-data:/data \
          --restart unless-stopped \
          redis:7-alpine \
          redis-server --appendonly yes --requirepass ${MYSQL_ROOT_PASSWORD} >/dev/null
    fi
fi

# 等待 Redis 就绪
for i in $(seq 1 10); do
    if docker exec "${REDIS_CONTAINER}" redis-cli -a ${MYSQL_ROOT_PASSWORD} ping 2>/dev/null | grep -q PONG; then
        log "Redis 就绪 (${REDIS_PORT})"
        break
    fi
    [ "$i" -eq 10 ] && die "Redis 未就绪，请检查: docker logs ${REDIS_CONTAINER}"
    sleep 1
done

log "环境初始化完成 ✓"
log "数据库连接信息: localhost:${MYSQL_PORT}  root/${MYSQL_ROOT_PASSWORD}  库: erp_db"

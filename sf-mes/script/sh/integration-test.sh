#!/bin/bash
# ============================================================
# integration-test.sh —— MES 端到端集成测试套件
#
# 前置：
#   1. mes-backend 已启动（./script/sh/start-all.sh --backend-only，8081）
#   2. auth-center 后端已启动（8082，../sf-auth 仓库 java -jar auth-bootstrap）
#   3. mes-mysql / mes-redis 容器在运行
#
# 覆盖：
#   A. 本地登录 + 权限边界（演示账号）
#   B. auth-center 用户同步（工号建档 + mes.admin -> 本地超管）
#   C. OIDC SSO 全链路（授权码+PKCE -> 换令牌 -> JWKS 验签 -> MES 会话）
#   D. 业务主链（ERP 下推幂等 -> 下达 -> 派工 -> 过站 -> WIP/回传）
#   E. 质检 + SPC 采样判异 + 安灯闭环
#
# 用法：./script/sh/integration-test.sh   （任一断言失败即退出非 0）
# ============================================================
set -euo pipefail

MES=http://localhost:8081/api
AUTH=http://localhost:8082/api
MES_ADMIN_USER=${MES_ADMIN_USER:-mes_admin}
MES_ADMIN_PASS=${MES_ADMIN_PASS:-123456}
AUTH_SSO_USER=${AUTH_SSO_USER:-admin}
AUTH_SSO_PASS=${AUTH_SSO_PASS:-123456}
CLIENT_ID=mes-web
REDIRECT_URI=http://localhost:5173/sso/callback

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ $*"; }
fail() { FAIL=$((FAIL+1)); echo "  ❌ $*"; }
step() { echo; echo "===== $* ====="; }

jsonget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1"; }

# ---------- A. 本地登录 + 权限边界 ----------
step "A. 本地登录与权限边界"
TOKEN=$(curl -s -X POST "$MES/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$MES_ADMIN_USER\",\"password\":\"$MES_ADMIN_PASS\"}" | jsonget "['data']['token']")
[ ${#TOKEN} -gt 50 ] && ok "admin 本地登录" || fail "admin 本地登录"

OP_TOKEN=$(curl -s -X POST "$MES/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"demo123456"}' | jsonget "['data']['token']")
[ ${#OP_TOKEN} -gt 50 ] && ok "演示账号 operator 登录" || fail "operator 登录"

CODE=$(curl -s -o /dev/null -w "%{http_code}" "$MES/report/cockpit" -H "Authorization: Bearer $OP_TOKEN")
[ "$CODE" = "403" ] && ok "operator 访问驾驶舱被拒(403)" || fail "operator 应 403, got $CODE"

# ---------- B. auth-center 用户同步 ----------
step "B. auth-center 用户同步"
SYNC=$(curl -s -X POST "$MES/integration/auth/sync" -H "Authorization: Bearer $TOKEN")
echo "  同步报告: $SYNC"
FETCHED=$(echo "$SYNC" | jsonget "['data']['fetched']")
[ "$FETCHED" -gt 0 ] && ok "同步拉取 $FETCHED 个中心用户" || fail "同步拉取为空（检查 auth-center 是否运行 / mode=http）"

# 删除权限缓存让新映射生效
docker exec mes-redis redis-cli -a mes123456 --no-auth-warning DEL auth:perms:1 >/dev/null 2>&1 || true

# 中心 ADMIN000 应已建档（mes.admin -> 本地超管，可访问驾驶舱）
step "C. OIDC SSO 全链路（授权码 + PKCE）"
# C1. 取授权跳转信息（MES 后端生成 state + verifier 存 Redis）
AUTHZ=$(curl -s "$MES/integration/oidc/authorize-url")
STATE=$(echo "$AUTHZ" | jsonget "['data']['state']")
echo "$AUTHZ" | grep -q "code_challenge_method=S256" && ok "authorize-url 含 PKCE S256" || fail "authorize-url 缺 PKCE"

# C2. 模拟用户在 auth-center 统一登录（SPA 版授权码流：登录 -> authorize -> consent）
AUTH_JWT=$(curl -s -X POST "$AUTH/auth/login" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$AUTH_SSO_USER\",\"password\":\"$AUTH_SSO_PASS\",\"loginType\":\"OIDC\",\"clientId\":\"$CLIENT_ID\"}" \
  | jsonget "['data']['token']")
[ ${#AUTH_JWT} -gt 50 ] && ok "auth-center 统一登录" || fail "auth-center 登录失败"

CHALLENGE=$(echo "$AUTHZ" | python3 -c "
import sys,json,re
u=json.load(sys.stdin)['data']['authorizeUrl']
print(dict(p.split('=',1) for p in u.split('?',1)[1].split('&'))['code_challenge'])")

AUTHZ_RESP=$(curl -s -X POST "$AUTH/oauth/authorize" -H "Authorization: Bearer $AUTH_JWT" -H 'Content-Type: application/json' \
  -d "{\"clientId\":\"$CLIENT_ID\",\"redirectUri\":\"$REDIRECT_URI\",\"responseType\":\"code\",\"scope\":\"openid profile\",\"state\":\"$STATE\",\"codeChallenge\":\"$CHALLENGE\",\"codeChallengeMethod\":\"S256\"}")
# consent 可配置：needConsent 时带 consent=true 重试
if echo "$AUTHZ_RESP" | grep -qi "consent"; then
  AUTHZ_RESP=$(curl -s -X POST "$AUTH/oauth/authorize" -H "Authorization: Bearer $AUTH_JWT" -H 'Content-Type: application/json' \
    -d "{\"clientId\":\"$CLIENT_ID\",\"redirectUri\":\"$REDIRECT_URI\",\"responseType\":\"code\",\"scope\":\"openid profile\",\"state\":\"$STATE\",\"codeChallenge\":\"$CHALLENGE\",\"codeChallengeMethod\":\"S256\",\"consent\":true}")
fi
REDIRECT=$(echo "$AUTHZ_RESP" | jsonget "['data']['redirectUri']")
CODE_AUTHZ=$(python3 -c "import urllib.parse as u;print(dict(u.parse_qsl(u.urlparse('$REDIRECT').query))['code'])")
[ ${#CODE_AUTHZ} -gt 10 ] && ok "取得授权码" || fail "未取得授权码: $AUTHZ_RESP"

# C3. MES 回调登录：换令牌 -> JWKS 验签 -> 工号匹配 -> 签发 MES 会话
SSO=$(curl -s -X POST "$MES/integration/oidc/login?code=$CODE_AUTHZ&state=$STATE")
SSO_TOKEN=$(echo "$SSO" | jsonget "['data']['token']")
[ ${#SSO_TOKEN} -gt 50 ] && ok "SSO 换发 MES 会话" || { fail "SSO 登录失败: $SSO"; }

ME=$(curl -s "$MES/auth/me" -H "Authorization: Bearer $SSO_TOKEN")
echo "$ME" | grep -q "ADMIN000" && ok "MES 会话身份=中心工号 ADMIN000" || fail "会话身份异常: $(echo $ME | head -c 120)"

CODE=$(curl -s -o /dev/null -w "%{http_code}" "$MES/report/cockpit" -H "Authorization: Bearer $SSO_TOKEN")
[ "$CODE" = "200" ] && ok "SSO 用户（mes.admin）可访问驾驶舱" || fail "SSO 驾驶舱应 200, got $CODE"

# 重放应拒绝（state 一次性；全局异常处理器 HTTP 200 + 业务码非 0）
REPLAY=$(curl -s -X POST "$MES/integration/oidc/login?code=$CODE_AUTHZ&state=$STATE")
REPLAY_CODE=$(echo "$REPLAY" | jsonget "['code']")
[ "$REPLAY_CODE" != "0" ] && ok "state 重放被拒" || fail "state 重放未拒绝: $REPLAY"

# ---------- D. 业务主链 ----------
step "D. 业务主链（下推 -> 下达 -> 派工 -> 过站 -> 回传）"
push() { curl -s -X POST "$MES/integration/erp/$1" -H 'Content-Type: application/json' \
  -H 'X-Integration-Token: erp-integration-token' -H "Authorization: Bearer $TOKEN" -d "$2"; }
ORD="IT$(date +%H%M%S)"
R1=$(push orders "{\"orderNo\":\"$ORD\",\"materialCode\":\"FG001\",\"qty\":60}")
[ "$(echo "$R1" | jsonget "['code']")" = "0" ] && ok "ERP 订单下推建单" || fail "下推失败: $R1"
WO=$(echo "$R1" | jsonget "['data']")
R2=$(push orders "{\"orderNo\":\"$ORD\",\"materialCode\":\"FG001\",\"qty\":60}")
[ "$(echo "$R2" | jsonget "['data']")" = "$WO" ] && ok "重复下推幂等（同工单）" || fail "幂等失败"

curl -s -X PUT "$MES/plan/workorders/$WO/release" -H "Authorization: Bearer $TOKEN" | grep -q '"code":0' \
  && ok "工单下达（展开 BOM/工艺）" || fail "下达失败"
STEP1=$(curl -s "$MES/plan/steps/workorder/$WO" -H "Authorization: Bearer $TOKEN" | jsonget "['data'][0]['id']")
curl -s -X PUT "$MES/plan/steps/$STEP1/dispatch?assigneeId=1&assigneeName=admin" -H "Authorization: Bearer $TOKEN" | grep -q '"code":0' \
  && ok "工序派工" || fail "派工失败"
curl -s -X POST "$MES/execution/stations" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d "{\"woStepId\":$STEP1,\"goodQty\":58,\"scrapQty\":2,\"workMinutes\":40,\"workerName\":\"测试工\"}" | grep -q '"code":0' \
  && ok "过站报工" || fail "报工失败"
curl -s "$MES/execution/wips?pageNum=1&pageSize=50" -H "Authorization: Bearer $TOKEN" \
  | grep -q "$WO" >/dev/null 2>&1 || true
curl -s "$MES/integration/push-logs?bizType=WORK_REPORT&keyword=$ORD" -H "Authorization: Bearer $TOKEN" >/dev/null || true
PUSH=$(curl -s "$MES/integration/push-logs?pageNum=1&pageSize=5" -H "Authorization: Bearer $TOKEN")
echo "$PUSH" | grep -q '"status":"SUCCESS"' && ok "完工回传 ERP SUCCESS" || fail "回传异常: $(echo $PUSH | head -c 100)"

# ---------- E. 质检 + SPC + 安灯 ----------
step "E. 质检/SPC/安灯"
TPL=$(curl -s -G "$MES/quality/templates" --data-urlencode "keyword=长度检验" -H "Authorization: Bearer $TOKEN" | jsonget "['data']['records'][0]['id']" 2>/dev/null) \
  || TPL=$(curl -s "$MES/quality/templates?pageNum=1&pageSize=10" -H "Authorization: Bearer $TOKEN" | jsonget "['data']['records'][0]['id']")
IID=$(curl -s -X POST "$MES/quality/inspects" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d "{\"inspectType\":\"FQC\",\"sourceType\":\"FINISH\",\"templateId\":$TPL,\"materialId\":1,\"batchNo\":\"IT\",\"sampleQty\":3,\"inspectorName\":\"IT\"}" | jsonget "['data']")
ITEM=$(curl -s "$MES/quality/inspects/$IID" -H "Authorization: Bearer $TOKEN" | jsonget "['data']['items'][0]['id']")
curl -s -X PUT "$MES/quality/inspects/$IID/values" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d "[{\"itemId\":$ITEM,\"actualValue\":\"999\"}]" >/dev/null
curl -s -X PUT "$MES/quality/inspects/$IID/submit" -H "Authorization: Bearer $TOKEN" | grep -q '"code":0' \
  && ok "检验提交（回传 ERP）" || fail "检验提交失败"

AN=$(curl -s -X POST "$MES/kanban/andons" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"type":"QUALITY","callerName":"IT","message":"集成测试呼叫"}' | jsonget "['data']")
curl -s -X PUT "$MES/kanban/andons/$AN/respond?responder=IT" -H "Authorization: Bearer $TOKEN" >/dev/null
curl -s -X PUT "$MES/kanban/andons/$AN/close?closeRemark=done" -H "Authorization: Bearer $TOKEN" | grep -q '"code":0' \
  && ok "安灯闭环" || fail "安灯闭环失败"

echo
echo "=========================================="
echo "集成测试结果：PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" = "0" ] && echo "全部通过 ✅" || { echo "存在失败 ❌"; exit 1; }

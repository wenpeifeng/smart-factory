#!/bin/bash
# ============================================================
# gen-data-dict.sh —— 从 mes_db 生成数据字典（docs/数据字典.md）
# 前置：mes-mysql 容器在运行。表结构变更后重跑即可刷新文档。
# 用法：./script/sh/gen-data-dict.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT=docs/数据字典.md

cat > "$OUT" <<'EOF'
# MES 数据字典（mes_db）

> 由 `script/sh/gen-data-dict.sh` 自动生成自 mes-mysql 容器（live 库），
> 表结构变更后重跑脚本刷新。字段注释以建表 DDL（script/sql/migration）为准。

EOF

python3 - <<'PYEOF' >> "$OUT"
import subprocess
import collections

DB = "mes_db"

MODULES = collections.OrderedDict([
    ("sys_",    "系统管理（用户副本/RBAC/字典/日志/租户）—— 身份来自 auth-center 同步，角色菜单为本系统自有"),
    ("org_",    "组织工厂（数据权限地基）"),
    ("base_",   "基础资料主数据（物料/客商/仓库/BOM/工艺路线/工作中心/班组，org_id=0 共享）"),
    ("plan_",   "计划（工单/工序任务/用料需求/齐套分析/排程）"),
    ("exe_",    "执行（过站报工记录/WIP/返工报废）"),
    ("qc_",     "质量（检验模板/检验单/不合格处置/SPC）"),
    ("mat_",    "车间物料（领退料/线边仓/批次SN/标签日志）"),
    ("eqp_",    "设备（台账/点检/保养维修）"),
    ("kb_",     "看板（安灯呼叫）"),
    ("trace_",  "追溯（追溯链路/电子批记录）"),
    ("int_",    "集成（ERP 回传推送日志）"),
    ("wf_",     "工作流（流程定义/实例/任务/消息）"),
    ("notify_", "通知中心（通道配置/发送日志）"),
])


def mysql(sql):
    r = subprocess.run(
        ["docker", "exec", "mes-mysql", "mysql", "--default-character-set=utf8mb4",
         "-uroot", "-pmes123456", DB, "-N", "-B", "-e", sql],
        capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        raise SystemExit(r.stderr)
    return [line.split("\t") for line in r.stdout.strip().split("\n") if line]


tables = [r[0] for r in mysql(
    "SELECT table_name FROM information_schema.tables "
    "WHERE table_schema='mes_db' AND table_type='BASE TABLE' ORDER BY table_name")]
tables = [t for t in tables if not t.startswith("flyway_")]

groups = collections.OrderedDict((desc, []) for desc in MODULES.values())
others = []
for t in tables:
    for prefix, desc in MODULES.items():
        if t.startswith(prefix):
            groups[desc].append(t)
            break
    else:
        others.append(t)

for desc, ts in groups.items():
    if not ts:
        continue
    print("\n## " + desc + "（" + str(len(ts)) + " 表）\n")
    for t in ts:
        comment = mysql("SELECT table_comment FROM information_schema.tables "
                        "WHERE table_schema='mes_db' AND table_name='" + t + "'")[0][0]
        print("### `" + t + "` — " + comment + "\n")
        print("| 列 | 类型 | 可空 | 默认 | 键 | 说明 |")
        print("|----|------|------|------|----|------|")
        cols = mysql(
            "SELECT column_name, column_type, is_nullable, column_default, "
            "COALESCE(column_key,''), COALESCE(column_comment,'') "
            "FROM information_schema.columns "
            "WHERE table_schema='mes_db' AND table_name='" + t + "' ORDER BY ordinal_position")
        for name, ctype, nullable, default, key, ccomment in cols:
            print("| `" + name + "` | " + ctype + " | " + nullable + " | "
                  + (default or "") + " | " + (key or "") + " | " + (ccomment or "") + " |")
        print()

if others:
    print("\n## 其他（" + str(len(others)) + " 表）\n")
    for t in others:
        print("- `" + t + "`")
PYEOF

TOTAL=$(grep -c "^### \`" "${OUT}")
echo "已生成 ${OUT}（${TOTAL} 张表）"

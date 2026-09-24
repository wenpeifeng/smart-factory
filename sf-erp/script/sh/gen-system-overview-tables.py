#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""重新生成 docs/系统概览.md §3 的数据库表目录（从 live 库 information_schema 取表注释）。

用法（新增表/改注释后）：
    python3 script/sh/gen-system-overview-tables.py            # 打印表目录片段
    python3 script/sh/gen-system-overview-tables.py --write    # 直接替换文档 §3 区段
"""
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOC = ROOT / 'docs' / '系统概览.md'
MARK_START = '## 3. 数据库表目录'
MARK_END = '## 4. 文档索引'

GROUP_ORDER = ['sys_', 'base_', 'pur_', 'sal_', 'inv_', 'wms_', 'qc_', 'fin_', 'cost_', 'mrp_',
               'ast_', 'hr_', 'eqp_', 'org_', 'budget_', 'wf_', 'notify_', 'int_', 'biz_']
GROUP_NAMES = OrderedDict([
    ('sys', '系统平台（RBAC/租户/字典/日志）'), ('base', '基础资料（物料/客商/仓库/价格中心）'),
    ('pur', '采购（订单/入库/退货/暂估/供应商评分）'), ('sal', '销售（报价/订单/出库/退货/价格策略）'),
    ('inv', '库存（库存账/流水/盘点/其他收发）'), ('wms', 'WMS（库区库位/任务/波次/ABC）'),
    ('qc', '质检'), ('fin', '财务（凭证/期间/账簿/往来/收付款/报表映射）'), ('cost', '成本'),
    ('mrp', '计划与生产（BOM/工艺/MRP/工单/领料/替代料）'), ('ast', '固定资产'), ('hr', '人力资源'),
    ('eqp', '设备'), ('org', '集团组织（工厂树/跨组织调拨/转移价格）'), ('budget', '预算'),
    ('wf', '审批流'), ('notify', '通知中心'), ('int', '集成（ERP↔MES）'),
    ('biz', '通用业务（红冲台账）'), ('flyway', '迁移框架'), ('other', '其他'),
])


def group_of(table: str) -> str:
    for p in GROUP_ORDER:
        if table.startswith(p):
            return p[:-1]
    return 'flyway' if table == 'flyway_schema_history' else 'other'


def fetch_tables() -> list:
    out = subprocess.run(
        ['docker', 'exec', '-e', 'LANG=C.UTF-8', 'erp-mysql', 'mysql',
         '--default-character-set=utf8mb4', '-uroot', '-perp123456', '-N', '-B', 'erp_db', '-e',
         'SELECT TABLE_NAME,TABLE_COMMENT FROM information_schema.TABLES '
         "WHERE TABLE_SCHEMA='erp_db' ORDER BY TABLE_NAME;"],
        capture_output=True, text=True)
    rows = [l.split('\t', 1) for l in out.stdout.strip().split('\n') if l]
    return [(t, c if len(c) > 1 or c.isalpha() else c) for t, c in [(r[0], r[1] if len(r) > 1 else '') for r in rows]]


def build_catalog(rows: list) -> str:
    groups = OrderedDict()
    for t, c in rows:
        groups.setdefault(group_of(t), []).append((t, c))
    lines = [MARK_START + '（%d 张，单库前缀分域；注释取自 information_schema.TABLE_COMMENT）\n' % len(rows)]
    for key, label in GROUP_NAMES.items():
        if key not in groups:
            continue
        lines.append('\n#### %s — %d 张\n' % (label, len(groups[key])))
        lines.append('| 表名 | 说明 |')
        lines.append('|---|---|')
        for t, c in groups[key]:
            lines.append('| `%s` | %s |' % (t, c or '—'))
    return '\n'.join(lines)


def main() -> None:
    rows = fetch_tables()
    catalog = build_catalog(rows)
    if '--write' in sys.argv:
        text = DOC.read_text(encoding='utf-8')
        head, rest = text.split(MARK_START, 1)
        _, tail = rest.split(MARK_END, 1)
        DOC.write_text(head + catalog + '\n\n' + MARK_END + tail, encoding='utf-8')
        print('已更新 %s（%d 张表）' % (DOC, len(rows)))
    else:
        print(catalog)


if __name__ == '__main__':
    main()

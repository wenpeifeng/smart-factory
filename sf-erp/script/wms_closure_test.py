#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WMS 三期闭环验证脚本（绕代理直连 8080）。
覆盖：入库审核→扫描器 PUTAWAY→领取→完成→库位行落位+汇总守恒；
出库审核→建波两单→路径序→逐条完成→波次完成→汇总扣减正确；
FEFO 两批次早的先出；序列号 入库2→核销1→追溯；ABC 重算；KPI 非零。
"""
import json
import time
import urllib.request

BASE = "http://localhost:8080/api"
TOKEN = None
PASS = []
FAIL = []


def call(method, path, body=None, expect=None):
    global TOKEN
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if TOKEN:
        req.add_header("Authorization", "Bearer " + TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            r = json.loads(resp.read().decode())
            if r.get("code") != 0:
                raise RuntimeError(f"{method} {path} code={r.get('code')} msg={r.get('message')}")
            return r.get("data")
    except urllib.error.HTTPError as e:
        body_txt = e.read().decode()[:300]
        raise RuntimeError(f"{method} {path} HTTP{e.code} {body_txt}")


def check(name, cond, detail=""):
    if cond:
        PASS.append(name)
        print(f"[PASS] {name} {detail}")
    else:
        FAIL.append(name)
        print(f"[FAIL] {name} {detail}")


def main():
    global TOKEN
    # ---------- 0. 登录 ----------
    login = call("POST", "/auth/login", {"username": "admin", "password": "admin123"})
    TOKEN = login["token"] if isinstance(login, dict) and "token" in login else login
    check("登录", True)

    # ---------- 1. 准备：仓库1 + 两个 STORAGE 库位 + 批次物料 ----------
    wh = 1
    mat = 8  # WL20260905003 批次物料
    zones = call("GET", f"/basedata/warehouses/{wh}/zones")
    storage_zone = next((z for z in zones if z["zoneType"] == "STORAGE"), None)
    check("库区种子/列表", len(zones) >= 5 and storage_zone is not None, f"zones={len(zones)}")

    # 建两个库位（编码带前缀保证可重跑）
    ts = int(time.time()) % 100000
    loc_a = call("POST", "/basedata/warehouses/locations",
                 {"warehouseId": wh, "zoneId": storage_zone["id"], "locType": "STORAGE",
                  "code": f"A-{ts}-01", "name": "闭环库位A", "capacity": 0, "status": 1})
    loc_b = call("POST", "/basedata/warehouses/locations",
                 {"warehouseId": wh, "zoneId": storage_zone["id"], "locType": "STORAGE",
                  "code": f"B-{ts}-02", "name": "闭环库位B", "capacity": 0, "status": 1})
    check("库位创建(带库区/类型)", loc_a > 0 and loc_b > 0, f"a={loc_a} b={loc_b}")

    tree = call("GET", f"/basedata/warehouses/{wh}/location-tree")
    tree_ok = any(n["zoneId"] == storage_zone["id"] and
                  any(l["id"] == loc_a for l in n["locations"]) for n in tree)
    check("仓库库位树", tree_ok, f"nodes={len(tree)}")

    # ---------- 2. 入库（其他入库，批次+效期）→ 扫描器 PUTAWAY ----------
    batch1 = f"B{ts}-1"
    in1 = call("POST", "/inventory/other-ins",
               {"warehouseId": wh, "reason": "WMS闭环-上架",
                "lines": [{"materialId": mat, "batchNo": batch1, "qty": 100,
                           "expiryDate": "2026-12-31"}]})
    call("POST", f"/inventory/other-ins/{in1['id']}/audit")
    total_before = stock_total(mat)
    check("入库审核过账", total_before >= 100, f"total={total_before}")

    # 触发扫描器（不等 30s 轮询，直接手动触发同一逻辑）
    scan = call("POST", "/inventory/wms/tasks/scan")
    tasks = call("GET", "/inventory/wms/tasks?taskType=PUTAWAY&pageSize=50")["records"]
    putaway = next((t for t in tasks if t["sourceType"] == "OTH_IN"
                    and t["sourceNo"] and batch1 in (t["batchNo"] or "")
                    and t["status"] == 0), None)
    check("扫描器生成PUTAWAY", putaway is not None, f"created={scan['created']}")
    if putaway:
        check("上架推荐库位", putaway["locationId"] is not None,
              f"reason={putaway.get('recommendReason')}")
        call("POST", f"/inventory/wms/tasks/{putaway['id']}/claim")
        t = call("GET", "/inventory/wms/tasks?pageSize=1&keyword=" + putaway["taskNo"])["records"][0]
        check("领取任务", t["status"] == 10 and t["assignee"] is not None, f"status={t['status']}")
        call("POST", f"/inventory/wms/tasks/{putaway['id']}/complete", {})
        total_after = stock_total(mat)
        loc_rows = stock_loc_rows(mat, wh)
        check("上架完成库位行落位",
              any(int(r["location_id"]) == int(putaway["locationId"]) and float(r["qty"]) > 0
                  for r in loc_rows if r["batch_no"] == putaway["batchNo"]),
              f"rows={loc_rows}")
        check("上架汇总守恒(总量不变)", abs(total_after - total_before) < 1e-9,
              f"before={total_before} after={total_after}")

    # ---------- 3. 出库审核 → 建波两单 → 路径序 → 逐条完成 → 波次完成 ----------
    # 先补两笔入库（两个不同批次、不同效期）供 FEFO 与波次用
    in2 = call("POST", "/inventory/other-ins",
               {"warehouseId": wh, "reason": "WMS闭环-FEFO晚效期",
                "lines": [{"materialId": mat, "batchNo": f"B{ts}-2", "qty": 50,
                           "expiryDate": "2027-06-30"}]})
    call("POST", f"/inventory/other-ins/{in2['id']}/audit")
    # FEFO 用：再入一个早效期批次（效期动态取=该物料现存最早效期-1天，保证严格最早）
    minrow = db_rows(f"SELECT MIN(expiry_date) d FROM inv_stock WHERE material_id={mat} AND deleted=0 AND expiry_date IS NOT NULL")
    base_d = minrow[0]["d"] if minrow and minrow[0]["d"] else "2026-10-31"
    import datetime
    d = (datetime.date.fromisoformat(str(base_d)) - datetime.timedelta(days=1)).isoformat()
    in3 = call("POST", "/inventory/other-ins",
               {"warehouseId": wh, "reason": "WMS闭环-FEFO早效期",
                "lines": [{"materialId": mat, "batchNo": f"B{ts}-0", "qty": 30,
                           "expiryDate": d}]})
    call("POST", f"/inventory/other-ins/{in3['id']}/audit")

    # 两张出库单（不同数量）
    o1 = call("POST", "/inventory/other-outs",
              {"warehouseId": wh, "reason": "WMS闭环-波次1",
               "lines": [{"materialId": mat, "qty": 5}]})
    o2 = call("POST", "/inventory/other-outs",
              {"warehouseId": wh, "reason": "WMS闭环-波次2",
               "lines": [{"materialId": mat, "qty": 3}]})
    total_pre_out = stock_total(mat)
    call("POST", f"/inventory/other-outs/{o1['id']}/audit")
    call("POST", f"/inventory/other-outs/{o2['id']}/audit")
    total_post_out = stock_total(mat)
    check("出库审核汇总扣减", abs((total_pre_out - total_post_out) - 8) < 1e-9,
          f"pre={total_pre_out} post={total_post_out}")

    # 建波（两单）
    wave = call("POST", "/inventory/wms/waves",
                {"warehouseId": wh, "remark": "闭环波次",
                 "docs": [{"docId": o1["id"], "sourceType": "OTH_OUT"},
                          {"docId": o2["id"], "sourceType": "OTH_OUT"}]})
    path = call("GET", f"/inventory/wms/waves/{wave['id']}/pick-path")
    check("建波生成拣选任务", len(path) == 2, f"tasks={len(path)}")
    codes = [t["locationCode"] or "" for t in path]
    check("波次路径序(库位编码升序)", codes == sorted(codes), f"codes={codes}")
    # 逐条完成（领取→完成）
    for t in path:
        if t["status"] == 0:
            call("POST", f"/inventory/wms/tasks/{t['id']}/claim")
        call("POST", f"/inventory/wms/tasks/{t['id']}/complete", {})
    call("POST", f"/inventory/wms/waves/{wave['id']}/complete")
    wd = call("GET", f"/inventory/wms/waves/{wave['id']}")
    check("波次完成", wd["status"] == 20 and wd["doneCount"] == 2,
          f"status={wd['status']} done={wd['doneCount']}/{wd['taskCount']}")
    total_after_wave = stock_total(mat)
    check("波次后汇总不再双扣", abs(total_after_wave - total_post_out) < 1e-9,
          f"afterWave={total_after_wave} postOut={total_post_out}")

    # ---------- 4. FEFO：早效期先出 ----------
    fefo_out = call("POST", "/inventory/other-outs",
                    {"warehouseId": wh, "reason": "WMS闭环-FEFO", "strategy": "FEFO",
                     "lines": [{"materialId": mat, "qty": 10}]})
    call("POST", f"/inventory/other-outs/{fefo_out['id']}/audit")
    rows = db_rows(f"SELECT batch_no, SUM(qty) q FROM inv_stock WHERE material_id={mat} AND deleted=0 GROUP BY batch_no")
    b0 = next((r["q"] for r in rows if r["batch_no"] == f"B{ts}-0"), 0)
    check("FEFO早效期先出", abs(float(b0) - 20) < 1e-9, f"B{ts}-0 剩余={b0}（应 30-10=20）")

    # ---------- 5. 序列号：入库2 → 出库核销1 → 追溯 ----------
    sn1, sn2 = f"SN{ts}0001", f"SN{ts}0002"
    reg = call("POST", "/inventory/serials",
               {"materialId": mat, "warehouseId": wh, "batchNo": batch1,
                "sourceNo": "闭环-" + sn1, "serialNos": [sn1, sn2]})
    check("序列号登记2条", reg["registered"] == 2)
    wo = call("POST", "/inventory/serials/write-off",
              {"outNo": "闭环出库-" + sn1, "serialNos": [sn1]})
    check("序列号核销1条", wo["writtenOff"] == 1)
    trace = call("GET", f"/inventory/serials?serialNo={sn1}&pageSize=10")["records"]
    check("序列号追溯", len(trace) == 1 and trace[0]["status"] == "OUT"
          and trace[0]["outNo"] == "闭环出库-" + sn1, f"row={trace[0]['status'] if trace else 'none'}")
    back = call("GET", f"/inventory/serials?materialId={mat}&pageSize=10")["records"]
    check("序列号反查(按物料)", len(back) >= 2, f"rows={len(back)}")

    # ---------- 6. ABC 重算 ----------
    counts = call("POST", "/inventory/wms/abc/recalc")
    total_abc = sum(c["cnt"] for c in counts)
    abc_page = call("GET", "/inventory/wms/abc?pageSize=5")
    check("ABC重算分级合理", total_abc > 0 and len(abc_page["records"]) > 0,
          f"counts={counts}")

    # ---------- 7. KPI ----------
    kpi = call("GET", "/inventory/wms/kpi")
    check("KPI非零", kpi["completedTasks"] >= 3,
          f"done={kpi['completedTasks']} avg={kpi['avgDurationMinutes']} timely={kpi['onTimeRate']}")

    # ---------- 8. 一期回归：库存页/批次/流水/盘点/调拨冒烟 ----------
    stocks = call("GET", "/inventory/stocks?keyword=WL20260905003&pageSize=5")
    check("即时库存回归(含库位列)", stocks["total"] >= 1
          and all("locationId" in r for r in stocks["records"]))
    batches = call("GET", f"/inventory/batches?materialId={mat}")
    check("批次清单回归(聚合+效期)", len(batches) >= 3
          and all("expiryDate" in b for b in batches))
    moves = call("GET", "/inventory/movements?bizType=OTH_OUT&pageSize=5")
    check("库存流水回归", moves["total"] >= 3)

    ck = create_check_with_retry(2)
    ckd = call("GET", f"/inventory/checks/{ck['id']}")
    lines = ckd["lines"]
    if lines:
        call("PUT", f"/inventory/checks/{ck['id']}",
             {"lines": [{"detailId": lines[0]["id"], "actualQty": float(lines[0]["bookQty"])}]})
    done = call("POST", f"/inventory/checks/{ck['id']}/complete")
    check("盘点回归(快照含库位列)", "locationId" in (lines[0] if lines else {}), f"lines={len(lines)}")

    # 调拨回归：取仓1有存量的物料（动态查询，避免测试数据依赖）
    rows = db_rows("SELECT material_id, SUM(qty) q FROM inv_stock "
                   "WHERE warehouse_id=1 AND deleted=0 GROUP BY material_id HAVING q>1 ORDER BY q DESC LIMIT 1")
    tr_mat = int(rows[0]["material_id"]) if rows else 9
    tr = call("POST", "/inventory/transfers",
              {"fromWarehouseId": 1, "toWarehouseId": 2,
               "lines": [{"materialId": tr_mat, "qty": 1}]})
    check("调拨回归", bool(tr.get("transferNo")))

    # ---------- 汇总 ----------
    print("\n========== 结果 ==========")
    print(f"PASS {len(PASS)} / FAIL {len(FAIL)}")
    for f in FAIL:
        print("  FAIL:", f)
    exit(1 if FAIL else 0)


def create_check_with_retry(wh):
    """盘点单创建：Redis 序列被环境重置时撞 CK 单号，重试即可（换号）。"""
    last = None
    for i in range(6):
        try:
            return call("POST", "/inventory/checks", {"warehouseId": wh, "remark": "闭环冒烟盘点"})
        except RuntimeError as e:
            last = e
            time.sleep(0.3)
    raise last


def stock_total(mat):
    rows = db_rows(f"SELECT COALESCE(SUM(qty),0) t FROM inv_stock WHERE material_id={mat} AND deleted=0")
    return float(rows[0]["t"])


def stock_loc_rows(mat, wh):
    return db_rows(f"SELECT location_id, batch_no, qty FROM inv_stock "
                   f"WHERE material_id={mat} AND warehouse_id={wh} AND deleted=0 AND location_id>0")


def db_rows(sql):
    import subprocess
    out = subprocess.run(
        ["docker", "exec", "erp-mysql", "mysql", "-uroot", "-perp123456", "-B", "erp_db", "-e", sql],
        capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError("SQL失败: " + out.stderr[:200])
    lines = out.stdout.strip().splitlines()
    if not lines:
        return []
    cols = lines[0].split("\t")
    return [dict(zip(cols, line.split("\t"))) for line in lines[1:]]


if __name__ == "__main__":
    main()

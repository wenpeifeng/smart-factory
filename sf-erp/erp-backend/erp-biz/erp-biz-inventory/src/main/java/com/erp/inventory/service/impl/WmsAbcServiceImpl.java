package com.erp.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.result.PageResult;
import com.erp.inventory.dto.request.WmsTaskQuery;
import com.erp.inventory.dto.vo.AbcClassVO;
import com.erp.inventory.entity.InvCheck;
import com.erp.inventory.entity.WmsAbcClass;
import com.erp.inventory.mapper.InvCheckMapper;
import com.erp.inventory.mapper.WmsAbcClassMapper;
import com.erp.inventory.service.InvCheckService;
import com.erp.inventory.service.WmsAbcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * WMS ABC 分级服务实现（V421）。
 * <p>
 * 分级算法：近 90 天出库频次（inv_movement 出库流水行数）降序，
 * 前 20% A / 随后 30% B / 其余 50% C——按排名百分比切分（不足 5 个物料时
 * 至少 1 个 A，避免小样本全 C）。
 * 循环盘点：每仓按其现存最高优先级分级决定间隔（A=1 月 / B=3 月 / C=12 月），
 * 距该仓上次 CYCLE 盘点超过间隔则自动创建盘点单（source=CYCLE）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WmsAbcServiceImpl implements WmsAbcService {

    /** 分级统计窗口（天）：近 90 天出库频次 */
    private static final int FREQ_WINDOW_DAYS = 90;

    /** A 类盘点间隔（月） */
    private static final int A_INTERVAL_MONTHS = 1;
    /** B 类盘点间隔（月） */
    private static final int B_INTERVAL_MONTHS = 3;
    /** C 类盘点间隔（月） */
    private static final int C_INTERVAL_MONTHS = 12;

    private final WmsAbcClassMapper abcMapper;
    private final InvCheckMapper checkMapper;
    private final InvCheckService checkService;

    @Override
    public PageResult<AbcClassVO> page(WmsTaskQuery query) {
        IPage<AbcClassVO> page = abcMapper.selectAbcPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> recalc() {
        List<Map<String, Object>> freqRows = abcMapper.selectOutboundFrequency(FREQ_WINDOW_DAYS);
        LocalDate today = LocalDate.now();

        // 旧分级整体软删（现行分级 = deleted=0），新旧切换同事务
        List<WmsAbcClass> olds = abcMapper.selectList(Wrappers.lambdaQuery(WmsAbcClass.class));
        for (WmsAbcClass old : olds) {
            abcMapper.deleteById(old.getId());
        }

        int total = freqRows.size();
        int aEnd = Math.max(total > 0 ? 1 : 0, (int) Math.ceil(total * 0.2));
        int bEnd = aEnd + (int) Math.ceil(total * 0.3);
        int rank = 0;
        for (Map<String, Object> row : freqRows) {
            rank++;
            Long materialId = asLong(row.get("material_id"));
            Long freq = asLong(row.get("freq"));
            if (materialId == null) {
                continue;
            }
            WmsAbcClass cls = new WmsAbcClass();
            cls.setMaterialId(materialId);
            cls.setFreq(freq == null ? 0L : freq);
            cls.setCalcDate(today);
            cls.setLevel(rank <= aEnd ? WmsAbcClass.LEVEL_A
                    : rank <= bEnd ? WmsAbcClass.LEVEL_B : WmsAbcClass.LEVEL_C);
            abcMapper.insert(cls);
        }
        log.info("[WMS][ABC] 重算完成：物料 {} 个（A={}, B={}, C={}），窗口 {} 天",
                total, aEnd, bEnd - aEnd, total - bEnd, FREQ_WINDOW_DAYS);
        return abcMapper.selectLevelCounts();
    }

    @Override
    public List<Map<String, Object>> levelCounts() {
        return abcMapper.selectLevelCounts();
    }

    @Override
    public int generateCycleChecks() {
        int created = 0;
        try {
            // 分级涉及的仓库（有库存行的仓库），按最高优先级分级的仓库集合分别判断到期
            List<Map<String, Object>> aWhs = abcMapper.selectWarehousesWithClassLevel(WmsAbcClass.LEVEL_A);
            List<Map<String, Object>> bWhs = abcMapper.selectWarehousesWithClassLevel(WmsAbcClass.LEVEL_B);
            List<Map<String, Object>> cWhs = abcMapper.selectWarehousesWithClassLevel(WmsAbcClass.LEVEL_C);
            created += createDueChecks(distinctWarehouses(aWhs), A_INTERVAL_MONTHS, "A类(月度)");
            created += createDueChecks(distinctWarehouses(bWhs), B_INTERVAL_MONTHS, "B类(季度)");
            created += createDueChecks(distinctWarehouses(cWhs), C_INTERVAL_MONTHS, "C类(年度)");
            if (created > 0) {
                log.info("[WMS][循环盘点] 本轮自动生成盘点单 {} 张", created);
            }
        } catch (Exception e) {
            log.error("[WMS][循环盘点] 生成异常：{}", e.getMessage(), e);
        }
        return created;
    }

    /** 对给定仓库集合：距上次 CYCLE 盘点超过 intervalMonths 则创建盘点单 */
    private int createDueChecks(List<Long> warehouseIds, int intervalMonths, String label) {
        int created = 0;
        LocalDateTime dueBefore = LocalDateTime.now().minusMonths(intervalMonths);
        for (Long warehouseId : warehouseIds) {
            InvCheck last = checkMapper.selectOne(Wrappers.lambdaQuery(InvCheck.class)
                    .eq(InvCheck::getWarehouseId, warehouseId)
                    .eq(InvCheck::getSource, InvCheck.SOURCE_CYCLE)
                    .orderByDesc(InvCheck::getId)
                    .last("LIMIT 1"));
            boolean due = last == null || last.getCreateTime() == null
                    || last.getCreateTime().isBefore(dueBefore);
            if (due) {
                Long id = checkService.createWithSource(warehouseId, InvCheck.SOURCE_CYCLE,
                        "ABC循环盘点自动生成（" + label + "）");
                log.info("[WMS][循环盘点] 仓库={} 生成盘点单 id={}（{}）", warehouseId, id, label);
                created++;
            }
        }
        return created;
    }

    private List<Long> distinctWarehouses(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> asLong(r.get("warehouse_id")))
                .filter(w -> w != null).distinct().toList();
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

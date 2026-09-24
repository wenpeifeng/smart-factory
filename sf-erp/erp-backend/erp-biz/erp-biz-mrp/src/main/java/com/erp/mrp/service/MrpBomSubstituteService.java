package com.erp.mrp.service;

import com.erp.mrp.dto.form.BomSubstituteBatchForm;
import com.erp.mrp.dto.vo.BomSubstituteVO;

import java.util.List;
import java.util.Map;

/**
 * BOM 子件替代组服务（V621）。
 * <p>
 * 主料 = mrp_bom_item 行自身物料；替代料按 priority 升序补足。
 * 仅 DRAFT 状态 BOM 允许维护（与 BOM 明细编辑规则一致）。
 */
public interface MrpBomSubstituteService {

    /**
     * 查询某 BOM 子件行的替代料（按优先级升序）。
     *
     * @param bomItemId BOM 子件行ID
     * @return 替代料列表（含物料回显）
     */
    List<BomSubstituteVO> listByItem(Long bomItemId);

    /**
     * 查询整个 BOM 的全部替代组（按子件行 → 替代料列表分组）。
     *
     * @param bomId BOMID
     * @return bomItemId → 替代料列表
     */
    Map<Long, List<BomSubstituteVO>> listByBom(Long bomId);

    /**
     * 按子件行整组重建替代料（先删后插；items 为空 = 清空）。
     *
     * @param bomItemId BOM 子件行ID
     * @param form      批量表单
     * @throws com.erp.common.exception.BusinessException 子件行不存在 / BOM 非草稿 /
     *                                                    替代料与主料相同 / 替代料重复 / 物料不存在
     */
    void saveBatch(Long bomItemId, BomSubstituteBatchForm form);

    /**
     * 删除单条替代料。
     *
     * @param id 替代组行ID
     */
    void delete(Long id);
}

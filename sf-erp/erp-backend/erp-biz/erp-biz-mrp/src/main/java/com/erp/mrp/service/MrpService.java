package com.erp.mrp.service;

import com.erp.common.result.PageResult;
import com.erp.mrp.dto.form.MrpRunForm;
import com.erp.mrp.dto.vo.BatchDetailVO;
import com.erp.mrp.dto.vo.CalcBatchVO;
import com.erp.mrp.dto.vo.MrpRunResultVO;

/**
 * MRP 运算服务 —— MRPII 闭环的核心运算器。
 * <p>
 * 公式（见 docs/架构设计.md 2.3）：
 * 净需求 = 毛需求 − 现有库存(inv_stock 汇总) − 在途(采购在途)；
 * 子件毛需求 = 父件净需求 × BOM用量 × (1 + 损耗率/100)；
 * 建议日期 = 需求日期 − 固定提前期7天（逐级再减）。
 */
public interface MrpService {

    /**
     * 执行 MRP 运算（事务内写入批次/展开明细/建议三张结果表）。
     *
     * @param form 运算表单（mpsIds 空=全部待运算行）
     * @return 运算结果摘要（批次号/明细数/建议数）
     * @throws com.erp.common.exception.BusinessException 无可运算计划 / 成品无有效BOM / BOM循环引用 / 层级超限
     */
    MrpRunResultVO run(MrpRunForm form);

    /**
     * 分页查询运算批次。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 批次分页
     */
    PageResult<CalcBatchVO> pageBatches(long pageNum, long pageSize);

    /**
     * 按批次查看全树展开明细（每个 MPS 成品一棵树，含层级/毛净需求/建议）。
     *
     * @param batchNo 运算批次号
     * @return 批次树明细
     * @throws com.erp.common.exception.BusinessException 批次不存在
     */
    BatchDetailVO batchDetails(String batchNo);
}

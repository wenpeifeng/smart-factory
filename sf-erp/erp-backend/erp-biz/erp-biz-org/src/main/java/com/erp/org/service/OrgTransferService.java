package com.erp.org.service;

import com.erp.common.result.PageResult;
import com.erp.org.dto.form.TransferForm;
import com.erp.org.dto.request.TransferQuery;
import com.erp.org.dto.vo.TransferLineStatVO;
import com.erp.org.dto.vo.TransferVO;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织间调拨单服务：新增/修改(仅草稿)/分页/详情/审核过账/取消 + 线路统计。
 */
public interface OrgTransferService {

    /**
     * 调拨单分页（状态/线路/关键词/创建日期区间）。
     */
    PageResult<TransferVO> page(TransferQuery query);

    /**
     * 调拨单详情（头 + 明细行，主子表）。
     *
     * @throws com.erp.common.exception.BusinessException 单据不存在
     */
    TransferVO.Detail detail(Long id);

    /**
     * 新增调拨单（草稿）。
     * <p>
     * 明细行成本单价取 cost_flow 最近加权价，内部结算单价按价目规则自动计算（无价目=成本）。
     *
     * @return 调拨单 ID
     * @throws com.erp.common.exception.BusinessException 工厂/仓库归属校验失败等
     */
    Long create(TransferForm form);

    /**
     * 修改调拨单（仅草稿可改；明细整体重算重存）。
     *
     * @throws com.erp.common.exception.BusinessException 非草稿状态或校验失败
     */
    void update(Long id, TransferForm form);

    /**
     * 审核过账（事务内）：调出仓扣库存 + 调入仓加库存 + inv_movement 两行
     * （ORG_OUT 负数 / ORG_IN 正数，remark 记 IT 单号）；任一行库存不足整体回滚。
     *
     * @throws com.erp.common.exception.BusinessException 非草稿/库存不足/并发审核冲突
     */
    void audit(Long id);

    /**
     * 取消（仅草稿；已审核单不允许取消，需另开红字单冲销——见 org.md 边界说明）。
     *
     * @throws com.erp.common.exception.BusinessException 非草稿状态
     */
    void cancel(Long id);

    /**
     * 明细价格预演（保存前前端实时展示成本/结算价列用，不落库）。
     *
     * @return 与提交行序一致的明细行（含自动计算的价格列）
     * @throws com.erp.common.exception.BusinessException 校验失败
     */
    List<TransferVO.LineVO> preview(TransferForm form);

    /**
     * 期间调拨金额按线路汇总（已审核单，审核时间落在 [from, to] 闭区间）。
     */
    List<TransferLineStatVO> statisticsLine(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);
}

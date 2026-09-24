package com.erp.sales.service;

import com.erp.common.result.PageResult;
import com.erp.sales.dto.form.SalQuotationForm;
import com.erp.sales.dto.form.SalQuotationToOrderForm;
import com.erp.sales.dto.vo.QuotationMarginBundle;
import com.erp.sales.dto.vo.SalQuotationDetailBundle;
import com.erp.sales.dto.vo.SalQuotationVO;

import java.time.LocalDate;

/**
 * 销售报价单 Service。
 * <p>
 * 状态机：0草稿 →(提交报价)→ 10已报价 →(下推)→ 20已转订单（终态）；
 * 已报价且过有效期 = 30已过期（查询时动态判定）；0/10 →(作废)→ 40已作废（终态）。
 * 草稿可修改/删除；下推销售订单复用 {@link SalOrderService}（注入调用，不改既有代码）。
 */
public interface SalQuotationService {

    /**
     * 分页查询报价单（有效状态含动态 30已过期）。
     *
     * @param pageNum    页码（从 1 开始）
     * @param pageSize   每页条数
     * @param keyword    关键词（报价单号/客户名称，可空）
     * @param customerId 客户 ID（可空）
     * @param status     有效状态（可空；传 30 查已过期）
     * @param validFrom  有效期至起（可空）
     * @param validTo    有效期至止（可空）
     * @return 报价单分页
     */
    PageResult<SalQuotationVO> page(long pageNum, long pageSize, String keyword,
                                    Long customerId, Integer status,
                                    LocalDate validFrom, LocalDate validTo);

    /**
     * 查询报价单详情（表头 + 明细行，含成本参考快照）。
     *
     * @param id 报价单 ID
     * @return 详情聚合 VO
     * @throws com.erp.common.exception.BusinessException 报价单不存在
     */
    SalQuotationDetailBundle getDetail(Long id);

    /**
     * 新增报价单（草稿态），明细成本参考取 base_material.ref_cost 快照。
     *
     * @param form 报价单表单
     * @return 新报价单 ID
     * @throws com.erp.common.exception.BusinessException 客户/物料不存在、日期或折扣率非法
     */
    Long create(SalQuotationForm form);

    /**
     * 修改报价单（仅草稿，明细整体替换，成本快照按最新 ref_cost 重取）。
     *
     * @param id   报价单 ID
     * @param form 报价单表单
     * @throws com.erp.common.exception.BusinessException 不存在或非草稿态
     */
    void update(Long id, SalQuotationForm form);

    /**
     * 删除报价单（仅草稿，逻辑删除，含明细行）。
     *
     * @param id 报价单 ID
     * @throws com.erp.common.exception.BusinessException 不存在或非草稿态
     */
    void delete(Long id);

    /**
     * 提交报价：0草稿 → 10已报价（此后可下推订单/可作废）。
     *
     * @param id 报价单 ID
     * @throws com.erp.common.exception.BusinessException 非草稿态或并发冲突
     */
    void submit(Long id);

    /**
     * 作废报价：0草稿/10已报价 → 40已作废（终态）。
     *
     * @param id 报价单 ID
     * @throws com.erp.common.exception.BusinessException 已转订单/已作废或并发冲突
     */
    void voidQuotation(Long id);

    /**
     * 行毛利率视图：(报价单价 − 成本参考)/报价单价，低于警戒线(20%)的行标记 warn。
     *
     * @param id 报价单 ID
     * @return 毛利率聚合视图
     * @throws com.erp.common.exception.BusinessException 报价单不存在
     */
    QuotationMarginBundle margin(Long id);

    /**
     * 下推生成销售订单草稿：校验[已报价且未过期]→组装订单
     * （客户/物料/数量带入，单价 = 报价 × (1 − 整单折扣率)，备注记"报价单生成"）
     * →调 {@link SalOrderService#create}→回写报价单状态 20已转订单 + 订单 ID/单号。
     *
     * @param id   报价单 ID
     * @param form 下推参数（仓库可空=取第一个在用仓库）
     * @return 生成的销售订单 ID
     * @throws com.erp.common.exception.BusinessException 非已报价态/已过期/订单创建失败
     */
    Long toOrder(Long id, SalQuotationToOrderForm form);
}

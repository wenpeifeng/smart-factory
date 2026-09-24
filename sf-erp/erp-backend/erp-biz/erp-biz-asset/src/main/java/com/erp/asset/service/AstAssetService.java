package com.erp.asset.service;

import com.erp.asset.dto.form.AssetForm;
import com.erp.asset.dto.form.DisposeForm;
import com.erp.asset.dto.request.AssetQuery;
import com.erp.asset.dto.vo.AssetVO;
import com.erp.asset.dto.vo.DisposeResultVO;
import com.erp.common.result.PageResult;

/**
 * 资产卡片服务：CRUD、入账自动凭证、处置自动凭证。
 */
public interface AstAssetService {

    /**
     * 资产分页查询（类别/状态/部门过滤 + 编码/名称模糊）。
     *
     * @param query    查询条件
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 页大小
     * @return 资产 VO 分页（含类别/部门/供应商名称回显）
     */
    PageResult<AssetVO> page(AssetQuery query, long pageNum, long pageSize);

    /**
     * 资产详情。
     *
     * @param id 资产 ID
     * @return 资产 VO
     * @throws com.erp.common.exception.BusinessException 资产不存在
     */
    AssetVO detail(Long id);

    /**
     * 新增资产卡片：生成 ZC 编码，并自动生成入账凭证
     * （借 固定资产(类别资产科目) / 贷 应付账款 2202，摘要"资产入账 {编码}"，POSTED）。
     *
     * @param form 资产表单
     * @return 资产 ID
     * @throws com.erp.common.exception.BusinessException 类别不存在 / 金额非法 / 科目非法
     */
    Long create(AssetForm form);

    /**
     * 修改资产：基本信息任意改；财务字段（类别/原值/残值/年限/入账日期）
     * 在已计提折旧或已处置后禁止变更；状态仅允许 在用/闲置 互转。
     *
     * @param id   资产 ID
     * @param form 资产表单
     * @throws com.erp.common.exception.BusinessException 资产不存在 / 已处置 / 财务字段被锁定后变更
     */
    void update(Long id, AssetForm form);

    /**
     * 删除资产（逻辑删除）：已计提折旧 / 已处置 / 存在折旧明细的资产禁止删除。
     *
     * @param id 资产 ID
     * @throws com.erp.common.exception.BusinessException 资产不存在 / 不满足删除前置条件
     */
    void delete(Long id);

    /**
     * 资产处置：净值转固定资产清理（借贷平衡自动凭证），状态 → DISPOSED，此后停提折旧。
     * <p>
     * 凭证分录：借 累计折旧(已提额) + 借 固定资产清理(净值) / 贷 固定资产(原值)；
     * 清理收入 &gt;0 时追加 借 库存现金 / 贷 固定资产清理。
     *
     * @param id   资产 ID
     * @param form 处置表单
     * @return 处置结果（含凭证 ID/凭证号）
     * @throws com.erp.common.exception.BusinessException 资产不存在 / 已处置
     */
    DisposeResultVO dispose(Long id, DisposeForm form);
}

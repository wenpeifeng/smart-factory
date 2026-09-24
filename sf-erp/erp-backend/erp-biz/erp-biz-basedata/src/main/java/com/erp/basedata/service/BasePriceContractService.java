package com.erp.basedata.service;

import com.erp.basedata.dto.form.PriceContractForm;
import com.erp.basedata.dto.vo.PriceContractVO;
import com.erp.basedata.entity.BasePriceContract;
import com.erp.common.result.PageResult;

/**
 * 采购合同价 Service（CRUD + 参照查询）。
 */
public interface BasePriceContractService {

    /**
     * 合同价分页。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param supplierId 供应商（可空）
     * @param materialId 物料（可空）
     * @param status     状态（可空）
     * @return 合同价分页
     */
    PageResult<PriceContractVO> page(long pageNum, long pageSize, Long supplierId, Long materialId, Integer status);

    /**
     * 取供应商+物料当前生效的合同价（status=1 且今天在 [from,to] 窗口内；
     * 重叠时取最新一条）。录单参照时合同价优先展示。
     *
     * @param supplierId 供应商 ID
     * @param materialId 物料 ID
     * @return 生效合同价；无则 null
     */
    BasePriceContract findEffective(Long supplierId, Long materialId);

    /**
     * 新增合同价。
     *
     * @param form 表单
     * @return 新增 ID
     */
    Long create(PriceContractForm form);

    /**
     * 修改合同价。
     *
     * @param id   ID
     * @param form 表单
     */
    void update(Long id, PriceContractForm form);

    /**
     * 删除合同价（逻辑删除）。
     *
     * @param id ID
     */
    void delete(Long id);
}

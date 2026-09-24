package com.erp.org.service;

import com.erp.common.result.PageResult;
import com.erp.org.dto.form.TransferPriceForm;
import com.erp.org.dto.request.TransferPriceQuery;
import com.erp.org.dto.vo.TransferPriceVO;

/**
 * 组织间调拨价目服务：CRUD / 启停。
 */
public interface OrgTransferPriceService {

    /**
     * 价目分页（物料/调出工厂/调入工厂/状态，带名称回显）。
     */
    PageResult<TransferPriceVO> page(TransferPriceQuery query);

    /**
     * 新增价目（线路 = 物料×调出工厂×调入工厂，唯一）。
     *
     * @return 价目 ID
     * @throws com.erp.common.exception.BusinessException 物料/工厂不存在、线路重复等
     */
    Long create(TransferPriceForm form);

    /**
     * 修改价目。
     *
     * @throws com.erp.common.exception.BusinessException 价目不存在或校验失败
     */
    void update(Long id, TransferPriceForm form);

    /**
     * 删除价目（逻辑删除）。
     */
    void delete(Long id);

    /**
     * 启用/停用价目（停用价目不参与结算价计算，等于“无价目=按成本结算”）。
     */
    void updateStatus(Long id, Integer status);
}

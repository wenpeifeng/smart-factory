package com.erp.basedata.service;

import com.erp.basedata.dto.form.BaseSettleTypeForm;
import com.erp.basedata.dto.vo.BaseSettleTypeVO;

import java.util.List;

/**
 * 结算方式 Service。
 * <p>对外能力：listAll 供销售/采购/财务模块选择结算方式并计算账期。
 */
public interface BaseSettleTypeService {

    /**
     * 全部结算方式（含停用，管理页用）。
     *
     * @return 结算方式 VO 列表
     */
    List<BaseSettleTypeVO> listAll();

    /**
     * 启用中的结算方式（下拉选择用）。
     *
     * @return 启用结算方式 VO 列表
     */
    List<BaseSettleTypeVO> listEnabled();

    /**
     * 按 ID 查详情。
     *
     * @param id 结算方式 ID
     * @return 结算方式 VO
     * @throws com.erp.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseSettleTypeVO getById(Long id);

    /**
     * 新增结算方式。
     *
     * @param form 结算方式表单
     * @return 新增后的 ID
     */
    Long create(BaseSettleTypeForm form);

    /**
     * 修改结算方式。
     *
     * @param id   结算方式 ID
     * @param form 结算方式表单
     */
    void update(Long id, BaseSettleTypeForm form);

    /**
     * 删除结算方式（逻辑删除）。
     *
     * @param id 结算方式 ID
     * @throws com.erp.common.exception.BusinessException DATA_IN_USE 被客户/供应商引用时
     */
    void delete(Long id);
}

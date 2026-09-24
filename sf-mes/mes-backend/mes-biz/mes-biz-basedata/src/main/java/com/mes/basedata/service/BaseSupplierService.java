package com.mes.basedata.service;

import com.mes.basedata.dto.form.BaseSupplierForm;
import com.mes.basedata.dto.vo.BaseSupplierVO;
import com.mes.basedata.entity.BaseSupplier;
import com.mes.common.result.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 供应商 Service。
 * <p>对外能力：getByIds / listEnabled 供采购、财务（应付）模块使用。
 */
public interface BaseSupplierService {

    /**
     * 分页查询供应商。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  关键词，模糊匹配编码/名称/联系人（可空）
     * @return 分页结果
     */
    PageResult<BaseSupplierVO> page(long pageNum, long pageSize, String keyword);

    /**
     * 按 ID 查详情。
     *
     * @param id 供应商 ID
     * @return 供应商 VO
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseSupplierVO getById(Long id);

    /**
     * 新增供应商（编码留空时自动生成 GYS+日期+3位流水）。
     *
     * @param form 供应商表单
     * @return 新增后的 ID
     */
    Long create(BaseSupplierForm form);

    /**
     * 修改供应商。
     *
     * @param id   供应商 ID
     * @param form 供应商表单
     */
    void update(Long id, BaseSupplierForm form);

    /**
     * 删除供应商（逻辑删除）。
     * <p>采购/应付模块上线后需补充单据引用校验并抛 DATA_IN_USE。
     *
     * @param id 供应商 ID
     */
    void delete(Long id);

    /**
     * 批量按 ID 查实体（跨模块用）。
     *
     * @param ids 供应商 ID 集合
     * @return 供应商实体列表
     */
    List<BaseSupplier> getByIds(Collection<Long> ids);

    /**
     * 启用供应商搜索（单据选择器用，最多 50 条）。
     *
     * @param keyword 编码/名称关键词（可空）
     * @return 启用供应商 VO 列表
     */
    List<BaseSupplierVO> listEnabled(String keyword);
}

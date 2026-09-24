package com.mes.basedata.service;

import com.mes.basedata.dto.form.BaseCustomerForm;
import com.mes.basedata.dto.vo.BaseCustomerVO;
import com.mes.basedata.entity.BaseCustomer;
import com.mes.common.result.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 客户 Service。
 * <p>对外能力：getByIds / listEnabled 供销售、财务（应收）模块使用。
 */
public interface BaseCustomerService {

    /**
     * 分页查询客户。
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  关键词，模糊匹配编码/名称/联系人（可空）
     * @return 分页结果
     */
    PageResult<BaseCustomerVO> page(long pageNum, long pageSize, String keyword);

    /**
     * 按 ID 查详情。
     *
     * @param id 客户 ID
     * @return 客户 VO
     * @throws com.mes.common.exception.BusinessException DATA_NOT_EXISTS 若不存在
     */
    BaseCustomerVO getById(Long id);

    /**
     * 新增客户（编码留空时自动生成 KH+日期+3位流水）。
     *
     * @param form 客户表单
     * @return 新增后的 ID
     */
    Long create(BaseCustomerForm form);

    /**
     * 修改客户。
     *
     * @param id   客户 ID
     * @param form 客户表单
     */
    void update(Long id, BaseCustomerForm form);

    /**
     * 删除客户（逻辑删除）。
     * <p>销售/应收模块上线后需补充单据引用校验并抛 DATA_IN_USE。
     *
     * @param id 客户 ID
     */
    void delete(Long id);

    /**
     * 批量按 ID 查实体（跨模块用）。
     *
     * @param ids 客户 ID 集合
     * @return 客户实体列表
     */
    List<BaseCustomer> getByIds(Collection<Long> ids);

    /**
     * 启用客户搜索（单据选择器用，最多 50 条）。
     *
     * @param keyword 编码/名称关键词（可空）
     * @return 启用客户 VO 列表
     */
    List<BaseCustomerVO> listEnabled(String keyword);
}

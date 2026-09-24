package com.erp.system.service;

import com.erp.system.dto.dept.DeptForm;
import com.erp.system.dto.dept.DeptVO;

import java.util.List;

/**
 * 部门管理服务（树形，parent_id 自关联）。
 */
public interface SysDeptService {

    /**
     * 全量部门树。
     *
     * @param keyword 名称模糊过滤（可选）
     * @return 部门树
     */
    List<DeptVO> tree(String keyword);

    /**
     * 部门详情。
     *
     * @param id 部门 ID
     * @return 部门 VO
     */
    DeptVO getDetail(Long id);

    /**
     * 新增部门。
     *
     * @param form 部门表单
     * @return 新部门 ID
     */
    Long create(DeptForm form);

    /**
     * 修改部门（上级不允许选自己或自己的后代）。
     *
     * @param form 部门表单（含 id）
     */
    void update(DeptForm form);

    /**
     * 删除部门（存在下级部门或挂靠用户时拒绝）。
     *
     * @param id 部门 ID
     */
    void delete(Long id);
}

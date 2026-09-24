package com.erp.hr.service;

import com.erp.common.result.PageResult;
import com.erp.hr.dto.form.EmployeeForm;
import com.erp.hr.dto.form.ResignForm;
import com.erp.hr.dto.vo.EmployeeOptionVO;
import com.erp.hr.dto.vo.EmployeeVO;

import java.util.List;

/**
 * 员工档案服务 —— CRUD、分页（部门/状态/姓名）、离职办理。
 * <p>
 * 身份证号出参一律脱敏（{@link EmployeeVO#maskIdCard}）；离职为单向操作。
 */
public interface HrEmployeeService {

    /**
     * 分页查询员工。
     *
     * @param pageNum 页码
     * @param pageSize 页大小
     * @param keyword  姓名模糊
     * @param deptId   部门过滤
     * @param status   状态过滤（ON_JOB/RESIGNED）
     */
    PageResult<EmployeeVO> page(long pageNum, long pageSize, String keyword, Long deptId, String status);

    /** 员工详情（身份证脱敏） */
    EmployeeVO detail(Long id);

    /** 新增员工（工号自动生成 EMP+日期+流水） */
    Long create(EmployeeForm form);

    /** 修改员工基本信息（状态/离职日期不可经此接口变更） */
    void update(Long id, EmployeeForm form);

    /** 删除员工（逻辑删除） */
    void delete(Long id);

    /** 离职办理：填离职日期 + 状态置 RESIGNED（单向） */
    void resign(Long id, ResignForm form);

    /** 员工下拉选项（keyword 匹配工号/姓名，默认在职） */
    List<EmployeeOptionVO> options(String keyword);
}

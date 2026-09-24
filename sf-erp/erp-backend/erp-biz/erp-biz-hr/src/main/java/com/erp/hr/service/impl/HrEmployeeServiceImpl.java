package com.erp.hr.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.hr.constant.HrConstants;
import com.erp.hr.dto.form.EmployeeForm;
import com.erp.hr.dto.form.ResignForm;
import com.erp.hr.dto.vo.EmployeeOptionVO;
import com.erp.hr.dto.vo.EmployeeVO;
import com.erp.hr.entity.HrEmployee;
import com.erp.hr.mapper.HrEmployeeMapper;
import com.erp.hr.mapper.HrIntegrationMapper;
import com.erp.hr.service.HrEmployeeService;
import com.erp.hr.service.SeqNoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 员工档案服务实现。
 * <p>
 * 出参 VO 身份证号统一脱敏；部门归属校验走集成网关只读 sys_dept；
 * 离职办理为单向状态流转，修改接口不允许直接改状态/离职日期。
 */
@Service
@RequiredArgsConstructor
public class HrEmployeeServiceImpl implements HrEmployeeService {

    private final HrEmployeeMapper employeeMapper;
    private final HrIntegrationMapper integrationMapper;
    private final SeqNoService seqNoService;

    @Override
    public PageResult<EmployeeVO> page(long pageNum, long pageSize, String keyword, Long deptId, String status) {
        var wrapper = Wrappers.lambdaQuery(HrEmployee.class)
                .like(keyword != null && !keyword.isBlank(), HrEmployee::getName, keyword)
                .eq(deptId != null, HrEmployee::getDeptId, deptId)
                .eq(status != null && !status.isBlank(), HrEmployee::getStatus, status)
                .orderByAsc(HrEmployee::getEmpNo);
        PageResult<HrEmployee> page = PageUtils.of(employeeMapper, wrapper, pageNum, pageSize);
        Map<Long, String> deptNames = deptNameMap(page.getRecords());
        return page.map(e -> toVO(e, deptNames));
    }

    @Override
    public EmployeeVO detail(Long id) {
        return toVO(requireEmployee(id), deptNameMap(List.of()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(EmployeeForm form) {
        validateDept(form.getDeptId());
        HrEmployee e = new HrEmployee();
        applyForm(e, form);
        e.setEmpNo(seqNoService.next(HrConstants.PREFIX_EMP_NO));
        e.setStatus(HrConstants.EMP_ON_JOB);
        employeeMapper.insert(e);
        return e.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, EmployeeForm form) {
        HrEmployee exists = requireEmployee(id);
        // 离职员工档案冻结基本信息，防止误改（如需修正走线下流程）
        if (HrConstants.EMP_RESIGNED.equals(exists.getStatus())) {
            throw BusinessException.ofKey("erp.hr.resigned-no-modify");
        }
        validateDept(form.getDeptId());
        HrEmployee e = new HrEmployee();
        e.setId(id);
        applyForm(e, form);
        employeeMapper.updateById(e);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireEmployee(id);
        employeeMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resign(Long id, ResignForm form) {
        HrEmployee e = requireEmployee(id);
        if (HrConstants.EMP_RESIGNED.equals(e.getStatus())) {
            throw BusinessException.ofKey("erp.hr.resigned-no-repeat");
        }
        if (form.getResignDate().isBefore(e.getHireDate())) {
            throw BusinessException.ofKey("erp.hr.leave-date-order", form.getResignDate(), e.getHireDate());
        }
        HrEmployee upd = new HrEmployee();
        upd.setId(id);
        upd.setResignDate(form.getResignDate());
        upd.setStatus(HrConstants.EMP_RESIGNED);
        employeeMapper.updateById(upd);
    }

    @Override
    public List<EmployeeOptionVO> options(String keyword) {
        var wrapper = Wrappers.lambdaQuery(HrEmployee.class)
                .eq(HrEmployee::getStatus, HrConstants.EMP_ON_JOB)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(HrEmployee::getEmpNo, keyword)
                        .or()
                        .like(HrEmployee::getName, keyword))
                .orderByAsc(HrEmployee::getEmpNo)
                .last("LIMIT 100");
        return employeeMapper.selectList(wrapper).stream().map(e -> {
            EmployeeOptionVO vo = new EmployeeOptionVO();
            vo.setId(e.getId());
            vo.setEmpNo(e.getEmpNo());
            vo.setName(e.getName());
            vo.setStatus(e.getStatus());
            return vo;
        }).toList();
    }

    // ==================== 私有工具 ====================

    private HrEmployee requireEmployee(Long id) {
        HrEmployee e = employeeMapper.selectById(id);
        if (e == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.hr.employee-not-exists", id);
        }
        return e;
    }

    /** 部门存在性校验（sys_dept 只读约定列；未填部门跳过） */
    private void validateDept(Long deptId) {
        if (deptId == null) {
            return;
        }
        if (integrationMapper.selectDeptName(deptId) == null) {
            throw BusinessException.ofKey("erp.hr.dept-not-exists", deptId);
        }
    }

    private void applyForm(HrEmployee e, EmployeeForm form) {
        e.setName(form.getName());
        e.setGender(form.getGender());
        e.setPhone(blankToNull(form.getPhone()));
        e.setIdCard(blankToNull(form.getIdCard()));
        e.setDeptId(form.getDeptId());
        e.setPosition(blankToNull(form.getPosition()));
        e.setHireDate(form.getHireDate());
        e.setBaseSalary(form.getBaseSalary());
        e.setUserId(form.getUserId());
        e.setRemark(form.getRemark());
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** 批量取部门名（去重后逐个查询，员工页数据量小可接受；部门可能已删，null 容忍） */
    private Map<Long, String> deptNameMap(List<HrEmployee> employees) {
        Map<Long, String> map = new java.util.HashMap<>();
        for (HrEmployee e : employees) {
            if (e.getDeptId() != null && !map.containsKey(e.getDeptId())) {
                map.put(e.getDeptId(), integrationMapper.selectDeptName(e.getDeptId()));
            }
        }
        return map;
    }

    private EmployeeVO toVO(HrEmployee e, Map<Long, String> deptNames) {
        EmployeeVO vo = new EmployeeVO();
        vo.setId(e.getId());
        vo.setEmpNo(e.getEmpNo());
        vo.setName(e.getName());
        vo.setGender(e.getGender());
        vo.setPhone(e.getPhone());
        vo.setIdCard(EmployeeVO.maskIdCard(e.getIdCard()));
        vo.setDeptId(e.getDeptId());
        vo.setDeptName(e.getDeptId() == null ? null : deptNames.get(e.getDeptId()));
        vo.setPosition(e.getPosition());
        vo.setHireDate(e.getHireDate());
        vo.setResignDate(e.getResignDate());
        vo.setStatus(e.getStatus());
        vo.setBaseSalary(e.getBaseSalary());
        vo.setUserId(e.getUserId());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}

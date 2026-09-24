package com.erp.hr.controller;

import com.erp.common.result.Result;
import com.erp.hr.dto.vo.DeptBriefVO;
import com.erp.hr.dto.vo.EmployeeOptionVO;
import com.erp.hr.dto.vo.MaterialBriefVO;
import com.erp.hr.mapper.HrIntegrationMapper;
import com.erp.hr.service.HrEmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人力资源支撑接口 —— 部门/物料/员工下拉数据（跨模块只读走集成网关）。
 */
@Tag(name = "人力资源-支撑数据")
@RestController
@RequestMapping("/hr/support")
@RequiredArgsConstructor
public class HrSupportController {

    private final HrIntegrationMapper integrationMapper;
    private final HrEmployeeService employeeService;

    /** 部门下拉（含 parentId 供前端组树，来源 sys_dept） */
    @Operation(summary = "部门下拉")
    @GetMapping("/depts")
    public Result<List<DeptBriefVO>> depts() {
        return Result.ok(integrationMapper.selectDepts());
    }

    /** 物料选择器（编码/名称模糊，限 50 条，来源 base_material） */
    @Operation(summary = "物料选择器")
    @GetMapping("/materials")
    public Result<List<MaterialBriefVO>> materials(@RequestParam(required = false) String keyword) {
        return Result.ok(integrationMapper.selectMaterials(keyword));
    }

    /** 在职员工下拉（工号/姓名模糊） */
    @Operation(summary = "员工下拉")
    @GetMapping("/employees")
    public Result<List<EmployeeOptionVO>> employees(@RequestParam(required = false) String keyword) {
        return Result.ok(employeeService.options(keyword));
    }
}

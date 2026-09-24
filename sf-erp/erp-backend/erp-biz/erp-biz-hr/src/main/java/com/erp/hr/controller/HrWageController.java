package com.erp.hr.controller;

import com.erp.common.result.Result;
import com.erp.hr.dto.vo.WageSummaryVO;
import com.erp.hr.service.WageSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 工资汇总接口 —— 员工应发 = 计件合计 + 加班天数×50 − 月薪基准/30×缺勤天数，
 * 仅计算汇总不生成凭证（凭证联动由 finance 后续扩展）。
 */
@Tag(name = "人力资源-工资汇总")
@RestController
@RequestMapping("/hr/wages")
@RequiredArgsConstructor
public class HrWageController {

    private final WageSummaryService wageSummaryService;

    /** 月度工资汇总 */
    @Operation(summary = "月度工资汇总")
    @RequirePerm("hr:piecewage:list")
    @GetMapping("/summary")
    public Result<List<WageSummaryVO>> summary(@RequestParam String month) {
        return Result.ok(wageSummaryService.summary(month));
    }
}

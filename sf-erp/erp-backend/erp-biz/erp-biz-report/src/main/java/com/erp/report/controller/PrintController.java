package com.erp.report.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.report.dto.vo.PrintDataVO;
import com.erp.report.dto.vo.PrintDocRowVO;
import com.erp.report.service.PrintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.security.RequirePerm;

/**
 * 单据打印中心接口（打印中心前端页面的唯一数据源）。
 * <p>
 * 两类接口：
 * <ul>
 *   <li>GET /report/print/list/{bizType}：打印单据选择列表（分页）；</li>
 *   <li>GET /report/print/{bizType}/{id}：标准打印 VO（前端 A4 模板直接渲染），
 *       statement 额外支持 ?month=yyyy-MM（默认当月）。</li>
 * </ul>
 * bizType=sal_order|delivery_note|pur_order|statement|voucher。全部只读。
 */
@Tag(name = "报表中心-单据打印")
@RestController
@RequestMapping("/report/print")
@RequiredArgsConstructor
public class PrintController {

    private final PrintService printService;

    /**
     * 打印单据选择列表（编号/往来单位/日期/金额/状态）。
     *
     * @param bizType 业务类型 sal_order|delivery_note|pur_order|statement|voucher
     * @param pageNum 页码（默认 1）
     * @param pageSize 页大小（默认 10，上限 100）
     * @param keyword  单号/往来单位模糊过滤（可空）
     * @param month    对账月份 yyyy-MM（仅 statement）
     */
    @Operation(summary = "打印单据选择列表（分页）")
    @RequirePerm("print:center:list")
    @GetMapping("/list/{bizType}")
    public Result<PageResult<PrintDocRowVO>> list(
            @Parameter(description = "业务类型") @PathVariable String bizType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String month) {
        return Result.ok(printService.listDocs(bizType, pageNum, pageSize, keyword, month));
    }

    /**
     * 打印数据：按单据类型返回标准打印 VO（主信息区/明细/合计/大写金额/签字栏）。
     *
     * @param bizType 业务类型 sal_order|delivery_note|pur_order|statement|voucher
     * @param id      单据 ID（statement 为客户 ID）
     * @param month   对账月份 yyyy-MM（仅 statement，默认当月）
     */
    @Operation(summary = "打印数据（标准打印 VO）")
    @RequirePerm("print:center:list")
    @GetMapping("/{bizType}/{id}")
    public Result<PrintDataVO> printData(
            @Parameter(description = "业务类型") @PathVariable String bizType,
            @Parameter(description = "单据 ID（statement 为客户 ID）") @PathVariable Long id,
            @RequestParam(required = false) String month) {
        return Result.ok(printService.buildPrintData(bizType, id, month));
    }
}

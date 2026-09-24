package com.erp.finance.controller;

import com.erp.common.result.Result;
import com.erp.finance.dto.request.BalanceSheetQuery;
import com.erp.finance.dto.request.IncomeStatementQuery;
import com.erp.finance.dto.request.PartnerAgingQuery;
import com.erp.finance.dto.vo.BalanceSheetVO;
import com.erp.finance.dto.vo.IncomeStatementVO;
import com.erp.finance.dto.vo.PartnerAgingVO;
import com.erp.finance.dto.vo.TrialBalanceVO;
import com.erp.finance.service.FinReportService;
import com.erp.finance.service.FinVoucherService;
import com.erp.finance.support.FinanceCsvExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.erp.framework.security.RequirePerm;

/**
 * 财务报表接口：试算平衡表 + 三大报表深化（资产负债表 / 利润表 / 应收应付账龄）。
 * <p>
 * 资产负债表与利润表从科目余额（POSTED 凭证分录聚合）取数，
 * 行项目映射 fin_report_mapping 驱动；账龄基于未结清应收应付行分桶。
 * 四个新接口均支持 {@code &export=csv} 附件导出；bookId 参数显式传入时
 * 优先于 X-Book-Id 请求头（供报表中心 CSV 下载等无法携带请求头的场景）。
 */
@Tag(name = "财务管理-账表")
@RestController
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinReportController {

    private final FinVoucherService voucherService;
    private final FinReportService reportService;

    @Operation(summary = "试算平衡表（期初/本期/期末 六栏 + 合计 + 是否平衡）")
    @RequirePerm("finance:trial:list")
    @GetMapping("/trial-balance")
    public Result<TrialBalanceVO> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(voucherService.trialBalance(from, to));
    }

    @Operation(summary = "资产负债表（期初/期末两列，资产=负债+权益 勾稽断言，export=csv 导出）")
    @RequirePerm("finance:report:list")
    @GetMapping("/reports/balance-sheet")
    public Object balanceSheet(BalanceSheetQuery query, HttpServletResponse response) throws IOException {
        BalanceSheetVO vo = reportService.balanceSheet(query.getPeriod(), query.getBookId());
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("行项目", "方向", "科目编码", "期初余额(元)", "期末余额(元)");
        List<List<String>> data = new ArrayList<>(vo.rows().size() + 4);
        for (BalanceSheetVO.Row r : vo.rows()) {
            data.add(List.of(
                    FinanceCsvExporter.nvl(r.itemName()),
                    FinanceCsvExporter.nvl(r.direction()),
                    FinanceCsvExporter.nvl(r.subjectCodes()),
                    FinanceCsvExporter.plain(r.opening()),
                    FinanceCsvExporter.plain(r.closing())));
        }
        data.add(List.of("勾稽：期末差额(资产-负债-权益)", "", "", "", FinanceCsvExporter.plain(vo.diff())));
        data.add(List.of("勾稽：期初差额(资产-负债-权益)", "", "", "", FinanceCsvExporter.plain(vo.openingDiff())));
        data.add(List.of("是否平衡(容差0.01)", "", "", "", vo.balanced() ? "是" : "否"));
        for (String w : vo.warnings()) {
            data.add(List.of("口径提示", "", "", "", w));
        }
        FinanceCsvExporter.export(response, FinanceCsvExporter.filename("balance_sheet",
                vo.period().replace("-", "")), headers, data);
        return null;
    }

    @Operation(summary = "利润表（本月数+本年累计数，export=csv 导出）")
    @RequirePerm("finance:report:list")
    @GetMapping("/reports/income-statement")
    public Object incomeStatement(IncomeStatementQuery query, HttpServletResponse response) throws IOException {
        IncomeStatementVO vo = reportService.incomeStatement(
                query.getPeriod(), query.getFrom(), query.getTo(), query.getBookId());
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        List<String> headers = List.of("行项目", "方向", "科目编码", "本月数(元)", "本年累计数(元)");
        List<List<String>> data = new ArrayList<>(vo.rows().size() + 2);
        for (IncomeStatementVO.Row r : vo.rows()) {
            data.add(List.of(
                    FinanceCsvExporter.nvl(r.itemName()),
                    FinanceCsvExporter.nvl(r.direction()),
                    FinanceCsvExporter.nvl(r.subjectCodes()),
                    FinanceCsvExporter.plain(r.monthAmount()),
                    FinanceCsvExporter.plain(r.ytdAmount())));
        }
        data.add(List.of("本期区间", "", "", vo.from() + "~" + vo.to(), vo.ytdFrom() + "~" + vo.ytdTo()));
        for (String w : vo.warnings()) {
            data.add(List.of("口径提示", "", "", "", w));
        }
        FinanceCsvExporter.export(response, FinanceCsvExporter.filename("income_statement",
                vo.period().replace("-", "")), headers, data);
        return null;
    }

    @Operation(summary = "应收账龄分析（客户×账龄分桶，未结算余额=amount-settled，export=csv 导出）")
    @RequirePerm("finance:report:list")
    @GetMapping("/reports/ar-aging")
    public Object arAging(PartnerAgingQuery query, HttpServletResponse response) throws IOException {
        PartnerAgingVO vo = reportService.arAging(query.getAsOf(), query.getBookId());
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        return agingCsv(vo, response, "ar_aging");
    }

    @Operation(summary = "应付账龄分析（供应商×账龄分桶，与应收对称，export=csv 导出）")
    @RequirePerm("finance:report:list")
    @GetMapping("/reports/ap-aging")
    public Object apAging(PartnerAgingQuery query, HttpServletResponse response) throws IOException {
        PartnerAgingVO vo = reportService.apAging(query.getAsOf(), query.getBookId());
        if (!"csv".equalsIgnoreCase(query.getExport())) {
            return Result.ok(vo);
        }
        return agingCsv(vo, response, "ap_aging");
    }

    /** 账龄 CSV（应收/应付共用：客商行 + 合计行 + 分桶占比） */
    private Object agingCsv(PartnerAgingVO vo, HttpServletResponse response, String prefix) throws IOException {
        String partnerTitle = "RECEIVABLE".equals(vo.side()) ? "客户" : "供应商";
        List<String> headers = List.of(partnerTitle, "单据数", "1-30天(元)", "31-60天(元)",
                "61-90天(元)", "91-180天(元)", "180天以上(元)", "未结算合计(元)", "占比");
        List<List<String>> data = new ArrayList<>(vo.rows().size() + 2);
        for (PartnerAgingVO.Row r : vo.rows()) {
            data.add(List.of(
                    r.partnerName() == null ? partnerTitle + "#" + r.partnerId() : r.partnerName(),
                    String.valueOf(r.docCount()),
                    FinanceCsvExporter.plain(r.d30()),
                    FinanceCsvExporter.plain(r.d60()),
                    FinanceCsvExporter.plain(r.d90()),
                    FinanceCsvExporter.plain(r.d180()),
                    FinanceCsvExporter.plain(r.d180p()),
                    FinanceCsvExporter.plain(r.total()),
                    FinanceCsvExporter.plainRaw(r.ratio())));
        }
        PartnerAgingVO.Row t = vo.totalRow();
        data.add(List.of("合计", String.valueOf(t.docCount()),
                FinanceCsvExporter.plain(t.d30()), FinanceCsvExporter.plain(t.d60()),
                FinanceCsvExporter.plain(t.d90()), FinanceCsvExporter.plain(t.d180()),
                FinanceCsvExporter.plain(t.d180p()), FinanceCsvExporter.plain(t.total()), "1.0000"));
        for (PartnerAgingVO.Bucket b : vo.buckets()) {
            data.add(List.of("分桶占比", b.label(), FinanceCsvExporter.plain(b.amount()),
                    FinanceCsvExporter.plainRaw(b.ratio()), "", "", "", "", ""));
        }
        FinanceCsvExporter.export(response, FinanceCsvExporter.filename(prefix,
                vo.asOf().toString().replace("-", "")), headers, data);
        return null;
    }
}

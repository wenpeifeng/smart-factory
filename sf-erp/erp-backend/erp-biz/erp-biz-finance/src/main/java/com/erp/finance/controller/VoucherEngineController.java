package com.erp.finance.controller;

import com.erp.common.result.Result;
import com.erp.finance.dto.vo.VoucherPreviewVO;
import com.erp.finance.service.VoucherEngineService;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 凭证模板引擎接口（V521）：试算预览 + 手动触发扫描。
 * <p>
 * 扫描器（30s 自动轮询）见 VoucherEngineScheduleConfig；本接口用于
 * 补扫指定类型/日期区间的历史单据与排查预览。
 */
@Tag(name = "财务管理-凭证引擎")
@RestController
@RequestMapping("/finance/voucher-engine")
@RequiredArgsConstructor
public class VoucherEngineController {

    private final VoucherEngineService engineService;

    @Operation(summary = "试算预览：按当前模板展示将生成的凭证内容（不落库，与实际生成同构）")
    @RequirePerm("finance:voucherTemplate:list")
    @GetMapping("/preview")
    public Result<VoucherPreviewVO> preview(
            @RequestParam String bizType,
            @RequestParam Long bizId) {
        return Result.ok(engineService.preview(bizType, bizId));
    }

    @Operation(summary = "手动触发扫描（当前账套；补扫历史单据可用 from/to 圈定业务日期区间）")
    @RequirePerm("finance:voucherTemplate:edit")
    @OperLog(module = "财务管理", operation = "手动触发凭证扫描")
    @Idempotent
    @PostMapping("/scan")
    public Result<Integer> scan(
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(engineService.scan(bizType, from, to, null));
    }
}

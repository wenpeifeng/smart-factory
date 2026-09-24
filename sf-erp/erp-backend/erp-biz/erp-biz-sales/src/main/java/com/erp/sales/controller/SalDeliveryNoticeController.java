package com.erp.sales.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.sales.dto.form.NoticeSettleForm;
import com.erp.sales.dto.form.SalDeliveryNoticeForm;
import com.erp.sales.dto.vo.PendingStockVO;
import com.erp.sales.dto.vo.SalNoticeDetailBundle;
import com.erp.sales.dto.vo.SalNoticeVO;
import com.erp.sales.service.SalDeliveryNoticeService;
import com.erp.system.annotation.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 销售发货通知单接口。
 * <p>
 * 状态机：0草稿 →(通知)→ 10已通知 →(全部核销)→ 20已出库；0/10（未出库）→(取消)→ 30已取消。
 * 已出库数量回写挂点：出库单 remark 记 FH 单号 + 本接口按出库单号核销（守卫不超通知量）。
 */
@Tag(name = "销售发货通知单")
@RestController
@RequestMapping("/sales/delivery-notices")
@RequiredArgsConstructor
public class SalDeliveryNoticeController {

    private final SalDeliveryNoticeService noticeService;

    /** 分页查询发货通知单（keyword 匹配通知单号/来源订单号/客户名称） */
    @Operation(summary = "分页查询发货通知单")
    @RequirePerm("sales:deliveryNotice:list")
    @GetMapping
    public Result<PageResult<SalNoticeVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(noticeService.page(pageNum, pageSize, keyword, status));
    }

    /** 备货参考：物料 × 仓库可用量（发货前查仓） */
    @Operation(summary = "备货参考（物料×仓库可用量）")
    @RequirePerm("sales:deliveryNotice:list")
    @GetMapping("/pending-stocks")
    public Result<List<PendingStockVO>> pendingStocks(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(noticeService.pendingStocks(warehouseId, keyword));
    }

    /** 查询通知单详情（表头 + 明细行，含已出库/剩余数量） */
    @Operation(summary = "查询发货通知单详情")
    @RequirePerm("sales:deliveryNotice:list")
    @GetMapping("/{id}")
    public Result<SalNoticeDetailBundle> detail(@PathVariable Long id) {
        return Result.ok(noticeService.getDetail(id));
    }

    /** 新增通知单（从订单选单生成 / 无来源直发） */
    @Operation(summary = "新增发货通知单")
    @RequirePerm("sales:deliveryNotice:add")
    @OperLog(module = "销售管理", operation = "新增发货通知单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SalDeliveryNoticeForm form) {
        return Result.ok(noticeService.create(form));
    }

    /** 修改通知单（仅草稿） */
    @Operation(summary = "修改发货通知单")
    @RequirePerm("sales:deliveryNotice:edit")
    @OperLog(module = "销售管理", operation = "修改发货通知单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SalDeliveryNoticeForm form) {
        noticeService.update(id, form);
        return Result.ok();
    }

    /** 删除通知单（仅草稿，逻辑删除） */
    @Operation(summary = "删除发货通知单")
    @RequirePerm("sales:deliveryNotice:delete")
    @OperLog(module = "销售管理", operation = "删除发货通知单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return Result.ok();
    }

    /** 通知发货：0草稿 → 10已通知 */
    @Operation(summary = "通知发货")
    @RequirePerm("sales:deliveryNotice:audit")
    @OperLog(module = "销售管理", operation = "通知发货")
    @Idempotent
    @PutMapping("/{id}/notify")
    public Result<Void> notifyShip(@PathVariable Long id) {
        noticeService.notifyShip(id);
        return Result.ok();
    }

    /** 取消通知：仅未发生出库时允许 */
    @Operation(summary = "取消发货通知")
    @RequirePerm("sales:deliveryNotice:cancel")
    @OperLog(module = "销售管理", operation = "取消发货通知")
    @Idempotent
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        noticeService.cancel(id);
        return Result.ok();
    }

    /** 按出库单号核销出库数量（守卫不超通知量；全部核销到位流转已出库） */
    @Operation(summary = "按出库单号核销出库数量")
    @RequirePerm("sales:deliveryNotice:settle")
    @OperLog(module = "销售管理", operation = "核销发货通知出库数量")
    @Idempotent
    @PostMapping("/{id}/settle")
    public Result<Void> settle(@PathVariable Long id, @Valid @RequestBody NoticeSettleForm form) {
        noticeService.settle(id, form);
        return Result.ok();
    }
}

package com.erp.basedata.controller;

import com.erp.basedata.dto.form.SupplierAdmitForm;
import com.erp.basedata.dto.form.PriceAuditForm;
import com.erp.basedata.dto.vo.SupplierAdmitVO;
import com.erp.basedata.service.SupplierAdmitService;
import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
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

import java.util.Map;

/**
 * 供应商准入接口（V141）。
 * <p>
 * 提交申请后自动尝试挂 workflow（bizType=supplier_admit）；
 * 无启用流程时由管理员经 approve/reject 简单审批。
 */
@Tag(name = "基础资料-供应商准入")
@RestController
@RequestMapping("/basedata/supplier-admit")
@RequiredArgsConstructor
public class SupplierAdmitController {

    private final SupplierAdmitService admitService;

    /** 准入申请分页（查询时惰性同步 workflow 审批结果） */
    @Operation(summary = "准入申请分页")
    @RequirePerm("basedata:supplierAdmit:list")
    @GetMapping
    public Result<PageResult<SupplierAdmitVO>> page(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.ok(admitService.page(pageNum, pageSize, keyword, status));
    }

    /** 申请详情 */
    @Operation(summary = "准入申请详情")
    @RequirePerm("basedata:supplierAdmit:list")
    @GetMapping("/{id}")
    public Result<SupplierAdmitVO> getById(@PathVariable Long id) {
        return Result.ok(admitService.getById(id));
    }

    /** 登记/编辑（submit=true 时登记后立即提交申请） */
    @Operation(summary = "登记/编辑准入申请")
    @RequirePerm(value = "basedata:supplierAdmit:add")
    @OperLog(module = "供应商准入", operation = "登记准入申请")
    @Idempotent
    @PostMapping
    public Result<Long> save(@Valid @RequestBody SupplierAdmitForm form) {
        return Result.ok(admitService.save(form));
    }

    /** 编辑已有申请（草稿/驳回可改，权限单独控） */
    @Operation(summary = "编辑准入申请")
    @RequirePerm("basedata:supplierAdmit:edit")
    @OperLog(module = "供应商准入", operation = "编辑准入申请")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierAdmitForm form) {
        form.setId(id);
        admitService.save(form);
        return Result.ok();
    }

    /** 提交申请（草稿/驳回 → 待审） */
    @Operation(summary = "提交准入申请")
    @RequirePerm("basedata:supplierAdmit:edit")
    @OperLog(module = "供应商准入", operation = "提交准入申请")
    @Idempotent
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        admitService.submit(id);
        return Result.ok();
    }

    /** 管理员通过（待审→通过，供应商启用） */
    @Operation(summary = "通过准入申请")
    @RequirePerm("basedata:supplierAdmit:audit")
    @OperLog(module = "供应商准入", operation = "通过准入申请")
    @Idempotent
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, @Valid @RequestBody(required = false) PriceAuditForm form) {
        admitService.approve(id, form == null ? null : form.getRemark());
        return Result.ok();
    }

    /** 管理员驳回（待审→驳回） */
    @Operation(summary = "驳回准入申请")
    @RequirePerm("basedata:supplierAdmit:audit")
    @OperLog(module = "供应商准入", operation = "驳回准入申请")
    @Idempotent
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @Valid @RequestBody(required = false) PriceAuditForm form) {
        admitService.reject(id, form == null ? null : form.getRemark());
        return Result.ok();
    }

    /** 拉黑（通过→黑名单，供应商停用+is_blacklist=1，采购下单拦截） */
    @Operation(summary = "拉黑供应商")
    @RequirePerm("basedata:supplierAdmit:blacklist")
    @OperLog(module = "供应商准入", operation = "拉黑供应商")
    @Idempotent
    @PutMapping("/{id}/blacklist")
    public Result<Void> blacklist(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        admitService.blacklist(id, body == null ? null : body.get("reason"));
        return Result.ok();
    }

    /** 移出黑名单（黑名单→通过，恢复启用） */
    @Operation(summary = "移出黑名单")
    @RequirePerm("basedata:supplierAdmit:blacklist")
    @OperLog(module = "供应商准入", operation = "移出黑名单")
    @Idempotent
    @PutMapping("/{id}/unblacklist")
    public Result<Void> unblacklist(@PathVariable Long id) {
        admitService.unblacklist(id);
        return Result.ok();
    }

    /** 删除申请（仅草稿/驳回） */
    @Operation(summary = "删除准入申请")
    @RequirePerm("basedata:supplierAdmit:delete")
    @OperLog(module = "供应商准入", operation = "删除准入申请")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        admitService.delete(id);
        return Result.ok();
    }
}

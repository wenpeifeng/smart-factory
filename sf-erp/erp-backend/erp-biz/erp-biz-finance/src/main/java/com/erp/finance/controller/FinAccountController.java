package com.erp.finance.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.finance.dto.form.AccountForm;
import com.erp.finance.dto.vo.AccountBalanceVO;
import com.erp.finance.dto.vo.AccountFlowVO;
import com.erp.finance.dto.vo.AccountVO;
import com.erp.finance.service.FinAccountService;
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
import com.erp.framework.idempotent.Idempotent;
import com.erp.framework.security.RequirePerm;
import com.erp.system.annotation.OperLog;

/**
 * 资金账户接口：账户 CRUD、余额快照、流水查询。
 */
@Tag(name = "财务管理-资金账户")
@RestController
@RequestMapping("/finance/accounts")
@RequiredArgsConstructor
public class FinAccountController {

    private final FinAccountService accountService;

    @Operation(summary = "账户列表")
    @RequirePerm("finance:account:list")
    @GetMapping
    public Result<List<AccountVO>> list() {
        return Result.ok(accountService.list());
    }

    @Operation(summary = "新增账户")
    @RequirePerm("finance:account:add")
    @OperLog(module = "财务管理", operation = "新增资金账户")
    @Idempotent
    @PostMapping
    public Result<Void> create(@Valid @RequestBody AccountForm form) {
        accountService.create(form);
        return Result.ok();
    }

    @Operation(summary = "修改账户")
    @RequirePerm("finance:account:edit")
    @OperLog(module = "财务管理", operation = "修改资金账户")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AccountForm form) {
        accountService.update(id, form);
        return Result.ok();
    }

    @Operation(summary = "删除账户（已有流水时拒绝）")
    @RequirePerm("finance:account:delete")
    @OperLog(module = "财务管理", operation = "删除资金账户")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "账户余额快照（期初/收入/支出/当前余额）")
    @RequirePerm("finance:account:list")
    @GetMapping("/{id}/balance")
    public Result<AccountBalanceVO> balance(@PathVariable Long id) {
        return Result.ok(accountService.balance(id));
    }

    @Operation(summary = "账户流水分页（按时间倒序）")
    @RequirePerm("finance:account:list")
    @GetMapping("/{id}/flows")
    public Result<PageResult<AccountFlowVO>> flows(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(accountService.flows(id, pageNum, pageSize));
    }
}

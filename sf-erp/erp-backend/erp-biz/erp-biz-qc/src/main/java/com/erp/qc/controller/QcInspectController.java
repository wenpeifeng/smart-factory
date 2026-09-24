package com.erp.qc.controller;

import com.erp.common.result.PageResult;
import com.erp.common.result.Result;
import com.erp.qc.dto.form.QcInspectForm;
import com.erp.qc.dto.form.QcInspectSubmitForm;
import com.erp.qc.dto.query.QcInspectQuery;
import com.erp.qc.dto.vo.InspectPendingVO;
import com.erp.qc.dto.vo.QcInspectVO;
import com.erp.qc.service.QcInspectService;
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
 * 检验单接口。
 * <p>
 * 含两类核心挂点（提供给一期模块文档化接线，约定见 docs/modules/qc.md）：
 * <ul>
 *   <li>POST /qc/inspects/from/{bizType}/{bizId} —— 按来源单自动生成检验单；</li>
 *   <li>GET /qc/inspects/pending?bizType&bizId —— 来源单审核前卡点：还有草稿检验单则 pending=true。</li>
 * </ul>
 */
@Tag(name = "质量管理-检验单")
@RestController
@RequestMapping("/qc/inspects")
@RequiredArgsConstructor
public class QcInspectController {

    private final QcInspectService inspectService;

    /** 分页查询检验单 */
    @Operation(summary = "分页查询检验单")
    @RequirePerm("qc:inspect:list")
    @GetMapping
    public Result<PageResult<QcInspectVO>> page(QcInspectQuery query) {
        return Result.ok(inspectService.page(query));
    }

    /** 检验单详情（含检验项目实检值明细） */
    @Operation(summary = "检验单详情")
    @RequirePerm("qc:inspect:list")
    @GetMapping("/{id}")
    public Result<QcInspectVO> detail(@PathVariable Long id) {
        return Result.ok(inspectService.getDetail(id));
    }

    /** 手工新增检验单（草稿，明细可自动按模板带出） */
    @Operation(summary = "手工新增检验单")
    @RequirePerm("qc:inspect:add")
    @OperLog(module = "质量管理", operation = "新增质检单")
    @Idempotent
    @PostMapping
    public Result<Long> create(@Valid @RequestBody QcInspectForm form) {
        return Result.ok(inspectService.create(form));
    }

    /** 修改草稿检验单 */
    @Operation(summary = "修改检验单（仅草稿）")
    @RequirePerm("qc:inspect:edit")
    @OperLog(module = "质量管理", operation = "修改质检单")
    @Idempotent
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody QcInspectForm form) {
        inspectService.update(id, form);
        return Result.ok();
    }

    /** 提交检验：录入实检值+结论，草稿→已检验 */
    @Operation(summary = "提交检验（实检值+结论）")
    @RequirePerm("qc:inspect:audit")
    @OperLog(module = "质量管理", operation = "提交质检结果")
    @Idempotent
    @PutMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, @Valid @RequestBody QcInspectSubmitForm form) {
        inspectService.submit(id, form);
        return Result.ok();
    }

    /** 删除草稿检验单 */
    @Operation(summary = "删除检验单（仅草稿）")
    @RequirePerm("qc:inspect:delete")
    @OperLog(module = "质量管理", operation = "删除质检单")
    @Idempotent
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        inspectService.delete(id);
        return Result.ok();
    }

    /** 挂点：按来源单自动生成检验单（幂等，返回新生成的检验单 ID 列表） */
    @Operation(summary = "按来源单生成检验单")
    @RequirePerm("qc:inspect:add")
    @OperLog(module = "质量管理", operation = "生成质检单")
    @Idempotent
    @PostMapping("/from/{bizType}/{bizId}")
    public Result<List<Long>> generateFromBiz(@PathVariable String bizType, @PathVariable Long bizId) {
        return Result.ok(inspectService.generateFromBiz(bizType, bizId));
    }

    /** 挂点：来源单是否还有未完成检验（一期单据审核前卡点） */
    @Operation(summary = "来源单未完成检验卡点查询")
    @RequirePerm("qc:inspect:list")
    @GetMapping("/pending")
    public Result<InspectPendingVO> pending(@RequestParam String bizType, @RequestParam Long bizId) {
        return Result.ok(inspectService.pendingOfSource(bizType, bizId));
    }
}

package com.erp.finance.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.finance.dto.form.VoucherTemplateForm;
import com.erp.finance.dto.vo.VoucherTemplateVO;
import com.erp.finance.entity.FinVoucherTemplate;
import com.erp.finance.mapper.FinVoucherTemplateMapper;
import com.erp.finance.service.VoucherTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 凭证模板管理实现（V521）。
 * <p>
 * 模板不区分账套（全局规则），科目编码在生成时按目标账套解析；
 * entryRules 保存前做结构校验（JSON 数组 + side/subjectCode/amountExpr 必填）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherTemplateServiceImpl implements VoucherTemplateService {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 支持的八类单据类型 */
    private static final Set<String> BIZ_TYPES = Set.of(
            "PUR_INSTOCK", "SAL_OUTSTOCK", "SAL_RET", "PUR_RET",
            "INVOICE_SALES", "INVOICE_PURCHASE", "RECEIPT", "PAYMENT");

    /** 支持的金额来源表达式 */
    private static final Set<String> AMOUNT_EXPRS = Set.of(
            "order_total", "detail_tax_excl_total", "tax_total", "detail_cost_total");

    private final FinVoucherTemplateMapper templateMapper;

    @Override
    public List<VoucherTemplateVO> list() {
        return templateMapper.selectList(Wrappers.lambdaQuery(FinVoucherTemplate.class)
                        .orderByAsc(FinVoucherTemplate::getBizType))
                .stream().map(VoucherTemplateVO::from).toList();
    }

    @Override
    public Long create(VoucherTemplateForm form) {
        validateBizType(form.bizType());
        requireUniqueBizType(form.bizType(), null);
        validateRules(form.entryRules());
        FinVoucherTemplate template = new FinVoucherTemplate();
        applyForm(template, form);
        templateMapper.insert(template);
        log.info("[凭证模板][新增] {} bizType={} enabled={}", template.getName(), template.getBizType(), template.getEnabled());
        return template.getId();
    }

    @Override
    public void update(Long id, VoucherTemplateForm form) {
        FinVoucherTemplate template = requireTemplate(id);
        // bizType 是 uk 键，不允许修改（改类型=删旧建新）
        if (!template.getBizType().equals(form.bizType().trim())) {
            throw BusinessException.ofKey("erp.fin.template-biz-type-immutable", template.getBizType(), form.bizType());
        }
        validateRules(form.entryRules());
        applyForm(template, form);
        templateMapper.updateById(template);
        log.info("[凭证模板][修改] {} bizType={} enabled={}", template.getName(), template.getBizType(), template.getEnabled());
    }

    @Override
    public void delete(Long id) {
        requireTemplate(id);
        templateMapper.deleteById(id);
        log.info("[凭证模板][删除] id={}", id);
    }

    // ------------------------------------------------------------------

    private FinVoucherTemplate requireTemplate(Long id) {
        FinVoucherTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.fin.voucher-template-not-exists", id);
        }
        return template;
    }

    private void applyForm(FinVoucherTemplate template, VoucherTemplateForm form) {
        template.setCode(form.code().trim());
        template.setName(form.name().trim());
        template.setBizType(form.bizType().trim());
        template.setEntryRules(form.entryRules().trim());
        template.setEnabled(form.enabled());
        template.setPriority(form.priority() == null ? 100 : form.priority());
        template.setRemark(form.remark());
    }

    private void requireUniqueBizType(String bizType, Long excludeId) {
        Long count = templateMapper.selectCount(Wrappers.lambdaQuery(FinVoucherTemplate.class)
                .eq(FinVoucherTemplate::getBizType, bizType)
                .ne(excludeId != null, FinVoucherTemplate::getId, excludeId));
        if (count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.fin.template-biz-type-exists", bizType);
        }
    }

    private void validateBizType(String bizType) {
        if (bizType == null || !BIZ_TYPES.contains(bizType.trim())) {
            throw BusinessException.ofKey("erp.fin.biz-type-invalid", bizType, String.join("/", BIZ_TYPES));
        }
    }

    /** 分录规则结构校验：JSON 数组，每元素 side/subjectCode/amountExpr 必填且合法 */
    private void validateRules(String entryRules) {
        List<Map<String, Object>> rules;
        try {
            rules = JSON.readValue(entryRules, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw BusinessException.ofKey("erp.fin.rule-not-json-array", e.getMessage());
        }
        if (rules == null || rules.isEmpty()) {
            throw BusinessException.ofKey("erp.fin.rule-required");
        }
        int line = 1;
        for (Map<String, Object> rule : rules) {
            String side = str(rule.get("side"));
            if (!"DEBIT".equals(side) && !"CREDIT".equals(side)) {
                throw BusinessException.ofKey("erp.fin.line-side-invalid", line);
            }
            if (str(rule.get("subjectCode")).isEmpty()) {
                throw BusinessException.ofKey("erp.fin.line-subject-code-required", line);
            }
            String expr = str(rule.get("amountExpr"));
            if (!AMOUNT_EXPRS.contains(expr)) {
                throw BusinessException.ofKey("erp.fin.line-amount-source-invalid", line, expr, String.join("/", AMOUNT_EXPRS));
            }
            line++;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}

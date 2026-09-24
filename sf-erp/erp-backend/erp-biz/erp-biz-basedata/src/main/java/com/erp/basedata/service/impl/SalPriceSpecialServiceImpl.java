package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.PriceSpecialForm;
import com.erp.basedata.dto.vo.PriceSpecialVO;
import com.erp.basedata.entity.BaseCustomer;
import com.erp.basedata.entity.BaseMaterial;
import com.erp.basedata.entity.SalPriceSpecial;
import com.erp.basedata.mapper.BaseCustomerMapper;
import com.erp.basedata.mapper.BaseMaterialMapper;
import com.erp.basedata.mapper.SalPriceSpecialMapper;
import com.erp.basedata.service.BaseCustomerService;
import com.erp.basedata.service.BaseMaterialService;
import com.erp.basedata.service.SalPriceSpecialService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import com.erp.framework.security.SecurityUtils;
import com.erp.workflow.dto.vo.StartResultVO;
import com.erp.workflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 特价审批 Service 实现。
 * <p>
 * 双通道审批：
 * <ul>
 *   <li><b>workflow</b>：申请时尝试 startFlow(bizType=sal_price_special)。存在启用流程
 *       → RUNNING，生成待办；结果由 {@link #findEffective} / 分页对 isApproved 惰性同步（0→10）。
 *       流程条件全不满足 → AGREED 即时通过。</li>
 *   <li><b>管理员简单审批</b>：bizType 未接白名单或无启用流程（SKIPPED）时，
 *       由 approve/reject 接口（sales:priceSpecial:audit）人工处理。</li>
 * </ul>
 * 状态推进全部条件 UPDATE（WHERE status=0），防并发重复审批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalPriceSpecialServiceImpl implements SalPriceSpecialService {

    private final SalPriceSpecialMapper specialMapper;
    private final BaseCustomerService customerService;
    private final BaseMaterialService materialService;
    private final BaseCustomerMapper customerMapper;
    private final BaseMaterialMapper materialMapper;
    private final WorkflowService workflowService;

    @Override
    public Long apply(PriceSpecialForm form) {
        BaseCustomer customer = customerService.getEntityById(form.getCustomerId());
        materialService.getEntityById(form.getMaterialId());
        if (form.getValidFrom() != null && form.getValidTo() != null
                && form.getValidTo().isBefore(form.getValidFrom())) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.basedata.effective-end-before-start");
        }
        SalPriceSpecial entity = new SalPriceSpecial();
        entity.setCustomerId(form.getCustomerId());
        entity.setMaterialId(form.getMaterialId());
        entity.setSpecialPrice(form.getSpecialPrice());
        entity.setApplyReason(form.getApplyReason());
        entity.setValidFrom(form.getValidFrom());
        entity.setValidTo(form.getValidTo());
        entity.setStatus(SalPriceSpecial.STATUS_PENDING);
        entity.setWfStarted(0);
        entity.setRemark(form.getRemark());
        specialMapper.insert(entity);

        // 申请即尝试挂审批流；无流程/未接入白名单则走管理员简单审批
        tryStartFlow(entity);
        return entity.getId();
    }

    @Override
    public PageResult<PriceSpecialVO> page(long pageNum, long pageSize, Long customerId, Long materialId, Integer status) {
        syncWorkflowApproved(customerId, materialId);
        var wrapper = Wrappers.lambdaQuery(SalPriceSpecial.class)
                .eq(customerId != null, SalPriceSpecial::getCustomerId, customerId)
                .eq(materialId != null, SalPriceSpecial::getMaterialId, materialId)
                .eq(status != null, SalPriceSpecial::getStatus, status)
                .orderByDesc(SalPriceSpecial::getId);
        PageResult<PriceSpecialVO> page = PageUtils.of(specialMapper, wrapper, pageNum, pageSize, PriceSpecialVO::from);
        fillNames(page.getRecords());
        return page;
    }

    @Override
    public void approve(Long id, String remark) {
        int rows = specialMapper.update(null, Wrappers.lambdaUpdate(SalPriceSpecial.class)
                .eq(SalPriceSpecial::getId, id)
                .eq(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_PENDING)
                .set(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_APPROVED)
                .set(SalPriceSpecial::getAuditBy, SecurityUtils.getCurrentUserId())
                .set(SalPriceSpecial::getAuditTime, LocalDateTime.now())
                .set(SalPriceSpecial::getAuditRemark, remark));
        if (rows == 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.basedata.special-price-not-exists-or-approved", id);
        }
    }

    @Override
    public void reject(Long id, String remark) {
        int rows = specialMapper.update(null, Wrappers.lambdaUpdate(SalPriceSpecial.class)
                .eq(SalPriceSpecial::getId, id)
                .eq(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_PENDING)
                .set(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_REJECTED)
                .set(SalPriceSpecial::getAuditBy, SecurityUtils.getCurrentUserId())
                .set(SalPriceSpecial::getAuditTime, LocalDateTime.now())
                .set(SalPriceSpecial::getAuditRemark, remark));
        if (rows == 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.basedata.special-price-not-exists-or-approved", id);
        }
    }

    @Override
    public void delete(Long id) {
        SalPriceSpecial entity = specialMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.special-price-not-exists", id);
        }
        if (entity.getStatus() == SalPriceSpecial.STATUS_APPROVED) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.basedata.special-price-approved-no-delete");
        }
        specialMapper.deleteById(id);
    }

    @Override
    public SalPriceSpecial findEffective(Long customerId, Long materialId) {
        // 惰性同步：workflow 已通过但状态仍待审的记录收敛为 10（条件 UPDATE 防并发）
        syncWorkflowApproved(customerId, materialId);
        LocalDate today = LocalDate.now();
        return specialMapper.selectOne(Wrappers.lambdaQuery(SalPriceSpecial.class)
                .eq(SalPriceSpecial::getCustomerId, customerId)
                .eq(SalPriceSpecial::getMaterialId, materialId)
                .eq(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_APPROVED)
                .and(w -> w.isNull(SalPriceSpecial::getValidFrom).or().le(SalPriceSpecial::getValidFrom, today))
                .and(w -> w.isNull(SalPriceSpecial::getValidTo).or().ge(SalPriceSpecial::getValidTo, today))
                .orderByDesc(SalPriceSpecial::getId)
                .last("LIMIT 1"));
    }

    /* ===================== 私有支撑 ===================== */

    /**
     * 申请后挂审批流。三类结果：
     * RUNNING → wf_started=1（待办审批）；AGREED → 即时通过；
     * SKIPPED/异常（未接白名单/无流程/无审批人）→ 管理员简单审批。
     */
    private void tryStartFlow(SalPriceSpecial entity) {
        String bizNo = "SP" + entity.getId();
        try {
            StartResultVO result = workflowService.startFlow(WF_BIZ_TYPE, entity.getId(), bizNo,
                    Map.of("amount", entity.getSpecialPrice(), "price", entity.getSpecialPrice()));
            if ("RUNNING".equals(result.getStatus())) {
                entity.setWfStarted(1);
                specialMapper.update(null, Wrappers.lambdaUpdate(SalPriceSpecial.class)
                        .eq(SalPriceSpecial::getId, entity.getId())
                        .set(SalPriceSpecial::getWfStarted, 1));
                log.info("[特价审批] 申请[{}]已提交审批流({})", entity.getId(), bizNo);
            } else if ("AGREED".equals(result.getStatus())) {
                specialMapper.update(null, Wrappers.lambdaUpdate(SalPriceSpecial.class)
                        .eq(SalPriceSpecial::getId, entity.getId())
                        .eq(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_PENDING)
                        .set(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_APPROVED)
                        .set(SalPriceSpecial::getAuditBy, 0L)
                        .set(SalPriceSpecial::getAuditTime, LocalDateTime.now())
                        .set(SalPriceSpecial::getAuditRemark, "流程条件全部不满足，自动通过"));
                log.info("[特价审批] 申请[{}]流程条件全不满足，即时通过", entity.getId());
            } else {
                log.info("[特价审批] 申请[{}]无启用审批流，转管理员简单审批", entity.getId());
            }
        } catch (BusinessException e) {
            // bizType 未接入审批流白名单/节点无审批人等：不影响申请提交，转管理员审批
            log.info("[特价审批] 申请[{}]未挂审批流({})，转管理员简单审批", entity.getId(), e.getMessage());
        }
    }

    /**
     * 惰性同步 workflow 审批结果：待审且 wf_started=1 的记录，
     * isApproved=true（最新实例 AGREED）则条件 UPDATE 收敛为 10（audit_by=0 系统）。
     *
     * @param customerId 限定客户（可空=分页场景全量扫待审，注意由调用方保证范围）
     * @param materialId 限定物料（可空）
     */
    private void syncWorkflowApproved(Long customerId, Long materialId) {
        List<SalPriceSpecial> pendings = specialMapper.selectList(Wrappers.lambdaQuery(SalPriceSpecial.class)
                .eq(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_PENDING)
                .eq(SalPriceSpecial::getWfStarted, 1)
                .eq(customerId != null, SalPriceSpecial::getCustomerId, customerId)
                .eq(materialId != null, SalPriceSpecial::getMaterialId, materialId)
                .orderByDesc(SalPriceSpecial::getId)
                .last("LIMIT 100"));
        for (SalPriceSpecial pending : pendings) {
            boolean approved;
            try {
                approved = workflowService.isApproved(WF_BIZ_TYPE, pending.getId());
            } catch (Exception e) {
                log.warn("[特价审批] 同步申请[{}]审批状态失败: {}", pending.getId(), e.getMessage());
                continue;
            }
            if (approved) {
                specialMapper.update(null, Wrappers.lambdaUpdate(SalPriceSpecial.class)
                        .eq(SalPriceSpecial::getId, pending.getId())
                        .eq(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_PENDING)
                        .set(SalPriceSpecial::getStatus, SalPriceSpecial.STATUS_APPROVED)
                        .set(SalPriceSpecial::getAuditBy, 0L)
                        .set(SalPriceSpecial::getAuditTime, LocalDateTime.now())
                        .set(SalPriceSpecial::getAuditRemark, "审批流通过（自动同步）"));
            }
        }
    }

    /** 批量填充客户/物料冗余信息 */
    private void fillNames(List<PriceSpecialVO> records) {
        if (records.isEmpty()) {
            return;
        }
        Map<Long, BaseCustomer> customers = customerMapper.selectBatchIds(
                        records.stream().map(PriceSpecialVO::getCustomerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseCustomer::getId, Function.identity(), (a, b) -> a));
        Map<Long, BaseMaterial> materials = materialMapper.selectBatchIds(
                        records.stream().map(PriceSpecialVO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity(), (a, b) -> a));
        for (PriceSpecialVO vo : records) {
            BaseCustomer customer = customers.get(vo.getCustomerId());
            if (customer != null) {
                vo.setCustomerCode(customer.getCode());
                vo.setCustomerName(customer.getName());
                vo.setCustomerLevel(customer.getLevel() == null || customer.getLevel().isBlank() ? "B" : customer.getLevel());
            }
            BaseMaterial material = materials.get(vo.getMaterialId());
            if (material != null) {
                vo.setMaterialCode(material.getCode());
                vo.setMaterialName(material.getName());
            }
        }
    }

}

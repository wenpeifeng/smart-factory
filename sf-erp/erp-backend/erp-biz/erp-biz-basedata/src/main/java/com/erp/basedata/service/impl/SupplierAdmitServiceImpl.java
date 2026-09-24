package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.SupplierAdmitForm;
import com.erp.basedata.dto.vo.SupplierAdmitVO;
import com.erp.basedata.entity.BaseSupplier;
import com.erp.basedata.entity.SupplierAdmit;
import com.erp.basedata.mapper.BaseSupplierMapper;
import com.erp.basedata.mapper.SupplierAdmitMapper;
import com.erp.basedata.service.BizCodeGenerator;
import com.erp.basedata.service.SupplierAdmitService;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 供应商准入 Service 实现（V141）。
 * <p>
 * 双通道审批（同 V131 特价审批先例）：
 * <ul>
 *   <li><b>workflow</b>：提交时尝试 startFlow(bizType=supplier_admit)。存在启用流程
 *       → RUNNING 生成待办，结果由分页查询惰性同步（10→20）；流程条件全不满足
 *       → AGREED 即时通过。</li>
 *   <li><b>管理员简单审批</b>：bizType 未接白名单或无启用流程（SKIPPED）时，
 *       由 approve/reject 接口人工处理。</li>
 * </ul>
 * 通过 → base_supplier.status=1（启用）；拉黑 → status=0 + is_blacklist=1
 * （采购订单创建/审核拦截，见 purchase 模块 BasedataMapper 校验）。
 * 状态推进全部条件 UPDATE（WHERE status=10），防并发重复审批。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierAdmitServiceImpl implements SupplierAdmitService {

    /** 审批流单据类型（dict:wf_biz_type V141） */
    private static final String WF_BIZ_TYPE = "supplier_admit";

    private final SupplierAdmitMapper admitMapper;
    private final BaseSupplierMapper supplierMapper;
    private final BizCodeGenerator codeGenerator;
    private final WorkflowService workflowService;

    @Override
    public PageResult<SupplierAdmitVO> page(long pageNum, long pageSize, String keyword, Integer status) {
        syncWorkflowApproved();
        String kw = keyword == null ? null : keyword.trim();
        var wrapper = Wrappers.lambdaQuery(SupplierAdmit.class)
                .and(kw != null && !kw.isBlank(), w -> w
                        .like(SupplierAdmit::getSupplierName, kw)
                        .or().like(SupplierAdmit::getCreditCode, kw))
                .eq(status != null, SupplierAdmit::getStatus, status)
                .orderByDesc(SupplierAdmit::getId);
        PageResult<SupplierAdmitVO> page = PageUtils.of(admitMapper, wrapper, pageNum, pageSize, SupplierAdmitVO::from);
        fillSupplierCodes(page.getRecords());
        return page;
    }

    @Override
    public SupplierAdmitVO getById(Long id) {
        SupplierAdmitVO vo = SupplierAdmitVO.from(requireAdmit(id));
        if (vo.getSupplierId() != null) {
            BaseSupplier supplier = supplierMapper.selectById(vo.getSupplierId());
            if (supplier != null) {
                vo.setSupplierCode(supplier.getCode());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SupplierAdmitForm form) {
        SupplierAdmit entity;
        if (form.getId() == null) {
            entity = new SupplierAdmit();
            entity.setStatus(SupplierAdmit.STATUS_DRAFT);
            entity.setWfStarted(0);
        } else {
            entity = requireAdmit(form.getId());
            if (entity.getStatus() != SupplierAdmit.STATUS_DRAFT
                    && entity.getStatus() != SupplierAdmit.STATUS_REJECTED) {
                throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.biz.sup-admit-not-editable",
                        entity.getStatus());
            }
        }
        applyForm(entity, form);
        if (form.getId() == null) {
            admitMapper.insert(entity);
        } else {
            admitMapper.updateById(entity);
        }
        if (form.getId() == null) {
            // 新登记：创建停用供应商（准入通过才启用），编码自动生成
            BaseSupplier supplier = new BaseSupplier();
            supplier.setCode(codeGenerator.nextCode(BizCodeGenerator.PREFIX_SUPPLIER));
            supplier.setName(form.getSupplierName());
            supplier.setContact(form.getContact());
            supplier.setPhone(form.getPhone());
            supplier.setAddress(form.getAddress());
            supplier.setCreditCode(form.getCreditCode());
            supplier.setInitPayable(java.math.BigDecimal.ZERO);
            supplier.setStatus(0);
            supplier.setIsBlacklist(0);
            supplier.setRemark("供应商准入登记自动创建（V141）");
            supplierMapper.insert(supplier);
            entity.setSupplierId(supplier.getId());
            admitMapper.updateById(entity);
        } else {
            // 同步档案基础信息（未启用前保持一致）
            syncSupplierInfo(entity);
        }
        if (form.isSubmit()) {
            submit(entity.getId());
        }
        return entity.getId();
    }

    @Override
    public void submit(Long id) {
        SupplierAdmit admit = requireAdmit(id);
        if (admit.getStatus() != SupplierAdmit.STATUS_DRAFT
                && admit.getStatus() != SupplierAdmit.STATUS_REJECTED) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.biz.sup-admit-not-submittable",
                    admit.getStatus());
        }
        if (admit.getLicenseFile() == null || admit.getLicenseFile().isBlank()) {
            throw BusinessException.ofKey(ErrorCode.BAD_REQUEST, "erp.biz.sup-admit-license-required");
        }
        int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                .eq(SupplierAdmit::getId, id)
                .in(SupplierAdmit::getStatus, SupplierAdmit.STATUS_DRAFT, SupplierAdmit.STATUS_REJECTED)
                .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_PENDING)
                .set(SupplierAdmit::getWfStarted, 0)
                .set(SupplierAdmit::getAuditBy, null)
                .set(SupplierAdmit::getAuditTime, null)
                .set(SupplierAdmit::getAuditRemark, null));
        if (rows == 0) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.sup-admit-not-submittable",
                    admit.getStatus());
        }
        tryStartFlow(admit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String remark) {
        int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                .eq(SupplierAdmit::getId, id)
                .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_PENDING)
                .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_APPROVED)
                .set(SupplierAdmit::getAuditBy, SecurityUtils.getCurrentUserId())
                .set(SupplierAdmit::getAuditTime, LocalDateTime.now())
                .set(SupplierAdmit::getAuditRemark, remark));
        if (rows == 0) {
            requireAdmit(id);
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.sup-admit-not-pending");
        }
        enableSupplier(id, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String remark) {
        int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                .eq(SupplierAdmit::getId, id)
                .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_PENDING)
                .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_REJECTED)
                .set(SupplierAdmit::getAuditBy, SecurityUtils.getCurrentUserId())
                .set(SupplierAdmit::getAuditTime, LocalDateTime.now())
                .set(SupplierAdmit::getAuditRemark, remark));
        if (rows == 0) {
            requireAdmit(id);
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.sup-admit-not-pending");
        }
        enableSupplier(id, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void blacklist(Long id, String reason) {
        int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                .eq(SupplierAdmit::getId, id)
                .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_APPROVED)
                .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_BLACKLIST)
                .set(SupplierAdmit::getBlacklistTime, LocalDateTime.now())
                .set(SupplierAdmit::getBlacklistReason, reason));
        if (rows == 0) {
            requireAdmit(id);
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.sup-admit-blacklist-approved-only");
        }
        SupplierAdmit admit = requireAdmit(id);
        supplierMapper.update(null, Wrappers.lambdaUpdate(BaseSupplier.class)
                .eq(BaseSupplier::getId, admit.getSupplierId())
                .set(BaseSupplier::getStatus, 0)
                .set(BaseSupplier::getIsBlacklist, 1));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unblacklist(Long id) {
        int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                .eq(SupplierAdmit::getId, id)
                .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_BLACKLIST)
                .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_APPROVED)
                .set(SupplierAdmit::getBlacklistTime, null)
                .set(SupplierAdmit::getBlacklistReason, null));
        if (rows == 0) {
            requireAdmit(id);
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.sup-admit-not-blacklisted");
        }
        SupplierAdmit admit = requireAdmit(id);
        supplierMapper.update(null, Wrappers.lambdaUpdate(BaseSupplier.class)
                .eq(BaseSupplier::getId, admit.getSupplierId())
                .set(BaseSupplier::getStatus, 1)
                .set(BaseSupplier::getIsBlacklist, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SupplierAdmit admit = requireAdmit(id);
        if (admit.getStatus() != SupplierAdmit.STATUS_DRAFT
                && admit.getStatus() != SupplierAdmit.STATUS_REJECTED) {
            throw BusinessException.ofKey(ErrorCode.BUSINESS_ERROR, "erp.biz.sup-admit-delete-draft-only");
        }
        admitMapper.deleteById(id);
        if (admit.getSupplierId() != null) {
            supplierMapper.deleteById(admit.getSupplierId());
        }
    }

    /* ===================== 私有支撑 ===================== */

    private SupplierAdmit requireAdmit(Long id) {
        SupplierAdmit admit = admitMapper.selectById(id);
        if (admit == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.biz.sup-admit-not-exists", id);
        }
        return admit;
    }

    private void applyForm(SupplierAdmit entity, SupplierAdmitForm form) {
        entity.setSupplierName(form.getSupplierName());
        entity.setCreditCode(form.getCreditCode());
        entity.setContact(form.getContact());
        entity.setPhone(form.getPhone());
        entity.setAddress(form.getAddress());
        entity.setLicenseFile(form.getLicenseFile());
        entity.setCertFiles(form.getCertFiles());
        entity.setBankInfo(form.getBankInfo());
        entity.setApplyReason(form.getApplyReason());
        entity.setRemark(form.getRemark());
    }

    /** 未启用供应商档案的基础信息同步（启用后走供应商管理页维护） */
    private void syncSupplierInfo(SupplierAdmit admit) {
        if (admit.getSupplierId() == null) {
            return;
        }
        supplierMapper.update(null, Wrappers.lambdaUpdate(BaseSupplier.class)
                .eq(BaseSupplier::getId, admit.getSupplierId())
                .set(BaseSupplier::getName, admit.getSupplierName())
                .set(BaseSupplier::getCreditCode, admit.getCreditCode())
                .set(BaseSupplier::getContact, admit.getContact())
                .set(BaseSupplier::getPhone, admit.getPhone())
                .set(BaseSupplier::getAddress, admit.getAddress()));
    }

    /** 通过=启用 / 驳回=保持停用 */
    private void enableSupplier(Long admitId, boolean enabled) {
        SupplierAdmit admit = requireAdmit(admitId);
        if (admit.getSupplierId() != null) {
            supplierMapper.update(null, Wrappers.lambdaUpdate(BaseSupplier.class)
                    .eq(BaseSupplier::getId, admit.getSupplierId())
                    .set(BaseSupplier::getStatus, enabled ? 1 : 0)
                    .set(BaseSupplier::getIsBlacklist, 0));
        }
    }

    /**
     * 提交后挂审批流（同 V131 先例）：RUNNING → wf_started=1（待办审批）；
     * AGREED → 即时通过；SKIPPED/异常 → 管理员简单审批。
     */
    private void tryStartFlow(SupplierAdmit admit) {
        String bizNo = "SA" + admit.getId();
        try {
            StartResultVO result = workflowService.startFlow(WF_BIZ_TYPE, admit.getId(), bizNo, Map.of());
            if ("RUNNING".equals(result.getStatus())) {
                admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                        .eq(SupplierAdmit::getId, admit.getId())
                        .set(SupplierAdmit::getWfStarted, 1));
                log.info("[供应商准入] 申请[{}]已提交审批流({})", admit.getId(), bizNo);
            } else if ("AGREED".equals(result.getStatus())) {
                approveSystem(admit.getId());
                log.info("[供应商准入] 申请[{}]流程条件全不满足，即时通过", admit.getId());
            } else {
                log.info("[供应商准入] 申请[{}]无启用审批流，转管理员简单审批", admit.getId());
            }
        } catch (BusinessException e) {
            log.info("[供应商准入] 申请[{}]未挂审批流({})，转管理员简单审批", admit.getId(), e.getMessage());
        }
    }

    /** 惰性同步 workflow 审批结果：待审且 wf_started=1，isApproved=true 收敛为 20（audit_by=0 系统） */
    private void syncWorkflowApproved() {
        List<SupplierAdmit> pendings = admitMapper.selectList(Wrappers.lambdaQuery(SupplierAdmit.class)
                .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_PENDING)
                .eq(SupplierAdmit::getWfStarted, 1)
                .orderByDesc(SupplierAdmit::getId)
                .last("LIMIT 100"));
        for (SupplierAdmit pending : pendings) {
            boolean approved;
            try {
                approved = workflowService.isApproved(WF_BIZ_TYPE, pending.getId());
            } catch (Exception e) {
                log.warn("[供应商准入] 同步申请[{}]审批状态失败: {}", pending.getId(), e.getMessage());
                continue;
            }
            if (approved) {
                int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                        .eq(SupplierAdmit::getId, pending.getId())
                        .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_PENDING)
                        .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_APPROVED)
                        .set(SupplierAdmit::getAuditBy, 0L)
                        .set(SupplierAdmit::getAuditTime, LocalDateTime.now())
                        .set(SupplierAdmit::getAuditRemark, "审批流通过（自动同步）"));
                if (rows > 0) {
                    enableSupplier(pending.getId(), true);
                }
            }
        }
    }

    /** 系统自动通过（流程条件全不满足 / workflow 回调场景） */
    private void approveSystem(Long id) {
        int rows = admitMapper.update(null, Wrappers.lambdaUpdate(SupplierAdmit.class)
                .eq(SupplierAdmit::getId, id)
                .eq(SupplierAdmit::getStatus, SupplierAdmit.STATUS_PENDING)
                .set(SupplierAdmit::getStatus, SupplierAdmit.STATUS_APPROVED)
                .set(SupplierAdmit::getAuditBy, 0L)
                .set(SupplierAdmit::getAuditTime, LocalDateTime.now())
                .set(SupplierAdmit::getAuditRemark, "流程条件全部不满足，自动通过"));
        if (rows > 0) {
            enableSupplier(id, true);
        }
    }

    /** 列表补供应商编码（档案冗余展示） */
    private void fillSupplierCodes(List<SupplierAdmitVO> records) {
        for (SupplierAdmitVO vo : records) {
            if (vo.getSupplierId() != null) {
                BaseSupplier supplier = supplierMapper.selectById(vo.getSupplierId());
                if (supplier != null) {
                    vo.setSupplierCode(supplier.getCode());
                }
            }
        }
    }
}

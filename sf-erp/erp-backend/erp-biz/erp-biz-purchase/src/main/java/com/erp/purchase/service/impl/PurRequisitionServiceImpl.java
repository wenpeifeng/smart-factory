package com.erp.purchase.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.purchase.dto.form.PurRequisitionForm;
import com.erp.purchase.dto.query.PurRequisitionQuery;
import com.erp.purchase.dto.vo.PurRequisitionVO;
import com.erp.purchase.entity.PurRequisition;
import com.erp.purchase.entity.PurRequisitionItem;
import com.erp.purchase.mapper.BasedataMapper;
import com.erp.purchase.mapper.PurRequisitionItemMapper;
import com.erp.purchase.mapper.PurRequisitionMapper;
import com.erp.purchase.service.OrderNoGenerator;
import com.erp.purchase.service.PurRequisitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 请购单 Service 实现。
 * <p>
 * 状态推进全部用条件 UPDATE（WHERE status=...）落地：并发重复提交时第二次
 * 更新影响 0 行直接报错，不会跳跃流转。已转采购数量（ordered_qty）不在本类写入，
 * 由询比价定标事务（PurRfqServiceImpl#award）通过条件 UPDATE 回写并带超转守卫。
 */
@Service
@RequiredArgsConstructor
public class PurRequisitionServiceImpl implements PurRequisitionService {

    private final PurRequisitionMapper requisitionMapper;
    private final PurRequisitionItemMapper itemMapper;
    private final BasedataMapper basedataMapper;
    private final OrderNoGenerator orderNoGenerator;

    @Override
    public PageResult<PurRequisitionVO> page(PurRequisitionQuery query) {
        var page = requisitionMapper.selectReqPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PurRequisitionVO detail(Long id) {
        PurRequisition req = requireRequisition(id);
        PurRequisitionVO vo = toVo(req);
        vo.setItems(itemMapper.selectItemsWithMaterial(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurRequisitionForm form) {
        validateBaseInfo(form);
        PurRequisition req = new PurRequisition();
        req.setReqNo(orderNoGenerator.next("PR"));
        req.setDeptId(form.getDeptId());
        // 请购人缺省取当前登录用户（未登录上下文为 0=系统，与审计口径一致）
        req.setApplicantId(form.getApplicantId() != null
                ? form.getApplicantId() : SecurityUtils.getCurrentUserId());
        req.setExpectDate(form.getExpectDate());
        req.setStatus(PurRequisition.STATUS_DRAFT);
        req.setRemark(form.getRemark());
        requisitionMapper.insert(req);
        insertItems(req.getId(), form);
        return req.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurRequisitionForm form) {
        PurRequisition req = requireRequisition(id);
        if (req.getStatus() != PurRequisition.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.pr-only-draft-modify", req.getStatus());
        }
        validateBaseInfo(form);
        // 修改即整体重写明细：逻辑删旧行 + 插新行，避免做差量合并的复杂度
        itemMapper.delete(Wrappers.lambdaQuery(PurRequisitionItem.class)
                .eq(PurRequisitionItem::getRequisitionId, id));
        req.setDeptId(form.getDeptId());
        req.setApplicantId(form.getApplicantId() != null
                ? form.getApplicantId() : req.getApplicantId());
        req.setExpectDate(form.getExpectDate());
        req.setRemark(form.getRemark());
        requisitionMapper.updateById(req);
        insertItems(id, form);
    }

    @Override
    public void submit(Long id) {
        int rows = requisitionMapper.submit(id);
        if (rows == 0) {
            requireRequisition(id);
            throw BusinessException.ofKey("erp.pur.pr-only-draft-submit");
        }
    }

    @Override
    public void close(Long id) {
        int rows = requisitionMapper.close(id);
        if (rows == 0) {
            requireRequisition(id);
            throw BusinessException.ofKey("erp.pur.pr-close-status-not-allowed");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurRequisition req = requireRequisition(id);
        if (req.getStatus() != PurRequisition.STATUS_DRAFT) {
            throw BusinessException.ofKey("erp.pur.pr-only-draft-delete", req.getStatus());
        }
        requisitionMapper.deleteById(id);
        itemMapper.delete(Wrappers.lambdaQuery(PurRequisitionItem.class)
                .eq(PurRequisitionItem::getRequisitionId, id));
    }

    /** 加载请购单，不存在（含已逻辑删）抛业务异常 */
    private PurRequisition requireRequisition(Long id) {
        PurRequisition req = requisitionMapper.selectById(id);
        if (req == null) {
            throw BusinessException.ofKey("erp.pur.pr-not-exists", id);
        }
        return req;
    }

    /** 校验部门启用 + 明细物料在用（直查标准表，不依赖 system/basedata Java 代码） */
    private void validateBaseInfo(PurRequisitionForm form) {
        if (requisitionMapper.countDeptById(form.getDeptId()) == 0) {
            throw BusinessException.ofKey("erp.pur.pr-dept-not-exists", form.getDeptId());
        }
        form.getItems().forEach(line -> {
            if (basedataMapper.countMaterialById(line.getMaterialId()) == 0) {
                throw BusinessException.ofKey("erp.pur.material-not-exists", line.getMaterialId());
            }
        });
    }

    /** 构建并插入明细行（已转采购数量固定从 0 开始） */
    private void insertItems(Long requisitionId, PurRequisitionForm form) {
        form.getItems().forEach(line -> {
            PurRequisitionItem item = new PurRequisitionItem();
            item.setRequisitionId(requisitionId);
            item.setMaterialId(line.getMaterialId());
            item.setQty(line.getQty());
            item.setOrderedQty(java.math.BigDecimal.ZERO);
            item.setRequireDate(line.getRequireDate());
            item.setRemark(line.getRemark());
            itemMapper.insert(item);
        });
    }

    /** 实体 -> VO（列表/详情通用：名称字段回查标准表，明细由调用方按需填充） */
    private PurRequisitionVO toVo(PurRequisition req) {
        PurRequisitionVO vo = new PurRequisitionVO();
        vo.setId(req.getId());
        vo.setReqNo(req.getReqNo());
        vo.setDeptId(req.getDeptId());
        vo.setDeptName(requisitionMapper.selectDeptName(req.getDeptId()));
        vo.setApplicantId(req.getApplicantId());
        vo.setApplicantName(req.getApplicantId() == null
                ? null : requisitionMapper.selectUserName(req.getApplicantId()));
        vo.setExpectDate(req.getExpectDate());
        vo.setStatus(req.getStatus());
        vo.setRemark(req.getRemark());
        vo.setCreateTime(req.getCreateTime());
        return vo;
    }
}

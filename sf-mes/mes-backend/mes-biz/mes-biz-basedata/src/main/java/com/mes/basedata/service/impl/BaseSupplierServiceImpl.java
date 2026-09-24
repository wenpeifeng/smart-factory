package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.dto.form.BaseSupplierForm;
import com.mes.basedata.dto.vo.BaseSupplierVO;
import com.mes.basedata.entity.BaseSettleType;
import com.mes.basedata.entity.BaseSupplier;
import com.mes.basedata.mapper.BaseSettleTypeMapper;
import com.mes.basedata.mapper.BaseSupplierMapper;
import com.mes.basedata.service.BaseSupplierService;
import com.mes.basedata.service.BizCodeGenerator;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 供应商 Service 实现（与客户结构对称，期初字段为应付）。
 */
@Service
@RequiredArgsConstructor
public class BaseSupplierServiceImpl implements BaseSupplierService {

    private static final int MAX_SELECTOR_ROWS = 50;

    private final BaseSupplierMapper supplierMapper;
    private final BaseSettleTypeMapper settleTypeMapper;
    private final BizCodeGenerator codeGenerator;

    @Override
    public PageResult<BaseSupplierVO> page(long pageNum, long pageSize, String keyword) {
        var wrapper = Wrappers.lambdaQuery(BaseSupplier.class)
                .and(notBlank(keyword), w -> w.like(BaseSupplier::getCode, keyword)
                        .or().like(BaseSupplier::getName, keyword)
                        .or().like(BaseSupplier::getContact, keyword))
                .orderByDesc(BaseSupplier::getId);
        PageResult<BaseSupplierVO> page = PageUtils.of(supplierMapper, wrapper, pageNum, pageSize,
                BaseSupplierVO::from);
        page.getRecords().forEach(this::fillSettleTypeName);
        return page;
    }

    @Override
    public BaseSupplierVO getById(Long id) {
        BaseSupplierVO vo = BaseSupplierVO.from(requireExists(id));
        fillSettleTypeName(vo);
        return vo;
    }

    @Override
    public Long create(BaseSupplierForm form) {
        BaseSupplier entity = new BaseSupplier();
        applyForm(entity, form, true);
        supplierMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseSupplierForm form) {
        BaseSupplier entity = requireExists(id);
        applyForm(entity, form, false);
        supplierMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // TODO 采购/应付模块上线后：被单据引用时抛 DATA_IN_USE
        supplierMapper.deleteById(id);
    }

    @Override
    public List<BaseSupplier> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return supplierMapper.selectBatchIds(ids);
    }

    @Override
    public List<BaseSupplierVO> listEnabled(String keyword) {
        return supplierMapper.selectList(Wrappers.lambdaQuery(BaseSupplier.class)
                        .eq(BaseSupplier::getStatus, 1)
                        .and(notBlank(keyword), w -> w.like(BaseSupplier::getCode, keyword)
                                .or().like(BaseSupplier::getName, keyword))
                        .orderByDesc(BaseSupplier::getId)
                        .last("LIMIT " + MAX_SELECTOR_ROWS))
                .stream().map(BaseSupplierVO::from).toList();
    }

    /** 表单字段拷贝；create 模式下编码留空自动生成（先校验后取号，避免校验失败浪费 Redis 流水） */
    private void applyForm(BaseSupplier entity, BaseSupplierForm form, boolean isCreate) {
        checkSettleTypeExists(form.getSettleTypeId());
        String code = form.getCode() == null ? "" : form.getCode().trim();
        if (isCreate) {
            entity.setCode(code.isEmpty() ? codeGenerator.nextCode(BizCodeGenerator.PREFIX_SUPPLIER) : code);
        } else if (!code.isEmpty()) {
            entity.setCode(code);
        }
        checkCodeUnique(entity.getCode(), entity.getId());
        checkSettleTypeExists(form.getSettleTypeId());
        entity.setName(form.getName().trim());
        entity.setContact(form.getContact());
        entity.setPhone(form.getPhone());
        entity.setAddress(form.getAddress());
        entity.setCreditCode(form.getCreditCode());
        entity.setSettleTypeId(form.getSettleTypeId());
        entity.setInitPayable(form.getInitPayable() == null ? BigDecimal.ZERO : form.getInitPayable());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 供应商编码唯一性校验（排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = supplierMapper.selectCount(Wrappers.lambdaQuery(BaseSupplier.class)
                .eq(BaseSupplier::getCode, code)
                .ne(excludeId != null, BaseSupplier::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "供应商编码已存在: %s".formatted(code));
        }
    }

    /** 结算方式存在性校验 */
    private void checkSettleTypeExists(Long settleTypeId) {
        if (settleTypeId != null && settleTypeMapper.selectById(settleTypeId) == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "结算方式不存在: %d".formatted(settleTypeId));
        }
    }

    /** 冗余结算方式名称 */
    private void fillSettleTypeName(BaseSupplierVO vo) {
        if (vo.getSettleTypeId() == null) {
            return;
        }
        BaseSettleType type = settleTypeMapper.selectById(vo.getSettleTypeId());
        vo.setSettleTypeName(type == null ? "" : type.getName());
    }

    private BaseSupplier requireExists(Long id) {
        BaseSupplier entity = supplierMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "供应商不存在: %d".formatted(id));
        }
        return entity;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

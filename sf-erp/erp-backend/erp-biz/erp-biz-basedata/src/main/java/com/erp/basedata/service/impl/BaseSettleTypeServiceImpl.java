package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseSettleTypeForm;
import com.erp.basedata.dto.vo.BaseSettleTypeVO;
import com.erp.basedata.entity.BaseCustomer;
import com.erp.basedata.entity.BaseSettleType;
import com.erp.basedata.entity.BaseSupplier;
import com.erp.basedata.mapper.BaseCustomerMapper;
import com.erp.basedata.mapper.BaseSettleTypeMapper;
import com.erp.basedata.mapper.BaseSupplierMapper;
import com.erp.basedata.service.BaseSettleTypeService;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 结算方式 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class BaseSettleTypeServiceImpl implements BaseSettleTypeService {

    private final BaseSettleTypeMapper settleTypeMapper;
    private final BaseCustomerMapper customerMapper;
    private final BaseSupplierMapper supplierMapper;

    @Override
    public List<BaseSettleTypeVO> listAll() {
        return settleTypeMapper.selectList(Wrappers.lambdaQuery(BaseSettleType.class)
                        .orderByAsc(BaseSettleType::getCode))
                .stream().map(BaseSettleTypeVO::from).toList();
    }

    @Override
    public List<BaseSettleTypeVO> listEnabled() {
        return settleTypeMapper.selectList(Wrappers.lambdaQuery(BaseSettleType.class)
                        .eq(BaseSettleType::getStatus, 1)
                        .orderByAsc(BaseSettleType::getCode))
                .stream().map(BaseSettleTypeVO::from).toList();
    }

    @Override
    public BaseSettleTypeVO getById(Long id) {
        return BaseSettleTypeVO.from(requireExists(id));
    }

    @Override
    public Long create(BaseSettleTypeForm form) {
        checkCodeUnique(form.getCode().trim(), null);
        BaseSettleType entity = new BaseSettleType();
        applyForm(entity, form);
        settleTypeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseSettleTypeForm form) {
        BaseSettleType entity = requireExists(id);
        checkCodeUnique(form.getCode().trim(), id);
        applyForm(entity, form);
        settleTypeMapper.updateById(entity);
    }

    /**
     * 删除结算方式：被客户或供应商引用时禁删。
     * <p>客户/供应商引用计数是两张表的读校验，无多表写入，无需事务；
     * 保留 @Transactional 语义说明：本方法单表删除，MP 自带原子性。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        Long customerRef = customerMapper.selectCount(Wrappers.lambdaQuery(BaseCustomer.class)
                .eq(BaseCustomer::getSettleTypeId, id));
        if (customerRef != null && customerRef > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.settle-method-in-use-customer", customerRef);
        }
        Long supplierRef = supplierMapper.selectCount(Wrappers.lambdaQuery(BaseSupplier.class)
                .eq(BaseSupplier::getSettleTypeId, id));
        if (supplierRef != null && supplierRef > 0) {
            throw BusinessException.ofKey(ErrorCode.DATA_IN_USE, "erp.basedata.settle-method-in-use-supplier", supplierRef);
        }
        settleTypeMapper.deleteById(id);
    }

    /** 表单字段拷贝 */
    private void applyForm(BaseSettleType entity, BaseSettleTypeForm form) {
        entity.setCode(form.getCode().trim());
        entity.setName(form.getName().trim());
        entity.setDays(form.getDays() == null ? 0 : form.getDays());
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 方式编码唯一性校验（排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = settleTypeMapper.selectCount(Wrappers.lambdaQuery(BaseSettleType.class)
                .eq(BaseSettleType::getCode, code)
                .ne(excludeId != null, BaseSettleType::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.settle-method-code-exists", code);
        }
    }

    private BaseSettleType requireExists(Long id) {
        BaseSettleType entity = settleTypeMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.settle-method-not-exists", id);
        }
        return entity;
    }
}

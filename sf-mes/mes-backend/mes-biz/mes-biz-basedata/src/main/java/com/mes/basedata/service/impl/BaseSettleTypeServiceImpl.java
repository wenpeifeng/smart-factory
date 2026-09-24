package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.dto.form.BaseSettleTypeForm;
import com.mes.basedata.dto.vo.BaseSettleTypeVO;
import com.mes.basedata.entity.BaseCustomer;
import com.mes.basedata.entity.BaseSettleType;
import com.mes.basedata.entity.BaseSupplier;
import com.mes.basedata.mapper.BaseCustomerMapper;
import com.mes.basedata.mapper.BaseSettleTypeMapper;
import com.mes.basedata.mapper.BaseSupplierMapper;
import com.mes.basedata.service.BaseSettleTypeService;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
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
            throw new BusinessException(ErrorCode.DATA_IN_USE,
                    "结算方式已被 %d 个客户引用，禁止删除".formatted(customerRef));
        }
        Long supplierRef = supplierMapper.selectCount(Wrappers.lambdaQuery(BaseSupplier.class)
                .eq(BaseSupplier::getSettleTypeId, id));
        if (supplierRef != null && supplierRef > 0) {
            throw new BusinessException(ErrorCode.DATA_IN_USE,
                    "结算方式已被 %d 个供应商引用，禁止删除".formatted(supplierRef));
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
            throw new BusinessException(ErrorCode.DUPLICATE_DATA, "方式编码已存在: %s".formatted(code));
        }
    }

    private BaseSettleType requireExists(Long id) {
        BaseSettleType entity = settleTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "结算方式不存在: %d".formatted(id));
        }
        return entity;
    }
}

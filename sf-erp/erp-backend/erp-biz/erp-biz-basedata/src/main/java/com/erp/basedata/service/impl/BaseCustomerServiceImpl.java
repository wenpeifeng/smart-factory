package com.erp.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.basedata.dto.form.BaseCustomerForm;
import com.erp.basedata.dto.vo.BaseCustomerVO;
import com.erp.basedata.entity.BaseCustomer;
import com.erp.basedata.entity.BaseSettleType;
import com.erp.basedata.mapper.BaseCustomerMapper;
import com.erp.basedata.mapper.BaseSettleTypeMapper;
import com.erp.basedata.service.BaseCustomerService;
import com.erp.basedata.service.BizCodeGenerator;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.ErrorCode;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * 客户 Service 实现。
 */
@Service
@RequiredArgsConstructor
public class BaseCustomerServiceImpl implements BaseCustomerService {

    private static final int MAX_SELECTOR_ROWS = 50;

    private final BaseCustomerMapper customerMapper;
    private final BaseSettleTypeMapper settleTypeMapper;
    private final BizCodeGenerator codeGenerator;

    @Override
    public PageResult<BaseCustomerVO> page(long pageNum, long pageSize, String keyword) {
        var wrapper = Wrappers.lambdaQuery(BaseCustomer.class)
                .and(notBlank(keyword), w -> w.like(BaseCustomer::getCode, keyword)
                        .or().like(BaseCustomer::getName, keyword)
                        .or().like(BaseCustomer::getContact, keyword))
                .orderByDesc(BaseCustomer::getId);
        PageResult<BaseCustomerVO> page = PageUtils.of(customerMapper, wrapper, pageNum, pageSize,
                BaseCustomerVO::from);
        page.getRecords().forEach(this::fillSettleTypeName);
        return page;
    }

    @Override
    public BaseCustomerVO getById(Long id) {
        BaseCustomerVO vo = BaseCustomerVO.from(requireExists(id));
        fillSettleTypeName(vo);
        return vo;
    }

    @Override
    public Long create(BaseCustomerForm form) {
        BaseCustomer entity = new BaseCustomer();
        applyForm(entity, form, true);
        customerMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, BaseCustomerForm form) {
        BaseCustomer entity = requireExists(id);
        applyForm(entity, form, false);
        customerMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        requireExists(id);
        // TODO 销售/应收模块上线后：被单据引用时抛 DATA_IN_USE
        customerMapper.deleteById(id);
    }

    @Override
    public List<BaseCustomer> getByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return customerMapper.selectBatchIds(ids);
    }

    @Override
    public BaseCustomer getEntityById(Long id) {
        return requireExists(id);
    }

    @Override
    public List<BaseCustomerVO> listEnabled(String keyword) {
        return customerMapper.selectList(Wrappers.lambdaQuery(BaseCustomer.class)
                        .eq(BaseCustomer::getStatus, 1)
                        .and(notBlank(keyword), w -> w.like(BaseCustomer::getCode, keyword)
                                .or().like(BaseCustomer::getName, keyword))
                        .orderByDesc(BaseCustomer::getId)
                        .last("LIMIT " + MAX_SELECTOR_ROWS))
                .stream().map(BaseCustomerVO::from).toList();
    }

    /** 表单字段拷贝；create 模式下编码留空自动生成（先校验后取号，避免校验失败浪费 Redis 流水） */
    private void applyForm(BaseCustomer entity, BaseCustomerForm form, boolean isCreate) {
        checkSettleTypeExists(form.getSettleTypeId());
        String code = form.getCode() == null ? "" : form.getCode().trim();
        if (isCreate) {
            entity.setCode(code.isEmpty() ? codeGenerator.nextCode(BizCodeGenerator.PREFIX_CUSTOMER) : code);
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
        entity.setCreditLimit(form.getCreditLimit() == null ? BigDecimal.ZERO : form.getCreditLimit());
        entity.setInitReceivable(form.getInitReceivable() == null ? BigDecimal.ZERO : form.getInitReceivable());
        // 客户等级（V131）：空/历史数据默认 B（与加列默认值一致）
        String level = form.getLevel() == null ? "" : form.getLevel().trim();
        entity.setLevel(level.isEmpty() ? "B" : level);
        entity.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        entity.setRemark(form.getRemark());
    }

    /** 客户编码唯一性校验（排除自身） */
    private void checkCodeUnique(String code, Long excludeId) {
        Long count = customerMapper.selectCount(Wrappers.lambdaQuery(BaseCustomer.class)
                .eq(BaseCustomer::getCode, code)
                .ne(excludeId != null, BaseCustomer::getId, excludeId));
        if (count != null && count > 0) {
            throw BusinessException.ofKey(ErrorCode.DUPLICATE_DATA, "erp.basedata.customer-code-exists", code);
        }
    }

    /** 结算方式存在性校验 */
    private void checkSettleTypeExists(Long settleTypeId) {
        if (settleTypeId != null && settleTypeMapper.selectById(settleTypeId) == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.settle-method-not-exists", settleTypeId);
        }
    }

    /** 冗余结算方式名称 */
    private void fillSettleTypeName(BaseCustomerVO vo) {
        if (vo.getSettleTypeId() == null) {
            return;
        }
        BaseSettleType type = settleTypeMapper.selectById(vo.getSettleTypeId());
        vo.setSettleTypeName(type == null ? "" : type.getName());
    }

    private BaseCustomer requireExists(Long id) {
        BaseCustomer entity = customerMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.ofKey(ErrorCode.DATA_NOT_EXISTS, "erp.basedata.customer-not-exists", id);
        }
        return entity;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

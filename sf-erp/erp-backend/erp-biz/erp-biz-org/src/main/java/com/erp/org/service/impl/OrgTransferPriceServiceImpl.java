package com.erp.org.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.org.constant.OrgConstants;
import com.erp.org.dto.form.TransferPriceForm;
import com.erp.org.dto.request.TransferPriceQuery;
import com.erp.org.dto.vo.TransferPriceVO;
import com.erp.org.entity.OrgFactory;
import com.erp.org.entity.OrgTransferPrice;
import com.erp.org.mapper.OrgFactoryMapper;
import com.erp.org.mapper.OrgTransferMapper;
import com.erp.org.mapper.OrgTransferPriceMapper;
import com.erp.org.service.OrgTransferPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 组织间调拨价目服务实现。
 * <p>
 * 线路唯一（物料×调出工厂×调入工厂）；加价率上限 1000% 防误录。
 */
@Service
@RequiredArgsConstructor
public class OrgTransferPriceServiceImpl implements OrgTransferPriceService {

    private final OrgTransferPriceMapper priceMapper;
    private final OrgFactoryMapper factoryMapper;
    private final OrgTransferMapper transferMapper;

    @Override
    public PageResult<TransferPriceVO> page(TransferPriceQuery query) {
        IPage<TransferPriceVO> page = priceMapper.pagePrice(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Long create(TransferPriceForm form) {
        validate(form);
        OrgTransferPrice price = build(form);
        try {
            priceMapper.insert(price);
        } catch (DuplicateKeyException e) {
            throw BusinessException.ofKey("erp.org.create-price-exists");
        }
        return price.getId();
    }

    @Override
    public void update(Long id, TransferPriceForm form) {
        requirePrice(id);
        validate(form);
        OrgTransferPrice price = build(form);
        price.setId(id);
        try {
            priceMapper.updateById(price);
        } catch (DuplicateKeyException e) {
            throw BusinessException.ofKey("erp.org.modify-price-exists");
        }
    }

    @Override
    public void delete(Long id) {
        requirePrice(id);
        priceMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != OrgConstants.STATUS_ENABLED
                && status != OrgConstants.STATUS_DISABLED)) {
            throw BusinessException.ofKey("erp.org.status-invalid");
        }
        OrgTransferPrice price = requirePrice(id);
        price.setStatus(status);
        priceMapper.updateById(price);
    }

    /** 表单 → 实体（状态默认启用） */
    private OrgTransferPrice build(TransferPriceForm form) {
        OrgTransferPrice price = new OrgTransferPrice();
        price.setMaterialId(form.getMaterialId());
        price.setFromFactoryId(form.getFromFactoryId());
        price.setToFactoryId(form.getToFactoryId());
        price.setMarkupType(form.getMarkupType());
        price.setMarkupValue(form.getMarkupValue());
        price.setStatus(form.getStatus() == null ? OrgConstants.STATUS_ENABLED : form.getStatus());
        price.setRemark(form.getRemark());
        return price;
    }

    /**
     * 业务校验：物料存在、两工厂存在且不同、加价类型取值与量纲上限。
     */
    private void validate(TransferPriceForm form) {
        if (transferMapper.countMaterialExists(form.getMaterialId()) == 0) {
            throw BusinessException.ofKey("erp.org.material-not-exists", form.getMaterialId());
        }
        OrgFactory from = requireFactory(form.getFromFactoryId());
        OrgFactory to = requireFactory(form.getToFactoryId());
        if (Objects.equals(from.getId(), to.getId())) {
            throw BusinessException.ofKey("erp.org.same-factory");
        }
        if (OrgConstants.MARKUP_RATE.equals(form.getMarkupType())
                && form.getMarkupValue().compareTo(BigDecimal.valueOf(OrgConstants.MAX_RATE_PERCENT)) > 0) {
            throw BusinessException.ofKey("erp.org.markup-over-limit", OrgConstants.MAX_RATE_PERCENT);
        }
    }

    private OrgFactory requireFactory(Long id) {
        OrgFactory factory = factoryMapper.selectById(id);
        if (factory == null) {
            throw BusinessException.ofKey("erp.org.factory-not-exists", id);
        }
        return factory;
    }

    private OrgTransferPrice requirePrice(Long id) {
        OrgTransferPrice price = priceMapper.selectById(id);
        if (price == null) {
            throw BusinessException.ofKey("erp.org.price-not-exists", id);
        }
        return price;
    }
}

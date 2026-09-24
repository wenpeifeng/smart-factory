package com.erp.org.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.org.constant.OrgConstants;
import com.erp.org.dto.form.AssignWarehouseForm;
import com.erp.org.dto.form.FactoryForm;
import com.erp.org.dto.request.FactoryQuery;
import com.erp.org.dto.raw.WarehouseRaw;
import com.erp.org.dto.vo.FactoryStockVO;
import com.erp.org.dto.vo.FactoryVO;
import com.erp.org.dto.vo.WarehouseBelongVO;
import com.erp.org.entity.OrgFactory;
import com.erp.org.mapper.OrgFactoryMapper;
import com.erp.org.service.OrgFactoryService;
import com.erp.org.support.OrgBizNoGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工厂档案服务实现。
 * <p>
 * 工厂是轻量多组织的最小组织单元：仓库经 base_warehouse.factory_id 挂靠工厂，
 * 本类负责档案维护与归属调整；库存统计走 inv_stock 只读聚合。
 */
@Service
@RequiredArgsConstructor
public class OrgFactoryServiceImpl implements OrgFactoryService {

    private final OrgFactoryMapper factoryMapper;
    private final OrgBizNoGenerator bizNoGenerator;

    @Override
    public PageResult<FactoryVO> page(FactoryQuery query) {
        IPage<FactoryVO> page = factoryMapper.pageFactory(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<FactoryVO> listEnabled() {
        List<OrgFactory> list = factoryMapper.selectList(Wrappers.<OrgFactory>lambdaQuery()
                .eq(OrgFactory::getStatus, OrgConstants.STATUS_ENABLED)
                .orderByAsc(OrgFactory::getId));
        return list.stream().map(FactoryVO::from).toList();
    }

    @Override
    public FactoryVO getDetail(Long id) {
        return FactoryVO.from(requireFactory(id));
    }

    @Override
    public Long create(FactoryForm form) {
        OrgFactory factory = new OrgFactory();
        // 编码留空自动生成 FC+yyyyMMdd+3位流水；显式传入则尊重用户编码
        factory.setCode(form.getCode() == null || form.getCode().isBlank()
                ? bizNoGenerator.next(OrgConstants.PREFIX_FACTORY) : form.getCode().trim());
        factory.setName(form.getName().trim());
        factory.setAddress(form.getAddress());
        factory.setManager(form.getManager());
        factory.setStatus(form.getStatus() == null ? OrgConstants.STATUS_ENABLED : form.getStatus());
        factory.setRemark(form.getRemark());
        try {
            factoryMapper.insert(factory);
        } catch (DuplicateKeyException e) {
            throw BusinessException.ofKey("erp.org.factory-code-exists", factory.getCode());
        }
        return factory.getId();
    }

    @Override
    public void update(Long id, FactoryForm form) {
        OrgFactory exist = requireFactory(id);
        // 编码是工厂唯一身份（仓库归属/调拨单据均引用），不允许修改
        exist.setName(form.getName().trim());
        exist.setAddress(form.getAddress());
        exist.setManager(form.getManager());
        exist.setStatus(form.getStatus() == null ? exist.getStatus() : form.getStatus());
        exist.setRemark(form.getRemark());
        factoryMapper.updateById(exist);
    }

    @Override
    public void delete(Long id) {
        requireFactory(id);
        if (id == 1L) {
            throw BusinessException.ofKey("erp.org.builtin-factory-no-delete");
        }
        if (factoryMapper.countFactoryWarehouses(id) > 0) {
            throw BusinessException.ofKey("erp.org.factory-has-warehouses");
        }
        factoryMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != OrgConstants.STATUS_ENABLED
                && status != OrgConstants.STATUS_DISABLED)) {
            throw BusinessException.ofKey("erp.org.status-invalid");
        }
        if (id == 1L && status == OrgConstants.STATUS_DISABLED) {
            throw BusinessException.ofKey("erp.org.builtin-factory-no-disable");
        }
        OrgFactory exist = requireFactory(id);
        exist.setStatus(status);
        factoryMapper.updateById(exist);
    }

    @Override
    public List<WarehouseBelongVO> listWarehouses(Long factoryId) {
        requireFactory(factoryId);
        return factoryMapper.selectFactoryWarehouses(factoryId);
    }

    @Override
    public void assignWarehouse(AssignWarehouseForm form) {
        OrgFactory factory = requireFactory(form.getFactoryId());
        if (factory.getStatus() != OrgConstants.STATUS_ENABLED) {
            throw BusinessException.ofKey("erp.org.assign-factory-disabled", factory.getName());
        }
        WarehouseRaw warehouse = factoryMapper.selectWarehouse(form.getWarehouseId());
        if (warehouse == null) {
            throw BusinessException.ofKey("erp.org.assign-warehouse-not-exists", form.getWarehouseId());
        }
        int rows = factoryMapper.updateWarehouseFactory(form.getWarehouseId(), form.getFactoryId());
        if (rows == 0) {
            throw BusinessException.ofKey("erp.org.assign-warehouse-update-failed", form.getWarehouseId());
        }
    }

    @Override
    public List<FactoryStockVO> statisticsStock(Long factoryId) {
        requireFactory(factoryId);
        return factoryMapper.statisticsStock(factoryId);
    }

    /**
     * 按主键取工厂，不存在/已删一律抛业务异常。
     */
    private OrgFactory requireFactory(Long id) {
        OrgFactory factory = factoryMapper.selectById(id);
        if (factory == null) {
            throw BusinessException.ofKey("erp.org.factory-not-exists", id);
        }
        return factory;
    }
}

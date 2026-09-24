package com.mes.material.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.basedata.entity.BaseMaterial;
import com.mes.basedata.mapper.BaseMaterialMapper;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.PageResult;
import com.mes.framework.mybatis.PageUtils;
import com.mes.material.dto.vo.ShopStockVO;
import com.mes.material.entity.MatShopStock;
import com.mes.material.mapper.MatShopStockMapper;
import com.mes.material.service.ShopStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 线边仓库存服务实现。
 * <p>同一事务内的先查后写在低并发车间场景可接受；并发扣减由 stockOut 的
 * 数量校验兜底（不足即报错回滚，不产生负库存）。
 */
@Service
@RequiredArgsConstructor
public class ShopStockServiceImpl implements ShopStockService {

    private final MatShopStockMapper shopStockMapper;
    private final BaseMaterialMapper materialMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockIn(Long materialId, String batchNo, Long workcenterId, BigDecimal qty) {
        String batch = batchNo == null ? "" : batchNo;
        MatShopStock stock = find(materialId, batch, workcenterId);
        if (stock == null) {
            stock = new MatShopStock();
            stock.setMaterialId(materialId);
            stock.setBatchNo(batch);
            stock.setWorkcenterId(workcenterId);
            stock.setQty(qty);
            shopStockMapper.insert(stock);
        } else {
            stock.setQty(stock.getQty().add(qty));
            shopStockMapper.updateById(stock);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stockOut(Long materialId, String batchNo, Long workcenterId, BigDecimal qty) {
        String batch = batchNo == null ? "" : batchNo;
        MatShopStock stock = find(materialId, batch, workcenterId);
        if (stock == null || stock.getQty().compareTo(qty) < 0) {
            throw new BusinessException("线边仓库存不足: 物料 %d 批次 %s", materialId, batch);
        }
        stock.setQty(stock.getQty().subtract(qty));
        shopStockMapper.updateById(stock);
    }

    @Override
    public PageResult<ShopStockVO> page(long pageNum, long pageSize, String keyword, Long workcenterId) {
        // 物料编码/名称过滤需先解析物料 id 集合（少量主数据，可接受）
        var wrapper = Wrappers.lambdaQuery(MatShopStock.class)
                .eq(workcenterId != null, MatShopStock::getWorkcenterId, workcenterId)
                .orderByDesc(MatShopStock::getId);
        if (StringUtils.hasText(keyword)) {
            List<Long> ids = materialMapper.selectList(Wrappers.lambdaQuery(BaseMaterial.class)
                            .like(BaseMaterial::getCode, keyword)
                            .or().like(BaseMaterial::getName, keyword))
                    .stream().map(BaseMaterial::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.of(List.of(), 0, pageNum, pageSize);
            }
            wrapper.in(MatShopStock::getMaterialId, ids);
        }
        PageResult<ShopStockVO> page = PageUtils.of(shopStockMapper, wrapper,
                pageNum, pageSize, ShopStockVO::from);
        if (!page.getRecords().isEmpty()) {
            Map<Long, BaseMaterial> mats = materialMapper.selectBatchIds(
                            page.getRecords().stream().map(ShopStockVO::getMaterialId).toList())
                    .stream().collect(Collectors.toMap(BaseMaterial::getId, Function.identity()));
            page.getRecords().forEach(vo -> {
                BaseMaterial mat = mats.get(vo.getMaterialId());
                if (mat != null) {
                    vo.setMaterialCode(mat.getCode());
                    vo.setMaterialName(mat.getName());
                }
            });
        }
        return page;
    }

    private MatShopStock find(Long materialId, String batchNo, Long workcenterId) {
        return shopStockMapper.selectOne(Wrappers.lambdaQuery(MatShopStock.class)
                .eq(MatShopStock::getMaterialId, materialId)
                .eq(MatShopStock::getBatchNo, batchNo)
                .eq(workcenterId != null, MatShopStock::getWorkcenterId, workcenterId)
                .last("LIMIT 1"));
    }
}

package com.mes.basedata.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mes.common.exception.BusinessException;
import com.mes.common.result.ErrorCode;
import com.mes.basedata.entity.BaseBom;
import com.mes.basedata.entity.BaseBomItem;
import com.mes.basedata.mapper.BaseBomItemMapper;
import com.mes.basedata.mapper.BaseBomMapper;
import com.mes.basedata.service.BaseBomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * BOM 服务实现。
 */
@Service
@RequiredArgsConstructor
public class BaseBomServiceImpl implements BaseBomService {

    private final BaseBomMapper bomMapper;
    private final BaseBomItemMapper bomItemMapper;

    @Override
    public BaseBom findActive(Long materialId, String version) {
        List<BaseBom> actives = bomMapper.selectList(Wrappers.lambdaQuery(BaseBom.class)
                .eq(BaseBom::getMaterialId, materialId)
                .eq(StringUtils.hasText(version), BaseBom::getVersion, version)
                .eq(BaseBom::getStatus, "ACTIVE"));
        if (actives.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_EXISTS, "物料 %d 无有效 BOM（版本 %s）", materialId, version);
        }
        if (!StringUtils.hasText(version) && actives.size() > 1) {
            throw new BusinessException("物料 %d 存在多个有效 BOM 版本，请指定版本", materialId);
        }
        return actives.get(0);
    }

    @Override
    public List<BaseBomItem> listItems(Long bomId) {
        return bomItemMapper.selectList(Wrappers.lambdaQuery(BaseBomItem.class)
                .eq(BaseBomItem::getBomId, bomId)
                .orderByAsc(BaseBomItem::getId));
    }

    @Override
    public List<BaseBom> listByMaterial(Long materialId) {
        return bomMapper.selectList(Wrappers.lambdaQuery(BaseBom.class)
                .eq(BaseBom::getMaterialId, materialId)
                .orderByDesc(BaseBom::getId));
    }
}

package com.mes.basedata.service;

import com.mes.basedata.entity.BaseBom;
import com.mes.basedata.entity.BaseBomItem;

import java.util.List;

/**
 * BOM 服务（供计划模块展开用料）。
 */
public interface BaseBomService {

    /**
     * 查询物料的有效 BOM（版本未指定时取唯一 ACTIVE 版本，多版本报错）。
     *
     * @param materialId 成品物料 id
     * @param version    BOM 版本（可空）
     * @return BOM 头
     */
    BaseBom findActive(Long materialId, String version);

    /**
     * 查询 BOM 行项目。
     *
     * @param bomId BOM 头 id
     * @return 子件明细列表
     */
    List<BaseBomItem> listItems(Long bomId);

    /**
     * 查询物料的全部 BOM 版本（下拉）。
     *
     * @param materialId 成品物料 id
     * @return BOM 列表（含草稿/停用，前端标注）
     */
    List<BaseBom> listByMaterial(Long materialId);
}

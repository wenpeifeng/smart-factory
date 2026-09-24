package com.erp.basedata.dto.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓库→库区→库位 三级树节点（V421）。
 * <p>
 * 顶层 = 库区（含一个虚拟的"未分区"组收编 zone_id 为空的库位），
 * children = 该库区下的库位列表；供 WMS 任务推荐库位与前端库位树展示。
 */
@Data
public class LocationTreeNodeVO {

    /** 库区 ID（未分组时为 0） */
    private Long zoneId;

    /** 库区编码（未分组为 "-"） */
    private String zoneCode;

    /** 库区名称（未分组为"未分区"） */
    private String zoneName;

    /** 库区类型（未分组为 STORAGE 语义；RECEIVE/STORAGE/PICKING/SHIP/DEFECT） */
    private String zoneType;

    /** 库区下库位（按编码升序） */
    private List<BaseLocationVO> locations = new ArrayList<>();
}

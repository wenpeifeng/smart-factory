package com.erp.mrp.dto.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 生产领料单 VO（详情含 items）。
 */
@Data
public class MaterialIssueVO implements Serializable {

    /** 领料单ID */
    private Long id;

    /** 领料单号 */
    private String issueNo;

    /** 生产订单ID */
    private Long workOrderId;

    /** 发料仓库 */
    private Long warehouseId;

    /** 单据类型：NORMAL 手工 / BACKFLUSH 完工倒冲（V621） */
    private String issueType;

    /** 状态：DRAFT/AUDITED */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 明细（详情时返回） */
    private List<MaterialIssueItemVO> items;
}

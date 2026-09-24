package com.erp.org.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 组织间调拨单头（org_transfer）。
 * <p>
 * 状态机单向流转：0 草稿 → 1 已审核（事务内过账库存）/ 2 已取消（仅草稿可取消）。
 * 审核过账：调出仓扣库存 + 调入仓加库存 + inv_movement 两行（ORG_OUT/ORG_IN）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("org_transfer")
public class OrgTransfer extends BaseEntity {

    /** 调拨单号（IT+yyyyMMdd+3位流水，Redis 自增） */
    private String transferNo;

    /** 调出工厂 ID（org_factory.id） */
    private Long fromFactoryId;

    /** 调出仓库 ID（base_warehouse.id，必须归属调出工厂） */
    private Long fromWarehouseId;

    /** 调入工厂 ID（org_factory.id） */
    private Long toFactoryId;

    /** 调入仓库 ID（base_warehouse.id，必须归属调入工厂） */
    private Long toWarehouseId;

    /** 状态：0 草稿 1 已审核 2 已取消 */
    private Integer status;

    /** 审核人（用户 ID） */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /** 备注 */
    private String remark;
}

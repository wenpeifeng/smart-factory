package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 即时库存 —— 仓库+物料+批次+库位维度的现存量（全系统唯一库存事实表）。
 * <p>
 * 关键约束：
 * <ul>
 *   <li>唯一键 uk_stock(warehouse_id, material_id, batch_no, location_id, deleted) 保证
 *       一仓一料一批次一库位一行；非批次物料恒为 batch_no='' 且 location_id=0 的汇总行
 *       （一期结构不变，库位行仅批次物料）；</li>
 *   <li>V421 口径：location_id=0 表示「汇总/未上架行」（= 二期批次行语义），
 *       location_id&gt;0 表示「库位行」= 真实库存行；全表 SUM(qty) 恒等于账面总量
 *       （上架=未上架行扣+库位行加同事务双写，总量守恒）；</li>
 *   <li>qty 永不为负（由 {@link com.erp.inventory.service.StockService#changeStock}
 *       的条件 UPDATE 保证，库存不足直接抛业务异常）；</li>
 *   <li>并发更新走「条件 UPDATE qty=qty+? AND qty+?>=0」的行锁方案，
 *       version 仅作为辅助排查的版本号（每次变动 +1）；</li>
 *   <li>first_in_at 仅在批次行首次插入时写入（UPSERT 不回写），
 *       是 FIFO 出库策略的排序键 = 批次最早入库时间；</li>
 *   <li>expiry_date（V422）批次效期，FEFO 出库策略的排序键；
 *       上架转移库位行时从来源行复制，同批次效期一致。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_stock")
public class InvStock extends BaseEntity {

    /** 仓库 ID（逻辑外键 base_warehouse.id） */
    private Long warehouseId;

    /** 库位 ID（0=汇总/未上架行；&gt;0=库位行 base_location.id，仅批次物料，V421） */
    private Long locationId;

    /** 物料 ID（逻辑外键 base_material.id） */
    private Long materialId;

    /** 批次号（非批次物料恒为空串；批次物料必填，V411 起） */
    private String batchNo;

    /** 该批次行首次入库时间（FIFO 排序键，仅首次插入时写入，不随后续入库回写） */
    private LocalDateTime firstInAt;

    /** 批次效期（FEFO 排序键，V422；上架时复制到库位行，可为 null） */
    private LocalDate expiryDate;

    /** 现存量（DECIMAL(18,4)，非负） */
    private BigDecimal qty;

    /** 乐观锁版本号，每次库存变动 +1 */
    private Integer version;
}

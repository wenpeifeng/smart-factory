package com.erp.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * WMS 库内作业任务（V421）—— 上架/拣选/移库/补货的作业指令与执行记录。
 * <p>
 * 状态机：0 待分配 → (领取) 10 待执行 → (开始) 20 执行中 → (完成) 30 完成；
 * 任一未完成态可取消 → 40 取消。
 * <p>
 * 生成途径：
 * <ul>
 *   <li>扫描器（wms.auto-task.enabled，默认开）：每 30s 扫已审核未生成任务的
 *       入库单（pur_instock/sal_return/inv_other_in/mrp 完工）→ PUTAWAY、
 *       出库单（sal_outstock/inv_other_out）→ PICK，零侵入采购/销售模块（只读约定表）；</li>
 *   <li>波次建波：选多张已审核出库单一次性生成 PICK 任务并入波（wave_id）；</li>
 *   <li>手工：MOVE 移库 / REPLENISH 补货（ STORAGE→PICKING）由用户指定两库位创建。</li>
 * </ul>
 * 库存事务口径（完成时）：
 * <ul>
 *   <li>PUTAWAY：批次物料 = 未上架行扣 + 库位行加（同事务双写、总量守恒、无流水）；
 *       非批次物料 = 账面不动（汇总行语义）；</li>
 *   <li>PICK（扫描器来源）：账面已在出库单审核时扣减，完成仅确认（不再扣，防双扣）；</li>
 *   <li>PICK（手工无来源）：stockOutAtLocation 精确行扣 + OTH_OUT 流水（账实同扣）；</li>
 *   <li>MOVE / REPLENISH：两库位行间转移（无流水）。</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wms_task")
public class WmsTask extends BaseEntity {

    /** 状态：待分配（未领用） */
    public static final int STATUS_PENDING = 0;
    /** 状态：待执行（已分配执行人） */
    public static final int STATUS_ASSIGNED = 10;
    /** 状态：执行中 */
    public static final int STATUS_DOING = 20;
    /** 状态：已完成 */
    public static final int STATUS_DONE = 30;
    /** 状态：已取消 */
    public static final int STATUS_CANCELLED = 40;

    /** 任务类型：上架 */
    public static final String TYPE_PUTAWAY = "PUTAWAY";
    /** 任务类型：拣选 */
    public static final String TYPE_PICK = "PICK";
    /** 任务类型：移库 */
    public static final String TYPE_MOVE = "MOVE";
    /** 任务类型：补货（STORAGE→PICKING） */
    public static final String TYPE_REPLENISH = "REPLENISH";

    /** 任务号（T+yyyyMMdd+4位流水） */
    private String taskNo;

    /** 任务类型：PUTAWAY上架 / PICK拣选 / MOVE移库 / REPLENISH补货 */
    private String taskType;

    /** 仓库 ID */
    private Long warehouseId;

    /** 物料 ID */
    private Long materialId;

    /** 批次号（非批次物料空串；PICK 来源库位行时=该行批次） */
    private String batchNo;

    /** 任务数量 */
    private BigDecimal qty;

    /** 目标/来源库位 ID（PUTAWAY/REPLENISH=目标库位；PICK=来源库位；可空=未推荐到） */
    private Long locationId;

    /** 调出库位 ID（MOVE/REPLENISH=来源库位） */
    private Long fromLocationId;

    /** 所属波次 ID（空=独立任务） */
    private Long waveId;

    /** 来源单据类型（PUR_IN/SAL_RET/OTH_IN/PROD_IN/SAL_OUT/OTH_OUT；空串=手工） */
    private String sourceType;

    /** 来源单据 ID（0=手工） */
    private Long sourceId;

    /** 来源单号 */
    private String sourceNo;

    /** 执行人用户 ID（NULL=待分配） */
    private Long assignee;

    /** 状态：0待分配 10待执行 20执行中 30完成 40取消 */
    private Integer status;

    /** 推荐库位理由（同物料现有库位 / 存储区空容库位 / 无库位库存等） */
    private String recommendReason;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 备注 */
    private String remark;
}

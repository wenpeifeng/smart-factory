package com.erp.basedata.dto.vo;

import com.erp.basedata.entity.BaseMaterial;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料响应 VO。
 * <p>冗余 categoryName/unitName 便于前端列表直显，避免二次查询。
 */
@Data
public class BaseMaterialVO {

    /** 主键 */
    private Long id;

    /** 物料编码 */
    private String code;

    /** 物料名称 */
    private String name;

    /** 规格型号 */
    private String spec;

    /** 物料分类 ID */
    private Long categoryId;

    /** 物料分类名称（冗余） */
    private String categoryName;

    /** 计量单位 ID */
    private Long unitId;

    /** 计量单位名称（冗余） */
    private String unitName;

    /** 条码 */
    private String barcode;

    /** 是否批次管理：1-是 0-否 */
    private Integer batchManaged;

    /** 是否序列号管理：1-是 0-否 */
    private Integer serialManaged;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 参考成本价 */
    private BigDecimal refCost;

    /** 状态：1-启用 0-停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从实体转换（分类名/单位名由 Service 批量填充）。
     *
     * @param entity 物料实体
     * @return VO
     */
    public static BaseMaterialVO from(BaseMaterial entity) {
        BaseMaterialVO vo = new BaseMaterialVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setSpec(entity.getSpec());
        vo.setCategoryId(entity.getCategoryId());
        vo.setUnitId(entity.getUnitId());
        vo.setBarcode(entity.getBarcode());
        vo.setBatchManaged(entity.getBatchManaged());
        vo.setSerialManaged(entity.getSerialManaged());
        vo.setSafetyStock(entity.getSafetyStock());
        vo.setRefCost(entity.getRefCost());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}

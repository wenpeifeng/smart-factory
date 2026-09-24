package com.mes.material.dto.vo;

import com.mes.material.entity.MatPicking;
import com.mes.material.entity.MatPickingItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 领料单 VO（列表与详情共用，详情含行项目）。
 */
@Data
public class PickingVO {
    private Long id;
    private String pickNo;
    private Long workorderId;
    private String woNo;
    private Long warehouseId;
    private String warehouseName;
    private String status;
    private String pickType;
    private LocalDateTime auditTime;
    private String auditor;
    private Integer erpPushed;
    private String remark;
    private LocalDateTime createTime;
    /** 行项目（详情时填充） */
    private List<ItemVO> items;

    /**
     * 实体转 VO。
     *
     * @param e 领料单实体
     * @return VO
     */
    public static PickingVO from(MatPicking e) {
        PickingVO vo = new PickingVO();
        vo.setId(e.getId());
        vo.setPickNo(e.getPickNo());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setWarehouseId(e.getWarehouseId());
        vo.setStatus(e.getStatus());
        vo.setPickType(e.getPickType());
        vo.setAuditTime(e.getAuditTime());
        vo.setAuditor(e.getAuditor());
        vo.setErpPushed(e.getErpPushed());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    /** 领料行 VO */
    @Data
    public static class ItemVO {
        private Long id;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String batchNo;
        private BigDecimal qty;

        /**
         * 实体转 VO。
         *
         * @param e 领料行实体
         * @return VO
         */
        public static ItemVO from(MatPickingItem e) {
            ItemVO vo = new ItemVO();
            vo.setId(e.getId());
            vo.setMaterialId(e.getMaterialId());
            vo.setBatchNo(e.getBatchNo());
            vo.setQty(e.getQty());
            return vo;
        }
    }
}

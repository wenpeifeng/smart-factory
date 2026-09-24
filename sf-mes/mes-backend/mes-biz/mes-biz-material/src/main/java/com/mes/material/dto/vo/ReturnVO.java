package com.mes.material.dto.vo;

import com.mes.material.entity.MatReturn;
import com.mes.material.entity.MatReturnItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 退料单 VO。
 */
@Data
public class ReturnVO {
    private Long id;
    private String returnNo;
    private Long workorderId;
    private String woNo;
    private String returnType;
    private Long warehouseId;
    private String warehouseName;
    private String status;
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
     * @param e 退料单实体
     * @return VO
     */
    public static ReturnVO from(MatReturn e) {
        ReturnVO vo = new ReturnVO();
        vo.setId(e.getId());
        vo.setReturnNo(e.getReturnNo());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setReturnType(e.getReturnType());
        vo.setWarehouseId(e.getWarehouseId());
        vo.setStatus(e.getStatus());
        vo.setAuditTime(e.getAuditTime());
        vo.setAuditor(e.getAuditor());
        vo.setErpPushed(e.getErpPushed());
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }

    /** 退料行 VO */
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
         * @param e 退料行实体
         * @return VO
         */
        public static ItemVO from(MatReturnItem e) {
            ItemVO vo = new ItemVO();
            vo.setId(e.getId());
            vo.setMaterialId(e.getMaterialId());
            vo.setBatchNo(e.getBatchNo());
            vo.setQty(e.getQty());
            return vo;
        }
    }
}

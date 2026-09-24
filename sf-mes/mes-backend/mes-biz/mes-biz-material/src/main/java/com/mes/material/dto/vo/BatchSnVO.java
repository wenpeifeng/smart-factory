package com.mes.material.dto.vo;

import com.mes.material.entity.MatBatchSn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批次/SN 档案 VO。
 */
@Data
public class BatchSnVO {
    private Long id;
    private String objType;
    private String objNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String sourceType;
    private String sourceNo;
    private Long workorderId;
    private BigDecimal qty;
    private Long supplierId;
    private String status;
    private String parentSn;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 档案实体
     * @return VO
     */
    public static BatchSnVO from(MatBatchSn e) {
        BatchSnVO vo = new BatchSnVO();
        vo.setId(e.getId());
        vo.setObjType(e.getObjType());
        vo.setObjNo(e.getObjNo());
        vo.setMaterialId(e.getMaterialId());
        vo.setSourceType(e.getSourceType());
        vo.setSourceNo(e.getSourceNo());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setQty(e.getQty());
        vo.setSupplierId(e.getSupplierId());
        vo.setStatus(e.getStatus());
        vo.setParentSn(e.getParentSn());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}

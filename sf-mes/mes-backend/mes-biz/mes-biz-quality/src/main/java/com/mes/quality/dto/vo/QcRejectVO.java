package com.mes.quality.dto.vo;

import com.mes.quality.entity.QcReject;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品处置单 VO。
 */
@Data
public class QcRejectVO {
    private Long id;
    private String rejectNo;
    private Long inspectId;
    private String inspectNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private BigDecimal qty;
    private Integer decision;
    private String disposeRemark;
    private String approver;
    private LocalDateTime createTime;

    /**
     * 实体转 VO。
     *
     * @param e 处置单实体
     * @return VO
     */
    public static QcRejectVO from(QcReject e) {
        QcRejectVO vo = new QcRejectVO();
        vo.setId(e.getId());
        vo.setRejectNo(e.getRejectNo());
        vo.setInspectId(e.getInspectId());
        vo.setInspectNo(e.getInspectNo());
        vo.setMaterialId(e.getMaterialId());
        vo.setQty(e.getQty());
        vo.setDecision(e.getDecision());
        vo.setDisposeRemark(e.getDisposeRemark());
        vo.setApprover(e.getApprover());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}

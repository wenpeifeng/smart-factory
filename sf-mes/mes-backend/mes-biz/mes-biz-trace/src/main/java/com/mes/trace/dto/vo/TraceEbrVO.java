package com.mes.trace.dto.vo;

import com.mes.trace.entity.TraceEbr;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电子批记录 VO（列表不含 content，详情含解析后的 JSON 文本）。
 */
@Data
public class TraceEbrVO {
    private Long id;
    private String ebrNo;
    private Long workorderId;
    private String woNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private String content;
    private LocalDateTime generateTime;

    /**
     * 实体转 VO。
     *
     * @param e EBR 实体
     * @return VO
     */
    public static TraceEbrVO from(TraceEbr e) {
        TraceEbrVO vo = new TraceEbrVO();
        vo.setId(e.getId());
        vo.setEbrNo(e.getEbrNo());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setMaterialId(e.getMaterialId());
        vo.setBatchNo(e.getBatchNo());
        vo.setContent(e.getContent());
        vo.setGenerateTime(e.getGenerateTime());
        return vo;
    }
}

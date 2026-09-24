package com.mes.execution.dto.vo;

import com.mes.execution.entity.ExeStationRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 过站记录 VO。
 */
@Data
public class StationRecordVO {
    private Long id;
    private String recordNo;
    private Long workorderId;
    private String woNo;
    private Long woStepId;
    private Integer seqNo;
    private String operationName;
    private Long workcenterId;
    private Long deviceId;
    private String sn;
    private String keyComponentSn;
    private BigDecimal goodQty;
    private BigDecimal scrapQty;
    private BigDecimal reworkQty;
    private Integer workMinutes;
    private Long workerId;
    private String workerName;
    private LocalDateTime stationTime;
    private String result;

    /**
     * 实体转 VO。
     *
     * @param e 过站记录实体
     * @return VO
     */
    public static StationRecordVO from(ExeStationRecord e) {
        StationRecordVO vo = new StationRecordVO();
        vo.setId(e.getId());
        vo.setRecordNo(e.getRecordNo());
        vo.setWorkorderId(e.getWorkorderId());
        vo.setWoNo(e.getWoNo());
        vo.setWoStepId(e.getWoStepId());
        vo.setSeqNo(e.getSeqNo());
        vo.setOperationName(e.getOperationName());
        vo.setWorkcenterId(e.getWorkcenterId());
        vo.setDeviceId(e.getDeviceId());
        vo.setSn(e.getSn());
        vo.setKeyComponentSn(e.getKeyComponentSn());
        vo.setGoodQty(e.getGoodQty());
        vo.setScrapQty(e.getScrapQty());
        vo.setReworkQty(e.getReworkQty());
        vo.setWorkMinutes(e.getWorkMinutes());
        vo.setWorkerId(e.getWorkerId());
        vo.setWorkerName(e.getWorkerName());
        vo.setStationTime(e.getStationTime());
        vo.setResult(e.getResult());
        return vo;
    }
}

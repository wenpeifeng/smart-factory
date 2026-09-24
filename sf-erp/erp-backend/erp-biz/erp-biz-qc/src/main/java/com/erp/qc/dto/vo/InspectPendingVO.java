package com.erp.qc.dto.vo;

import lombok.Data;

import java.util.List;

/**
 * 来源单未完成检验卡点 VO（GET /qc/inspects/pending）。
 * <p>
 * 一期单据审核前调用：pending=true 表示该来源单仍有草稿（未检验）检验单，
 * 按约定应阻止来源单审核（集成约定见 docs/modules/qc.md）。
 */
@Data
public class InspectPendingVO {

    /** 来源单据类型 */
    private String bizType;

    /** 来源单据 ID */
    private Long bizId;

    /** 是否存在未完成检验（true=审核卡点） */
    private boolean pending;

    /** 未完成（草稿）检验单数 */
    private long pendingCount;

    /** 未完成检验单列表（id+单号） */
    private List<QcStatisticsVO.PendingInspectVO> pendingList;
}

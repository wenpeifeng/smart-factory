package com.mes.integration.service;

import com.mes.integration.erp.dto.ErpPickingDTO;
import com.mes.integration.erp.dto.ErpOrderStatusDTO;
import com.mes.integration.erp.dto.ErpQualityResultDTO;
import com.mes.integration.erp.dto.ErpReturnDTO;
import com.mes.integration.erp.dto.ErpWorkReportDTO;

/**
 * ERP 回传推送服务（MES -> ERP）。
 * <p>统一入口：落推送日志（幂等）-> 调 ERP 客户端 -> 更新状态；
 * 失败由定时任务重试（上限 max_retry），推送日志列表即对账依据。
 */
public interface ErpPushService {

    /**
     * 回传完工报工（按过站记录维度，同一记录只回传一次）。
     *
     * @param recordId 过站扫码记录 id（exe_station_record.id，幂等键之一）
     * @param dto      报文
     */
    void pushWorkReport(Long recordId, ErpWorkReportDTO dto);

    /**
     * 回传领料单。
     *
     * @param pickingId 领料单 id
     * @param dto       报文
     */
    void pushPicking(Long pickingId, ErpPickingDTO dto);

    /**
     * 回传退料单。
     *
     * @param returnId 退料单 id
     * @param dto      报文
     */
    void pushReturn(Long returnId, ErpReturnDTO dto);

    /**
     * 回传质检结果。
     *
     * @param inspectId 检验单 id
     * @param dto       报文
     */
    void pushQualityResult(Long inspectId, ErpQualityResultDTO dto);

    /**
     * 推送工单进度镜像（状态流转事件触发，woNo 维度幂等）。
     *
     * @param workorderId 工单 id
     * @param dto         进度报文
     */
    void pushOrderStatus(Long workorderId, ErpOrderStatusDTO dto);
}

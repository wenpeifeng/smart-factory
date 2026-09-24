package com.mes.integration.erp;

import com.mes.integration.erp.dto.ErpPickingDTO;
import com.mes.integration.erp.dto.ErpOrderStatusDTO;
import com.mes.integration.erp.dto.ErpQualityResultDTO;
import com.mes.integration.erp.dto.ErpReturnDTO;
import com.mes.integration.erp.dto.ErpWorkReportDTO;

/**
 * ERP 出站客户端（MES -> ERP 回传契约）。
 * <p>回传三类：完工报工 / 领料 / 退料 / 质检结果（四方法）。
 * 实现：stub（契约桩，默认）/ http（ERP 就绪后接入）。
 * 调用方为 {@code ErpPushService}，已统一做幂等 + 推送日志 + 重试，实现类无需自行幂等。
 */
public interface ErpClient {

    /**
     * 回传完工报工。
     *
     * @param dto 报工数据（woNo+operationName 幂等）
     */
    void pushWorkReport(ErpWorkReportDTO dto);

    /**
     * 回传领料单。
     *
     * @param dto 领料数据（pickNo 幂等）
     */
    void pushPicking(ErpPickingDTO dto);

    /**
     * 回传退料单。
     *
     * @param dto 退料数据（returnNo 幂等）
     */
    void pushReturn(ErpReturnDTO dto);

    /**
     * 回传质检结果。
     *
     * @param dto 质检结果（inspectNo 幂等）
     */
    void pushQualityResult(ErpQualityResultDTO dto);

    /**
     * 推送工单进度镜像（woNo 幂等，仅更新 ERP mes_status 镜像列）。
     *
     * @param dto 工单进度
     */
    void pushOrderStatus(ErpOrderStatusDTO dto);
}

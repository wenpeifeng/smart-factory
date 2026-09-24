package com.erp.integration.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.integration.dto.MesOrderStatusDTO;
import com.erp.integration.dto.MesPickingDTO;
import com.erp.integration.dto.MesQualityResultDTO;
import com.erp.integration.dto.MesReturnDTO;
import com.erp.integration.dto.MesWorkReportDTO;
import com.erp.integration.entity.IntMessageLog;

/**
 * MES 入站服务（MES→ERP 回传契约，六类接口之 #3~#6 的 ERP 侧落地）。
 * <p>统一幂等：int_message_log 唯一键 (biz_type, biz_key, event_time)，
 * 重复消息直接返回成功；业务处理与消息日志同一事务。
 * 入站链路无登录上下文，租户走 OrgContextHolder 默认租户 1000
 * （与定时任务先例一致口径）。
 */
public interface MesInboundService {

    /** 完工报工回传：产成品入库(PROD_IN) + 工单 finish_qty/bad_qty 累计（#4） */
    String receiveWorkReport(MesWorkReportDTO dto);

    /** 领料回传：材料出库(PROD_USE，扣仓库账)（#5） */
    String receivePicking(MesPickingDTO dto);

    /** 退料回传：反向入库(OTH_IN 冲销)（#5） */
    String receiveReturn(MesReturnDTO dto);

    /** 质检结果回传：按结论分支处理（#6，最小实现见实现类注释） */
    String receiveQualityResult(MesQualityResultDTO dto);

    /** 工单进度镜像：更新 mrp_work_order.mes_status 镜像列（#3） */
    String receiveOrderStatus(MesOrderStatusDTO dto);

    /** 入站消息日志分页（管理端可视，含 DUPLICATE 标记） */
    IPage<IntMessageLog> pageLogs(long pageNum, long pageSize, String bizType, String status);
}

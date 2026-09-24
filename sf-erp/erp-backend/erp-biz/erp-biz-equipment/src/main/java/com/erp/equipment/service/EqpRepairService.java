package com.erp.equipment.service;

import com.erp.common.result.PageResult;
import com.erp.equipment.dto.form.RepairAcceptForm;
import com.erp.equipment.dto.form.RepairCompleteForm;
import com.erp.equipment.dto.form.RepairReportForm;
import com.erp.equipment.dto.request.RepairQuery;
import com.erp.equipment.dto.vo.RepairDetailVO;
import com.erp.equipment.dto.vo.RepairVO;

/**
 * 维修工单 Service —— 报修→接单→完工→关闭 的全流程。
 * <p>
 * 完工是唯一触碰库存的动作：按备件明细逐行扣 inv_stock、写 EQP_OUT 流水，
 * 库存不足抛 BusinessException 整体回滚（工单保持维修中）。
 */
public interface EqpRepairService {

    /**
     * 工单分页（含设备信息与备件成本合计）。
     *
     * @param query 查询条件（status/deviceId/keyword）
     * @return 分页结果
     */
    PageResult<RepairVO> page(RepairQuery query);

    /**
     * 工单详情（头 + 备件明细）。
     *
     * @param id 工单 ID
     * @return 工单详情
     * @throws com.erp.common.exception.BusinessException 工单不存在
     */
    RepairDetailVO detail(Long id);

    /**
     * 报修：创建待维修工单（WX+日期+流水，报修时间=当前）。
     *
     * @param form 报修表单
     * @return 新工单 ID
     * @throws com.erp.common.exception.BusinessException 设备不存在或已报废
     */
    Long report(RepairReportForm form);

    /**
     * 接单：待维修 → 维修中，登记维修人；设备状态联动置「维修中」。
     *
     * @param id   工单 ID
     * @param form 维修人
     */
    void accept(Long id, RepairAcceptForm form);

    /**
     * 完工：维修中 → 已修复，登记故障原因与备件明细；
     * 备件逐行出库（EQP_OUT），任一行不足整体回滚；设备状态联动回「运行」。
     *
     * @param id   工单 ID
     * @param form 完工表单（故障原因/仓库/备件行）
     */
    void complete(Long id, RepairCompleteForm form);

    /**
     * 关闭：已修复 → 已关闭（归档，无库存动作）。
     *
     * @param id 工单 ID
     */
    void close(Long id);
}

package com.erp.cost.service;

import com.erp.cost.dto.vo.CostPeriodVO;
import com.erp.cost.dto.vo.SettleResultVO;
import com.erp.cost.dto.vo.SettleSummaryVO;

import java.util.List;

/**
 * 存货月末核算服务：期间管理 / 结账（自动转账凭证）/ 收发存汇总。
 */
public interface CostSettleService {

    /** 全部核算期间列表（倒序，供前端期间列表） */
    List<CostPeriodVO> periods();

    /**
     * 月末结账（POST /cost/settle/{period}）。
     * <p>
     * 事务内执行：校验期间 OPEN（不存在则视为 OPEN 创建）→ 汇总本期出库成本 →
     * 自动生成转账凭证（借 6401 主营业务成本=销售/其他出库、借 5001 生产成本=生产领料、
     * 贷 1405 库存商品=出库成本合计，DRAFT 状态待会计复核）→ 期间置 CLOSED。
     * 本期出库成本为 0 时不生成凭证（空凭证无记账意义），期间直接 CLOSED。
     * 后续期间已结账时拒绝（结账必须按期间顺序）。
     */
    SettleResultVO settle(String period);

    /**
     * 收发存汇总（GET /cost/settle/summary/{period}）：
     * 期初（期间前最后一行运行余额）+ 本期入/出 + 期末（推算），按物料粒度 + 合计。
     */
    SettleSummaryVO summary(String period);
}

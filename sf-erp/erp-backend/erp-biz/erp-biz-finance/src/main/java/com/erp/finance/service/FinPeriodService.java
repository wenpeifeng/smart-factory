package com.erp.finance.service;

import com.erp.finance.dto.vo.PeriodVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 总账会计期间服务（V521）。
 * <p>
 * 期间 = 凭证日期所在自然月（yyyyMM），按账套隔离；
 * 结账要求该期凭证全部 POSTED；CLOSED 期间禁止新增/修改/过账/删除该期凭证
 * （凭证四入口调用 {@link #assertPeriodOpen} 校验）。
 */
public interface FinPeriodService {

    /**
     * 期间列表（当前账套）：凭证日期出现过的自然月 ∪ fin_period 已建行，
     * 附该期凭证数 / 未过账数，期间倒序。
     *
     * @return 期间行列表
     */
    List<PeriodVO> list();

    /**
     * 结账：校验该期凭证全部 POSTED（存在 DRAFT 拒绝并提示数量）→ CLOSED。
     * fin_period 无行时插入后结账（懒建行）。
     *
     * @param period 期间（yyyyMM）
     */
    void close(String period);

    /**
     * 反结账：CLOSED → OPEN（记录反结账人/时间，操作日志由 Controller 注解留痕）。
     *
     * @param period 期间（yyyyMM）
     */
    void reopen(String period);

    /**
     * 期间控制：校验凭证日期所在期间未结账，已结账抛业务异常。
     * <p>
     * 凭证新增/修改/过账/删除四入口均调用；无 fin_period 行视为 OPEN（未结账）。
     *
     * @param voucherDate 凭证日期（期间判定=该日期所在月）
     * @param bookId      账套 ID
     * @param action      动作描述（用于异常文案，如"新增凭证"）
     */
    void assertPeriodOpen(LocalDate voucherDate, Long bookId, String action);
}

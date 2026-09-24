package com.erp.finance.dto.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证引擎扫描候选行（biz_type + 单据 id + 单号，V521）。
 * <p>
 * record 构造器按列序位置映射（与发票模块 InvQueryMapper 同一约定）。
 *
 * @param bizType 业务类型（PUR_INSTOCK / SAL_OUTSTOCK / ... 八类）
 * @param id      来源单据 ID
 * @param docNo   来源单号
 * @param docDate 业务日期（凭证日期候选）
 */
public record EngineScanCandidateVO(String bizType, Long id, String docNo, LocalDate docDate) {
}

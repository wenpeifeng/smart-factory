package com.erp.finance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.finance.dto.vo.PeriodStatRowVO;
import com.erp.finance.entity.FinPeriod;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 总账会计期间 Mapper。
 * <p>
 * 期间 = 凭证日期所在自然月（DATE_FORMAT(voucher_date,'%Y%m')），
 * 凭证数/未过账数聚合按账套隔离；租户插件自动追加 tenant_id。
 */
@Mapper
public interface FinPeriodMapper extends BaseMapper<FinPeriod> {

    /**
     * 当前账套各自然月的凭证数 / 未过账数 / 已过账数聚合。
     *
     * @param bookId 账套 ID
     * @return 每月一行
     */
    @Select("""
            SELECT DATE_FORMAT(voucher_date, '%Y%m') AS period,
                   COUNT(*) AS voucherCount,
                   SUM(CASE WHEN status = 'DRAFT' THEN 1 ELSE 0 END) AS draftCount,
                   SUM(CASE WHEN status = 'POSTED' THEN 1 ELSE 0 END) AS postedCount
            FROM fin_voucher
            WHERE deleted = 0 AND book_id = #{bookId}
            GROUP BY DATE_FORMAT(voucher_date, '%Y%m')
            """)
    List<PeriodStatRowVO> selectPeriodStats(@Param("bookId") Long bookId);

    /**
     * 统计某期间未过账（DRAFT）凭证数（结账前置校验）。
     *
     * @param bookId 账套 ID
     * @param period 期间（yyyyMM）
     * @return DRAFT 凭证数
     */
    @Select("""
            SELECT COUNT(*)
            FROM fin_voucher
            WHERE deleted = 0 AND book_id = #{bookId} AND status = 'DRAFT'
              AND DATE_FORMAT(voucher_date, '%Y%m') = #{period}
            """)
    Long countDraftVouchers(@Param("bookId") Long bookId, @Param("period") String period);
}

package com.erp.finance.mapper;

import com.erp.finance.dto.vo.EngineDocDetailVO;
import com.erp.finance.dto.vo.EngineDocHeaderVO;
import com.erp.finance.dto.vo.EngineScanCandidateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 凭证模板引擎跨模块只读 Mapper（SQL 见 resources/mapper/finance/VoucherEngineMapper.xml，V521）。
 * <p>
 * 遵循并行协作约定：只读 pur_instock/pur_return/sal_outstock/sal_return/
 * inv_sales_invoice/inv_purchase_invoice/fin_receipt/fin_payment 八类约定表
 * （SELECT / NOT EXISTS），不 import 对方代码、不做物理外键——
 * 与 WMS 扫描器（WmsTaskMapper）、发票模块（InvQueryMapper）同一模式。
 * 租户插件对全部 SQL 自动追加 tenant_id。
 */
@Mapper
public interface VoucherEngineMapper {

    /**
     * 扫描候选：已生效（已审核/已开票已认证/已落库）且当前账套尚无对应凭证的八类单据。
     * <p>
     * 生效口径：出入库/退货 status=10；销项发票 status=10（含红字负数票）；
     * 进项发票 status=10（已认证）；收付款单无状态列，落库即生效。
     *
     * @param bizType  业务类型（null=全部八类）
     * @param from     业务日期起（含，可空）
     * @param to       业务日期止（含，可空）
     * @param daysBack 回看窗口天数（自动扫描用；与 from/to 同时给出时取交集，可空=不限）
     * @param bookId   账套 ID（NOT EXISTS 判断该账套是否已生成过凭证）
     * @return 候选行（biz_type + 单据 id + 单号）
     */
    List<EngineScanCandidateVO> selectScanCandidates(@Param("bizType") String bizType,
                                                     @Param("from") LocalDate from,
                                                     @Param("to") LocalDate to,
                                                     @Param("daysBack") Integer daysBack,
                                                     @Param("bookId") Long bookId);

    /**
     * 来源单据头（八类归一化视图）。
     *
     * @param bizType 业务类型
     * @param id      来源单据 ID
     * @return 单据头快照（含金额/日期/账户/红字标志）
     */
    EngineDocHeaderVO selectDocHeader(@Param("bizType") String bizType, @Param("id") Long id);

    /**
     * 来源单据明细（采购/销售四类有明细；发票与收付款单无明细返回空）。
     *
     * @param bizType 业务类型
     * @param id      来源单据 ID
     * @return 明细行（归一化列，见 {@link EngineDocDetailVO}）
     */
    List<EngineDocDetailVO> selectDocDetails(@Param("bizType") String bizType, @Param("id") Long id);

    /**
     * 按来源定位凭证 ID（含逻辑删行——用于 uk_voucher_source 冲突诊断）。
     * <p>
     * MP 逻辑删查询自动过滤 deleted=1，软删的旧凭证仍占用 uk(book_id,source_type,source_id)，
     * 重新生成会撞唯一键；本方法绕过过滤定位占用行。
     *
     * @param bookId  账套 ID
     * @param bizType 业务类型
     * @param bizId   来源单据 ID
     * @return 凭证 ID（含已删行；无则 null）
     */
    Long selectAnyVoucherIdBySource(@Param("bookId") Long bookId, @Param("bizType") String bizType,
                                    @Param("bizId") Long bizId);

    /**
     * 释放已逻辑删凭证占用的来源键（deleted=1 才生效，返回影响行数）。
     * <p>
     * 软删行对所有业务查询不可见，置空 source 列无副作用，
     * 使"删除自动凭证后重扫重新生成"成为可能（uk 冲突让位）。
     *
     * @param voucherId 凭证 ID
     * @return 影响行数（0=行非软删或不存在，调用方按并发冲突处理）
     */
    int clearSourceOfDeletedVoucher(@Param("voucherId") Long voucherId);
}

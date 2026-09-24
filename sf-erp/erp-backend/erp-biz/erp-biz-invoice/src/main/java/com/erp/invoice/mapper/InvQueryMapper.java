package com.erp.invoice.mapper;

import com.erp.invoice.dto.vo.BizStatRawVO;
import com.erp.invoice.dto.vo.InstockRawVO;
import com.erp.invoice.dto.vo.MonthlyRawVO;
import com.erp.invoice.dto.vo.OutstockRawVO;
import com.erp.invoice.dto.vo.TaxStatRawVO;
import com.erp.invoice.dto.vo.ToBeInvoicedVO;
import com.erp.invoice.dto.vo.ToBeRegisteredVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 发票模块复杂查询 Mapper（SQL 见 resources/mapper/invoice/InvQueryMapper.xml）。
 * <p>
 * 职责：
 * <ul>
 *   <li>跨模块只读：销售出库单（头/明细汇总）、采购入库单（头/明细税率）、
 *       客商名称 —— 直接 SELECT sal_outstock / sal_outstock_detail / pur_instock /
 *       pur_instock_detail / base_customer / base_supplier，不 import 对方模块代码
 *       （并行协作约定）；客商表未落库时由 support 层降级（名称返回 null）；</li>
 *   <li>待开票 / 待登记清单：已审核业务单 − 未红冲蓝字发票占用；</li>
 *   <li>税负统计 / 月度价税 / 对账聚合。</li>
 * </ul>
 */
@Mapper
public interface InvQueryMapper {

    /**
     * 查询销售出库单头（只读快照）。
     *
     * @param id 出库单 ID
     * @return null 表示不存在或已删
     */
    OutstockRawVO selectOutstock(@Param("id") Long id);

    /**
     * 汇总出库明细不含税金额（开票金额以此为准，明细缺失回退单头合计）。
     *
     * @param outId 出库单 ID
     * @return 明细金额合计（空明细返回 0）
     */
    java.math.BigDecimal sumOutstockDetailAmount(@Param("outId") Long outId);

    /**
     * 查询采购入库单头（只读快照）。
     *
     * @param id 入库单 ID
     * @return null 表示不存在或已删
     */
    InstockRawVO selectInstock(@Param("id") Long id);

    /**
     * 查询入库明细的去重税率集合（用于登记进项发票时的默认税率推断）。
     *
     * @param instockId 入库单 ID
     * @return 去重后的税率列表（百分数）
     */
    List<java.math.BigDecimal> selectInstockDetailTaxRates(@Param("instockId") Long instockId);

    /**
     * 客户待开票清单：已审核出库 − 未红冲蓝字发票占用。
     *
     * @param customerId 客户 ID（null = 全部客户）
     * @return 清单行（金额取出库明细汇总，回退单头合计）
     */
    List<ToBeInvoicedVO> selectToBeInvoicedOutstocks(@Param("customerId") Long customerId);

    /**
     * 供应商待登记清单：已审核入库 − 未红冲蓝字发票占用。
     *
     * @param supplierId 供应商 ID（null = 全部供应商）
     * @return 清单行
     */
    List<ToBeRegisteredVO> selectToBeRegisteredInstocks(@Param("supplierId") Long supplierId);

    /**
     * 按主键集合查询客户名称（客商名称降级查询用）。
     *
     * @param ids 客户 ID 集合（非空）
     * @return 行数据：id / name 两列
     */
    List<Map<String, Object>> selectCustomerRows(@Param("ids") List<Long> ids);

    /**
     * 按主键集合查询供应商名称。
     *
     * @param ids 供应商 ID 集合（非空）
     * @return 行数据：id / name 两列
     */
    List<Map<String, Object>> selectSupplierRows(@Param("ids") List<Long> ids);

    /**
     * 期间销项聚合（不含税金额 / 税额，已红冲蓝票计入、红字负数冲减）。
     *
     * @param from 期间起（invoice_date ≥ from）
     * @param to   期间止（invoice_date ≤ to）
     * @return 合计行（SUM 由 COALESCE 兜底为 0）
     */
    TaxStatRawVO selectSalesTaxStat(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 期间进项聚合（仅已生效票，按认证日期归属期间）。
     *
     * @param from 期间起（auth_date ≥ from）
     * @param to   期间止（auth_date ≤ to）
     * @return 合计行
     */
    TaxStatRawVO selectPurchaseTaxStat(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 年度销项月度价税聚合（按开票日期分月）。
     *
     * @param from 年初（含）
     * @param to   年末（含）
     * @return 有发生额的月份行（月份 1-12）
     */
    List<MonthlyRawVO> selectSalesMonthly(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 年度进项月度价税聚合（按开票日期分月）。
     *
     * @param from 年初（含）
     * @param to   年末（含）
     * @return 有发生额的月份行
     */
    List<MonthlyRawVO> selectPurchaseMonthly(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 客户对账业务侧聚合：期间内已审核出库单。
     *
     * @param customerId 客户 ID
     * @param from       出库日期起（含）
     * @param to         出库日期止（含）
     * @return 单据数与金额合计
     */
    BizStatRawVO sumCustomerOutstock(@Param("partnerId") Long customerId,
                                     @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 客户对账开票侧聚合：期间内已生效销项发票（净额）。
     *
     * @param customerId 客户 ID
     * @param from       开票日期起（含）
     * @param to         开票日期止（含）
     * @return 发票数与金额合计
     */
    BizStatRawVO sumSalesInvoiced(@Param("partnerId") Long customerId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 供应商对账业务侧聚合：期间内已审核入库单。
     *
     * @param supplierId 供应商 ID
     * @param from       入库日期起（含）
     * @param to         入库日期止（含）
     * @return 单据数与金额合计
     */
    BizStatRawVO sumSupplierInstock(@Param("partnerId") Long supplierId,
                                    @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 供应商对账开票侧聚合：期间内已生效进项发票（净额）。
     *
     * @param supplierId 供应商 ID
     * @param from       开票日期起（含）
     * @param to         开票日期止（含）
     * @return 发票数与金额合计
     */
    BizStatRawVO sumPurchaseInvoiced(@Param("partnerId") Long supplierId,
                                     @Param("from") LocalDate from, @Param("to") LocalDate to);
}

package com.erp.basedata.service;

import com.erp.basedata.dto.form.PriceLevelForm;
import com.erp.basedata.dto.form.PriceQtyForm;
import com.erp.basedata.dto.vo.PriceLevelVO;
import com.erp.basedata.dto.vo.PriceQtyVO;
import com.erp.basedata.entity.SalPriceLevel;
import com.erp.basedata.entity.SalPriceQty;
import com.erp.common.result.PageResult;

import java.math.BigDecimal;

/**
 * 销售价格策略 Service（等级价 + 批量价两张策略表的 CRUD 与引擎取数）。
 */
public interface SalPriceStrategyService {

    /* ===================== 等级价 ===================== */

    /**
     * 等级价分页。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param materialId 物料（可空）
     * @param level      等级 A/B/C（可空）
     * @param status     状态（可空）
     * @return 等级价分页
     */
    PageResult<PriceLevelVO> pageLevels(long pageNum, long pageSize, Long materialId, String level, Integer status);

    /**
     * 取物料在指定等级的启用等级价（uk 物料+等级，至多一条）。
     *
     * @param materialId 物料 ID
     * @param level      客户等级 A/B/C
     * @return 启用的等级价；无则 null
     */
    SalPriceLevel findEnabledLevel(Long materialId, String level);

    /**
     * 新增等级价（uk 物料+等级，重复抛 DUPLICATE_DATA）。
     *
     * @param form 表单
     * @return 新增 ID
     */
    Long createLevel(PriceLevelForm form);

    /**
     * 修改等级价。
     *
     * @param id   ID
     * @param form 表单
     */
    void updateLevel(Long id, PriceLevelForm form);

    /**
     * 删除等级价（逻辑删除）。
     *
     * @param id ID
     */
    void deleteLevel(Long id);

    /* ===================== 批量价 ===================== */

    /**
     * 批量价分页。
     *
     * @param pageNum    页码
     * @param pageSize   页大小
     * @param materialId 物料（可空）
     * @param status     状态（可空）
     * @return 批量价分页（物料+起订量升序）
     */
    PageResult<PriceQtyVO> pageQtys(long pageNum, long pageSize, Long materialId, Integer status);

    /**
     * 匹配数量阶梯：满足 qty 的最高 min_qty 档（即满足数量的最低价格档）。
     *
     * @param materialId 物料 ID
     * @param qty        数量（空视为 0，即匹配不到任何 &gt;0 的阶梯）
     * @return 命中的批量价档；无则 null
     */
    SalPriceQty matchQtyTier(Long materialId, BigDecimal qty);

    /**
     * 新增批量价（uk 物料+起订量，重复抛 DUPLICATE_DATA）。
     *
     * @param form 表单
     * @return 新增 ID
     */
    Long createQty(PriceQtyForm form);

    /**
     * 修改批量价。
     *
     * @param id   ID
     * @param form 表单
     */
    void updateQty(Long id, PriceQtyForm form);

    /**
     * 删除批量价（逻辑删除）。
     *
     * @param id ID
     */
    void deleteQty(Long id);
}

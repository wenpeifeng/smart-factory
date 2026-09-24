package com.erp.mrp.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.mrp.dto.vo.MaterialBriefVO;
import com.erp.mrp.dto.vo.MaterialQtyVO;
import com.erp.mrp.dto.vo.SupplierBriefVO;
import com.erp.mrp.dto.vo.WarehouseBriefVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨模块集成网关 Mapper —— 按"并行协作约定"直接读写其他模块的约定表，
 * 本模块不 import 对方任何 Java 代码；表结构以 docs/modules/mrp.md 中的约定列为准。
 * <p>
 * 读：base_material / base_warehouse / base_supplier / inv_stock（汇总口径）/ pur_order(+detail)；
 * 写：fin_payable（委外应付）。
 * <p>
 * <b>V2101 起 inv_stock / inv_movement 的全部写入已切换为 erp-inventory 的
 * StockService（stockIn/stockOut，批次感知 + FIFO 策略），本 Mapper 不再保留
 * 对库存表的任何写 SQL。</b>
 * <p>
 * 契约（详见 docs/modules/mrp.md）：
 * <ul>
 *   <li>库存汇总（MRP 运算"全仓合计"口径）只读 inv_stock；</li>
 *   <li>fin_payable 写入仅委外加工费（状态 UNSETTLED）；</li>
 *   <li>pur_order.status='APPROVED' 且未入库完的明细为采购在途。</li>
 * </ul>
 */
@Mapper
public interface MrpIntegrationMapper {

    /**
     * 批量查物料简要信息（编码/名称/规格/单位）。
     *
     * @param ids 物料ID集合（空集合直接返回空列表）
     * @return 物料简要信息列表
     */
    List<MaterialBriefVO> selectMaterials(@Param("ids") List<Long> ids);

    /**
     * 分页查询物料（前端物料选择器用）。
     *
     * @param page    分页参数（由 MP 分页拦截器处理）
     * @param keyword 编码/名称模糊关键词，可空
     * @return 物料分页
     */
    IPage<MaterialBriefVO> pageMaterials(Page<MaterialBriefVO> page, @Param("keyword") String keyword);

    /**
     * 按编码/名称关键词查物料ID（最多200条，列表页关键词过滤用，参数化防注入）。
     *
     * @param keyword 编码/名称模糊关键词，可空（空=不过滤直接返回前200个ID）
     * @return 物料ID列表
     */
    List<Long> selectMaterialIds(@Param("keyword") String keyword);

    /**
     * 查仓库简要列表（发料/入库仓库选择器用）。
     *
     * @return 仓库列表（id + name）
     */
    List<WarehouseBriefVO> selectWarehouses();

    /**
     * 查供应商简要列表（委外单选择器用）。
     *
     * @param keyword 名称模糊关键词，可空
     * @return 供应商列表（id + name），最多 100 条
     */
    List<SupplierBriefVO> selectSuppliers(@Param("keyword") String keyword);

    /**
     * 按物料汇总现有库存（MRP 运算取“全仓合计”口径）。
     *
     * @param materialIds 物料ID集合
     * @return materialId → qty 汇总行
     */
    List<MaterialQtyVO> sumStockByMaterial(@Param("ids") List<Long> materialIds);

    /**
     * 按物料汇总采购在途数量（pur_order 已审核未入库完的明细）。
     * <p>
     * 注意：pur_order 表不存在时由 Service 先行探测并跳过（采购模块未上线时在途=0）。
     *
     * @param materialIds 物料ID集合
     * @return materialId → 在途数量
     */
    List<MaterialQtyVO> sumOnwayByMaterial(@Param("ids") List<Long> materialIds);

    /**
     * 写应付（fin_payable 集成约定，委外加工费；状态固定 UNSETTLED 未核销）。
     *
     * @param supplierId 供应商ID
     * @param bizNo      来源业务单号（委外单号）
     * @param amount     应付金额
     * @param remark     摘要
     * @return 影响行数
     */
    int insertPayable(@Param("supplierId") Long supplierId,
                      @Param("bizNo") String bizNo,
                      @Param("amount") BigDecimal amount,
                      @Param("remark") String remark);

    /**
     * 探测当前库中表是否存在（并行模块未上线时优雅降级用）。
     *
     * @param tableName 表名
     * @return 1=存在 0=不存在
     */
    /** information_schema 探测不走租户过滤（该库无 tenant_id 列） */
    @InterceptorIgnore(tenantLine = "true")
    int tableExists(@Param("tableName") String tableName);
}

package com.erp.qc.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 跨模块只读 Mapper —— 直查一期模块与基础资料的约定表。
 * <p>
 * 并行开发约定（docs/开发规范.md）：跨模块【只按约定表结构读写，不 import 他人业务代码】。
 * 本 Mapper 覆盖（表结构按各模块迁移脚本对齐）：
 * <ul>
 *   <li>pur_instock / pur_instock_detail（V201）—— IQC 来源单；</li>
 *   <li>mrp_work_order（V601）—— FQC 来源单；</li>
 *   <li>sal_return / sal_return_detail（V303）—— 销售退货 IQC 复检来源单；</li>
 *   <li>base_material（V101，单位经 unit_id 关联 base_unit.name）—— 物料主数据。</li>
 * </ul>
 * 本 Mapper 只读，禁止出现 INSERT/UPDATE/DELETE。
 */
public interface CrossModuleMapper {

    /**
     * 采购入库单引用（IQC 来源）。
     */
    @Data
    class InstockRef {
        private Long id;
        /** 入库单号 */
        private String instockNo;
        /** 供应商 ID */
        private Long supplierId;
    }

    /**
     * 生产工单引用（FQC 来源）。
     */
    @Data
    class WorkOrderRef {
        private Long id;
        /** 工单号 */
        private String orderNo;
        /** 工单物料 ID */
        private Long materialId;
        /** 工单数量 */
        private BigDecimal qty;
    }

    /**
     * 销售退货单引用（IQC 复检来源）。
     */
    @Data
    class ReturnRef {
        private Long id;
        /** 退货单号 */
        private String returnNo;
    }

    /**
     * 来源单物料行（生成检验单时带出）。
     */
    @Data
    class SourceLine {
        /** 物料 ID */
        private Long materialId;
        /** 数量（入库/退货数量） */
        private BigDecimal qty;
    }

    /**
     * 查询采购入库单（不含已删）。
     *
     * @return 不存在返回 null
     */
    @Select("SELECT id, instock_no AS instockNo, supplier_id AS supplierId "
            + "FROM pur_instock WHERE id = #{id} AND deleted = 0")
    InstockRef findInstock(@Param("id") Long id);

    /**
     * 查询采购入库单物料行（含停用物料，保留历史资料口径）。
     */
    @Select("SELECT material_id AS materialId, qty FROM pur_instock_detail "
            + "WHERE instock_id = #{instockId} AND deleted = 0 ORDER BY id")
    List<SourceLine> findInstockLines(@Param("instockId") Long instockId);

    /**
     * 查询生产工单（不含已删）。
     *
     * @return 不存在返回 null
     */
    @Select("SELECT id, order_no AS orderNo, material_id AS materialId, qty "
            + "FROM mrp_work_order WHERE id = #{id} AND deleted = 0")
    WorkOrderRef findWorkOrder(@Param("id") Long id);

    /**
     * 查询销售退货单（不含已删）。
     *
     * @return 不存在返回 null
     */
    @Select("SELECT id, return_no AS returnNo FROM sal_return WHERE id = #{id} AND deleted = 0")
    ReturnRef findSalReturn(@Param("id") Long id);

    /**
     * 查询销售退货单物料行。
     */
    @Select("SELECT material_id AS materialId, qty FROM sal_return_detail "
            + "WHERE return_id = #{returnId} AND deleted = 0 ORDER BY id")
    List<SourceLine> findSalReturnLines(@Param("returnId") Long returnId);

    /**
     * 物料引用（校验/展示/模板匹配用）。
     */
    @Data
    class MaterialRef {
        private Long id;
        /** 物料编码 */
        private String code;
        /** 物料名称 */
        private String name;
        /** 规格 */
        private String spec;
        /** 计量单位名称（关联 base_unit.name） */
        private String unit;
        /** 物料分类 ID（模板匹配用，无分类为 null） */
        private Long categoryId;
    }

    /**
     * 查询物料（不含已删；含停用，保留历史资料口径）。
     *
     * @return 不存在返回 null
     */
    @Select("""
            SELECT m.id, m.code, m.name, m.spec, u.name AS unit, m.category_id AS categoryId
            FROM base_material m
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE m.id = #{id} AND m.deleted = 0
            """)
    MaterialRef findMaterial(@Param("id") Long id);

    /**
     * 查询供应商名称（展示用，含已停用的历史数据）。
     */
    @Select("SELECT name FROM base_supplier WHERE id = #{id} AND deleted = 0")
    String selectSupplierName(@Param("id") Long id);

    /**
     * 查询物料分类名称（模板详情展示用；不存在返回 null）。
     */
    @Select("SELECT name FROM base_material_category WHERE id = #{id} AND deleted = 0")
    String selectCategoryName(@Param("id") Long id);
}

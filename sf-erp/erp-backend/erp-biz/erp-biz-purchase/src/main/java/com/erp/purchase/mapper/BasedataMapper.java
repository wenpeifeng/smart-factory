package com.erp.purchase.mapper;

import com.erp.purchase.dto.vo.OptionVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 基础资料只读 Mapper——直查 base_supplier / base_material / base_unit / base_warehouse 表。
 * <p>
 * 并行开发约定：basedata 模块的 Java 代码由他人并行开发，采购模块【不 import 对方任何类】，
 * 跨模块数据一律通过本 Mapper 的 SQL JOIN/投影自查库表（表结构按 basedata V101 实际脚本对齐：
 * base_material 的计量单位是 unit_id 外键，名称在 base_unit.name；启用状态 status=1）。
 * 本 Mapper 只读，禁止出现 INSERT/UPDATE/DELETE。
 */
public interface BasedataMapper {

    /**
     * 供应商下拉选项（仅启用，按编码排序，ERP 基础资料量级可控）。
     */
    @Select("SELECT id, code, name FROM base_supplier WHERE deleted = 0 AND status = 1 ORDER BY code LIMIT 500")
    List<OptionVO> listSuppliers();

    /**
     * 物料下拉选项（仅启用，含规格与计量单位名称，单位经 base_unit 关联）。
     */
    @Select("""
            SELECT m.id, m.code, m.name, m.spec, u.name AS unit
            FROM base_material m
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE m.deleted = 0 AND m.status = 1
            ORDER BY m.code LIMIT 1000
            """)
    List<OptionVO> listMaterials();

    /**
     * 仓库下拉选项（仅启用）。
     */
    @Select("SELECT id, code, name FROM base_warehouse WHERE deleted = 0 AND status = 1 ORDER BY code LIMIT 200")
    List<OptionVO> listWarehouses();

    /**
     * 校验供应商是否存在且启用。
     *
     * @return 1 存在 / 0 不存在或停用
     */
    @Select("SELECT COUNT(*) FROM base_supplier WHERE id = #{id} AND deleted = 0 AND status = 1")
    int countSupplierById(@Param("id") Long id);

    /**
     * 查询供应商黑名单标记（V141 供应商准入）：1 黑名单 / 0 否 / null 不存在。
     */
    @Select("SELECT is_blacklist FROM base_supplier WHERE id = #{id} AND deleted = 0")
    Integer selectSupplierBlacklist(@Param("id") Long id);

    /**
     * 校验物料是否存在且启用。
     */
    @Select("SELECT COUNT(*) FROM base_material WHERE id = #{id} AND deleted = 0 AND status = 1")
    int countMaterialById(@Param("id") Long id);

    /**
     * 校验仓库是否存在且启用。
     */
    @Select("SELECT COUNT(*) FROM base_warehouse WHERE id = #{id} AND deleted = 0 AND status = 1")
    int countWarehouseById(@Param("id") Long id);

    /**
     * 查询供应商名称（单据展示用，含已停用的历史数据；不存在返回 null）。
     */
    @Select("SELECT name FROM base_supplier WHERE id = #{id} AND deleted = 0")
    String selectSupplierName(@Param("id") Long id);

    /**
     * 查询仓库名称（单据展示用；不存在返回 null）。
     */
    @Select("SELECT name FROM base_warehouse WHERE id = #{id} AND deleted = 0")
    String selectWarehouseName(@Param("id") Long id);
}

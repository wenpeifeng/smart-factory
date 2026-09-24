package com.erp.hr.mapper;

import com.erp.hr.dto.vo.DeptBriefVO;
import com.erp.hr.dto.vo.MaterialBriefVO;
import com.erp.hr.dto.vo.WorkOrderSourceVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 跨模块集成网关 Mapper —— 按"并行协作约定"直接读其他模块的约定表，
 * 本模块不 import 对方任何 Java 代码；表结构以 docs/modules/hr.md 中的约定列为准。
 * <p>
 * 只读约定列：
 * <ul>
 *   <li>sys_dept(id, parent_id, name, status) —— 员工部门回显/校验；</li>
 *   <li>base_material(id, code, name, spec, status) —— 计件价目物料回显/选择器；</li>
 *   <li>mrp_work_order(id, order_no, material_id, finish_qty, status, create_by, update_time)
 *       —— 计件归集的完工数据来源（状态 FINISHED/CLOSED 且 finish_qty&gt;0）。</li>
 * </ul>
 */
@Mapper
public interface HrIntegrationMapper {

    /** 判断约定表是否已建（模块未上线时优雅降级提示） */
    /** information_schema 探测不走租户过滤（该库无 tenant_id 列） */
    @InterceptorIgnore(tenantLine = "true")
    int tableExists(@Param("tableName") String tableName);

    /** 部门下拉（含 parentId 供前端组树） */
    List<DeptBriefVO> selectDepts();

    /** 按主键取部门名（员工建档校验），不存在返回 null */
    String selectDeptName(@Param("id") Long id);

    /** 按主键取物料简要信息（价目校验），不存在返回 null */
    MaterialBriefVO selectMaterial(@Param("id") Long id);

    /** 物料选择器（编码/名称模糊，限 50 条） */
    List<MaterialBriefVO> selectMaterials(@Param("keyword") String keyword);

    /**
     * 查询某期间可归集的完工生产订单。
     * 口径：状态 FINISHED/CLOSED、finish_qty&gt;0、update_time 落在该期间
     * （一期无完工时间字段，以最后更新时间近似），且未被其他期间归集过（防跨期重复计件）。
     */
    List<WorkOrderSourceVO> selectFinishedWorkOrders(@Param("period") String period,
                                                     @Param("periodId") Long periodId);
}

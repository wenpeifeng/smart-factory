package com.erp.asset.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.erp.asset.dto.vo.CategoryStatsRawVO;
import com.erp.asset.dto.vo.DeptStatsRawVO;
import com.erp.asset.entity.AstDepreciation;

import java.util.List;
import java.util.Map;

/**
 * 固定资产复杂查询 Mapper（SQL 见 resources/mapper/asset/AstQueryMapper.xml）。
 * <p>
 * 职责：
 * <ul>
 *   <li>折旧期间行锁查询（FOR UPDATE 串行化同期间并发 run）；</li>
 *   <li>跨模块只读名称查询：sys_dept 部门名 / base_supplier 供应商名
 *       （不 import 对方代码，遵守并行协作约定；表未落库时 Service 层降级为 null）；</li>
 *   <li>资产统计：部门 / 类别维度的原值、累计折旧（截至期间）、净值汇总。</li>
 * </ul>
 */
@Mapper
public interface AstQueryMapper {

    /**
     * 按期间号加行锁查询折旧期间（事务内调用，串行化同期间并发计提）。
     *
     * @param period 会计期间（yyyyMM）
     * @return 期间实体；不存在返回 null
     */
    AstDepreciation selectPeriodForUpdate(@Param("period") String period);

    /**
     * 按主键集合查询部门名称（sys_dept 只读）。
     *
     * @param ids 部门 ID 集合（非空）
     * @return 行数据：id / name 两列
     */
    List<Map<String, Object>> selectDeptNames(@Param("ids") List<Long> ids);

    /**
     * 按主键集合查询供应商名称（base_supplier 只读）。
     *
     * @param ids 供应商 ID 集合（非空）
     * @return 行数据：id / name 两列
     */
    List<Map<String, Object>> selectSupplierNames(@Param("ids") List<Long> ids);

    /**
     * 按部门维度统计：原值 / 截至期间累计折旧 / 净值（不含处置资产，入账日期 ≤ 期间末）。
     *
     * @param period    会计期间（yyyyMM）
     * @param periodEnd 期间末日
     * @return 行：deptId（NULL=未分配部门）/ originalSum / depSum / netSum
     */
    List<DeptStatsRawVO> selectStatsByDept(@Param("period") String period,
                                           @Param("periodEnd") String periodEnd);

    /**
     * 按类别维度统计（口径同 {@link #selectStatsByDept}）。
     *
     * @param period    会计期间（yyyyMM）
     * @param periodEnd 期间末日
     * @return 行：categoryId / originalSum / depSum / netSum
     */
    List<CategoryStatsRawVO> selectStatsByCategory(@Param("period") String period,
                                                   @Param("periodEnd") String periodEnd);
}

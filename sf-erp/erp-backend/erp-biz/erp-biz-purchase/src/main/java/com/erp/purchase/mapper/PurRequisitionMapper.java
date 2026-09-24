package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.purchase.dto.query.PurRequisitionQuery;
import com.erp.purchase.dto.vo.OptionVO;
import com.erp.purchase.dto.vo.PurRequisitionVO;
import com.erp.purchase.entity.PurRequisition;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 请购单 Mapper。
 * 分页关联查询（部门/请购人名称）见 resources/mapper/purchase/PurRequisitionMapper.xml。
 * <p>
 * 请购部门取自 system 模块 sys_dept 标准表：与 basedata 同口径，
 * 本模块只做只读投影（选项/存在性校验），不 import system Java 代码。
 */
public interface PurRequisitionMapper extends BaseMapper<PurRequisition> {

    /** 部门下拉选项（仅启用，按 sort 排序） */
    @Select("SELECT id, name FROM sys_dept WHERE deleted = 0 AND status = 1 ORDER BY sort, id LIMIT 200")
    List<OptionVO> listDeptOptions();

    /** 校验部门是否存在且启用：1 存在 / 0 不存在或停用 */
    @Select("SELECT COUNT(*) FROM sys_dept WHERE id = #{id} AND deleted = 0 AND status = 1")
    int countDeptById(@Param("id") Long id);

    /** 查询部门名称（详情展示用，含已停用历史数据；不存在返回 null） */
    @Select("SELECT name FROM sys_dept WHERE id = #{id} AND deleted = 0")
    String selectDeptName(@Param("id") Long id);

    /** 查询用户姓名（详情展示用；不存在返回 null） */
    @Select("SELECT nickname FROM sys_user WHERE id = #{id} AND deleted = 0")
    String selectUserName(@Param("id") Long id);

    /**
     * 分页查询请购单（JOIN sys_dept / sys_user 带出名称，物料筛选走 EXISTS 子查询）。
     *
     * @param page MP 分页参数（由分页插件注入 LIMIT）
     * @param q    查询条件
     * @return 请购单 VO 分页
     */
    IPage<PurRequisitionVO> selectReqPage(Page<PurRequisitionVO> page, @Param("q") PurRequisitionQuery q);

    /**
     * 提交：0草稿 -> 10已提交。
     * 条件 UPDATE 保证并发下不重复提交/跳跃流转，返回受影响行数（0=不可提交）。
     *
     * @param id 请购单 ID
     * @return 受影响行数
     */
    @Update("UPDATE pur_requisition SET status = 10, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int submit(@Param("id") Long id);

    /**
     * 关闭：10已提交/20已转采购 -> 30已关闭（终态，未转完的余量不再采购）。
     *
     * @param id 请购单 ID
     * @return 受影响行数（0=当前状态不可关闭）
     */
    @Update("UPDATE pur_requisition SET status = 30, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status IN (10, 20)")
    int close(@Param("id") Long id);

    /**
     * 标记已转采购：10已提交 -> 20已转采购（询比价定标事务回写；
     * 已是 20 保持不变，关闭/草稿不允许被驱动）。
     *
     * @param id 请购单 ID
     * @return 受影响行数
     */
    @Update("UPDATE pur_requisition SET status = 20, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 10")
    int markOrdered(@Param("id") Long id);
}

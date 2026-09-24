package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.purchase.dto.query.PurRfqQuery;
import com.erp.purchase.dto.vo.PurRfqVO;
import com.erp.purchase.entity.PurRfq;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 询比价单 Mapper。
 * 分页关联查询（来源请购单号/被邀请供应商过滤）见 resources/mapper/purchase/PurRfqMapper.xml。
 */
public interface PurRfqMapper extends BaseMapper<PurRfq> {

    /**
     * 分页查询询比价单（LEFT JOIN pur_requisition 带出来源单号，
     * 供应商筛选走 pur_rfq_supplier EXISTS 子查询）。
     *
     * @param page MP 分页参数
     * @param q    查询条件
     * @return 询比价 VO 分页
     */
    IPage<PurRfqVO> selectRfqPage(Page<PurRfqVO> page, @Param("q") PurRfqQuery q);

    /**
     * 定标：0询价中 -> 10已定标（条件 UPDATE 防并发重复定标，0 行即整体回滚）。
     *
     * @param id      询比价单 ID
     * @param awardBy 定标人（当前登录用户 ID）
     * @return 受影响行数
     */
    @Update("UPDATE pur_rfq SET status = 10, award_by = #{awardBy}, award_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int award(@Param("id") Long id, @Param("awardBy") Long awardBy);

    /**
     * 取消：0询价中 -> 30已取消（终态）。
     *
     * @param id 询比价单 ID
     * @return 受影响行数（0=已定标/已取消不可取消）
     */
    @Update("UPDATE pur_rfq SET status = 30, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int cancel(@Param("id") Long id);
}

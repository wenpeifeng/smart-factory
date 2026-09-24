package com.erp.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.qc.dto.query.QcRejectQuery;
import com.erp.qc.dto.vo.QcRejectVO;
import com.erp.qc.dto.vo.RejectSourceVO;
import com.erp.qc.entity.QcReject;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/**
 * 不合格品处置单 Mapper。
 */
public interface QcRejectMapper extends BaseMapper<QcReject> {

    /**
     * 分页查询处置单（JOIN 检验单/物料带出名称）。
     */
    IPage<QcRejectVO> selectRejectPage(Page<QcRejectVO> page, @Param("q") QcRejectQuery q);

    /**
     * 处置单详情（JOIN 物料带出名称）。
     *
     * @return 不存在或已删返回 null
     */
    QcRejectVO selectRejectById(@Param("id") Long id);

    /**
     * 可生成处置单的检验单选项：已检验、不合格数>0，扣除已处置仍有余额。
     * 上限 200 条（按检验单号倒序）。
     */
    List<RejectSourceVO> selectRejectSources();

    /**
     * 统计检验单已累计处置数量（新建传 excludeId=0；修改传自身 ID 排除）。
     */
    @Select("SELECT COALESCE(SUM(qty), 0) FROM qc_reject "
            + "WHERE inspect_id = #{inspectId} AND deleted = 0 AND id != #{excludeId}")
    BigDecimal sumDisposedQty(@Param("inspectId") Long inspectId, @Param("excludeId") Long excludeId);
}

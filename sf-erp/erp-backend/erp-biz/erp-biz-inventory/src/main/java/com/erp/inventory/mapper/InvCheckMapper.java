package com.erp.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.inventory.dto.request.CheckQuery;
import com.erp.inventory.dto.vo.CheckVO;
import com.erp.inventory.entity.InvCheck;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 库存盘点单 Mapper。
 */
@Mapper
public interface InvCheckMapper extends BaseMapper<InvCheck> {

    /**
     * 盘点单分页（含行数/已盘行数/差异行数统计）。
     */
    IPage<CheckVO> selectCheckPage(IPage<CheckVO> page, @Param("q") CheckQuery query);

    /**
     * KPI 库存准确率原始数据（V421）：窗口内已完成盘点单的行数与差异行数。
     *
     * @param beginTime 窗口起（含，按完成时间过滤）
     * @param endTime   窗口止（不含）
     * @return {check_rows=盘点行数, diff_rows=差异行数}；无盘点返回 null
     */
    java.util.Map<String, Object> selectKpiAccuracy(@Param("beginTime") java.time.LocalDateTime beginTime,
                                                    @Param("endTime") java.time.LocalDateTime endTime);
}

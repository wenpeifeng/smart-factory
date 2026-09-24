package com.erp.org.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.org.dto.request.TransferQuery;
import com.erp.org.dto.vo.TransferLineStatVO;
import com.erp.org.dto.vo.TransferVO;
import com.erp.org.entity.OrgTransfer;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织间调拨单 Mapper。SQL 见 resources/mapper/org/OrgTransferMapper.xml。
 */
public interface OrgTransferMapper extends BaseMapper<OrgTransfer> {

    /**
     * 调拨单分页（状态/线路/关键词/创建日期区间，带工厂仓库名称与行聚合）。
     */
    IPage<TransferVO> pageTransfer(IPage<TransferVO> page, @Param("q") TransferQuery query);

    /**
     * 调拨单头（带名称回显），不存在或已删返回 null。
     */
    TransferVO selectHead(@Param("id") Long id);

    /**
     * 调拨单明细行（带物料编码名称回显与价格快照）。
     */
    List<TransferVO.LineVO> selectLines(@Param("transferId") Long transferId);

    /**
     * 期间调拨金额按线路汇总（已审核单，按审核时间落在 [from, to] 闭区间）。
     */
    List<TransferLineStatVO> statisticsLine(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 物料存在性校验（base_material 只读点查，调拨/价目保存前校验用）。
     *
     * @return 1 存在 0 不存在
     */
    int countMaterialExists(@Param("materialId") Long materialId);
}

package com.mes.equipment.service;

import com.mes.equipment.dto.vo.OeeVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OEE 计算服务（工作中心维度）。
 */
public interface OeeService {

    /**
     * 计算工作中心在时间区间的 OEE（全部工作中心或指定）。
     *
     * @param workcenterId 工作中心 id（空=全部）
     * @param from         开始时间
     * @param to           结束时间
     * @return 各工作中心 OEE 列表
     */
    List<OeeVO> calc(Long workcenterId, LocalDateTime from, LocalDateTime to);
}

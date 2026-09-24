package com.mes.basedata.service;

import com.mes.basedata.dto.vo.BaseWorkcenterVO;

import java.util.List;

/**
 * 工作中心服务（排程/派工的资源单元）。
 */
public interface BaseWorkcenterService {

    /**
     * 查询全部启用工作中心（下拉）。
     *
     * @return 启用工作中心列表
     */
    List<BaseWorkcenterVO> listEnabled();
}

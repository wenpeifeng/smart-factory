package com.erp.equipment.dto.request;

import lombok.Data;

/**
 * 设备台账分页查询参数。
 */
@Data
public class DeviceQuery {

    /** 页码，从 1 开始 */
    private Long pageNum = 1L;

    /** 每页条数 */
    private Long pageSize = 10L;

    /** 设备状态：1运行 2停机 3维修中 4报废（null=全部） */
    private Integer status;

    /** 模糊搜索：编号/名称/型号 */
    private String keyword;
}

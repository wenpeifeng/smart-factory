package com.mes.kanban.service;

import com.mes.common.result.PageResult;
import com.mes.kanban.dto.form.AndonForm;
import com.mes.kanban.dto.vo.AndonVO;

/**
 * 安灯呼叫服务（OPEN→RESPOND→CLOSED 闭环）。
 */
public interface AndonService {

    /**
     * 发起呼叫（OPEN）。
     *
     * @param form 表单
     * @return 呼叫 id
     */
    Long call(AndonForm form);

    /**
     * 响应（OPEN→RESPOND）。
     *
     * @param id        呼叫 id
     * @param responder 响应人
     */
    void respond(Long id, String responder);

    /**
     * 关闭（RESPOND→CLOSED）。
     *
     * @param id          呼叫 id
     * @param closeRemark 关闭说明
     */
    void close(Long id, String closeRemark);

    /**
     * 分页查询（看板/管理页）。
     *
     * @param pageNum  页码
     * @param pageSize 页大小
     * @param status   状态过滤
     * @param type     类型过滤
     * @return 分页结果
     */
    PageResult<AndonVO> page(long pageNum, long pageSize, String status, String type);
}

package com.mes.material.service;

import com.mes.material.dto.form.LabelPrintForm;

/**
 * 标签打印服务（登记打印日志；P2 生成打印内容由前端模板渲染，服务端存日志）。
 */
public interface LabelPrintService {

    /**
     * 登记一次标签打印。
     *
     * @param form 表单
     * @return 日志 id
     */
    Long record(LabelPrintForm form);
}

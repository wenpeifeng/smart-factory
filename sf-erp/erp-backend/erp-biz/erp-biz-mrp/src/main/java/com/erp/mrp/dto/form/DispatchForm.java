package com.erp.mrp.dto.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 派工表单 —— 将待派工的工序任务派给员工姓名或设备。
 */
@Data
public class DispatchForm implements Serializable {

    /** 派工对象（员工姓名或设备名） */
    @NotBlank(message = "派工对象不能为空")
    @Size(max = 50, message = "派工对象最长50字符")
    private String assignee;

    /** 计划开工时间（不传则保留顺排生成的计划开始时间） */
    private LocalDateTime planStartTime;
}

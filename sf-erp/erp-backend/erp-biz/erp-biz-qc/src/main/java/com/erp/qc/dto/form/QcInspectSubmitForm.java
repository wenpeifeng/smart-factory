package com.erp.qc.dto.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 检验单提交表单（草稿 → 已检验）：录入实检值 + 单项判定 + 结论。
 * <p>
 * 口径校验（服务层执行）：
 * 合格数 + 不合格数 = 抽检数；结论=1(合格) ⇔ 不合格数=0；结论=2/3 时不合格数必须>0。
 */
@Data
public class QcInspectSubmitForm {

    /** 检验人姓名 */
    @NotBlank(message = "检验人不能为空")
    @Size(max = 50, message = "检验人最长 50 字")
    private String inspectorName;

    /** 检验日期 */
    @NotNull(message = "检验日期不能为空")
    private LocalDate inspectDate;

    /** 检验结论：1合格 2让步接收 3退货 */
    @NotNull(message = "检验结论不能为空")
    @Min(value = 1, message = "检验结论取值不合法")
    @Max(value = 3, message = "检验结论取值不合法")
    private Integer conclusion;

    /** 合格数量（≥0） */
    @NotNull(message = "合格数量不能为空")
    @DecimalMin(value = "0", message = "合格数量不能为负数")
    private BigDecimal qualifiedQty;

    /** 不合格数量（≥0） */
    @NotNull(message = "不合格数量不能为空")
    @DecimalMin(value = "0", message = "不合格数量不能为负数")
    private BigDecimal unqualifiedQty;

    /** 备注（覆盖表头备注） */
    @Size(max = 500, message = "备注最长 500 字")
    private String remark;

    /** 检验项目实检值（按明细行 id 整体提交，至少一行；未在列表中的行不更新） */
    @NotEmpty(message = "检验项目实检值不能为空")
    @Valid
    private List<SubmitItemForm> items;

    /**
     * 检验项目实检值行。
     */
    @Data
    public static class SubmitItemForm {

        /** 检验单明细 ID */
        @NotNull(message = "明细行 ID 不能为空")
        private Long id;

        /** 实检值（文本/数值均可） */
        @Size(max = 200, message = "实检值最长 200 字")
        private String actualValue;

        /** 单项判定：1合格 0不合格（空=未判定） */
        @Min(value = 0, message = "单项判定取值不合法")
        @Max(value = 1, message = "单项判定取值不合法")
        private Integer lineResult;
    }
}

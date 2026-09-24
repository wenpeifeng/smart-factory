package com.erp.inventory.dto.form;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * WMS 任务完成表单（V421）。
 * <p>
 * actualQty 可小于任务数量（部分上架/部分拣选，剩余量留在任务上继续执行）；
 * locationId 可覆盖任务推荐库位（现场临时改位）。
 */
@Data
public class WmsTaskCompleteForm {

    /** 实际完成数量（&gt;0；空=按任务数量全部完成） */
    @DecimalMin(value = "0", inclusive = false, message = "完成数量必须大于0")
    private BigDecimal actualQty;

    /** 实际库位（空=用任务推荐/指定库位；PUTAWAY/PICK/MOVE 现场改位用） */
    private Long locationId;
}

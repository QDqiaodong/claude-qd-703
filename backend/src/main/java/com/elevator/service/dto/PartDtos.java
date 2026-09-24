package com.elevator.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块五（配件更换）入参。 */
public final class PartDtos {

    private PartDtos() {
    }

    @Data
    public static class PartSaveReq {

        @NotNull(message = "所属电梯不能为空")
        private Long elevatorId;

        @NotBlank(message = "配件名称不能为空")
        private String partName;

        private String spec;

        @NotNull(message = "数量不能为空")
        private Integer quantity;

        @NotNull(message = "单价不能为空")
        private BigDecimal unitPrice;

        /** 可空：默认今天 */
        private LocalDate replaceDate;

        private String operator;

        /** ORDER 维保工单 / FAULT 故障报修 */
        private String refType;

        private Long refId;

        private String supplier;
    }
}

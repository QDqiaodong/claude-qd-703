package com.elevator.service.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块三（维保计划与工单）入参。 */
public final class MaintenanceDtos {

    private MaintenanceDtos() {
    }

    @Data
    public static class PlanSaveReq {

        @NotBlank(message = "计划编号不能为空")
        private String planNo;

        @NotNull(message = "所属电梯不能为空")
        private Long elevatorId;

        @NotBlank(message = "维保周期不能为空")
        private String cycle;

        @NotNull(message = "计划执行日不能为空")
        private LocalDate planDate;

        private String team;

        private String worker;

        private String remark;
    }

    @Data
    public static class OrderSaveReq {

        @NotBlank(message = "工单号不能为空")
        private String orderNo;

        /** 可空：临时派工单没有来源计划 */
        private Long planId;

        @NotNull(message = "所属电梯不能为空")
        private Long elevatorId;

        @NotBlank(message = "维保周期不能为空")
        private String cycle;

        private LocalDate planDate;

        private String team;

        private String worker;

        private String remark;
    }

    /** 执行：填执行结果、实际完成日，状态置 DONE */
    @Data
    public static class ExecuteReq {

        private String worker;

        @NotBlank(message = "执行结果不能为空")
        private String executeResult;

        private LocalDate actualDate;
    }

    /** 延期：顺延天数 */
    @Data
    public static class DelayReq {

        /** 可空：默认顺延 15 天 */
        private Integer days;
    }
}

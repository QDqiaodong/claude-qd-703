package com.elevator.service.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块四（故障报修）入参。 */
public final class FaultDtos {

    private FaultDtos() {
    }

    @Data
    public static class FaultSaveReq {

        @NotBlank(message = "报修单号不能为空")
        private String faultNo;

        @NotNull(message = "所属电梯不能为空")
        private Long elevatorId;

        @NotBlank(message = "故障现象不能为空")
        private String faultType;

        /** 可空：默认当前时间 */
        private LocalDateTime reportTime;

        /** 响应时长（分钟） */
        private Integer responseMinutes;

        /** 到达时长（分钟） */
        private Integer arriveMinutes;

        /** 是否困人：可空时按故障现象判定（困人/停梯默认算困人） */
        private Integer trapped;

        private String reporter;

        private String handleResult;
    }

    @Data
    public static class FixReq {

        @NotBlank(message = "处理结果不能为空")
        private String handleResult;
    }

    /** 受理应急报修：指定当前负责人（抢修负责人） */
    @Data
    public static class AcceptReq {

        /** 当前负责人，可空时默认「值班抢修组」 */
        private String assignee;

        /** 抢修班组，可空 */
        private String team;

        /** 维保员，可空 */
        private String worker;

        /** 操作员（并发受理失败时用于提示），可空 */
        private String operator;
    }

    /** 推进/处置：处理中 → 待验收 */
    @Data
    public static class ProcessReq {

        /** 处理结果（必填） */
        @NotBlank(message = "处理结果不能为空")
        private String handleResult;

        /** 实际到场时间（必填） */
        @NotNull(message = "实际到场时间不能为空")
        private LocalDateTime arriveTime;

        /** 到达时长（分钟），可空时按到场时间与受理时间推算 */
        private Integer arriveMinutes;

        private String operator;
    }

    /** 验收闭环：待验收 → 已修复 */
    @Data
    public static class CloseReq {

        /** 确认电梯已恢复运行，必须为 true */
        @NotNull(message = "必须确认电梯恢复运行后才能闭环")
        private Boolean resumed;

        /** 验收备注，可空 */
        private String handleResult;

        private String operator;
    }

    /** 误报撤销：留下撤销原因，进入独立终态 CANCELLED */
    @Data
    public static class CancelReq {

        @NotBlank(message = "误报撤销必须填写撤销原因")
        private String cancelReason;

        private String operator;
    }
}

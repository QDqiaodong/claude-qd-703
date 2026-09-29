package com.elevator.service.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块四（故障报修 + 应急联动）入参。 */
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

        /** 响应时长（分钟）——登记时可不填，受理时自动按受理时间计算 */
        private Integer responseMinutes;

        /** 到达时长（分钟）——登记时可不填，到场登记时按实际到场时间计算 */
        private Integer arriveMinutes;

        /** 是否困人：可空时按故障现象判定（困人/停梯默认算困人） */
        private Integer trapped;

        private String reporter;

        private String handleResult;
    }

    /** 受理应急报修：填写当前负责人（抢修责任人） */
    @Data
    public static class AcceptReq {

        /** 当前负责人/抢修责任人，可空时用默认值班人 */
        private String owner;
    }

    /** 到场登记：实际到场时间（可空，默认现在）+ 到场说明 */
    @Data
    public static class ArriveReq {

        /** 实际到场时间，可空默认当前时间 */
        private LocalDateTime arriveTime;

        private String note;
    }

    /** 送验收：处理结果必填 */
    @Data
    public static class VerifyReq {

        @NotBlank(message = "处理结果不能为空")
        private String handleResult;
    }

    /** 闭环（验收通过）：必须填处理结果并确认电梯恢复运行 */
    @Data
    public static class FixReq {

        @NotBlank(message = "处理结果不能为空")
        private String handleResult;

        /** 必须显式确认电梯已恢复运行，否则不能闭环 */
        @NotNull(message = "必须确认电梯已恢复运行才能闭环")
        private Boolean elevatorRecovered;
    }

    /** 误报撤销：必须留撤销原因，走单独的「已撤销」终态 */
    @Data
    public static class CancelReq {

        @NotBlank(message = "误报撤销必须填写撤销原因")
        private String cancelReason;
    }
}

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
}

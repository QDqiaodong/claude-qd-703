package com.elevator.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块二（电梯台账）入参。 */
public final class ElevatorDtos {

    private ElevatorDtos() {
    }

    @Data
    public static class ElevatorSaveReq {

        @NotBlank(message = "电梯编号不能为空")
        private String elevatorNo;

        @NotNull(message = "所属单元不能为空")
        private Long unitId;

        @NotBlank(message = "品牌不能为空")
        private String brand;

        @NotBlank(message = "型号不能为空")
        private String model;

        @NotNull(message = "额定载重不能为空")
        private Integer ratedLoad;

        @NotNull(message = "额定速度不能为空")
        private BigDecimal ratedSpeed;

        @NotNull(message = "层站数不能为空")
        private Integer floorStation;

        private LocalDate installDate;

        private LocalDate lastInspectDate;

        private LocalDate nextInspectDate;

        private String maintVendor;

        /** 可空：默认 RUNNING */
        private String status;

        /** 可空：默认停靠 1 层 */
        private Integer currentFloor;
    }

    @Data
    public static class StatusReq {

        @NotBlank(message = "目标状态不能为空")
        private String status;
    }

    /** 井道剖面图：让轿厢停靠到指定楼层 */
    @Data
    public static class DockReq {

        @NotNull(message = "停靠楼层不能为空")
        private Integer floor;
    }
}

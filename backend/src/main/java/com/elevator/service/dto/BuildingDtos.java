package com.elevator.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 模块一（楼宇与单元）入参。 */
public final class BuildingDtos {

    private BuildingDtos() {
    }

    @Data
    public static class BuildingSaveReq {

        @NotBlank(message = "楼宇编号不能为空")
        private String buildingNo;

        @NotBlank(message = "楼宇名称不能为空")
        private String name;

        @NotBlank(message = "地址不能为空")
        private String address;

        @NotNull(message = "层数不能为空")
        private Integer floorCount;

        @NotNull(message = "建成年代不能为空")
        private Integer buildYear;

        private String propertyContact;

        private String propertyPhone;

        /** 可空：默认 ACTIVE */
        private String status;
    }

    @Data
    public static class UnitSaveReq {

        @NotBlank(message = "单元编号不能为空")
        private String unitNo;

        @NotNull(message = "所属楼宇不能为空")
        private Long buildingId;

        @NotNull(message = "单元层数不能为空")
        private Integer floorCount;

        @NotNull(message = "电梯数不能为空")
        private Integer elevatorCount;

        private String note;

        /** 可空：默认 ACTIVE */
        private String status;
    }

    @Data
    public static class StatusReq {

        @NotBlank(message = "目标状态不能为空")
        private String status;
    }
}

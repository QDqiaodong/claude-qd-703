package com.elevator.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 各模块出参视图（实体转前端用的扁平 DTO，由 Service 手工组装）。 */
public final class Views {

    private Views() {
    }

    // ---------------- 模块一：楼宇与单元 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuildingView {
        private Long id;
        private String buildingNo;
        private String name;
        private String address;
        private Integer floorCount;
        private Integer buildYear;
        private String propertyContact;
        private String propertyPhone;
        private String status;
        private String statusName;
        /** 单元数（实时统计） */
        private Long unitCount;
        /** 电梯数（实时统计） */
        private Long elevatorCount;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnitView {
        private Long id;
        private String unitNo;
        private Long buildingId;
        private String buildingName;
        private Integer floorCount;
        private Integer elevatorCount;
        /** 台账里实际挂了多少台电梯 */
        private Long realElevatorCount;
        private String note;
        private String status;
        private String statusName;
        private LocalDateTime updatedAt;
    }

    // ---------------- 模块二：电梯台账 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ElevatorView {
        private Long id;
        private String elevatorNo;
        private Long unitId;
        private String unitNo;
        private Long buildingId;
        private String buildingName;
        private String brand;
        private String model;
        private Integer ratedLoad;
        private BigDecimal ratedSpeed;
        private Integer floorStation;
        private LocalDate installDate;
        private LocalDate lastInspectDate;
        private LocalDate nextInspectDate;
        private String maintVendor;
        private String status;
        private String statusName;
        private Integer currentFloor;
        /** 距下次年检天数，负数表示已超期 */
        private Long inspectDueDays;
        private LocalDateTime updatedAt;
    }

    /** 井道剖面图：当前电梯 + 停靠层 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShaftView {
        private ElevatorView elevator;
        /** 层站数 */
        private Integer floorCount;
        /** 当前停靠层 */
        private Integer currentFloor;
        /** 轿厢状态色：RUNNING/OUT_OF_SERVICE/REPAIRING/INSPECTING */
        private String status;
        private String statusName;
    }

    // ---------------- 模块三：维保计划与工单 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanView {
        private Long id;
        private String planNo;
        private Long elevatorId;
        private String elevatorNo;
        private Long unitId;
        private String unitNo;
        private Long buildingId;
        private String buildingName;
        private String cycle;
        private String cycleName;
        private LocalDate planDate;
        /** 甘特条落点年月，如 2026-03 */
        private String planMonth;
        private String team;
        private String worker;
        private String status;
        private String statusName;
        private String remark;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderView {
        private Long id;
        private String orderNo;
        private Long planId;
        private String planNo;
        private Long elevatorId;
        private String elevatorNo;
        private Long unitId;
        private String unitNo;
        private Long buildingId;
        private String buildingName;
        private String cycle;
        private String cycleName;
        private LocalDate planDate;
        private String team;
        private String worker;
        private String executeResult;
        private LocalDate actualDate;
        private String status;
        private String statusName;
        private String remark;
        private LocalDateTime updatedAt;
    }

    // ---------------- 模块四：故障报修 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FaultView {
        private Long id;
        private String faultNo;
        private Long elevatorId;
        private String elevatorNo;
        private Long unitId;
        private String unitNo;
        private Long buildingId;
        private String buildingName;
        private String faultType;
        private String faultTypeName;
        private LocalDateTime reportTime;
        private Integer responseMinutes;
        private Integer arriveMinutes;
        private LocalDateTime fixTime;
        private Integer trapped;
        private String handleResult;
        private String reporter;
        private String status;
        private String statusName;
        /** 到达时长超过 30 分钟红线 */
        private Boolean arriveOverdue;
        private LocalDateTime updatedAt;
    }

    // ---------------- 模块五：配件更换 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartView {
        private Long id;
        private String partName;
        private String spec;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal amount;
        private LocalDate replaceDate;
        private String operator;
        private String refType;
        /** ORDER 维保工单 / FAULT 故障报修 */
        private String refTypeName;
        private Long refId;
        private String refNo;
        private String supplier;
        private Long elevatorId;
        private String elevatorNo;
        private Long buildingId;
        private String buildingName;
        private String status;
        private String statusName;
        private LocalDateTime updatedAt;
    }

    /** 配件金额汇总（页面底部合计） */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartSummaryView {
        private Long recordCount;
        private Long totalQuantity;
        private BigDecimal totalAmount;
        /** 按供应商分组的金额 */
        private List<SupplierAmountView> bySupplier;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupplierAmountView {
        private String supplier;
        private Long count;
        private BigDecimal amount;
    }

    // ---------------- 顶栏总览 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverviewView {
        private Long buildingCount;
        private Long unitCount;
        private Long elevatorCount;
        private Long runningCount;
        private Long outOfServiceCount;
        private Long repairingCount;
        private Long inspectingCount;
        private Long inspectDueCount;
        private Long planPendingCount;
        private Long planDelayedCount;
        private Long orderPendingCount;
        private Long faultOpenCount;
        private Long faultTrappedCount;
        private BigDecimal partAmount;
    }

    // ---------------- 级联面板 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TreeBuildingView {
        private Long id;
        private String buildingNo;
        private String name;
        private String address;
        private Integer floorCount;
        private Integer buildYear;
        private String status;
        private String statusName;
        private List<TreeUnitView> units;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TreeUnitView {
        private Long id;
        private String unitNo;
        private Integer floorCount;
        private Integer elevatorCount;
        private String status;
        private String statusName;
        private List<TreeElevatorView> elevators;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TreeElevatorView {
        private Long id;
        private String elevatorNo;
        private String brand;
        private String model;
        private Integer floorStation;
        private String status;
        private String statusName;
        private Integer currentFloor;
    }

    // ---------------- 过滤器开关对比 ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCompareView {
        /** 查询方法：ElevatorRepository#findAllByOrderByElevatorNoAsc */
        private String queryMethod;
        private Long buildingId;
        private String buildingName;
        /** 本次启用的过滤器 */
        private String filterName;
        /** 过滤器条件 */
        private String filterCondition;
        /** 同方法·开过滤器 */
        private Long filteredCount;
        /** 同方法·关过滤器 */
        private Long unfilteredCount;
        private List<String> filteredCodes;
        private List<String> unfilteredCodes;
    }
}

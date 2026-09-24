package com.elevator.service.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 电梯维保业务词典（状态枚举、维保周期、故障现象）与少量业务规则。
 * 规则集中在此，便于出题时被要求调整。
 */
public final class ElevatorDict {

    private ElevatorDict() {
    }

    // ---------------- 电梯状态 ----------------
    private static final Map<String, String> ELEVATOR_STATUS = Map.of(
            "RUNNING", "运行",
            "OUT_OF_SERVICE", "停用",
            "REPAIRING", "维修",
            "INSPECTING", "年检中");

    /** 停用电梯不参与维保计划排程 */
    public static final String ST_RUNNING = "RUNNING";
    public static final String ST_OUT = "OUT_OF_SERVICE";
    public static final String ST_REPAIR = "REPAIRING";
    public static final String ST_INSPECT = "INSPECTING";

    // ---------------- 维保周期 ----------------
    private static final Map<String, String> CYCLE = Map.of(
            "HALF_MONTH", "半月",
            "MONTH", "月度",
            "QUARTER", "季度",
            "HALF_YEAR", "半年",
            "YEAR", "年度");

    /** 各周期的法定间隔天数，用于推算下一次计划执行日 */
    private static final Map<String, Integer> CYCLE_DAYS = Map.of(
            "HALF_MONTH", 15,
            "MONTH", 30,
            "QUARTER", 90,
            "HALF_YEAR", 180,
            "YEAR", 365);

    // ---------------- 计划 / 工单状态 ----------------
    private static final Map<String, String> PLAN_STATUS = Map.of(
            "PENDING", "待执行",
            "DONE", "已执行",
            "DELAYED", "已延期",
            "VOID", "作废");

    public static final String P_PENDING = "PENDING";
    public static final String P_DONE = "DONE";
    public static final String P_DELAYED = "DELAYED";
    public static final String P_VOID = "VOID";

    // ---------------- 故障现象 ----------------
    private static final Map<String, String> FAULT_TYPE = Map.of(
            "TRAPPED", "困人",
            "NOISE", "异响",
            "LEVELING", "不平层",
            "STOPPED", "停梯",
            "DOOR", "门系统",
            "OTHER", "其他");

    /** 故障现象是否默认算困人 */
    private static final Map<String, Boolean> FAULT_TRAPPED = Map.of(
            "TRAPPED", Boolean.TRUE,
            "NOISE", Boolean.FALSE,
            "LEVELING", Boolean.FALSE,
            "STOPPED", Boolean.TRUE,
            "DOOR", Boolean.FALSE,
            "OTHER", Boolean.FALSE);

    // ---------------- 报修状态 ----------------
    private static final Map<String, String> FAULT_STATUS = Map.of(
            "PENDING", "待处理",
            "PROCESSING", "处理中",
            "FIXED", "已修复");

    public static final String F_PENDING = "PENDING";
    public static final String F_PROCESSING = "PROCESSING";
    public static final String F_FIXED = "FIXED";

    /** 困人报修的到达时长红线（分钟），超时在页面标红 */
    public static final int ARRIVE_SLA_MINUTES = 30;

    // ---------------- 楼宇 / 单元状态 ----------------
    private static final Map<String, String> ARCHIVE_STATUS = Map.of(
            "ACTIVE", "在管",
            "ARCHIVED", "已撤场");

    public static final String ACTIVE = "ACTIVE";
    public static final String ARCHIVED = "ARCHIVED";

    // ---------------- 配件记录状态 ----------------
    private static final Map<String, String> PART_STATUS = Map.of(
            "NORMAL", "已登记",
            "VOID", "作废");

    // ---------------- 取值方法 ----------------

    public static boolean isValidElevatorStatus(String status) {
        return status != null && ELEVATOR_STATUS.containsKey(status);
    }

    public static String elevatorStatusName(String status) {
        return status == null ? "" : ELEVATOR_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidCycle(String cycle) {
        return cycle != null && CYCLE.containsKey(cycle);
    }

    public static String cycleName(String cycle) {
        return cycle == null ? "" : CYCLE.getOrDefault(cycle, cycle);
    }

    public static int cycleDays(String cycle) {
        return CYCLE_DAYS.getOrDefault(cycle, 30);
    }

    public static boolean isValidPlanStatus(String status) {
        return status != null && PLAN_STATUS.containsKey(status);
    }

    public static String planStatusName(String status) {
        return status == null ? "" : PLAN_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidFaultType(String type) {
        return type != null && FAULT_TYPE.containsKey(type);
    }

    public static String faultTypeName(String type) {
        return type == null ? "" : FAULT_TYPE.getOrDefault(type, type);
    }

    public static boolean faultTypeTrapped(String type) {
        return Boolean.TRUE.equals(FAULT_TRAPPED.get(type));
    }

    public static boolean isValidFaultStatus(String status) {
        return status != null && FAULT_STATUS.containsKey(status);
    }

    public static String faultStatusName(String status) {
        return status == null ? "" : FAULT_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidArchiveStatus(String status) {
        return status != null && ARCHIVE_STATUS.containsKey(status);
    }

    public static String archiveStatusName(String status) {
        return status == null ? "" : ARCHIVE_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidPartStatus(String status) {
        return status != null && PART_STATUS.containsKey(status);
    }

    public static String partStatusName(String status) {
        return status == null ? "" : PART_STATUS.getOrDefault(status, status);
    }

    /** 金额 = 数量 × 单价，保留两位 */
    public static BigDecimal amount(Integer quantity, BigDecimal unitPrice) {
        int qty = quantity == null ? 0 : quantity;
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        return price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 下一次计划执行日 = 本次执行日 + 周期天数 */
    public static int nextPlanOffsetDays(String cycle) {
        return cycleDays(cycle);
    }
}

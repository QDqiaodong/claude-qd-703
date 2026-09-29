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
            "INSPECTING", "年检中",
            "EMERGENCY", "应急维修");

    /** 停用电梯不参与维保计划排程 */
    public static final String ST_RUNNING = "RUNNING";
    public static final String ST_OUT = "OUT_OF_SERVICE";
    public static final String ST_REPAIR = "REPAIRING";
    public static final String ST_INSPECT = "INSPECTING";
    /** 应急维修：被未闭环应急报修占用，禁止新建普通维保计划/工单 */
    public static final String ST_EMERGENCY = "EMERGENCY";

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
            "VOID", "作废",
            "SUSPENDED", "应急暂停");

    public static final String P_PENDING = "PENDING";
    public static final String P_DONE = "DONE";
    public static final String P_DELAYED = "DELAYED";
    public static final String P_VOID = "VOID";
    /** 被应急占用暂停：计划保留不删除，闭环/撤销后恢复为暂停前状态 */
    public static final String P_SUSPENDED = "SUSPENDED";

    /** 工单类型：普通维保工单 / 应急抢修工单 */
    public static final String ORDER_NORMAL = "NORMAL";
    public static final String ORDER_RESCUE = "RESCUE";

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
            "WAIT_ACCEPT", "待验收",
            "FIXED", "已修复",
            "CANCELLED", "已撤销");

    public static final String F_PENDING = "PENDING";
    public static final String F_PROCESSING = "PROCESSING";
    public static final String F_WAIT_ACCEPT = "WAIT_ACCEPT";
    public static final String F_FIXED = "FIXED";
    /** 误报撤销：单独终态，不能伪装成已修复 */
    public static final String F_CANCELLED = "CANCELLED";

    /** 风险等级 */
    private static final Map<String, String> RISK_LEVEL = Map.of(
            "HIGH", "高风险",
            "NORMAL", "普通");

    public static final String RISK_HIGH = "HIGH";
    public static final String RISK_NORMAL = "NORMAL";

    /** 困人 / 停梯 自动进入应急态 */
    public static boolean isEmergencyFaultType(String type) {
        return "TRAPPED".equals(type) || "STOPPED".equals(type);
    }

    public static String riskName(String risk) {
        return risk == null ? "" : RISK_LEVEL.getOrDefault(risk, risk);
    }

    /** 应急处置阶段中文名（随 status 给页面展示，含受理后细化阶段） */
    public static String emergencyStageName(String status) {
        return switch (status) {
            case F_PENDING -> "待处理";
            case F_PROCESSING -> "处理中";
            case F_WAIT_ACCEPT -> "待验收";
            case F_FIXED -> "已修复";
            case F_CANCELLED -> "已撤销";
            default -> status;
        };
    }

    /** 报修单是否已终态（已修复 / 已撤销都算闭环） */
    public static boolean isFaultClosed(String status) {
        return F_FIXED.equals(status) || F_CANCELLED.equals(status);
    }

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

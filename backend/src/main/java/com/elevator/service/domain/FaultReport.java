package com.elevator.service.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

/**
 * 模块四：故障报修（含应急联动）。
 *
 * <p>{@code statusFilter} 只留未闭环报修（终态 = {@code FIXED 已修复} 与 {@code CANCELLED 已撤销}）；
 * 选中楼宇后切到 {@code buildingFilter}，历史已闭环单（含误报撤销单）一起回来。
 *
 * <p>{@code emergencyFlag}=1 的困人/停梯单进入应急态：受理时自动生成/关联抢修工单，
 * 电梯进入 EMERGENCY 占用。{@code version} 乐观锁保证两个操作员同时受理/闭环只有一个成功。
 */
@Data
@Entity
@Table(name = "fault_report")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status NOT IN ('FIXED','CANCELLED')")
public class FaultReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 报修单号，如 FR-2026-0001 */
    @Column(name = "fault_no", nullable = false, length = 24)
    private String faultNo;

    @Column(name = "elevator_id", nullable = false)
    private Long elevatorId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 故障现象：TRAPPED 困人 / NOISE 异响 / LEVELING 不平层 / STOPPED 停梯 / DOOR 门系统 / OTHER 其他 */
    @Column(name = "fault_type", nullable = false, length = 16)
    private String faultType;

    /** HIGH 高风险（困人/停梯应急）/ NORMAL 普通 */
    @Column(name = "risk_level", nullable = false, length = 8)
    private String riskLevel;

    /** 是否应急报修：1 是 / 0 否（困人、停梯自动进入应急态） */
    @Column(name = "emergency_flag", nullable = false)
    private Integer emergencyFlag;

    /** 报修时间 */
    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    /** 受理时间 */
    @Column(name = "accept_time")
    private LocalDateTime acceptTime;

    /** 响应时长（分钟）：报修 → 受理 */
    @Column(name = "response_minutes")
    private Integer responseMinutes;

    /** 到达时长（分钟）：报修 → 实际到场 */
    @Column(name = "arrive_minutes")
    private Integer arriveMinutes;

    /** 实际到场时间（受理后维保员登记） */
    @Column(name = "actual_arrive_time")
    private LocalDateTime actualArriveTime;

    /** 修复/闭环时间 */
    @Column(name = "fix_time")
    private LocalDateTime fixTime;

    /** 是否困人：1 是 / 0 否 */
    @Column(name = "trapped")
    private Integer trapped;

    /** 处理结果 */
    @Column(name = "handle_result", length = 255)
    private String handleResult;

    /** 报修人 */
    @Column(name = "reporter", length = 32)
    private String reporter;

    /** 当前负责人（受理人/抢修责任人） */
    @Column(name = "owner", length = 32)
    private String owner;

    /** 关联抢修工单 id */
    @Column(name = "rescue_order_id")
    private Long rescueOrderId;

    /** 关联抢修工单号（冗余，三处单号一致） */
    @Column(name = "rescue_order_no", length = 24)
    private String rescueOrderNo;

    /** 误报撤销原因（仅 CANCELLED 时填写） */
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** 应急处置阶段（随状态的中文展示，含受理后细化阶段） */
    @Column(name = "stage", length = 16)
    private String stage;

    /** PENDING 待处理 / PROCESSING 处理中 / WAIT_ACCEPT 待验收 / FIXED 已修复 / CANCELLED 已撤销 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 乐观锁版本：并发受理/闭环只允许一个事务提交 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

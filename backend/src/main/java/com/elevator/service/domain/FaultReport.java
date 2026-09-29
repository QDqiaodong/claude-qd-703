package com.elevator.service.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 模块四：故障报修。
 *
 * <p>{@code statusFilter} 只留未闭环报修（{@code status NOT IN ('FIXED','CANCELLED')}）；
 * 选中楼宇后切到 {@code buildingFilter}，已修复/已撤销的历史单会一起回来。
 *
 * <p>应急联动：故障现象为困人/停梯（{@code emergency=1}）的报修受理后自动生成一张应急抢修工单，
 * 电梯进入 EMERGENCY_REPAIR，状态只能按 待处理→处理中→待验收→已修复 推进；误报走独立终态 CANCELLED。
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

    /** 是否应急联动故障（困人/停梯）：1 是 / 0 否；普通故障仍走原报修流程 */
    @Column(name = "emergency", nullable = false)
    private Integer emergency;

    /** 风险等级：TRAPPED 高风险 / STOPPED 高风险 / 其余普通 */
    @Column(name = "risk_level", length = 16)
    private String riskLevel;

    /** 报修时间 */
    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    /** 受理时间：应急报修被受理、抢修工单生成的时刻 */
    @Column(name = "accept_time")
    private LocalDateTime acceptTime;

    /** 当前负责人（受理人/抢修负责人） */
    @Column(name = "assignee", length = 32)
    private String assignee;

    /** 响应时长（分钟）：接单到派工 */
    @Column(name = "response_minutes")
    private Integer responseMinutes;

    /** 到达时长（分钟）：接单到维保员到场 */
    @Column(name = "arrive_minutes")
    private Integer arriveMinutes;

    /** 实际到场时间（闭环必填） */
    @Column(name = "arrive_time")
    private LocalDateTime arriveTime;

    /** 修复时间 */
    @Column(name = "fix_time")
    private LocalDateTime fixTime;

    /** 是否困人：1 是 / 0 否 */
    @Column(name = "trapped")
    private Integer trapped;

    /** 处理结果 */
    @Column(name = "handle_result", length = 255)
    private String handleResult;

    /** 误报撤销原因；仅 CANCELLED 终态有值 */
    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** 报修人 */
    @Column(name = "reporter", length = 32)
    private String reporter;

    /** 关联抢修工单 id（受理后生成或关联；故障/工单/电梯三处单号一致） */
    @Column(name = "order_id")
    private Long orderId;

    /** 关联抢修工单号，冗余便于直接展示 */
    @Column(name = "order_no", length = 24)
    private String orderNo;

    /** 闭环验收人（确认电梯恢复运行的操作员） */
    @Column(name = "confirm_user", length = 32)
    private String confirmUser;

    /** PENDING 待处理 / PROCESSING 处理中 / WAIT_ACCEPT 待验收 / FIXED 已修复 / CANCELLED 已撤销 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 处置阶段（中文阶段名冗余，列表直接展示）：待受理/已受理/抢修中/待验收/已修复/已撤销 */
    @Column(name = "stage", length = 16)
    private String stage;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

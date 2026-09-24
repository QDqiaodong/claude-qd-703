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
 * <p>{@code statusFilter} 只留未闭环报修（{@code status <> 'FIXED'}）；
 * 选中楼宇后切到 {@code buildingFilter}，已修复的历史单会一起回来。
 */
@Data
@Entity
@Table(name = "fault_report")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status <> 'FIXED'")
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

    /** 报修时间 */
    @Column(name = "report_time", nullable = false)
    private LocalDateTime reportTime;

    /** 响应时长（分钟）：接单到派工 */
    @Column(name = "response_minutes")
    private Integer responseMinutes;

    /** 到达时长（分钟）：接单到维保员到场 */
    @Column(name = "arrive_minutes")
    private Integer arriveMinutes;

    /** 修复时间 */
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

    /** PENDING 待处理 / PROCESSING 处理中 / FIXED 已修复 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

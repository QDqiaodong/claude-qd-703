package com.elevator.service.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Filter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/** 模块三：维保计划（按周期排程，甘特条的数据源）。 */
@Data
@Entity
@Table(name = "maintenance_plan")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status <> 'VOID'")
public class MaintenancePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 计划编号，如 MP-2026-0001 */
    @Column(name = "plan_no", nullable = false, length = 24)
    private String planNo;

    @Column(name = "elevator_id", nullable = false)
    private Long elevatorId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 维保周期：HALF_MONTH 半月 / MONTH 月 / QUARTER 季 / HALF_YEAR 半年 / YEAR 年 */
    @Column(name = "cycle", nullable = false, length = 16)
    private String cycle;

    /** 计划执行日（甘特条落点） */
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** 维保班组 */
    @Column(name = "team", length = 32)
    private String team;

    /** 维保员 */
    @Column(name = "worker", length = 32)
    private String worker;

    /** PENDING 待执行 / DONE 已执行 / DELAYED 已延期 / VOID 作废 / SUSPENDED 应急暂停 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 暂停该计划的应急报修单 id（NULL 未暂停） */
    @Column(name = "suspended_by_fault_id")
    private Long suspendedByFaultId;

    /** 暂停该计划的应急报修单号（冗余） */
    @Column(name = "suspended_by_fault_no", length = 24)
    private String suspendedByFaultNo;

    /** 被占用原因，如「电梯 EL-... 应急抢修中（FR-...）」 */
    @Column(name = "suspend_reason", length = 128)
    private String suspendReason;

    @Column(name = "suspended_at")
    private java.time.LocalDateTime suspendedAt;

    /** 暂停前状态 PENDING/DELAYED，闭环/撤销后恢复 */
    @Column(name = "status_before_suspend", length = 16)
    private String statusBeforeSuspend;

    @Column(name = "remark", length = 128)
    private String remark;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

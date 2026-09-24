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

/** 模块三：维保工单（计划落到执行，或临时派工直接开单）。 */
@Data
@Entity
@Table(name = "maintenance_order")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status <> 'VOID'")
public class MaintenanceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工单号，如 MO-2026-0001 */
    @Column(name = "order_no", nullable = false, length = 24)
    private String orderNo;

    /** 来源计划；临时派工可为空 */
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "elevator_id", nullable = false)
    private Long elevatorId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 维保周期：HALF_MONTH / MONTH / QUARTER / HALF_YEAR / YEAR */
    @Column(name = "cycle", nullable = false, length = 16)
    private String cycle;

    /** 计划执行日 */
    @Column(name = "plan_date")
    private LocalDate planDate;

    /** 维保班组 */
    @Column(name = "team", length = 32)
    private String team;

    /** 维保员 */
    @Column(name = "worker", length = 32)
    private String worker;

    /** 执行结果，如「例行保养合格，更换门刀滑块」 */
    @Column(name = "execute_result", length = 255)
    private String executeResult;

    /** 实际完成日 */
    @Column(name = "actual_date")
    private LocalDate actualDate;

    /** PENDING 待执行 / DONE 已执行 / DELAYED 已延期 / VOID 作废 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 128)
    private String remark;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

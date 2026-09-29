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

/** 模块三：维保工单（计划落到执行，或临时派工直接开单；应急抢修单由应急报修受理时生成）。 */
@Data
@Entity
@Table(name = "maintenance_order")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status <> 'VOID'")
public class MaintenanceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工单号，如 MO-2026-0001；应急抢修单为 EX-2026-xxxx */
    @Column(name = "order_no", nullable = false, length = 24)
    private String orderNo;

    /** 来源计划；临时派工/应急抢修可为空 */
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "elevator_id", nullable = false)
    private Long elevatorId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** NORMAL 常规维保工单 / EMERGENCY 应急抢修工单 */
    @Column(name = "order_kind", nullable = false, length = 16)
    private String orderKind;

    /** 维保周期：HALF_MONTH / MONTH / QUARTER / HALF_YEAR / YEAR（应急单可空） */
    @Column(name = "cycle", length = 16)
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

    /**
     * 常规：PENDING 待执行 / DONE 已执行 / DELAYED 已延期 / VOID 作废；
     * 应急抢修：PENDING / PROCESSING / WAIT_ACCEPT / FIXED / CANCELLED
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 应急抢修单关联的故障报修单 id（常规单为 null） */
    @Column(name = "fault_id")
    private Long faultId;

    /** 应急抢修单关联的报修单号，冗余展示 */
    @Column(name = "fault_no", length = 24)
    private String faultNo;

    /** 实际到场时间（应急单闭环必填） */
    @Column(name = "arrive_time")
    private LocalDateTime arriveTime;

    @Column(name = "remark", length = 128)
    private String remark;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

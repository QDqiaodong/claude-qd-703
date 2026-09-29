package com.elevator.service.domain;

import java.math.BigDecimal;
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

/**
 * 模块二：电梯台账。
 *
 * <p>{@code statusFilter} 只留非停用电梯——左侧级联面板没选楼宇时，
 * 台账默认看不到停用梯；选中楼宇后切到 {@code buildingFilter}，停用梯会重新出现。
 */
@Data
@Entity
@Table(name = "elevator")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status <> 'OUT_OF_SERVICE'")
public class Elevator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 电梯编号（注册代码），如 EL-2019-018 */
    @Column(name = "elevator_no", nullable = false, length = 24)
    private String elevatorNo;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    /** 冗余楼宇 id：buildingFilter 直接作用在电梯表上 */
    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "brand", nullable = false, length = 32)
    private String brand;

    @Column(name = "model", nullable = false, length = 32)
    private String model;

    /** 额定载重（kg） */
    @Column(name = "rated_load", nullable = false)
    private Integer ratedLoad;

    /** 额定速度（m/s） */
    @Column(name = "rated_speed", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratedSpeed;

    /** 层站数 */
    @Column(name = "floor_station", nullable = false)
    private Integer floorStation;

    /** 安装日期 */
    @Column(name = "install_date")
    private LocalDate installDate;

    /** 上次年检日 */
    @Column(name = "last_inspect_date")
    private LocalDate lastInspectDate;

    /** 下次年检日 */
    @Column(name = "next_inspect_date")
    private LocalDate nextInspectDate;

    /** 维保单位 */
    @Column(name = "maint_vendor", length = 64)
    private String maintVendor;

    /** RUNNING 运行 / OUT_OF_SERVICE 停用 / REPAIRING 维修 / INSPECTING 年检中 / EMERGENCY_REPAIR 应急维修 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 进入应急维修前的原状态，闭环后据此恢复；非应急占用时为 NULL */
    @Column(name = "prev_status", length = 16)
    private String prevStatus;

    /** 当前占用该电梯的有效应急报修单 id，与 emergency_lock 表一致 */
    @Column(name = "emergency_fault_id")
    private Long emergencyFaultId;

    /** 当前停靠楼层（井道剖面图上轿厢位置） */
    @Column(name = "current_floor")
    private Integer currentFloor;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

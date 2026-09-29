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
 *
 * <p>应急联动新增：{@code EMERGENCY}（应急维修）状态 + {@code emergencyLock}（占用锁）。
 * 占用锁在库里有唯一约束，两台应急单抢同一台电梯时，第二个事务必然落库失败。
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

    /** RUNNING 运行 / OUT_OF_SERVICE 停用 / REPAIRING 维修 / INSPECTING 年检中 / EMERGENCY 应急维修 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 应急占用锁：指向当前未闭环应急报修单 id；NULL 表示未被占用，库上有唯一约束 */
    @Column(name = "emergency_lock")
    private Long emergencyLock;

    /** 进入应急维修前的电梯状态，闭环/误报撤销时恢复 */
    @Column(name = "status_before_emergency", length = 16)
    private String statusBeforeEmergency;

    /** 当前停靠楼层（井道剖面图上轿厢位置） */
    @Column(name = "current_floor")
    private Integer currentFloor;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

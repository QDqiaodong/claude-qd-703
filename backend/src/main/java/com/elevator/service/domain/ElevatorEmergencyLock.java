package com.elevator.service.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * 电梯应急占用锁（一台电梯最多一行有效行）。
 *
 * <p>受理应急报修时在同一事务里 INSERT：
 * 唯一键 {@code uk_lock_elevator} 保证两个操作员/两条应急报修并发受理时，
 * 只有一个事务能成功生成抢修工单，另一个事务整笔回滚，不会留下半条关联数据。
 * 闭环（已修复/已撤销）时删除对应行，释放占用，被暂停的计划随之恢复。
 */
@Data
@Entity
@Table(name = "elevator_emergency_lock")
public class ElevatorEmergencyLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elevator_id", nullable = false)
    private Long elevatorId;

    /** 当前有效应急报修单 id */
    @Column(name = "fault_id", nullable = false)
    private Long faultId;

    @Column(name = "fault_no", nullable = false, length = 24)
    private String faultNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no", length = 24)
    private String orderNo;

    /** 受理（加锁）操作员 */
    @Column(name = "locked_by", length = 32)
    private String lockedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

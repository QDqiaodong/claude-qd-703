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
 * 应急处置操作记录（审计轨迹）。
 *
 * <p>登记、受理（含并发受理失败）、推进、误报撤销、闭环、异常失败重试都会追加一行，
 * 只追加不修改；服务重启后状态、关联关系与操作记录都可从库中恢复。
 */
@Data
@Entity
@Table(name = "fault_operation_log")
public class FaultOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fault_id", nullable = false)
    private Long faultId;

    @Column(name = "fault_no", nullable = false, length = 24)
    private String faultNo;

    /** CREATE 登记 / ACCEPT 受理 / ADVANCE 推进 / CANCEL 误报撤销 / CLOSE 闭环 / REJECT 并发拦截 */
    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    /** 操作人（前端操作员；未登录态由调用方给出，默认「值班员」） */
    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no", length = 24)
    private String orderNo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

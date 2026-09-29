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
 * 故障应急处置操作记录（审计轨迹）。
 *
 * <p>每一步状态流转都插一条：登记 / 受理 / 到场 / 送验收 / 闭环 / 误报撤销。
 * 数据落库，服务重启或页面刷新后，未闭环应急单的处置过程仍可完整回放。
 */
@Data
@Entity
@Table(name = "fault_action_log")
public class FaultActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fault_id", nullable = false)
    private Long faultId;

    @Column(name = "fault_no", nullable = false, length = 24)
    private String faultNo;

    /** CREATE / ACCEPT / PROCESS / ARRIVE / VERIFY / FIX / CANCEL */
    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "from_status", length = 16)
    private String fromStatus;

    @Column(name = "to_status", length = 16)
    private String toStatus;

    @Column(name = "operator", length = 32)
    private String operator;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

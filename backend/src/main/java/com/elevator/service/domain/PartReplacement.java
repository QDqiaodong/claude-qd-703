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

/** 模块五：配件更换。可关联维保工单或故障报修单，金额 = 数量 × 单价。 */
@Data
@Entity
@Table(name = "part_replacement")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status <> 'VOID'")
public class PartReplacement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elevator_id", nullable = false)
    private Long elevatorId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 配件名称 */
    @Column(name = "part_name", nullable = false, length = 64)
    private String partName;

    /** 规格型号 */
    @Column(name = "spec", length = 64)
    private String spec;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 单价 */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** 金额 = 数量 × 单价 */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 更换日期 */
    @Column(name = "replace_date")
    private LocalDate replaceDate;

    /** 更换人 */
    @Column(name = "operator", length = 32)
    private String operator;

    /** 关联单据类型：ORDER 维保工单 / FAULT 故障报修 */
    @Column(name = "ref_type", length = 16)
    private String refType;

    /** 关联单据 id */
    @Column(name = "ref_id")
    private Long refId;

    /** 关联单号（冗余展示用） */
    @Column(name = "ref_no", length = 24)
    private String refNo;

    /** 供应商 */
    @Column(name = "supplier", length = 64)
    private String supplier;

    /** NORMAL 已登记 / VOID 作废 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

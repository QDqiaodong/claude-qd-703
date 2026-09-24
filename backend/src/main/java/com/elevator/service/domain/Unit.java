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

/** 模块一：楼栋单元。一台电梯挂在一个单元下，单元带冗余 building_id 供过滤器收口。 */
@Data
@Entity
@Table(name = "unit")
@Filter(name = FilterNames.BUILDING, condition = "building_id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status = 'ACTIVE'")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 单元编号，如 1 号楼 2 单元 → U-01-02 */
    @Column(name = "unit_no", nullable = false, length = 24)
    private String unitNo;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    /** 单元层数 */
    @Column(name = "floor_count", nullable = false)
    private Integer floorCount;

    /** 电梯数 */
    @Column(name = "elevator_count", nullable = false)
    private Integer elevatorCount;

    @Column(name = "note", length = 128)
    private String note;

    /** ACTIVE 在管 / ARCHIVED 已撤场 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

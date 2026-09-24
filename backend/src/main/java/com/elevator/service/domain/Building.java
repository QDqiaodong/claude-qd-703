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

/**
 * 模块一：楼宇。
 *
 * <p>楼宇是全局过滤器的根：{@code buildingFilter} 在楼宇上按主键收口，
 * 其余实体都带冗余的 {@code building_id}，按同一过滤器一并收口。
 */
@Data
@Entity
@Table(name = "building")
@Filter(name = FilterNames.BUILDING, condition = "id = :buildingId")
@Filter(name = FilterNames.STATUS, condition = "status = 'ACTIVE'")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 楼宇编号，如 BL-001 */
    @Column(name = "building_no", nullable = false, length = 16)
    private String buildingNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "address", nullable = false, length = 128)
    private String address;

    /** 层数 */
    @Column(name = "floor_count", nullable = false)
    private Integer floorCount;

    /** 建成年代 */
    @Column(name = "build_year", nullable = false)
    private Integer buildYear;

    /** 物业联系人 */
    @Column(name = "property_contact", length = 32)
    private String propertyContact;

    @Column(name = "property_phone", length = 32)
    private String propertyPhone;

    /** ACTIVE 在管 / ARCHIVED 已撤场 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

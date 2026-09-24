package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.Building;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    /** 列表查询统一走它，过滤与否完全由 buildingFilter / statusFilter 决定 */
    List<Building> findAllByOrderByBuildingNoAsc();

    Optional<Building> findByBuildingNo(String buildingNo);

    boolean existsByBuildingNo(String buildingNo);
}

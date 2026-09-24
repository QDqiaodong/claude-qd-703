package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.MaintenancePlan;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, Long> {

    List<MaintenancePlan> findAllByOrderByPlanDateDesc();

    boolean existsByPlanNo(String planNo);
}

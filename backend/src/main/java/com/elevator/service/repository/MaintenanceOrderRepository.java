package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.MaintenanceOrder;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {

    List<MaintenanceOrder> findAllByOrderByPlanDateDescIdDesc();

    boolean existsByOrderNo(String orderNo);
}

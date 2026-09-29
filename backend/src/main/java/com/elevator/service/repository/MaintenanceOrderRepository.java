package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.MaintenanceOrder;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {

    List<MaintenanceOrder> findAllByOrderByPlanDateDescIdDesc();

    boolean existsByOrderNo(String orderNo);

    /** 按来源应急报修单找抢修工单（自愈重试、状态三处对齐用） */
    Optional<MaintenanceOrder> findByFaultId(Long faultId);

    /** 该电梯未闭环的抢修工单是否存在（应急占用期间不允许重复生成） */
    long countByElevatorIdAndOrderTypeAndStatusNotIn(Long elevatorId, String orderType, List<String> statuses);
}

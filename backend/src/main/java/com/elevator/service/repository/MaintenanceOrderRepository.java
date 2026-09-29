package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.MaintenanceOrder;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {

    List<MaintenanceOrder> findAllByOrderByPlanDateDescIdDesc();

    boolean existsByOrderNo(String orderNo);

    /** 某台电梯未完成（非已执行/作废）的工单，用于判断应急占用时需要标出的在途普通单 */
    List<MaintenanceOrder> findByElevatorIdAndStatusInOrderByIdAsc(Long elevatorId, List<String> statuses);
}

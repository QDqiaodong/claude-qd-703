package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.MaintenancePlan;

public interface MaintenancePlanRepository extends JpaRepository<MaintenancePlan, Long> {

    List<MaintenancePlan> findAllByOrderByPlanDateDesc();

    boolean existsByPlanNo(String planNo);

    /** 某台电梯未完成（待执行/已延期）的计划，应急受理时需要保留并暂停 */
    List<MaintenancePlan> findByElevatorIdAndStatusInOrderByPlanDateAsc(Long elevatorId, List<String> statuses);

    /** 被某张应急报修单暂停的全部计划，闭环后恢复 */
    List<MaintenancePlan> findByOccupiedByFaultIdOrderByIdAsc(Long faultId);
}

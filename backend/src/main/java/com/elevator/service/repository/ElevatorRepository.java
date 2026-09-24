package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.Elevator;

public interface ElevatorRepository extends JpaRepository<Elevator, Long> {

    /** 过滤器开关对比用的就是这一条方法：开/关 buildingFilter 得到不同结果集 */
    List<Elevator> findAllByOrderByElevatorNoAsc();

    Optional<Elevator> findByElevatorNo(String elevatorNo);

    boolean existsByElevatorNo(String elevatorNo);

    long countByStatus(String status);
}

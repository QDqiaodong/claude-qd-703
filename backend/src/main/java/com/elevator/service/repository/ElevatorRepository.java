package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elevator.service.domain.Elevator;

public interface ElevatorRepository extends JpaRepository<Elevator, Long> {

    /** 过滤器开关对比用的就是这一条方法：开/关 buildingFilter 得到不同结果集 */
    List<Elevator> findAllByOrderByElevatorNoAsc();

    Optional<Elevator> findByElevatorNo(String elevatorNo);

    boolean existsByElevatorNo(String elevatorNo);

    long countByStatus(String status);

    /**
     * 应急受理时对电梯行加悲观写锁：两个操作员同时受理同一台电梯的应急单时，
     * 后到的事务阻塞，先到事务提交占用锁（emergency_lock 唯一约束）后，后到者必然失败。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Elevator e where e.id = :id")
    Optional<Elevator> findByIdForUpdate(@Param("id") Long id);
}

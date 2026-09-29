package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.elevator.service.domain.FaultReport;

public interface FaultReportRepository extends JpaRepository<FaultReport, Long> {

    List<FaultReport> findAllByOrderByReportTimeDesc();

    boolean existsByFaultNo(String faultNo);

    /** 某台电梯上未闭环（非已修复/已撤销）的应急报修单，按报修时间升序 */
    List<FaultReport> findByElevatorIdAndEmergencyAndStatusNotInOrderByReportTimeAsc(
            Long elevatorId, Integer emergency, List<String> statuses);

    Optional<FaultReport> findByOrderId(Long orderId);

    /** 悲观行锁：同一故障单的并发受理/闭环在此串行化 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FaultReport f where f.id = :id")
    Optional<FaultReport> findLockedById(@Param("id") Long id);
}

package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.FaultOperationLog;

public interface FaultOperationLogRepository extends JpaRepository<FaultOperationLog, Long> {

    List<FaultOperationLog> findAllByFaultIdOrderByCreatedAtAscIdAsc(Long faultId);
}

package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.FaultActionLog;

public interface FaultActionLogRepository extends JpaRepository<FaultActionLog, Long> {

    List<FaultActionLog> findByFaultIdOrderByIdAsc(Long faultId);
}

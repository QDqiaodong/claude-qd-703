package com.elevator.service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.ElevatorEmergencyLock;

public interface ElevatorEmergencyLockRepository extends JpaRepository<ElevatorEmergencyLock, Long> {

    Optional<ElevatorEmergencyLock> findByElevatorId(Long elevatorId);

    Optional<ElevatorEmergencyLock> findByFaultId(Long faultId);

    boolean existsByElevatorId(Long elevatorId);

    List<ElevatorEmergencyLock> findAllByOrderByCreatedAtDesc();
}

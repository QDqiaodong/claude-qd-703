package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.FaultReport;

public interface FaultReportRepository extends JpaRepository<FaultReport, Long> {

    List<FaultReport> findAllByOrderByReportTimeDesc();

    boolean existsByFaultNo(String faultNo);
}

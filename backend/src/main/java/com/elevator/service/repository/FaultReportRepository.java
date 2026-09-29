package com.elevator.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.elevator.service.domain.FaultReport;

public interface FaultReportRepository extends JpaRepository<FaultReport, Long> {

    List<FaultReport> findAllByOrderByReportTimeDesc();

    boolean existsByFaultNo(String faultNo);

    /** 未闭环应急报修：默认列表只看这些（应急处置台口径）。终态 = FIXED / CANCELLED */
    List<FaultReport> findByEmergencyFlagAndStatusNotInOrderByReportTimeDesc(Integer emergencyFlag,
                                                                             List<String> statuses);

    /**
     * 同一台电梯是否存在「未闭环」应急单（并发报第二张应急单时拦截）。
     * 终态只有 FIXED / CANCELLED。
     */
    long countByElevatorIdAndEmergencyFlagAndStatusNotIn(Long elevatorId,
                                                         Integer emergencyFlag,
                                                         List<String> statuses);
}

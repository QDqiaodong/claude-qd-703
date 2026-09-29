package com.elevator.service.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.MaintenanceOrder;
import com.elevator.service.domain.MaintenancePlan;
import com.elevator.service.domain.PartReplacement;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.MaintenanceOrderRepository;
import com.elevator.service.repository.MaintenancePlanRepository;
import com.elevator.service.repository.PartReplacementRepository;
import com.elevator.service.repository.UnitRepository;

/** 顶栏总览计数器：全公司口径（不套过滤器，供对比「过滤后的列表」用）。 */
@Service
public class OverviewService {

    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final ElevatorRepository elevatorRepository;
    private final MaintenancePlanRepository planRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final FaultReportRepository faultRepository;
    private final PartReplacementRepository partRepository;

    public OverviewService(BuildingRepository buildingRepository,
                           UnitRepository unitRepository,
                           ElevatorRepository elevatorRepository,
                           MaintenancePlanRepository planRepository,
                           MaintenanceOrderRepository orderRepository,
                           FaultReportRepository faultRepository,
                           PartReplacementRepository partRepository) {
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.elevatorRepository = elevatorRepository;
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.faultRepository = faultRepository;
        this.partRepository = partRepository;
    }

    @Transactional(readOnly = true)
    public Views.OverviewView overview() {
        LocalDate today = LocalDate.now();
        long inspectDue = 0;
        for (var elevator : elevatorRepository.findAll()) {
            if (elevator.getNextInspectDate() != null
                    && (elevator.getNextInspectDate().isBefore(today)
                    || elevator.getNextInspectDate().minusDays(30).isBefore(today))) {
                inspectDue++;
            }
        }
        long planPending = 0;
        long planDelayed = 0;
        for (MaintenancePlan plan : planRepository.findAll()) {
            if (ElevatorDict.P_PENDING.equals(plan.getStatus())) {
                planPending++;
            } else if (ElevatorDict.P_DELAYED.equals(plan.getStatus())) {
                planDelayed++;
            }
        }
        long orderPending = 0;
        for (MaintenanceOrder order : orderRepository.findAll()) {
            if (ElevatorDict.P_PENDING.equals(order.getStatus()) || ElevatorDict.P_DELAYED.equals(order.getStatus())) {
                orderPending++;
            }
        }
        long faultOpen = 0;
        long faultTrapped = 0;
        for (FaultReport report : faultRepository.findAll()) {
            if (!ElevatorDict.isFaultClosed(report.getStatus())) {
                faultOpen++;
                if (report.getTrapped() != null && report.getTrapped() > 0) {
                    faultTrapped++;
                }
            }
        }
        long emergencyActive = elevatorRepository.countByStatus(ElevatorDict.ST_EMERGENCY);
        BigDecimal partAmount = BigDecimal.ZERO;
        for (PartReplacement part : partRepository.findAll()) {
            if (!"VOID".equals(part.getStatus()) && part.getAmount() != null) {
                partAmount = partAmount.add(part.getAmount());
            }
        }
        return new Views.OverviewView(
                buildingRepository.count(),
                unitRepository.count(),
                elevatorRepository.count(),
                elevatorRepository.countByStatus(ElevatorDict.ST_RUNNING),
                elevatorRepository.countByStatus(ElevatorDict.ST_OUT),
                elevatorRepository.countByStatus(ElevatorDict.ST_REPAIR),
                elevatorRepository.countByStatus(ElevatorDict.ST_INSPECT),
                inspectDue,
                planPending,
                planDelayed,
                orderPending,
                faultOpen,
                faultTrapped,
                emergencyActive,
                partAmount.setScale(2, java.math.RoundingMode.HALF_UP));
    }
}

package com.elevator.service.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elevator.service.common.BizException;
import com.elevator.service.domain.Building;
import com.elevator.service.domain.Elevator;
import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.Unit;
import com.elevator.service.dto.FaultDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.UnitRepository;

/**
 * 模块四：故障报修。
 *
 * <p>列表经 {@link FilterScope}：不传楼宇时 {@code statusFilter}(status &lt;&gt; 'FIXED')
 * 只留未闭环单；传入楼宇时 {@code buildingFilter} 把历史已修复单一起放出来。
 */
@Service
public class FaultService {

    private final FaultReportRepository faultRepository;
    private final ElevatorRepository elevatorRepository;
    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final FilterScope filterScope;

    public FaultService(FaultReportRepository faultRepository,
                        ElevatorRepository elevatorRepository,
                        UnitRepository unitRepository,
                        BuildingRepository buildingRepository,
                        FilterScope filterScope) {
        this.faultRepository = faultRepository;
        this.elevatorRepository = elevatorRepository;
        this.unitRepository = unitRepository;
        this.buildingRepository = buildingRepository;
        this.filterScope = filterScope;
    }

    @Transactional(readOnly = true)
    public List<Views.FaultView> list(Long buildingId, Long elevatorId) {
        List<FaultReport> reports = filterScope.withFilter(buildingId, faultRepository::findAllByOrderByReportTimeDesc);
        Map<Long, String> unitNo = new HashMap<>();
        for (Unit unit : unitRepository.findAll()) {
            unitNo.put(unit.getId(), unit.getUnitNo());
        }
        Map<Long, String> buildingName = new HashMap<>();
        for (Building building : buildingRepository.findAll()) {
            buildingName.put(building.getId(), building.getName());
        }
        Map<Long, String> elevatorNo = new HashMap<>();
        for (Elevator elevator : elevatorRepository.findAll()) {
            elevatorNo.put(elevator.getId(), elevator.getElevatorNo());
        }
        List<Views.FaultView> list = new ArrayList<>();
        for (FaultReport report : reports) {
            if (elevatorId != null && !elevatorId.equals(report.getElevatorId())) {
                continue;
            }
            list.add(toView(report, unitNo, buildingName, elevatorNo));
        }
        return list;
    }

    @Transactional
    public Views.FaultView create(FaultDtos.FaultSaveReq req) {
        String faultNo = trim(req.getFaultNo());
        if (faultRepository.existsByFaultNo(faultNo)) {
            throw new BizException("报修单号 " + faultNo + " 已存在，不能重复登记");
        }
        if (req.getElevatorId() == null) {
            throw new BizException("所属电梯不能为空");
        }
        Elevator elevator = elevatorRepository.findById(req.getElevatorId())
                .orElseThrow(() -> new BizException("电梯不存在：id=" + req.getElevatorId()));
        String faultType = req.getFaultType() == null ? "" : trim(req.getFaultType()).toUpperCase();
        if (!ElevatorDict.isValidFaultType(faultType)) {
            throw new BizException("故障现象只能是 TRAPPED/NOISE/LEVELING/STOPPED/DOOR/OTHER，收到的是：" + req.getFaultType());
        }
        Integer responseMinutes = req.getResponseMinutes() == null ? 0 : req.getResponseMinutes();
        Integer arriveMinutes = req.getArriveMinutes() == null ? 0 : req.getArriveMinutes();
        if (responseMinutes < 0 || arriveMinutes < 0) {
            throw new BizException("响应时长与到达时长不能为负数");
        }
        if (arriveMinutes < responseMinutes) {
            throw new BizException("到达时长不能小于响应时长");
        }
        FaultReport report = new FaultReport();
        report.setFaultNo(faultNo);
        report.setElevatorId(elevator.getId());
        report.setUnitId(elevator.getUnitId());
        report.setBuildingId(elevator.getBuildingId());
        report.setFaultType(faultType);
        report.setReportTime(req.getReportTime() == null ? LocalDateTime.now() : req.getReportTime());
        report.setResponseMinutes(responseMinutes);
        report.setArriveMinutes(arriveMinutes);
        report.setTrapped(req.getTrapped() == null
                ? (ElevatorDict.faultTypeTrapped(faultType) ? 1 : 0) : (req.getTrapped() > 0 ? 1 : 0));
        report.setHandleResult(trim(req.getHandleResult()));
        report.setReporter(trim(req.getReporter()));
        report.setStatus(ElevatorDict.F_PENDING);
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        return toView(report, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** 推进状态：待处理 → 处理中 → 已修复 */
    @Transactional
    public Views.FaultView advance(Long id) {
        FaultReport report = faultRepository.findById(id).orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        switch (report.getStatus()) {
            case ElevatorDict.F_PENDING -> report.setStatus(ElevatorDict.F_PROCESSING);
            case ElevatorDict.F_PROCESSING -> {
                report.setStatus(ElevatorDict.F_FIXED);
                report.setFixTime(LocalDateTime.now());
                if (report.getHandleResult() == null || report.getHandleResult().isBlank()) {
                    report.setHandleResult("现场处理完毕，恢复运行");
                }
            }
            default -> throw new BizException("报修单 " + report.getFaultNo() + " 已修复，不能再推进");
        }
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        return toView(report, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    /** 直接修复：填处理结果并闭环 */
    @Transactional
    public Views.FaultView fix(Long id, FaultDtos.FixReq req) {
        FaultReport report = faultRepository.findById(id).orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        if (ElevatorDict.F_FIXED.equals(report.getStatus())) {
            throw new BizException("报修单 " + report.getFaultNo() + " 已修复，不要重复闭环");
        }
        String result = req == null ? null : trim(req.getHandleResult());
        if (result == null || result.isEmpty()) {
            throw new BizException("处理结果不能为空");
        }
        report.setHandleResult(result);
        report.setFixTime(LocalDateTime.now());
        report.setStatus(ElevatorDict.F_FIXED);
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        return toView(report, new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    private Views.FaultView toView(FaultReport report,
                                   Map<Long, String> unitNo,
                                   Map<Long, String> buildingName,
                                   Map<Long, String> elevatorNo) {
        Integer arrive = report.getArriveMinutes() == null ? 0 : report.getArriveMinutes();
        return new Views.FaultView(
                report.getId(),
                report.getFaultNo(),
                report.getElevatorId(),
                elevatorNo.getOrDefault(report.getElevatorId(), ""),
                report.getUnitId(),
                unitNo.getOrDefault(report.getUnitId(), ""),
                report.getBuildingId(),
                buildingName.getOrDefault(report.getBuildingId(), ""),
                report.getFaultType(),
                ElevatorDict.faultTypeName(report.getFaultType()),
                report.getReportTime(),
                report.getResponseMinutes(),
                report.getArriveMinutes(),
                report.getFixTime(),
                report.getTrapped(),
                report.getHandleResult(),
                report.getReporter(),
                report.getStatus(),
                ElevatorDict.faultStatusName(report.getStatus()),
                arrive > ElevatorDict.ARRIVE_SLA_MINUTES,
                report.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

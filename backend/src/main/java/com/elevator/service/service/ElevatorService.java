package com.elevator.service.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import com.elevator.service.domain.FilterNames;
import com.elevator.service.domain.Unit;
import com.elevator.service.dto.ElevatorDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.UnitRepository;

/**
 * 模块二：电梯台账。
 *
 * <p>列表查询全部经 {@link FilterScope}：
 * 不传楼宇 → {@code statusFilter}(status &lt;&gt; 'OUT_OF_SERVICE')，列表里看不到停用梯；
 * 传入楼宇 → {@code buildingFilter}(building_id = :buildingId)，停用梯重新出现。
 * {@link #compareFilter} 用同一条 repository 方法把开/关两种结果一起返回给页面。
 */
@Service
public class ElevatorService {

    private final ElevatorRepository elevatorRepository;
    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final FaultReportRepository faultRepository;
    private final FilterScope filterScope;

    public ElevatorService(ElevatorRepository elevatorRepository,
                           UnitRepository unitRepository,
                           BuildingRepository buildingRepository,
                           FaultReportRepository faultRepository,
                           FilterScope filterScope) {
        this.elevatorRepository = elevatorRepository;
        this.unitRepository = unitRepository;
        this.buildingRepository = buildingRepository;
        this.faultRepository = faultRepository;
        this.filterScope = filterScope;
    }

    @Transactional(readOnly = true)
    public List<Views.ElevatorView> list(Long buildingId, Long unitId, Long elevatorId) {
        List<Elevator> elevators = filterScope.withFilter(buildingId, elevatorRepository::findAllByOrderByElevatorNoAsc);
        Map<Long, String> unitNo = new HashMap<>();
        for (Unit unit : unitRepository.findAll()) {
            unitNo.put(unit.getId(), unit.getUnitNo());
        }
        Map<Long, String> buildingName = new HashMap<>();
        for (Building building : buildingRepository.findAll()) {
            buildingName.put(building.getId(), building.getName());
        }
        List<Views.ElevatorView> list = new ArrayList<>();
        for (Elevator elevator : elevators) {
            if (unitId != null && !unitId.equals(elevator.getUnitId())) {
                continue;
            }
            if (elevatorId != null && !elevatorId.equals(elevator.getId())) {
                continue;
            }
            list.add(toView(elevator, unitNo, buildingName));
        }
        return list;
    }

    /** 井道剖面图数据源：单台电梯 + 层站数 + 当前停靠层 */
    @Transactional(readOnly = true)
    public Views.ShaftView shaft(Long elevatorId) {
        Elevator elevator = elevatorRepository.findById(elevatorId)
                .orElseThrow(() -> new BizException("电梯不存在：id=" + elevatorId));
        Map<Long, String> unitNo = new HashMap<>();
        for (Unit unit : unitRepository.findAll()) {
            unitNo.put(unit.getId(), unit.getUnitNo());
        }
        Map<Long, String> buildingName = new HashMap<>();
        for (Building building : buildingRepository.findAll()) {
            buildingName.put(building.getId(), building.getName());
        }
        Views.ElevatorView view = toView(elevator, unitNo, buildingName);
        return new Views.ShaftView(view, elevator.getFloorStation(), currentFloorOf(elevator),
                elevator.getStatus(), ElevatorDict.elevatorStatusName(elevator.getStatus()));
    }

    /**
     * 【过滤器开关对比】同一条查询方法 {@code findAllByOrderByElevatorNoAsc()}，
     * 开 buildingFilter 与不开，返回两个不同大小的结果集。
     */
    @Transactional(readOnly = true)
    public Views.FilterCompareView compareFilter(Long buildingId) {
        List<Elevator> filtered = filterScope.withFilter(buildingId, elevatorRepository::findAllByOrderByElevatorNoAsc);
        List<String> filteredCodes = new ArrayList<>();
        for (Elevator elevator : filtered) {
            filteredCodes.add(elevator.getElevatorNo() + "(" + ElevatorDict.elevatorStatusName(elevator.getStatus()) + ")");
        }
        // 关掉过滤器再跑同一条方法
        List<Elevator> unfiltered = elevatorRepository.findAllByOrderByElevatorNoAsc();
        List<String> unfilteredCodes = new ArrayList<>();
        for (Elevator elevator : unfiltered) {
            unfilteredCodes.add(elevator.getElevatorNo() + "(" + ElevatorDict.elevatorStatusName(elevator.getStatus()) + ")");
        }
        String buildingName = buildingId == null ? "（未选楼宇）"
                : buildingRepository.findById(buildingId).map(Building::getName).orElse("（楼宇不存在）");
        boolean byBuilding = buildingId != null && buildingId > 0;
        return new Views.FilterCompareView(
                "ElevatorRepository#findAllByOrderByElevatorNoAsc",
                buildingId,
                buildingName,
                byBuilding ? FilterNames.BUILDING : FilterNames.STATUS,
                byBuilding ? "building_id = :buildingId" : "status <> 'OUT_OF_SERVICE'",
                (long) filtered.size(),
                (long) unfiltered.size(),
                filteredCodes,
                unfilteredCodes);
    }

    @Transactional
    public Views.ElevatorView create(ElevatorDtos.ElevatorSaveReq req) {
        String elevatorNo = trim(req.getElevatorNo());
        if (elevatorRepository.existsByElevatorNo(elevatorNo)) {
            throw new BizException("电梯编号 " + elevatorNo + " 已存在，不能重复建档");
        }
        Unit unit = unitRepository.findById(req.getUnitId())
                .orElseThrow(() -> new BizException("所属单元不存在：id=" + req.getUnitId()));
        if (ElevatorDict.ARCHIVED.equals(unit.getStatus())) {
            throw new BizException("单元「" + unit.getUnitNo() + "」已撤场，不能再挂电梯");
        }
        Integer ratedLoad = req.getRatedLoad() == null ? 0 : req.getRatedLoad();
        Integer floorStation = req.getFloorStation() == null ? 0 : req.getFloorStation();
        if (ratedLoad <= 0) {
            throw new BizException("额定载重必须大于 0");
        }
        if (floorStation <= 0) {
            throw new BizException("层站数必须大于 0");
        }
        if (req.getRatedSpeed() == null || req.getRatedSpeed().signum() <= 0) {
            throw new BizException("额定速度必须大于 0");
        }
        if (floorStation > unit.getFloorCount()) {
            throw new BizException("层站数 " + floorStation + " 超过单元层数 " + unit.getFloorCount());
        }
        String status = req.getStatus() == null || req.getStatus().isBlank()
                ? ElevatorDict.ST_RUNNING : trim(req.getStatus()).toUpperCase();
        if (!ElevatorDict.isValidElevatorStatus(status)) {
            throw new BizException("电梯状态只能是 RUNNING/OUT_OF_SERVICE/REPAIRING/INSPECTING，收到的是：" + req.getStatus());
        }
        Elevator elevator = new Elevator();
        elevator.setElevatorNo(elevatorNo);
        elevator.setUnitId(unit.getId());
        elevator.setBuildingId(unit.getBuildingId());
        elevator.setBrand(trim(req.getBrand()));
        elevator.setModel(trim(req.getModel()));
        elevator.setRatedLoad(ratedLoad);
        elevator.setRatedSpeed(req.getRatedSpeed());
        elevator.setFloorStation(floorStation);
        elevator.setInstallDate(req.getInstallDate());
        elevator.setLastInspectDate(req.getLastInspectDate());
        elevator.setNextInspectDate(req.getNextInspectDate());
        elevator.setMaintVendor(trim(req.getMaintVendor()));
        elevator.setStatus(status);
        elevator.setCurrentFloor(1);
        elevator.setUpdatedAt(LocalDateTime.now());
        elevatorRepository.save(elevator);
        return toView(elevator, new HashMap<>(), new HashMap<>());
    }

    @Transactional
    public Views.ElevatorView changeStatus(Long id, String status) {
        Elevator elevator = elevatorRepository.findById(id)
                .orElseThrow(() -> new BizException("电梯不存在：id=" + id));
        String target = status == null ? "" : trim(status).toUpperCase();
        if (!ElevatorDict.isValidElevatorStatus(target)) {
            throw new BizException("电梯状态只能是 RUNNING/OUT_OF_SERVICE/REPAIRING/INSPECTING，收到的是：" + status);
        }
        if (ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus()) && !ElevatorDict.ST_EMERGENCY.equals(target)) {
            throw new BizException("电梯正被应急处置占用，不能手工改状态，需由应急报修闭环或撤销后自动恢复");
        }
        if (ElevatorDict.ST_EMERGENCY.equals(target)) {
            throw new BizException("应急维修状态只能由应急报修受理时自动进入，不能手工设置");
        }
        elevator.setStatus(target);
        elevator.setUpdatedAt(LocalDateTime.now());
        elevatorRepository.save(elevator);
        return toView(elevator, new HashMap<>(), new HashMap<>());
    }

    /** 井道剖面图：轿厢停靠到指定楼层 */
    @Transactional
    public Views.ElevatorView dock(Long id, Integer floor) {
        Elevator elevator = elevatorRepository.findById(id)
                .orElseThrow(() -> new BizException("电梯不存在：id=" + id));
        int target = floor == null ? 1 : floor;
        if (target < 1 || elevator.getFloorStation() != null && target > elevator.getFloorStation()) {
            throw new BizException("停靠楼层需在 1~" + elevator.getFloorStation() + " 层之间，收到的是：" + target);
        }
        if (ElevatorDict.ST_OUT.equals(elevator.getStatus())) {
            throw new BizException("电梯已停用，不能调整轿厢位置");
        }
        elevator.setCurrentFloor(target);
        elevator.setUpdatedAt(LocalDateTime.now());
        elevatorRepository.save(elevator);
        return toView(elevator, new HashMap<>(), new HashMap<>());
    }

    private Views.ElevatorView toView(Elevator elevator, Map<Long, String> unitNo, Map<Long, String> buildingName) {
        String uNo = unitNo.getOrDefault(elevator.getUnitId(), "");
        String bName = buildingName.getOrDefault(elevator.getBuildingId(), "");
        String emergencyFaultNo = null;
        String emergencyOrderNo = null;
        if (ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus()) && elevator.getEmergencyFaultId() != null) {
            emergencyFaultNo = faultRepository.findById(elevator.getEmergencyFaultId())
                    .map(FaultReport::getFaultNo).orElse(null);
            emergencyOrderNo = faultRepository.findById(elevator.getEmergencyFaultId())
                    .map(FaultReport::getOrderNo).orElse(null);
        }
        return new Views.ElevatorView(
                elevator.getId(),
                elevator.getElevatorNo(),
                elevator.getUnitId(),
                uNo,
                elevator.getBuildingId(),
                bName,
                elevator.getBrand(),
                elevator.getModel(),
                elevator.getRatedLoad(),
                elevator.getRatedSpeed(),
                elevator.getFloorStation(),
                elevator.getInstallDate(),
                elevator.getLastInspectDate(),
                elevator.getNextInspectDate(),
                elevator.getMaintVendor(),
                elevator.getStatus(),
                ElevatorDict.elevatorStatusName(elevator.getStatus()),
                currentFloorOf(elevator),
                inspectDueDays(elevator.getNextInspectDate()),
                ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus()),
                emergencyFaultNo,
                emergencyOrderNo,
                elevator.getUpdatedAt());
    }

    private static int currentFloorOf(Elevator elevator) {
        if (elevator.getCurrentFloor() == null || elevator.getCurrentFloor() < 1) {
            return 1;
        }
        if (elevator.getFloorStation() != null && elevator.getCurrentFloor() > elevator.getFloorStation()) {
            return elevator.getFloorStation();
        }
        return elevator.getCurrentFloor();
    }

    private static Long inspectDueDays(LocalDate nextInspectDate) {
        if (nextInspectDate == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), nextInspectDate);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

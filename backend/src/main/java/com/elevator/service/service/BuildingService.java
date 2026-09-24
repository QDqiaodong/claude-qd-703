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
import com.elevator.service.domain.Unit;
import com.elevator.service.dto.BuildingDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.UnitRepository;

/**
 * 模块一：楼宇与单元。
 *
 * <p>列表一律经 {@link FilterScope#withFilter}：传楼宇 id 走 {@code buildingFilter}，
 * 不传走 {@code statusFilter}（撤场的楼宇默认不出现在列表里）。
 */
@Service
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final UnitRepository unitRepository;
    private final ElevatorRepository elevatorRepository;
    private final FilterScope filterScope;

    public BuildingService(BuildingRepository buildingRepository,
                           UnitRepository unitRepository,
                           ElevatorRepository elevatorRepository,
                           FilterScope filterScope) {
        this.buildingRepository = buildingRepository;
        this.unitRepository = unitRepository;
        this.elevatorRepository = elevatorRepository;
        this.filterScope = filterScope;
    }

    @Transactional(readOnly = true)
    public List<Views.BuildingView> list(Long buildingId) {
        List<Building> buildings = filterScope.withFilter(buildingId, buildingRepository::findAllByOrderByBuildingNoAsc);
        // 统计量与过滤器无关：全量统计后再按楼宇归集
        Map<Long, Long> unitCount = new HashMap<>();
        for (Unit unit : unitRepository.findAll()) {
            unitCount.merge(unit.getBuildingId(), 1L, Long::sum);
        }
        Map<Long, Long> elevatorCount = new HashMap<>();
        for (Elevator elevator : elevatorRepository.findAll()) {
            elevatorCount.merge(elevator.getBuildingId(), 1L, Long::sum);
        }
        List<Views.BuildingView> list = new ArrayList<>();
        for (Building building : buildings) {
            list.add(new Views.BuildingView(
                    building.getId(),
                    building.getBuildingNo(),
                    building.getName(),
                    building.getAddress(),
                    building.getFloorCount(),
                    building.getBuildYear(),
                    building.getPropertyContact(),
                    building.getPropertyPhone(),
                    building.getStatus(),
                    ElevatorDict.archiveStatusName(building.getStatus()),
                    unitCount.getOrDefault(building.getId(), 0L),
                    elevatorCount.getOrDefault(building.getId(), 0L),
                    building.getUpdatedAt()));
        }
        return list;
    }

    /**
     * 左侧三级级联面板数据：楼宇 → 单元 → 电梯。
     * 传了楼宇就开 buildingFilter 收口；没传则不做过滤——面板要能选到停用梯和撤场楼。
     */
    @Transactional(readOnly = true)
    public List<Views.TreeBuildingView> tree(Long buildingId) {
        List<Building> buildings = buildingId == null
                ? buildingRepository.findAllByOrderByBuildingNoAsc()
                : filterScope.withFilter(buildingId, buildingRepository::findAllByOrderByBuildingNoAsc);
        List<Unit> units = buildingId == null
                ? unitRepository.findAllByOrderByBuildingIdAscUnitNoAsc()
                : filterScope.withFilter(buildingId, unitRepository::findAllByOrderByBuildingIdAscUnitNoAsc);
        List<Elevator> elevators = buildingId == null
                ? elevatorRepository.findAllByOrderByElevatorNoAsc()
                : filterScope.withFilter(buildingId, elevatorRepository::findAllByOrderByElevatorNoAsc);

        Map<Long, List<Views.TreeElevatorView>> elevatorsByUnit = new HashMap<>();
        for (Elevator elevator : elevators) {
            elevatorsByUnit.computeIfAbsent(elevator.getUnitId(), key -> new ArrayList<>()).add(
                    new Views.TreeElevatorView(
                            elevator.getId(),
                            elevator.getElevatorNo(),
                            elevator.getBrand(),
                            elevator.getModel(),
                            elevator.getFloorStation(),
                            elevator.getStatus(),
                            ElevatorDict.elevatorStatusName(elevator.getStatus()),
                            elevator.getCurrentFloor()));
        }
        Map<Long, List<Views.TreeUnitView>> unitsByBuilding = new HashMap<>();
        for (Unit unit : units) {
            unitsByBuilding.computeIfAbsent(unit.getBuildingId(), key -> new ArrayList<>()).add(
                    new Views.TreeUnitView(
                            unit.getId(),
                            unit.getUnitNo(),
                            unit.getFloorCount(),
                            unit.getElevatorCount(),
                            unit.getStatus(),
                            ElevatorDict.archiveStatusName(unit.getStatus()),
                            elevatorsByUnit.getOrDefault(unit.getId(), new ArrayList<>())));
        }
        List<Views.TreeBuildingView> list = new ArrayList<>();
        for (Building building : buildings) {
            list.add(new Views.TreeBuildingView(
                    building.getId(),
                    building.getBuildingNo(),
                    building.getName(),
                    building.getAddress(),
                    building.getFloorCount(),
                    building.getBuildYear(),
                    building.getStatus(),
                    ElevatorDict.archiveStatusName(building.getStatus()),
                    unitsByBuilding.getOrDefault(building.getId(), new ArrayList<>())));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Views.UnitView> listUnits(Long buildingId) {
        List<Unit> units = filterScope.withFilter(buildingId, unitRepository::findAllByOrderByBuildingIdAscUnitNoAsc);
        Map<Long, String> buildingName = new HashMap<>();
        for (Building building : buildingRepository.findAll()) {
            buildingName.put(building.getId(), building.getName());
        }
        Map<Long, Long> realCount = new HashMap<>();
        for (Elevator elevator : elevatorRepository.findAll()) {
            realCount.merge(elevator.getUnitId(), 1L, Long::sum);
        }
        List<Views.UnitView> list = new ArrayList<>();
        for (Unit unit : units) {
            list.add(new Views.UnitView(
                    unit.getId(),
                    unit.getUnitNo(),
                    unit.getBuildingId(),
                    buildingName.get(unit.getBuildingId()),
                    unit.getFloorCount(),
                    unit.getElevatorCount(),
                    realCount.getOrDefault(unit.getId(), 0L),
                    unit.getNote(),
                    unit.getStatus(),
                    ElevatorDict.archiveStatusName(unit.getStatus()),
                    unit.getUpdatedAt()));
        }
        return list;
    }

    @Transactional
    public Views.BuildingView create(BuildingDtos.BuildingSaveReq req) {
        String buildingNo = trim(req.getBuildingNo());
        if (buildingRepository.existsByBuildingNo(buildingNo)) {
            throw new BizException("楼宇编号 " + buildingNo + " 已存在，不能重复建档");
        }
        Integer floorCount = req.getFloorCount() == null ? 0 : req.getFloorCount();
        Integer buildYear = req.getBuildYear() == null ? 0 : req.getBuildYear();
        if (floorCount <= 0) {
            throw new BizException("层数必须大于 0");
        }
        if (buildYear < 1900 || buildYear > LocalDateTime.now().getYear()) {
            throw new BizException("建成年代需在 1900 与今年之间，收到的是：" + buildYear);
        }
        String status = req.getStatus() == null || req.getStatus().isBlank()
                ? ElevatorDict.ACTIVE : trim(req.getStatus()).toUpperCase();
        if (!ElevatorDict.isValidArchiveStatus(status)) {
            throw new BizException("楼宇状态只能是 ACTIVE 在管 / ARCHIVED 已撤场，收到的是：" + req.getStatus());
        }
        Building building = new Building();
        building.setBuildingNo(buildingNo);
        building.setName(trim(req.getName()));
        building.setAddress(trim(req.getAddress()));
        building.setFloorCount(floorCount);
        building.setBuildYear(buildYear);
        building.setPropertyContact(trim(req.getPropertyContact()));
        building.setPropertyPhone(trim(req.getPropertyPhone()));
        building.setStatus(status);
        building.setUpdatedAt(LocalDateTime.now());
        buildingRepository.save(building);
        return toView(building);
    }

    @Transactional
    public Views.BuildingView changeStatus(Long id, String status) {
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> new BizException("楼宇不存在：id=" + id));
        String target = status == null ? "" : trim(status).toUpperCase();
        if (!ElevatorDict.isValidArchiveStatus(target)) {
            throw new BizException("楼宇状态只能是 ACTIVE 在管 / ARCHIVED 已撤场，收到的是：" + status);
        }
        building.setStatus(target);
        building.setUpdatedAt(LocalDateTime.now());
        buildingRepository.save(building);
        return toView(building);
    }

    @Transactional
    public Views.UnitView createUnit(BuildingDtos.UnitSaveReq req) {
        String unitNo = trim(req.getUnitNo());
        if (unitRepository.existsByUnitNo(unitNo)) {
            throw new BizException("单元编号 " + unitNo + " 已存在，不能重复建档");
        }
        Building building = buildingRepository.findById(req.getBuildingId())
                .orElseThrow(() -> new BizException("所属楼宇不存在：id=" + req.getBuildingId()));
        if (ElevatorDict.ARCHIVED.equals(building.getStatus())) {
            throw new BizException("楼宇「" + building.getName() + "」已撤场，不能再新增单元");
        }
        Integer floorCount = req.getFloorCount() == null ? 0 : req.getFloorCount();
        Integer elevatorCount = req.getElevatorCount() == null ? 0 : req.getElevatorCount();
        if (floorCount <= 0) {
            throw new BizException("单元层数必须大于 0");
        }
        if (floorCount > building.getFloorCount()) {
            throw new BizException("单元层数 " + floorCount + " 超过楼宇总层数 " + building.getFloorCount());
        }
        if (elevatorCount <= 0) {
            throw new BizException("电梯数必须大于 0");
        }
        String status = req.getStatus() == null || req.getStatus().isBlank()
                ? ElevatorDict.ACTIVE : trim(req.getStatus()).toUpperCase();
        if (!ElevatorDict.isValidArchiveStatus(status)) {
            throw new BizException("单元状态只能是 ACTIVE 在管 / ARCHIVED 已撤场，收到的是：" + req.getStatus());
        }
        Unit unit = new Unit();
        unit.setUnitNo(unitNo);
        unit.setBuildingId(building.getId());
        unit.setFloorCount(floorCount);
        unit.setElevatorCount(elevatorCount);
        unit.setNote(trim(req.getNote()));
        unit.setStatus(status);
        unit.setUpdatedAt(LocalDateTime.now());
        unitRepository.save(unit);
        return new Views.UnitView(unit.getId(), unit.getUnitNo(), unit.getBuildingId(), building.getName(),
                unit.getFloorCount(), unit.getElevatorCount(), 0L, unit.getNote(),
                unit.getStatus(), ElevatorDict.archiveStatusName(unit.getStatus()), unit.getUpdatedAt());
    }

    @Transactional
    public Views.UnitView changeUnitStatus(Long id, String status) {
        Unit unit = unitRepository.findById(id).orElseThrow(() -> new BizException("单元不存在：id=" + id));
        String target = status == null ? "" : trim(status).toUpperCase();
        if (!ElevatorDict.isValidArchiveStatus(target)) {
            throw new BizException("单元状态只能是 ACTIVE 在管 / ARCHIVED 已撤场，收到的是：" + status);
        }
        unit.setStatus(target);
        unit.setUpdatedAt(LocalDateTime.now());
        unitRepository.save(unit);
        String buildingName = buildingRepository.findById(unit.getBuildingId())
                .map(Building::getName).orElse("");
        return new Views.UnitView(unit.getId(), unit.getUnitNo(), unit.getBuildingId(), buildingName,
                unit.getFloorCount(), unit.getElevatorCount(), 0L, unit.getNote(),
                unit.getStatus(), ElevatorDict.archiveStatusName(unit.getStatus()), unit.getUpdatedAt());
    }

    private Views.BuildingView toView(Building building) {
        return new Views.BuildingView(building.getId(), building.getBuildingNo(), building.getName(),
                building.getAddress(), building.getFloorCount(), building.getBuildYear(),
                building.getPropertyContact(), building.getPropertyPhone(), building.getStatus(),
                ElevatorDict.archiveStatusName(building.getStatus()), 0L, 0L, building.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

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
import com.elevator.service.domain.ElevatorEmergencyLock;
import com.elevator.service.domain.FaultOperationLog;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.Unit;
import com.elevator.service.dto.FaultDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorEmergencyLockRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultOperationLogRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.UnitRepository;

/**
 * 模块四：故障报修。
 *
 * <p>列表经 {@link FilterScope}：不传楼宇时 {@code statusFilter}
 * (status NOT IN ('FIXED','CANCELLED')) 只留未闭环单；传入楼宇时 {@code buildingFilter}
 * 把历史已修复/已撤销单一起放出来。困人/停梯类故障登记即进入应急态（emergency=1、高风险），
 * 受理后由 {@link EmergencyLinkService} 串起抢修工单与电梯占用；普通异响等仍走原报修流程。
 */
@Service
public class FaultService {

    private final FaultReportRepository faultRepository;
    private final ElevatorRepository elevatorRepository;
    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final ElevatorEmergencyLockRepository lockRepository;
    private final FaultOperationLogRepository logRepository;
    private final FilterScope filterScope;

    public FaultService(FaultReportRepository faultRepository,
                        ElevatorRepository elevatorRepository,
                        UnitRepository unitRepository,
                        BuildingRepository buildingRepository,
                        ElevatorEmergencyLockRepository lockRepository,
                        FaultOperationLogRepository logRepository,
                        FilterScope filterScope) {
        this.faultRepository = faultRepository;
        this.elevatorRepository = elevatorRepository;
        this.unitRepository = unitRepository;
        this.buildingRepository = buildingRepository;
        this.lockRepository = lockRepository;
        this.logRepository = logRepository;
        this.filterScope = filterScope;
    }

    @Transactional(readOnly = true)
    public List<Views.FaultView> list(Long buildingId, Long elevatorId) {
        List<FaultReport> reports = filterScope.withFilter(buildingId, faultRepository::findAllByOrderByReportTimeDesc);
        NameContext ctx = new NameContext();
        List<Views.FaultView> list = new ArrayList<>();
        for (FaultReport report : reports) {
            if (elevatorId != null && !elevatorId.equals(report.getElevatorId())) {
                continue;
            }
            list.add(toView(report, ctx));
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
        boolean emergency = EmergencyLinkService.isEmergency(faultType);
        // 同一电梯已有有效应急处置时，允许继续登记（排队），但给出明确提示，不阻断新的报修
        String queueTip = "";
        if (emergency) {
            ElevatorEmergencyLock lock = lockRepository.findByElevatorId(elevator.getId()).orElse(null);
            if (lock != null) {
                queueTip = "；注意：该电梯正被应急报修 " + lock.getFaultNo() + " 占用，"
                        + "本单已登记排队，需待其闭环后才能受理，且不会再生成第二张抢修工单";
            }
        }
        FaultReport report = new FaultReport();
        report.setFaultNo(faultNo);
        report.setElevatorId(elevator.getId());
        report.setUnitId(elevator.getUnitId());
        report.setBuildingId(elevator.getBuildingId());
        report.setFaultType(faultType);
        report.setEmergency(emergency ? 1 : 0);
        report.setRiskLevel(EmergencyLinkService.riskLevel(faultType));
        report.setReportTime(req.getReportTime() == null ? LocalDateTime.now() : req.getReportTime());
        report.setResponseMinutes(responseMinutes);
        report.setArriveMinutes(arriveMinutes);
        report.setTrapped(req.getTrapped() == null
                ? (ElevatorDict.faultTypeTrapped(faultType) ? 1 : 0) : (req.getTrapped() > 0 ? 1 : 0));
        report.setHandleResult(trim(req.getHandleResult()));
        report.setReporter(trim(req.getReporter()));
        report.setStatus(ElevatorDict.F_PENDING);
        report.setStage(EmergencyLinkService.stageName(ElevatorDict.F_PENDING));
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);

        FaultOperationLog log = new FaultOperationLog();
        log.setFaultId(report.getId());
        log.setFaultNo(report.getFaultNo());
        log.setAction("CREATE");
        log.setFromStatus(null);
        log.setToStatus(ElevatorDict.F_PENDING);
        log.setOperator(notBlank(req.getReporter()) ? req.getReporter().trim() : "报修登记");
        log.setDetail((emergency ? "登记应急故障（困人/停梯），进入应急态" : "登记普通故障") + queueTip);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);

        return toView(report, new NameContext());
    }

    /**
     * 普通故障推进：待处理 → 处理中 → 已修复（原有报修流程）。
     * 应急故障不能走这里，必须按 待处理→处理中→待验收→已修复 的应急流程。
     */
    @Transactional
    public Views.FaultView advance(Long id) {
        FaultReport report = faultRepository.findById(id).orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        if (Integer.valueOf(1).equals(report.getEmergency())) {
            throw new BizException("应急报修单 " + report.getFaultNo()
                    + " 必须走「受理 → 处置 → 验收闭环」流程，不能用普通推进");
        }
        switch (report.getStatus()) {
            case ElevatorDict.F_PENDING -> {
                report.setStatus(ElevatorDict.F_PROCESSING);
                report.setStage(EmergencyLinkService.stageName(report));
            }
            case ElevatorDict.F_PROCESSING -> {
                report.setStatus(ElevatorDict.F_FIXED);
                report.setStage(EmergencyLinkService.stageName(report));
                report.setFixTime(LocalDateTime.now());
                if (report.getHandleResult() == null || report.getHandleResult().isBlank()) {
                    report.setHandleResult("现场处理完毕，恢复运行");
                }
            }
            default -> throw new BizException("报修单 " + report.getFaultNo() + " 已闭环，不能再推进");
        }
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        return toView(report, new NameContext());
    }

    /** 普通故障直接修复：填处理结果并闭环 */
    @Transactional
    public Views.FaultView fix(Long id, FaultDtos.FixReq req) {
        FaultReport report = faultRepository.findById(id).orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        if (Integer.valueOf(1).equals(report.getEmergency())) {
            throw new BizException("应急报修单 " + report.getFaultNo()
                    + " 必须在处置界面填写到场信息并经验收后闭环，不能用普通闭环");
        }
        if (ElevatorDict.F_FIXED.equals(report.getStatus())) {
            throw new BizException("报修单 " + report.getFaultNo() + " 已修复，不要重复闭环");
        }
        if (ElevatorDict.F_CANCELLED.equals(report.getStatus())) {
            throw new BizException("报修单 " + report.getFaultNo() + " 已撤销");
        }
        String result = req == null ? null : trim(req.getHandleResult());
        if (result == null || result.isEmpty()) {
            throw new BizException("处理结果不能为空");
        }
        report.setHandleResult(result);
        report.setFixTime(LocalDateTime.now());
        report.setStatus(ElevatorDict.F_FIXED);
        report.setStage(EmergencyLinkService.stageName(report));
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        return toView(report, new NameContext());
    }

    @Transactional(readOnly = true)
    public Views.FaultView getViewById(Long id) {
        FaultReport report = faultRepository.findById(id)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        return toView(report, new NameContext());
    }

    @Transactional(readOnly = true)
    public List<Views.FaultLogView> logs(Long id) {
        FaultReport report = faultRepository.findById(id)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        List<Views.FaultLogView> list = new ArrayList<>();
        for (FaultOperationLog l : logRepository.findAllByFaultIdOrderByCreatedAtAscIdAsc(id)) {
            list.add(new Views.FaultLogView(
                    l.getId(), l.getFaultId(), l.getFaultNo(), l.getAction(),
                    EmergencyLinkService.actionName(l.getAction()),
                    l.getFromStatus(), l.getToStatus(), l.getOperator(), l.getDetail(),
                    l.getOrderId(), l.getOrderNo(), l.getCreatedAt()));
        }
        return list;
    }

    Views.FaultView toView(FaultReport report, NameContext ctx) {
        Integer arrive = report.getArriveMinutes() == null ? 0 : report.getArriveMinutes();
        boolean occupied = report.getOrderId() != null
                && !ElevatorDict.isFaultClosed(report.getStatus())
                && lockRepository.findByFaultId(report.getId()).isPresent();
        return new Views.FaultView(
                report.getId(),
                report.getFaultNo(),
                report.getElevatorId(),
                ctx.elevatorNo(report.getElevatorId()),
                report.getUnitId(),
                ctx.unitNo(report.getUnitId()),
                report.getBuildingId(),
                ctx.buildingName(report.getBuildingId()),
                report.getFaultType(),
                ElevatorDict.faultTypeName(report.getFaultType()),
                Integer.valueOf(1).equals(report.getEmergency()),
                report.getRiskLevel(),
                EmergencyLinkService.riskLevelName(report.getRiskLevel()),
                report.getReportTime(),
                report.getAcceptTime(),
                report.getAssignee(),
                report.getResponseMinutes(),
                report.getArriveMinutes(),
                report.getArriveTime(),
                report.getFixTime(),
                report.getTrapped(),
                report.getHandleResult(),
                report.getCancelReason(),
                report.getReporter(),
                report.getOrderId(),
                report.getOrderNo(),
                report.getConfirmUser(),
                report.getStatus(),
                ElevatorDict.faultStatusName(report.getStatus()),
                report.getStage() == null ? EmergencyLinkService.stageName(report) : report.getStage(),
                occupied,
                arrive > ElevatorDict.ARRIVE_SLA_MINUTES,
                report.getUpdatedAt());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** 名称缓存：一次列表里楼宇/单元/电梯名只查一遍全量表 */
    final class NameContext {

        private Map<Long, String> unitNo;
        private Map<Long, String> buildingName;
        private Map<Long, String> elevatorNo;

        String unitNo(Long id) {
            if (unitNo == null) {
                unitNo = new HashMap<>();
                for (Unit unit : unitRepository.findAll()) {
                    unitNo.put(unit.getId(), unit.getUnitNo());
                }
            }
            return unitNo.getOrDefault(id, "");
        }

        String buildingName(Long id) {
            if (buildingName == null) {
                buildingName = new HashMap<>();
                for (Building building : buildingRepository.findAll()) {
                    buildingName.put(building.getId(), building.getName());
                }
            }
            return buildingName.getOrDefault(id, "");
        }

        String elevatorNo(Long id) {
            if (elevatorNo == null) {
                elevatorNo = new HashMap<>();
                for (Elevator elevator : elevatorRepository.findAll()) {
                    elevatorNo.put(elevator.getId(), elevator.getElevatorNo());
                }
            }
            return elevatorNo.getOrDefault(id, "");
        }
    }
}

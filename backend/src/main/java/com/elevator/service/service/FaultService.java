package com.elevator.service.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elevator.service.common.BizException;
import com.elevator.service.domain.Building;
import com.elevator.service.domain.Elevator;
import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.FaultActionLog;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.MaintenanceOrder;
import com.elevator.service.domain.MaintenancePlan;
import com.elevator.service.domain.Unit;
import com.elevator.service.dto.FaultDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultActionLogRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.MaintenanceOrderRepository;
import com.elevator.service.repository.MaintenancePlanRepository;
import com.elevator.service.repository.UnitRepository;

/**
 * 模块四：故障报修 + 应急联动。
 *
 * <p>两条流程：
 * <ul>
 *   <li><b>普通故障</b>（异响等）：待处理 → 处理中 → 已修复，不占电梯、不建抢修单，沿用原报修流程；</li>
 *   <li><b>应急故障</b>（困人/停梯）：待处理 → 处理中 → 待验收 → 已修复；
 *       受理时由 {@link EmergencyLinkService} 生成/关联抢修工单并占用电梯、暂停计划；
 *       误报必须填撤销原因，走单独的「已撤销」终态。</li>
 * </ul>
 *
 * <p>列表口径：未选楼宇时默认只展示未闭环应急项（处置台）；按楼宇筛选时返回该楼全部报修（含历史闭环/撤销），
 * 同时保留电梯筛选行为。{@code scope=all} 可显式查看全部报修。
 */
@Service
public class FaultService {

    private static final List<String> CLOSED_STATUS = List.of(ElevatorDict.F_FIXED, ElevatorDict.F_CANCELLED);

    private final FaultReportRepository faultRepository;
    private final FaultActionLogRepository logRepository;
    private final ElevatorRepository elevatorRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final MaintenancePlanRepository planRepository;
    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final FilterScope filterScope;
    private final EmergencyLinkService emergencyLink;

    public FaultService(FaultReportRepository faultRepository,
                        FaultActionLogRepository logRepository,
                        ElevatorRepository elevatorRepository,
                        MaintenanceOrderRepository orderRepository,
                        MaintenancePlanRepository planRepository,
                        UnitRepository unitRepository,
                        BuildingRepository buildingRepository,
                        FilterScope filterScope,
                        EmergencyLinkService emergencyLink) {
        this.faultRepository = faultRepository;
        this.logRepository = logRepository;
        this.elevatorRepository = elevatorRepository;
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.unitRepository = unitRepository;
        this.buildingRepository = buildingRepository;
        this.filterScope = filterScope;
        this.emergencyLink = emergencyLink;
    }

    // ---------------- 列表 / 详情 ----------------

    /**
     * @param scope "emergency"（默认，只看未闭环应急项）/ "all"（全部报修，仍受楼宇/电梯过滤）
     */
    @Transactional(readOnly = true)
    public List<Views.FaultView> list(Long buildingId, Long elevatorId, String scope) {
        boolean emergencyOnly = !"all".equalsIgnoreCase(scope);
        List<FaultReport> reports;
        if (buildingId != null && buildingId > 0) {
            // 按楼宇收口：buildingFilter（含历史已修复/已撤销），楼内历史闭环可查
            reports = filterScope.withFilter(buildingId, faultRepository::findAllByOrderByReportTimeDesc);
            if (emergencyOnly) {
                reports = reports.stream().filter(r -> r.getEmergencyFlag() != null && r.getEmergencyFlag() == 1
                        && !ElevatorDict.isFaultClosed(r.getStatus())).toList();
            }
        } else if (emergencyOnly) {
            // 处置台默认口径：全公司未闭环应急项
            reports = faultRepository.findByEmergencyFlagAndStatusNotInOrderByReportTimeDesc(1, CLOSED_STATUS);
        } else {
            reports = filterScope.withFilter(null, faultRepository::findAllByOrderByReportTimeDesc);
        }
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

    /** 故障详情：本体 + 抢修工单 + 被暂停计划 + 操作记录（从故障行直接查看关联工单） */
    @Transactional(readOnly = true)
    public Views.FaultDetailView detail(Long id) {
        FaultReport report = faultRepository.findById(id)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        NameContext ctx = new NameContext();
        Views.OrderView rescue = null;
        if (report.getRescueOrderId() != null) {
            rescue = orderRepository.findById(report.getRescueOrderId())
                    .map(o -> toOrderView(o, ctx)).orElse(null);
        }
        if (rescue == null) {
            rescue = orderRepository.findByFaultId(id).map(o -> toOrderView(o, ctx)).orElse(null);
        }
        List<Views.PlanView> suspended = new ArrayList<>();
        for (MaintenancePlan plan : planRepository.findBySuspendedByFaultId(id)) {
            suspended.add(toPlanView(plan, ctx));
        }
        List<Views.FaultLogView> logs = new ArrayList<>();
        for (FaultActionLog logRow : logRepository.findByFaultIdOrderByIdAsc(id)) {
            logs.add(toLogView(logRow));
        }
        return new Views.FaultDetailView(toView(report, ctx), rescue, suspended, logs);
    }

    // ---------------- 登记 ----------------

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
        Integer responseMinutes = req.getResponseMinutes();
        Integer arriveMinutes = req.getArriveMinutes();
        if (responseMinutes != null && responseMinutes < 0) {
            throw new BizException("响应时长不能为负数");
        }
        if (arriveMinutes != null && arriveMinutes < 0) {
            throw new BizException("到达时长不能为负数");
        }
        if (responseMinutes != null && arriveMinutes != null && arriveMinutes < responseMinutes) {
            throw new BizException("到达时长不能小于响应时长");
        }
        boolean emergency = ElevatorDict.isEmergencyFaultType(faultType);

        FaultReport report = new FaultReport();
        report.setFaultNo(faultNo);
        report.setElevatorId(elevator.getId());
        report.setUnitId(elevator.getUnitId());
        report.setBuildingId(elevator.getBuildingId());
        report.setFaultType(faultType);
        report.setEmergencyFlag(emergency ? 1 : 0);
        report.setRiskLevel(emergency ? ElevatorDict.RISK_HIGH : ElevatorDict.RISK_NORMAL);
        report.setReportTime(req.getReportTime() == null ? LocalDateTime.now() : req.getReportTime());
        report.setResponseMinutes(responseMinutes);
        report.setArriveMinutes(arriveMinutes);
        report.setTrapped(req.getTrapped() == null
                ? (ElevatorDict.faultTypeTrapped(faultType) ? 1 : 0) : (req.getTrapped() > 0 ? 1 : 0));
        report.setHandleResult(trim(req.getHandleResult()));
        report.setReporter(trim(req.getReporter()));
        report.setStatus(ElevatorDict.F_PENDING);
        report.setStage(ElevatorDict.emergencyStageName(ElevatorDict.F_PENDING));
        report.setVersion(0);
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);

        // 第二张应急单允许登记（留痕），但列表/受理都会明确提示该电梯已有应急单在处置
        emergencyLink.log(report, "CREATE", null, ElevatorDict.F_PENDING, trim(req.getReporter()),
                emergency ? (faultTypeName(faultType) + "应急报修登记，等待受理") : "普通故障报修登记");

        return toView(report, new NameContext());
    }

    // ---------------- 普通故障推进（沿用原报修流程） ----------------

    /** 普通故障：待处理 → 处理中 → 已修复；应急故障不允许走这里（必须走受理/到场/验收/闭环） */
    @Transactional
    public Views.FaultView advance(Long id) {
        FaultReport report = requireOpen(id);
        if (isEmergency(report)) {
            throw new BizException("应急报修 " + report.getFaultNo()
                    + " 必须走「受理 → 到场登记 → 送验收 → 闭环」流程，不能用普通推进");
        }
        switch (report.getStatus()) {
            case ElevatorDict.F_PENDING -> {
                report.setStatus(ElevatorDict.F_PROCESSING);
                report.setStage(ElevatorDict.emergencyStageName(ElevatorDict.F_PROCESSING));
                report.setOwner(report.getOwner() == null ? "值班员" : report.getOwner());
                emergencyLink.log(report, "PROCESS", ElevatorDict.F_PENDING, ElevatorDict.F_PROCESSING,
                        report.getOwner(), "普通故障开始处理");
            }
            case ElevatorDict.F_PROCESSING -> {
                report.setStatus(ElevatorDict.F_FIXED);
                report.setStage("已修复");
                report.setFixTime(LocalDateTime.now());
                if (blank(report.getHandleResult())) {
                    report.setHandleResult("现场处理完毕，恢复运行");
                }
                emergencyLink.log(report, "FIX", ElevatorDict.F_PROCESSING, ElevatorDict.F_FIXED,
                        report.getOwner(), "普通故障处理完成并闭环");
            }
            default -> throw new BizException("报修单 " + report.getFaultNo() + " 当前状态不能再推进");
        }
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        return toView(report, new NameContext());
    }

    /** 普通故障直接闭环（填处理结果） */
    @Transactional
    public Views.FaultView fix(Long id, FaultDtos.FixReq req) {
        FaultReport report = requireOpen(id);
        if (isEmergency(report)) {
            throw new BizException("应急报修 " + report.getFaultNo()
                    + " 必须在「待验收」阶段、填写处理结果与实际到场信息并确认电梯恢复运行后才能闭环");
        }
        String result = req == null ? null : trim(req.getHandleResult());
        if (blank(result)) {
            throw new BizException("处理结果不能为空");
        }
        report.setHandleResult(result);
        report.setFixTime(LocalDateTime.now());
        report.setStatus(ElevatorDict.F_FIXED);
        report.setStage("已修复");
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        emergencyLink.log(report, "FIX", ElevatorDict.F_PROCESSING, ElevatorDict.F_FIXED,
                report.getOwner(), "普通故障处理完成并闭环：" + result);
        return toView(report, new NameContext());
    }

    // ---------------- 应急流程：受理 → 到场 → 送验收 → 闭环 / 撤销 ----------------

    /** 受理应急报修：自动生成/关联抢修工单、电梯进入应急维修、暂停在执行计划（并发只成功一个） */
    @Transactional
    public Views.FaultDetailView accept(Long id, FaultDtos.AcceptReq req) {
        FaultReport report = requireEmergency(id);
        if (ElevatorDict.isFaultClosed(report.getStatus())) {
            throw new BizException("应急报修 " + report.getFaultNo() + " 已闭环，不能再受理");
        }
        String owner = req == null ? null : trim(req.getOwner());
        try {
            emergencyLink.accept(report, owner);
            faultRepository.save(report);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException("应急报修 " + report.getFaultNo() + " 刚被其他操作员受理/更新，请刷新列表后重试");
        }
        return detail(id);
    }

    /** 实际到场登记：受理之后才能登记，记录到场时间并自动算到达时长 */
    @Transactional
    public Views.FaultDetailView arrive(Long id, FaultDtos.ArriveReq req) {
        FaultReport report = requireEmergency(id);
        if (ElevatorDict.isFaultClosed(report.getStatus())) {
            throw new BizException("应急报修 " + report.getFaultNo() + " 已闭环，不能再登记到场");
        }
        if (report.getAcceptTime() == null) {
            throw new BizException("报修单 " + report.getFaultNo() + " 尚未受理，请先生成抢修工单并受理");
        }
        LocalDateTime arriveTime = req == null || req.getArriveTime() == null
                ? LocalDateTime.now() : req.getArriveTime();
        if (arriveTime.isBefore(report.getAcceptTime())) {
            throw new BizException("实际到场时间不能早于受理时间（" + report.getAcceptTime() + "）");
        }
        report.setActualArriveTime(arriveTime);
        report.setArriveMinutes((int) Math.max(0, ChronoUnit.MINUTES.between(report.getReportTime(), arriveTime)));
        if (ElevatorDict.F_PENDING.equals(report.getStatus())) {
            report.setStatus(ElevatorDict.F_PROCESSING);
        }
        report.setStage("处理中");
        report.setUpdatedAt(LocalDateTime.now());
        faultRepository.save(report);
        String note = req == null ? "" : trim(req.getNote());
        emergencyLink.log(report, "ARRIVE", report.getStatus(), report.getStatus(), report.getOwner(),
                "实际到场登记：到场用时 " + report.getArriveMinutes() + " 分钟"
                        + (report.getArriveMinutes() > ElevatorDict.ARRIVE_SLA_MINUTES ? "（超过 30 分钟红线）" : "")
                        + (note.isEmpty() ? "" : "；" + note));
        return detail(id);
    }

    /** 抢修完成送验收：处理中 → 待验收（必须先到场并填写处理结果） */
    @Transactional
    public Views.FaultDetailView submitVerify(Long id, String handleResult) {
        FaultReport report = requireEmergency(id);
        if (!ElevatorDict.F_PROCESSING.equals(report.getStatus())) {
            throw new BizException("只有「处理中」的应急报修才能提交验收，" + report.getFaultNo()
                    + " 当前为「" + ElevatorDict.faultStatusName(report.getStatus()) + "」");
        }
        if (report.getActualArriveTime() == null) {
            throw new BizException("尚未登记实际到场信息，不能提交验收");
        }
        String result = trim(handleResult);
        if (blank(result)) {
            throw new BizException("请先填写抢修处理结果，再提交验收");
        }
        report.setHandleResult(result);
        report.setStatus(ElevatorDict.F_WAIT_ACCEPT);
        report.setStage(ElevatorDict.emergencyStageName(ElevatorDict.F_WAIT_ACCEPT));
        report.setUpdatedAt(LocalDateTime.now());

        MaintenanceOrder rescue = currentRescue(report);
        rescue.setExecuteResult(result);
        rescue.setStatus(ElevatorDict.F_WAIT_ACCEPT);
        rescue.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(rescue);
        faultRepository.save(report);

        emergencyLink.log(report, "VERIFY", ElevatorDict.F_PROCESSING, ElevatorDict.F_WAIT_ACCEPT, report.getOwner(),
                "抢修完成提交验收：" + result);
        return detail(id);
    }

    /**
     * 闭环（验收通过）：只能从「待验收」推进；必须有处理结果、实际到场信息，并显式确认电梯恢复运行。
     * 闭环后释放电梯占用、恢复被暂停计划、抢修工单置完成。
     */
    @Transactional
    public Views.FaultDetailView verifyAndFix(Long id, FaultDtos.FixReq req) {
        FaultReport report = requireEmergency(id);
        if (!ElevatorDict.F_WAIT_ACCEPT.equals(report.getStatus())) {
            throw new BizException("应急报修只能在「待验收」阶段闭环，" + report.getFaultNo()
                    + " 当前为「" + ElevatorDict.faultStatusName(report.getStatus()) + "」，请先抢修并提交验收");
        }
        String result = req == null ? null : trim(req.getHandleResult());
        if (blank(result)) {
            throw new BizException("处理结果不能为空，闭环前必须填写抢修处理结果");
        }
        if (report.getActualArriveTime() == null) {
            throw new BizException("缺少实际到场信息，不能闭环");
        }
        if (req.getElevatorRecovered() == null || !req.getElevatorRecovered()) {
            throw new BizException("必须确认电梯已恢复运行后才能闭环");
        }
        report.setHandleResult(result);
        try {
            emergencyLink.closeAsFixed(report, result);
            faultRepository.save(report);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException("应急报修 " + report.getFaultNo() + " 刚被其他操作员闭环，请刷新列表确认");
        }
        emergencyLink.log(report, "FIX", ElevatorDict.F_WAIT_ACCEPT, ElevatorDict.F_FIXED, report.getOwner(),
                "验收通过并闭环：" + result + "；电梯已确认恢复运行，占用解除、暂停计划已恢复");
        faultRepository.save(report);
        return detail(id);
    }

    /** 误报撤销：必须填撤销原因，走独立「已撤销」终态；同样释放占用、恢复计划 */
    @Transactional
    public Views.FaultDetailView cancel(Long id, FaultDtos.CancelReq req) {
        FaultReport report = requireEmergency(id);
        if (ElevatorDict.isFaultClosed(report.getStatus())) {
            throw new BizException("应急报修 " + report.getFaultNo() + " 已闭环，不能再撤销");
        }
        String reason = req == null ? null : trim(req.getCancelReason());
        if (blank(reason)) {
            throw new BizException("判定误报必须填写撤销原因");
        }
        String from = report.getStatus();
        emergencyLink.closeAsCancelled(report, reason);
        faultRepository.save(report);
        emergencyLink.log(report, "CANCEL", from, ElevatorDict.F_CANCELLED, report.getOwner(),
                "误报撤销：" + reason + "；电梯占用解除、暂停计划已恢复");
        faultRepository.save(report);
        return detail(id);
    }

    // ---------------- 内部 ----------------

    private FaultReport requireOpen(Long id) {
        FaultReport report = faultRepository.findById(id)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + id));
        if (ElevatorDict.isFaultClosed(report.getStatus())) {
            throw new BizException("报修单 " + report.getFaultNo()
                    + (ElevatorDict.F_CANCELLED.equals(report.getStatus()) ? " 已撤销" : " 已修复") + "，不能再操作");
        }
        return report;
    }

    private FaultReport requireEmergency(Long id) {
        FaultReport report = requireOpen(id);
        if (!isEmergency(report)) {
            throw new BizException("报修单 " + report.getFaultNo() + " 是普通故障，请使用普通处理流程");
        }
        return report;
    }

    private boolean isEmergency(FaultReport report) {
        return report.getEmergencyFlag() != null && report.getEmergencyFlag() == 1;
    }

    private MaintenanceOrder currentRescue(FaultReport report) {
        MaintenanceOrder rescue = report.getRescueOrderId() == null ? null
                : orderRepository.findById(report.getRescueOrderId()).orElse(null);
        if (rescue == null) {
            rescue = orderRepository.findByFaultId(report.getId()).orElseThrow(() ->
                    new BizException("应急报修 " + report.getFaultNo() + " 尚未关联抢修工单，请先受理"));
        }
        return rescue;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String faultTypeName(String type) {
        return ElevatorDict.faultTypeName(type);
    }

    private Views.FaultView toView(FaultReport report, NameContext ctx) {
        Integer arrive = report.getArriveMinutes();
        Elevator elevator = ctx.elevator(report.getElevatorId());
        String elevatorStatus = elevator == null ? null : elevator.getStatus();
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
                report.getRiskLevel() == null ? ElevatorDict.RISK_NORMAL : report.getRiskLevel(),
                ElevatorDict.riskName(report.getRiskLevel() == null ? ElevatorDict.RISK_NORMAL : report.getRiskLevel()),
                isEmergency(report),
                report.getReportTime(),
                report.getAcceptTime(),
                report.getResponseMinutes(),
                arrive,
                report.getActualArriveTime(),
                report.getFixTime(),
                report.getTrapped(),
                report.getHandleResult(),
                report.getReporter(),
                report.getOwner(),
                report.getRescueOrderId(),
                report.getRescueOrderNo(),
                report.getCancelReason(),
                report.getStage() == null ? ElevatorDict.emergencyStageName(report.getStatus()) : report.getStage(),
                report.getStatus(),
                ElevatorDict.faultStatusName(report.getStatus()),
                ElevatorDict.isFaultClosed(report.getStatus()),
                arrive != null && arrive > ElevatorDict.ARRIVE_SLA_MINUTES,
                elevatorStatus,
                ElevatorDict.elevatorStatusName(elevatorStatus),
                report.getUpdatedAt());
    }

    private Views.OrderView toOrderView(MaintenanceOrder order, NameContext ctx) {
        String planNo = order.getPlanId() == null ? null
                : planRepository.findById(order.getPlanId()).map(MaintenancePlan::getPlanNo).orElse(null);
        String cycleName = order.getCycle() == null ? "应急抢修" : ElevatorDict.cycleName(order.getCycle());
        String typeName = ElevatorDict.ORDER_RESCUE.equals(order.getOrderType()) ? "抢修工单" : "维保工单";
        String statusName = "CANCELLED".equals(order.getStatus()) ? "已撤销"
                : ElevatorDict.F_WAIT_ACCEPT.equals(order.getStatus()) ? "待验收"
                : ElevatorDict.planStatusName(order.getStatus());
        return new Views.OrderView(
                order.getId(),
                order.getOrderNo(),
                order.getPlanId(),
                planNo,
                order.getElevatorId(),
                ctx.elevatorNo(order.getElevatorId()),
                order.getUnitId(),
                ctx.unitNo(order.getUnitId()),
                order.getBuildingId(),
                ctx.buildingName(order.getBuildingId()),
                order.getCycle(),
                cycleName,
                order.getPlanDate(),
                order.getTeam(),
                order.getWorker(),
                order.getExecuteResult(),
                order.getActualDate(),
                order.getStatus(),
                statusName,
                order.getOrderType() == null ? ElevatorDict.ORDER_NORMAL : order.getOrderType(),
                typeName,
                order.getFaultId(),
                order.getFaultNo(),
                order.getRemark(),
                order.getUpdatedAt());
    }

    private Views.PlanView toPlanView(MaintenancePlan plan, NameContext ctx) {
        boolean suspended = ElevatorDict.P_SUSPENDED.equals(plan.getStatus());
        return new Views.PlanView(
                plan.getId(),
                plan.getPlanNo(),
                plan.getElevatorId(),
                ctx.elevatorNo(plan.getElevatorId()),
                plan.getUnitId(),
                ctx.unitNo(plan.getUnitId()),
                plan.getBuildingId(),
                ctx.buildingName(plan.getBuildingId()),
                plan.getCycle(),
                ElevatorDict.cycleName(plan.getCycle()),
                plan.getPlanDate(),
                plan.getPlanDate() == null ? null : plan.getPlanDate().toString().substring(0, 7),
                plan.getTeam(),
                plan.getWorker(),
                plan.getStatus(),
                ElevatorDict.planStatusName(plan.getStatus()),
                plan.getRemark(),
                suspended,
                plan.getSuspendedByFaultNo(),
                plan.getSuspendReason(),
                plan.getStatusBeforeSuspend(),
                plan.getSuspendedAt(),
                plan.getUpdatedAt());
    }

    private Views.FaultLogView toLogView(FaultActionLog logRow) {
        return new Views.FaultLogView(
                logRow.getId(),
                logRow.getFaultId(),
                logRow.getFaultNo(),
                logRow.getAction(),
                actionName(logRow.getAction()),
                logRow.getFromStatus(),
                logRow.getToStatus(),
                logRow.getOperator(),
                logRow.getDetail(),
                logRow.getCreatedAt());
    }

    private String actionName(String action) {
        return switch (action) {
            case "CREATE" -> "登记";
            case "ACCEPT" -> "受理";
            case "PROCESS" -> "开始处理";
            case "ARRIVE" -> "到场登记";
            case "VERIFY" -> "送验收";
            case "FIX" -> "闭环";
            case "CANCEL" -> "误报撤销";
            default -> action;
        };
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** 名称缓存：一次请求内楼宇/单元/电梯只查一遍 */
    private final class NameContext {

        private Map<Long, String> buildingName;
        private Map<Long, String> unitNo;
        private Map<Long, Elevator> elevatorMap;

        private String buildingName(Long id) {
            if (buildingName == null) {
                buildingName = new HashMap<>();
                for (Building building : buildingRepository.findAll()) {
                    buildingName.put(building.getId(), building.getName());
                }
            }
            return buildingName.getOrDefault(id, "");
        }

        private String unitNo(Long id) {
            if (unitNo == null) {
                unitNo = new HashMap<>();
                for (Unit unit : unitRepository.findAll()) {
                    unitNo.put(unit.getId(), unit.getUnitNo());
                }
            }
            return unitNo.getOrDefault(id, "");
        }

        private Elevator elevator(Long id) {
            loadElevators();
            return elevatorMap.get(id);
        }

        private String elevatorNo(Long id) {
            loadElevators();
            Elevator e = elevatorMap.get(id);
            return e == null ? "" : e.getElevatorNo();
        }

        private void loadElevators() {
            if (elevatorMap == null) {
                elevatorMap = new HashMap<>();
                for (Elevator elevator : elevatorRepository.findAll()) {
                    elevatorMap.put(elevator.getId(), elevator);
                }
            }
        }
    }
}

package com.elevator.service.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elevator.service.common.BizException;
import com.elevator.service.domain.Elevator;
import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.ElevatorEmergencyLock;
import com.elevator.service.domain.FaultOperationLog;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.MaintenanceOrder;
import com.elevator.service.domain.MaintenancePlan;
import com.elevator.service.dto.FaultDtos;
import com.elevator.service.repository.ElevatorEmergencyLockRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultOperationLogRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.MaintenanceOrderRepository;
import com.elevator.service.repository.MaintenancePlanRepository;

/**
 * 故障报修应急联动核心：把报修单、电梯台账、抢修工单、被暂停的维保计划串成一个处置闭环。
 *
 * <p>闭环保证：
 * <ul>
 *   <li>受理：在同一事务里「抢锁（elevator_emergency_lock 唯一键）→ 生成抢修工单 →
 *       电梯进入应急维修 → 暂停未完成计划 → 回写关联关系」，并发受理只有一个事务能提交，
 *       失败者整笔回滚，不留半条关联数据，并得到明确提示；</li>
 *   <li>推进：应急故障只能 待处理→处理中→待验收→已修复，误报走独立终态「已撤销」；</li>
 *   <li>闭环：处理结果、实际到场信息齐全且确认电梯恢复运行后才能闭环，
 *       闭环释放占用、电梯恢复原状态、被暂停计划恢复。</li>
 * </ul>
 */
@Service
public class EmergencyLinkService {

    private final FaultReportRepository faultRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final MaintenancePlanRepository planRepository;
    private final ElevatorRepository elevatorRepository;
    private final ElevatorEmergencyLockRepository lockRepository;
    private final FaultOperationLogRepository logRepository;

    public EmergencyLinkService(FaultReportRepository faultRepository,
                                MaintenanceOrderRepository orderRepository,
                                MaintenancePlanRepository planRepository,
                                ElevatorRepository elevatorRepository,
                                ElevatorEmergencyLockRepository lockRepository,
                                FaultOperationLogRepository logRepository) {
        this.faultRepository = faultRepository;
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.elevatorRepository = elevatorRepository;
        this.lockRepository = lockRepository;
        this.logRepository = logRepository;
    }

    private static final List<String> OPEN_PLAN_STATUS =
            List.of(ElevatorDict.P_PENDING, ElevatorDict.P_DELAYED);

    // ---------------- 受理：待处理 → 处理中，生成抢修工单 ----------------

    /**
     * 受理应急报修。并发安全：依赖 elevator_emergency_lock 的唯一键串行化同一电梯的受理。
     */
    @Transactional
    public FaultReport accept(Long faultId, FaultDtos.AcceptReq req) {
        String operator = operatorOf(req == null ? null : req.getOperator());
        // 行锁串行化同一条故障单的并发受理（两个操作员同时点同一条单）
        FaultReport fault = faultRepository.findLockedById(faultId)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + faultId));
        if (!Integer.valueOf(1).equals(fault.getEmergency())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 不是应急故障，无需应急受理");
        }
        if (ElevatorDict.F_CANCELLED.equals(fault.getStatus())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已判定误报撤销，不能再受理");
        }
        if (ElevatorDict.F_FIXED.equals(fault.getStatus())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已闭环，不能重复受理");
        }
        if (fault.getOrderId() != null) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已受理，抢修工单 "
                    + fault.getOrderNo() + " 已存在，不能重复生成");
        }
        Elevator elevator = elevatorRepository.findById(fault.getElevatorId())
                .orElseThrow(() -> new BizException("电梯不存在：id=" + fault.getElevatorId()));
        if (ElevatorDict.ST_OUT.equals(elevator.getStatus())) {
            throw new BizException("电梯 " + elevator.getElevatorNo() + " 已停用，不能派应急抢修单");
        }

        // 唯一键 uk_lock_elevator：同一电梯已有有效应急报修占用时，直接给出明确提示
        ElevatorEmergencyLock existing = lockRepository.findByElevatorId(elevator.getId()).orElse(null);
        if (existing != null) {
            throw new BizException("电梯 " + elevator.getElevatorNo() + " 已被应急报修单 " + existing.getFaultNo()
                    + (existing.getOrderNo() != null ? "（抢修工单 " + existing.getOrderNo() + "）" : "（正在受理中）")
                    + " 占用，同一时间只允许一张有效抢修工单，请等待其闭环后再受理本单");
        }

        // 并发兜底：两个事务同时通过上面的检查时，唯一索引只放行一个 INSERT；
        // 另一个事务在此抛唯一约束冲突并整笔回滚——catch 里不能再碰这个会话（已被异常污染）。
        ElevatorEmergencyLock lock = new ElevatorEmergencyLock();
        lock.setElevatorId(elevator.getId());
        lock.setFaultId(fault.getId());
        lock.setFaultNo(fault.getFaultNo());
        lock.setLockedBy(operator);
        lock.setCreatedAt(LocalDateTime.now());
        try {
            lockRepository.saveAndFlush(lock);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("电梯 " + elevator.getElevatorNo()
                    + " 正被另一张应急报修并发受理占用，同一时间只允许一张有效抢修工单；"
                    + "本次受理已回滚，未生成工单或任何关联数据，请刷新后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        // 生成抢修工单
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(nextEmergencyOrderNo());
        order.setPlanId(null);
        order.setElevatorId(elevator.getId());
        order.setUnitId(elevator.getUnitId());
        order.setBuildingId(elevator.getBuildingId());
        order.setOrderKind(ElevatorDict.KIND_EMERGENCY);
        order.setCycle(null);
        order.setPlanDate(java.time.LocalDate.now());
        order.setTeam(req != null && notBlank(req.getTeam()) ? req.getTeam().trim() : "应急抢修组");
        order.setWorker(req != null && notBlank(req.getWorker()) ? req.getWorker().trim() : null);
        order.setStatus(ElevatorDict.F_PROCESSING);
        order.setFaultId(fault.getId());
        order.setFaultNo(fault.getFaultNo());
        order.setArriveTime(fault.getArriveTime());
        order.setRemark("应急抢修：" + ElevatorDict.faultTypeName(fault.getFaultType()));
        order.setUpdatedAt(now);
        orderRepository.saveAndFlush(order);

        // 回写报修单关联关系 + 受理时间/负责人 + 状态阶段
        fault.setOrderId(order.getId());
        fault.setOrderNo(order.getOrderNo());
        fault.setAcceptTime(now);
        fault.setAssignee(notBlank(req == null ? null : req.getAssignee())
                ? req.getAssignee().trim() : (notBlank(order.getWorker()) ? order.getWorker() : "应急抢修组"));
        fault.setStatus(ElevatorDict.F_PROCESSING);
        fault.setStage(stageName(ElevatorDict.F_PROCESSING));
        fault.setUpdatedAt(now);
        faultRepository.save(fault);

        // 电梯进入应急维修，保留原状态供闭环恢复
        elevator.setPrevStatus(elevator.getStatus());
        elevator.setStatus(ElevatorDict.ST_EMERGENCY);
        elevator.setEmergencyFaultId(fault.getId());
        elevator.setUpdatedAt(now);
        elevatorRepository.save(elevator);

        // 原有未完成计划必须保留并标出被占用原因（状态置 SUSPENDED，记住原状态）
        for (MaintenancePlan plan : planRepository.findByElevatorIdAndStatusInOrderByPlanDateAsc(
                elevator.getId(), OPEN_PLAN_STATUS)) {
            plan.setPrevStatus(plan.getStatus());
            plan.setStatus(ElevatorDict.P_SUSPENDED);
            plan.setOccupiedByFaultId(fault.getId());
            plan.setOccupiedReason("应急抢修 " + fault.getFaultNo() + "（抢修工单 " + order.getOrderNo()
                    + "）占用电梯，计划暂停；闭环后自动恢复");
            plan.setUpdatedAt(now);
            planRepository.save(plan);
        }

        // 补上锁的工单号
        lock.setOrderId(order.getId());
        lock.setOrderNo(order.getOrderNo());
        lockRepository.save(lock);

        log(fault, "ACCEPT", ElevatorDict.F_PENDING, ElevatorDict.F_PROCESSING, operator,
                "受理应急报修，生成抢修工单 " + order.getOrderNo() + "，电梯进入应急维修", order);
        return fault;
    }

    // ---------------- 推进：处理中 → 待验收（必填处理结果与实际到场） ----------------

    @Transactional
    public FaultReport process(Long faultId, FaultDtos.ProcessReq req) {
        FaultReport fault = faultRepository.findLockedById(faultId)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + faultId));
        if (!Integer.valueOf(1).equals(fault.getEmergency())) {
            throw new BizException("普通报修请走原报修流程的推进/闭环操作");
        }
        if (ElevatorDict.F_CANCELLED.equals(fault.getStatus())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已撤销，不能再处置");
        }
        if (!ElevatorDict.F_PROCESSING.equals(fault.getStatus())) {
            throw new BizException("应急报修单 " + fault.getFaultNo() + " 当前为「"
                    + ElevatorDict.faultStatusName(fault.getStatus()) + "」，只有处理中才能填报处置结果");
        }
        if (fault.getOrderId() == null) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 尚未受理，抢修工单未生成");
        }
        String result = req == null ? null : trim(req.getHandleResult());
        if (!notBlank(result)) {
            throw new BizException("处理结果不能为空");
        }
        LocalDateTime arriveTime = req == null ? null : req.getArriveTime();
        if (arriveTime == null) {
            throw new BizException("实际到场时间不能为空");
        }
        if (arriveTime.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new BizException("实际到场时间不能晚于当前时间");
        }
        LocalDateTime baseTime = fault.getAcceptTime() != null ? fault.getAcceptTime() : fault.getReportTime();
        if (arriveTime.isBefore(baseTime.minusMinutes(1))) {
            throw new BizException("实际到场时间不能早于受理/报修时间");
        }
        Integer arriveMinutes = req.getArriveMinutes();
        if (arriveMinutes == null || arriveMinutes < 0) {
            arriveMinutes = (int) Math.max(0, Duration.between(baseTime, arriveTime).toMinutes());
        }

        LocalDateTime now = LocalDateTime.now();
        fault.setHandleResult(result);
        fault.setArriveTime(arriveTime);
        fault.setArriveMinutes(arriveMinutes);
        fault.setStatus(ElevatorDict.F_WAIT_ACCEPT);
        fault.setStage(stageName(ElevatorDict.F_WAIT_ACCEPT));
        fault.setUpdatedAt(now);
        faultRepository.save(fault);

        MaintenanceOrder order = orderRepository.findById(fault.getOrderId())
                .orElseThrow(() -> new BizException("关联抢修工单数据缺失：id=" + fault.getOrderId()));
        order.setExecuteResult(result);
        order.setArriveTime(arriveTime);
        order.setStatus(ElevatorDict.F_WAIT_ACCEPT);
        order.setUpdatedAt(now);
        orderRepository.save(order);

        log(fault, "ADVANCE", ElevatorDict.F_PROCESSING, ElevatorDict.F_WAIT_ACCEPT,
                operatorOf(req == null ? null : req.getOperator()),
                "填报处置结果与实际到场（" + arriveMinutes + " 分钟），提交验收", order);
        return fault;
    }

    // ---------------- 验收闭环：待验收 → 已修复 ----------------

    @Transactional
    public FaultReport close(Long faultId, FaultDtos.CloseReq req) {
        String operator = operatorOf(req == null ? null : req.getOperator());
        FaultReport fault = faultRepository.findLockedById(faultId)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + faultId));
        if (!Integer.valueOf(1).equals(fault.getEmergency())) {
            throw new BizException("普通报修请走原闭环操作");
        }
        if (ElevatorDict.F_FIXED.equals(fault.getStatus())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已闭环，不要重复闭环");
        }
        if (ElevatorDict.F_CANCELLED.equals(fault.getStatus())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已按误报撤销");
        }
        if (!ElevatorDict.F_WAIT_ACCEPT.equals(fault.getStatus())) {
            throw new BizException("应急报修单 " + fault.getFaultNo() + " 当前为「"
                    + ElevatorDict.faultStatusName(fault.getStatus())
                    + "」，须先填报处理结果与实际到场并进入待验收，才能闭环");
        }
        if (!notBlank(fault.getHandleResult())) {
            throw new BizException("处理结果未填写，不能闭环");
        }
        if (fault.getArriveTime() == null) {
            throw new BizException("实际到场信息未填写，不能闭环");
        }
        if (req == null || !Boolean.TRUE.equals(req.getResumed())) {
            throw new BizException("必须勾选确认电梯已恢复运行，才能闭环");
        }

        LocalDateTime now = LocalDateTime.now();
        fault.setStatus(ElevatorDict.F_FIXED);
        fault.setStage(stageName(ElevatorDict.F_FIXED));
        fault.setFixTime(now);
        fault.setConfirmUser(operator);
        if (notBlank(req.getHandleResult())) {
            fault.setHandleResult(trim(req.getHandleResult()));
        }
        fault.setUpdatedAt(now);
        faultRepository.save(fault);

        if (fault.getOrderId() != null) {
            MaintenanceOrder order = orderRepository.findById(fault.getOrderId()).orElse(null);
            if (order != null) {
                order.setStatus(ElevatorDict.F_FIXED);
                order.setActualDate(java.time.LocalDate.now());
                order.setExecuteResult(fault.getHandleResult());
                order.setArriveTime(fault.getArriveTime());
                order.setUpdatedAt(now);
                orderRepository.save(order);
            }
        }

        releaseOccupation(fault, now);

        log(fault, "CLOSE", ElevatorDict.F_WAIT_ACCEPT, ElevatorDict.F_FIXED, operator,
                "验收通过，确认电梯恢复运行，解除占用并恢复被暂停的计划",
                fault.getOrderId() == null ? null
                        : orderRepository.findById(fault.getOrderId()).orElse(null));
        return fault;
    }

    // ---------------- 误报撤销：独立终态 CANCELLED，必须留原因 ----------------

    @Transactional
    public FaultReport cancel(Long faultId, FaultDtos.CancelReq req) {
        String operator = operatorOf(req == null ? null : req.getOperator());
        FaultReport fault = faultRepository.findLockedById(faultId)
                .orElseThrow(() -> new BizException("报修单不存在：id=" + faultId));
        if (!Integer.valueOf(1).equals(fault.getEmergency())) {
            throw new BizException("普通报修不支持应急撤销操作");
        }
        if (ElevatorDict.F_FIXED.equals(fault.getStatus()) || ElevatorDict.F_CANCELLED.equals(fault.getStatus())) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 已闭环（"
                    + ElevatorDict.faultStatusName(fault.getStatus()) + "），不能再撤销");
        }
        String reason = req == null ? null : trim(req.getCancelReason());
        if (!notBlank(reason)) {
            throw new BizException("判定误报必须填写撤销原因");
        }

        String fromStatus = fault.getStatus();
        LocalDateTime now = LocalDateTime.now();
        fault.setStatus(ElevatorDict.F_CANCELLED);
        fault.setStage(stageName(ElevatorDict.F_CANCELLED));
        fault.setCancelReason(reason);
        fault.setConfirmUser(operator);
        fault.setUpdatedAt(now);
        // 误报绝不伪装成已修复：不写 fixTime/已修复结果
        faultRepository.save(fault);

        MaintenanceOrder order = fault.getOrderId() == null ? null
                : orderRepository.findById(fault.getOrderId()).orElse(null);
        if (order != null) {
            order.setStatus(ElevatorDict.F_CANCELLED);
            order.setRemark(trimToLen(("误报撤销：" + reason), 128));
            order.setUpdatedAt(now);
            orderRepository.save(order);
        }

        // 已受理（已占电梯）的误报同样要释放占用、恢复计划；未受理的单没有锁
        if (lockRepository.findByFaultId(fault.getId()).isPresent()) {
            releaseOccupation(fault, now);
        }

        log(fault, "CANCEL", fromStatus, ElevatorDict.F_CANCELLED, operator,
                "判定误报并撤销，原因：" + reason, order);
        return fault;
    }

    // ---------------- 占用释放：电梯恢复原状态 + 暂停计划恢复 ----------------

    private void releaseOccupation(FaultReport fault, LocalDateTime now) {
        Elevator elevator = elevatorRepository.findById(fault.getElevatorId()).orElse(null);
        if (elevator != null && ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus())
                && fault.getId().equals(elevator.getEmergencyFaultId())) {
            String restore = elevator.getPrevStatus();
            elevator.setStatus(restore == null ? ElevatorDict.ST_RUNNING : restore);
            elevator.setPrevStatus(null);
            elevator.setEmergencyFaultId(null);
            elevator.setUpdatedAt(now);
            elevatorRepository.save(elevator);
        }
        // 恢复被本单暂停的计划
        for (MaintenancePlan plan : planRepository.findByOccupiedByFaultIdOrderByIdAsc(fault.getId())) {
            plan.setStatus(plan.getPrevStatus() == null ? ElevatorDict.P_PENDING : plan.getPrevStatus());
            plan.setPrevStatus(null);
            plan.setOccupiedByFaultId(null);
            plan.setOccupiedReason(null);
            plan.setUpdatedAt(now);
            planRepository.save(plan);
        }
        lockRepository.findByFaultId(fault.getId()).ifPresent(lockRepository::delete);
    }

    // ---------------- 工具 ----------------

    /** 登记阶段的应急判定（困人/停梯进入应急态） */
    public static boolean isEmergency(String faultType) {
        return ElevatorDict.isEmergencyFaultType(faultType);
    }

    public static String riskLevel(String faultType) {
        return isEmergency(faultType) ? "HIGH" : "NORMAL";
    }

    public static String riskLevelName(String level) {
        return "HIGH".equals(level) ? "高风险" : "普通";
    }

    public static String stageName(String status) {
        return switch (status) {
            case ElevatorDict.F_PENDING -> "待受理";
            case ElevatorDict.F_PROCESSING -> "抢修中";
            case ElevatorDict.F_WAIT_ACCEPT -> "待验收";
            case ElevatorDict.F_FIXED -> "已修复";
            case ElevatorDict.F_CANCELLED -> "已撤销";
            default -> status;
        };
    }

    /** 处置阶段名（普通故障单的 PROCESSING 语义为处理中而非抢修中） */
    public static String stageName(FaultReport fault) {
        if (Integer.valueOf(1).equals(fault.getEmergency())) {
            return stageName(fault.getStatus());
        }
        return switch (fault.getStatus()) {
            case ElevatorDict.F_PENDING -> "待处理";
            case ElevatorDict.F_PROCESSING -> "处理中";
            case ElevatorDict.F_FIXED -> "已修复";
            case ElevatorDict.F_CANCELLED -> "已撤销";
            case ElevatorDict.F_WAIT_ACCEPT -> "待验收";
            default -> fault.getStatus();
        };
    }

    public static String actionName(String action) {
        return switch (action) {
            case "CREATE" -> "登记报修";
            case "ACCEPT" -> "受理/生成抢修单";
            case "ADVANCE" -> "处置推进";
            case "CANCEL" -> "误报撤销";
            case "CLOSE" -> "验收闭环";
            case "REJECT" -> "并发拦截";
            default -> action;
        };
    }

    private String nextEmergencyOrderNo() {
        String no;
        do {
            long count = orderRepository.count() + 1;
            no = "EX-" + java.time.LocalDate.now().getYear() + "-" + String.format("%04d", count);
        } while (orderRepository.existsByOrderNo(no));
        return no;
    }

    private void log(FaultReport fault, String action, String from, String to,
                     String operator, String detail, MaintenanceOrder order) {
        FaultOperationLog l = new FaultOperationLog();
        l.setFaultId(fault.getId());
        l.setFaultNo(fault.getFaultNo());
        l.setAction(action);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setOperator(operator);
        l.setDetail(trimToLen(detail, 255));
        l.setOrderId(order == null ? fault.getOrderId() : order.getId());
        l.setOrderNo(order == null ? fault.getOrderNo() : order.getOrderNo());
        l.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        logRepository.save(l);
    }

    private static String operatorOf(String operator) {
        return notBlank(operator) ? operator.trim() : "值班员";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToLen(String value, int len) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.length() <= len ? v : v.substring(0, len);
    }
}

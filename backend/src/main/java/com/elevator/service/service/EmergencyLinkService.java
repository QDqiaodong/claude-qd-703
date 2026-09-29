package com.elevator.service.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elevator.service.common.BizException;
import com.elevator.service.domain.Elevator;
import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.FaultActionLog;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.MaintenanceOrder;
import com.elevator.service.domain.MaintenancePlan;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultActionLogRepository;
import com.elevator.service.repository.MaintenanceOrderRepository;
import com.elevator.service.repository.MaintenancePlanRepository;

/**
 * 应急联动核心：把「报修单 ↔ 抢修工单 ↔ 电梯台账 ↔ 在执行计划」串成一个处置闭环。
 *
 * <p>并发口径：
 * <ul>
 *   <li>受理时先对电梯行加悲观写锁（{@code findByIdForUpdate}），串行化同电梯的受理；</li>
 *   <li>电梯 {@code emergency_lock} 唯一约束 + 抢修工单 {@code fault_id} 唯一约束兜底，
 *       即使应用层判断失效，数据库也保证「一台未闭环应急单 = 一张抢修单 = 一次占用」；</li>
 *   <li>报修单 {@code @Version} 乐观锁保证两个操作员同时受理/闭环只有一个提交成功；</li>
 *   <li>冲突时整个事务回滚，不允许留下「工单已建、电梯没占用」之类的半条关联；
 *       若历史数据真的处于半挂状态（如种子 FR-2026-0008），再次受理走自愈分支补齐。</li>
 * </ul>
 */
@Service
public class EmergencyLinkService {

    private static final List<String> CLOSED_ORDER_STATUS = List.of(ElevatorDict.P_DONE, ElevatorDict.P_VOID, "CANCELLED");

    private final ElevatorRepository elevatorRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final MaintenancePlanRepository planRepository;
    private final FaultActionLogRepository logRepository;

    public EmergencyLinkService(ElevatorRepository elevatorRepository,
                                MaintenanceOrderRepository orderRepository,
                                MaintenancePlanRepository planRepository,
                                FaultActionLogRepository logRepository) {
        this.elevatorRepository = elevatorRepository;
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.logRepository = logRepository;
    }

    /** 记录一条操作轨迹（在调用方事务内，随主数据一起提交/回滚） */
    public void log(FaultReport fault, String action, String fromStatus, String toStatus,
                    String operator, String detail) {
        FaultActionLog row = new FaultActionLog();
        row.setFaultId(fault.getId());
        row.setFaultNo(fault.getFaultNo());
        row.setAction(action);
        row.setFromStatus(fromStatus);
        row.setToStatus(toStatus);
        row.setOperator(operator);
        row.setDetail(detail);
        row.setCreatedAt(LocalDateTime.now());
        logRepository.save(row);
    }

    /**
     * 受理应急报修：加锁电梯 → 占用并转 EMERGENCY → 生成/挂接抢修工单 → 暂停在执行计划。
     * 幂等 + 自愈：
     *   - 已关联抢修单且电梯已占用：说明重复受理，直接报业务错误（明确提示）；
     *   - 有关联单但电梯没占用（异常失败遗留）：补齐电梯占用与计划暂停，不重复建工单。
     */
    @Transactional
    public MaintenanceOrder accept(FaultReport fault, String owner) {
        if (fault.getEmergencyFlag() == null || fault.getEmergencyFlag() != 1) {
            throw new BizException("报修单 " + fault.getFaultNo() + " 不是应急报修，不能走应急受理");
        }
        // 行锁串行化同电梯的并发受理
        Elevator elevator = elevatorRepository.findByIdForUpdate(fault.getElevatorId())
                .orElseThrow(() -> new BizException("电梯不存在：id=" + fault.getElevatorId()));

        MaintenanceOrder rescue;
        boolean healOnly = false;
        Optional<MaintenanceOrder> existing = fault.getRescueOrderId() == null
                ? orderRepository.findByFaultId(fault.getId())
                : orderRepository.findById(fault.getRescueOrderId());
        if (existing.isPresent()) {
            rescue = existing.get();
            if (!ElevatorDict.ORDER_RESCUE.equals(rescue.getOrderType()) || !fault.getId().equals(rescue.getFaultId())) {
                throw new BizException("关联工单 " + rescue.getOrderNo() + " 不是本应急单的抢修工单，数据异常，请联系管理员");
            }
            if (elevator.getEmergencyLock() != null && fault.getId().equals(elevator.getEmergencyLock())) {
                throw new BizException("应急报修 " + fault.getFaultNo() + " 已被受理（抢修工单 " + rescue.getOrderNo()
                        + "），当前负责人 " + fault.getOwner() + "，请勿重复受理");
            }
            // 半挂数据自愈：工单在，电梯占用/计划暂停没落地
            healOnly = true;
        } else {
            if (elevator.getEmergencyLock() != null && !fault.getId().equals(elevator.getEmergencyLock())) {
                throw occupiedMessage(elevator);
            }
            rescue = createRescueOrder(fault, elevator, owner);
        }

        occupyElevator(elevator, fault);
        suspendPlans(fault, elevator);

        // 受理时间 / 负责人 / 响应时长 / 关联单号（自愈时同样补齐）
        LocalDateTime now = LocalDateTime.now();
        boolean firstAccept = fault.getAcceptTime() == null;
        if (firstAccept) {
            fault.setAcceptTime(now);
            fault.setResponseMinutes(minutesBetween(fault.getReportTime(), now));
        }
        if (owner != null && !owner.isBlank()) {
            fault.setOwner(owner.trim());
        } else if (fault.getOwner() == null || fault.getOwner().isBlank()) {
            fault.setOwner(rescue.getWorker() != null ? rescue.getWorker() : "应急值班员");
        }
        fault.setRescueOrderId(rescue.getId());
        fault.setRescueOrderNo(rescue.getOrderNo());
        fault.setStatus(ElevatorDict.F_PROCESSING);
        fault.setStage("处理中");
        fault.setUpdatedAt(now);

        if (healOnly) {
            log(fault, "ACCEPT", ElevatorDict.F_PROCESSING, ElevatorDict.F_PROCESSING, fault.getOwner(),
                    "检测到抢修工单 " + rescue.getOrderNo() + " 已存在但电梯占用未落地，已自动补齐关联并恢复处置");
        } else {
            log(fault, "ACCEPT", ElevatorDict.F_PENDING, ElevatorDict.F_PROCESSING, fault.getOwner(),
                    "受理并生成抢修工单 " + rescue.getOrderNo() + "，电梯 " + elevator.getElevatorNo()
                            + " 进入应急维修，暂停在执行维保计划");
        }
        return rescue;
    }

    private MaintenanceOrder createRescueOrder(FaultReport fault, Elevator elevator, String owner) {
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(rescueOrderNo(fault));
        order.setPlanId(null);
        order.setElevatorId(elevator.getId());
        order.setUnitId(elevator.getUnitId());
        order.setBuildingId(elevator.getBuildingId());
        order.setCycle(null);
        order.setPlanDate(java.time.LocalDate.now());
        order.setTeam("应急抢修组");
        order.setWorker(owner == null || owner.isBlank() ? "应急值班员" : owner.trim());
        order.setExecuteResult(null);
        order.setActualDate(null);
        order.setStatus(ElevatorDict.F_PROCESSING);
        order.setOrderType(ElevatorDict.ORDER_RESCUE);
        order.setFaultId(fault.getId());
        order.setFaultNo(fault.getFaultNo());
        order.setRemark(fault.getFaultNo() + " 应急抢修工单");
        order.setUpdatedAt(LocalDateTime.now());
        try {
            return orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // uk_order_fault 冲突：并发下另一事务已建单，本事务整体回滚
            throw new BizException("应急报修 " + fault.getFaultNo() + " 的抢修工单已由其他操作员生成，请勿重复受理");
        }
    }

    /** 抢修工单号 ER-{年}-{报修单流水}，由报修单确定性派生，并发两次也只会得到同一个号 */
    private String rescueOrderNo(FaultReport fault) {
        String year = String.valueOf(java.time.LocalDate.now().getYear());
        String digits = fault.getFaultNo().replaceAll("\\D", "");
        String seq = digits.length() > 4 ? digits.substring(digits.length() - 4) : String.format("%04d", fault.getId());
        return "ER-" + year + "-" + seq;
    }

    private void occupyElevator(Elevator elevator, FaultReport fault) {
        if (elevator.getEmergencyLock() != null && fault.getId().equals(elevator.getEmergencyLock())
                && ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus())) {
            return;
        }
        if (!ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus())) {
            elevator.setStatusBeforeEmergency(elevator.getStatus());
        }
        elevator.setEmergencyLock(fault.getId());
        elevator.setStatus(ElevatorDict.ST_EMERGENCY);
        elevator.setUpdatedAt(LocalDateTime.now());
        try {
            elevatorRepository.saveAndFlush(elevator);
        } catch (DataIntegrityViolationException e) {
            // uk_elevator_emergency_lock：同一台电梯被另一张未闭环应急单占用
            throw occupiedMessage(elevator);
        }
    }

    private BizException occupiedMessage(Elevator elevator) {
        return new BizException("电梯 " + elevator.getElevatorNo() + " 已有未闭环应急报修在处置（占用单号 id="
                + elevator.getEmergencyLock() + "），一张应急单只允许生成一张抢修工单，请先处理完当前应急单");
    }

    /** 暂停该电梯在执行（待执行/已延期）的维保计划；已暂停的不重复处理 */
    private void suspendPlans(FaultReport fault, Elevator elevator) {
        for (MaintenancePlan plan : planRepository.findAll()) {
            if (!plan.getElevatorId().equals(elevator.getId())) {
                continue;
            }
            if (ElevatorDict.P_SUSPENDED.equals(plan.getStatus())) {
                continue;
            }
            if (ElevatorDict.P_PENDING.equals(plan.getStatus()) || ElevatorDict.P_DELAYED.equals(plan.getStatus())) {
                plan.setStatusBeforeSuspend(plan.getStatus());
                plan.setStatus(ElevatorDict.P_SUSPENDED);
                plan.setSuspendedByFaultId(fault.getId());
                plan.setSuspendedByFaultNo(fault.getFaultNo());
                plan.setSuspendReason("电梯 " + elevator.getElevatorNo() + " 应急抢修中（" + fault.getFaultNo() + "），暂停排程");
                plan.setSuspendedAt(LocalDateTime.now());
                plan.setUpdatedAt(LocalDateTime.now());
                planRepository.save(plan);
            }
        }
    }

    /**
     * 闭环（已修复）：释放电梯占用并恢复原状态，抢修工单完成，恢复被暂停的计划。
     * 必须在报修单已校验「处理结果 + 实际到场 + 确认恢复运行」后调用。
     */
    @Transactional
    public void closeAsFixed(FaultReport fault, String handleResult) {
        Elevator elevator = elevatorRepository.findById(fault.getElevatorId())
                .orElseThrow(() -> new BizException("电梯不存在：id=" + fault.getElevatorId()));
        LocalDateTime now = LocalDateTime.now();

        MaintenanceOrder rescue = orderRepository.findByFaultId(fault.getId()).orElse(null);
        if (rescue == null && fault.getRescueOrderId() != null) {
            rescue = orderRepository.findById(fault.getRescueOrderId()).orElse(null);
        }
        if (rescue == null) {
            throw new BizException("应急报修 " + fault.getFaultNo() + " 缺少关联抢修工单，关联数据不完整，不能闭环");
        }
        rescue.setExecuteResult(handleResult);
        rescue.setActualDate(java.time.LocalDate.now());
        rescue.setStatus(ElevatorDict.P_DONE);
        rescue.setRemark("应急抢修完成，电梯恢复运行");
        rescue.setUpdatedAt(now);
        orderRepository.save(rescue);

        releaseElevator(elevator, fault);
        resumePlans(fault);

        fault.setRescueOrderId(rescue.getId());
        fault.setRescueOrderNo(rescue.getOrderNo());
        fault.setStatus(ElevatorDict.F_FIXED);
        fault.setStage("已修复");
        fault.setFixTime(now);
        fault.setUpdatedAt(now);
    }

    /**
     * 误报撤销：独立终态。释放电梯占用、恢复计划；抢修工单标记 CANCELLED（不是 DONE，不能伪装成已修复）。
     */
    @Transactional
    public void closeAsCancelled(FaultReport fault, String cancelReason) {
        Elevator elevator = elevatorRepository.findById(fault.getElevatorId()).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        MaintenanceOrder rescue = orderRepository.findByFaultId(fault.getId()).orElse(null);
        if (rescue == null && fault.getRescueOrderId() != null) {
            rescue = orderRepository.findById(fault.getRescueOrderId()).orElse(null);
        }
        if (rescue != null) {
            rescue.setStatus("CANCELLED");
            rescue.setRemark("误报撤销：" + cancelReason);
            rescue.setUpdatedAt(now);
            orderRepository.save(rescue);
        }
        if (elevator != null) {
            releaseElevator(elevator, fault);
            resumePlans(fault);
        }
        fault.setCancelReason(cancelReason);
        fault.setStatus(ElevatorDict.F_CANCELLED);
        fault.setStage("已撤销（误报）");
        fault.setFixTime(now);
        fault.setUpdatedAt(now);
    }

    private void releaseElevator(Elevator elevator, FaultReport fault) {
        if (fault.getId().equals(elevator.getEmergencyLock())) {
            String before = elevator.getStatusBeforeEmergency();
            elevator.setEmergencyLock(null);
            elevator.setStatusBeforeEmergency(null);
            elevator.setStatus(before == null || before.isBlank() ? ElevatorDict.ST_RUNNING : before);
            elevator.setUpdatedAt(LocalDateTime.now());
            elevatorRepository.save(elevator);
        }
    }

    private void resumePlans(FaultReport fault) {
        for (MaintenancePlan plan : planRepository.findBySuspendedByFaultId(fault.getId())) {
            String before = plan.getStatusBeforeSuspend();
            plan.setStatus((before == null || before.isBlank()) ? ElevatorDict.P_PENDING : before);
            plan.setStatusBeforeSuspend(null);
            plan.setSuspendedByFaultId(null);
            plan.setSuspendedByFaultNo(null);
            String reason = plan.getSuspendReason();
            plan.setSuspendReason(null);
            plan.setSuspendedAt(null);
            plan.setRemark(appendResumeNote(plan.getRemark(), reason, fault.getFaultNo()));
            plan.setUpdatedAt(LocalDateTime.now());
            planRepository.save(plan);
        }
    }

    private String appendResumeNote(String remark, String reason, String faultNo) {
        String note = "应急单 " + faultNo + " 已闭环，计划恢复";
        String base = remark == null ? "" : remark.trim();
        if (base.isEmpty()) {
            return note;
        }
        return base.contains("应急暂停") ? base.replaceFirst("（应急暂停）", "") + "；" + note : base + "；" + note;
    }

    /** 乐观锁/唯一约束冲突 → 转成明确中文提示（@Transactional 方法内捕获后重抛会被全局处理器处理） */
    public static boolean isConcurrencyFailure(RuntimeException e) {
        return e instanceof ObjectOptimisticLockingFailureException
                || e instanceof DataIntegrityViolationException;
    }

    private Integer minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null) {
            return null;
        }
        long minutes = ChronoUnit.MINUTES.between(from, to);
        return (int) Math.max(0, minutes);
    }
}

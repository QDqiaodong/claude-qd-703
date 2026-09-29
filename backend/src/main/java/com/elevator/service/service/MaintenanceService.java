package com.elevator.service.service;

import java.time.LocalDate;
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
import com.elevator.service.domain.MaintenanceOrder;
import com.elevator.service.domain.MaintenancePlan;
import com.elevator.service.domain.Unit;
import com.elevator.service.dto.MaintenanceDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.MaintenanceOrderRepository;
import com.elevator.service.repository.MaintenancePlanRepository;
import com.elevator.service.repository.UnitRepository;

/**
 * 模块三：维保计划与工单。
 *
 * <p>计划按周期排程（甘特条数据源），工单是执行结果。
 * 两个列表都经 {@link FilterScope}：不传楼宇时 {@code statusFilter} 过滤掉作废单，
 * 传入楼宇时 {@code buildingFilter} 只留这栋楼的全部单据（含作废，便于审计）。
 */
@Service
public class MaintenanceService {

    private final MaintenancePlanRepository planRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final ElevatorRepository elevatorRepository;
    private final UnitRepository unitRepository;
    private final BuildingRepository buildingRepository;
    private final FilterScope filterScope;

    public MaintenanceService(MaintenancePlanRepository planRepository,
                              MaintenanceOrderRepository orderRepository,
                              ElevatorRepository elevatorRepository,
                              UnitRepository unitRepository,
                              BuildingRepository buildingRepository,
                              FilterScope filterScope) {
        this.planRepository = planRepository;
        this.orderRepository = orderRepository;
        this.elevatorRepository = elevatorRepository;
        this.unitRepository = unitRepository;
        this.buildingRepository = buildingRepository;
        this.filterScope = filterScope;
    }

    // ---------------- 计划 ----------------

    @Transactional(readOnly = true)
    public List<Views.PlanView> listPlans(Long buildingId, Long elevatorId) {
        List<MaintenancePlan> plans = filterScope.withFilter(buildingId, planRepository::findAllByOrderByPlanDateDesc);
        NameContext ctx = new NameContext();
        List<Views.PlanView> list = new ArrayList<>();
        for (MaintenancePlan plan : plans) {
            if (elevatorId != null && !elevatorId.equals(plan.getElevatorId())) {
                continue;
            }
            list.add(toPlanView(plan, ctx));
        }
        return list;
    }

    @Transactional
    public Views.PlanView createPlan(MaintenanceDtos.PlanSaveReq req) {
        String planNo = trim(req.getPlanNo());
        if (planRepository.existsByPlanNo(planNo)) {
            throw new BizException("计划编号 " + planNo + " 已存在，不能重复排程");
        }
        Elevator elevator = requireElevator(req.getElevatorId());
        checkElevatorUsable(elevator);
        String cycle = requireCycle(req.getCycle());
        LocalDate planDate = req.getPlanDate();
        if (planDate == null) {
            throw new BizException("计划执行日不能为空");
        }
        MaintenancePlan plan = new MaintenancePlan();
        plan.setPlanNo(planNo);
        plan.setElevatorId(elevator.getId());
        plan.setUnitId(elevator.getUnitId());
        plan.setBuildingId(elevator.getBuildingId());
        plan.setCycle(cycle);
        plan.setPlanDate(planDate);
        plan.setTeam(trim(req.getTeam()));
        plan.setWorker(trim(req.getWorker()));
        plan.setStatus(ElevatorDict.P_PENDING);
        plan.setRemark(trim(req.getRemark()));
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        return toPlanView(plan, new NameContext());
    }

    /** 执行计划：计划置已执行，并生成一张已执行工单 */
    @Transactional
    public Views.OrderView executePlan(Long id, MaintenanceDtos.ExecuteReq req) {
        MaintenancePlan plan = planRepository.findById(id).orElseThrow(() -> new BizException("维保计划不存在：id=" + id));
        if (ElevatorDict.P_VOID.equals(plan.getStatus())) {
            throw new BizException("计划 " + plan.getPlanNo() + " 已作废，不能再执行");
        }
        if (ElevatorDict.P_SUSPENDED.equals(plan.getStatus())) {
            throw new BizException("计划 " + plan.getPlanNo() + " 已被应急处置暂停（" + plan.getOccupiedReason()
                    + "），需等应急闭环自动恢复后再执行");
        }
        if (ElevatorDict.P_DONE.equals(plan.getStatus())) {
            throw new BizException("计划 " + plan.getPlanNo() + " 已执行，不要重复执行");
        }
        if (ElevatorDict.P_DELAYED.equals(plan.getStatus()) && plan.getPlanDate() != null
                && plan.getPlanDate().isAfter(LocalDate.now())) {
            throw new BizException("计划已延期到 " + plan.getPlanDate() + "，未到执行日");
        }
        elevatorRepository.findById(plan.getElevatorId()).ifPresent(this::checkElevatorUsable);
        String result = req == null ? null : trim(req.getExecuteResult());
        if (result == null || result.isEmpty()) {
            throw new BizException("执行结果不能为空");
        }
        plan.setStatus(ElevatorDict.P_DONE);
        plan.setUpdatedAt(LocalDateTime.now());
        if (req.getWorker() != null && !req.getWorker().isBlank()) {
            plan.setWorker(trim(req.getWorker()));
        }
        planRepository.save(plan);

        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(nextOrderNo());
        order.setPlanId(plan.getId());
        order.setElevatorId(plan.getElevatorId());
        order.setUnitId(plan.getUnitId());
        order.setBuildingId(plan.getBuildingId());
        order.setOrderKind(ElevatorDict.KIND_NORMAL);
        order.setCycle(plan.getCycle());
        order.setPlanDate(plan.getPlanDate());
        order.setTeam(plan.getTeam());
        order.setWorker(plan.getWorker());
        order.setExecuteResult(result);
        order.setActualDate(req.getActualDate() == null ? LocalDate.now() : req.getActualDate());
        order.setStatus(ElevatorDict.P_DONE);
        order.setRemark(plan.getRemark());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return toOrderView(order, new NameContext());
    }

    @Transactional
    public Views.PlanView delayPlan(Long id, MaintenanceDtos.DelayReq req) {
        MaintenancePlan plan = planRepository.findById(id).orElseThrow(() -> new BizException("维保计划不存在：id=" + id));
        if (ElevatorDict.P_VOID.equals(plan.getStatus())) {
            throw new BizException("已作废的计划不能再延期");
        }
        if (ElevatorDict.P_SUSPENDED.equals(plan.getStatus())) {
            throw new BizException("计划 " + plan.getPlanNo() + " 已被应急处置暂停，不能延期，闭环后自动恢复");
        }
        if (ElevatorDict.P_DONE.equals(plan.getStatus())) {
            throw new BizException("已执行的计划不能再延期");
        }
        int days = req == null || req.getDays() == null ? 15 : req.getDays();
        if (days <= 0) {
            throw new BizException("顺延天数必须大于 0");
        }
        LocalDate base = plan.getPlanDate() == null ? LocalDate.now() : plan.getPlanDate();
        plan.setPlanDate(base.plusDays(days));
        plan.setStatus(ElevatorDict.P_DELAYED);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        return toPlanView(plan, new NameContext());
    }

    @Transactional
    public Views.PlanView voidPlan(Long id) {
        MaintenancePlan plan = planRepository.findById(id).orElseThrow(() -> new BizException("维保计划不存在：id=" + id));
        if (ElevatorDict.P_DONE.equals(plan.getStatus())) {
            throw new BizException("已执行的计划不能作废");
        }
        plan.setStatus(ElevatorDict.P_VOID);
        plan.setUpdatedAt(LocalDateTime.now());
        planRepository.save(plan);
        return toPlanView(plan, new NameContext());
    }

    // ---------------- 工单 ----------------

    @Transactional(readOnly = true)
    public List<Views.OrderView> listOrders(Long buildingId, Long elevatorId) {
        List<MaintenanceOrder> orders = filterScope.withFilter(buildingId,
                orderRepository::findAllByOrderByPlanDateDescIdDesc);
        NameContext ctx = new NameContext();
        List<Views.OrderView> list = new ArrayList<>();
        for (MaintenanceOrder order : orders) {
            if (elevatorId != null && !elevatorId.equals(order.getElevatorId())) {
                continue;
            }
            list.add(toOrderView(order, ctx));
        }
        return list;
    }

    @Transactional
    public Views.OrderView createOrder(MaintenanceDtos.OrderSaveReq req) {
        String orderNo = trim(req.getOrderNo());
        if (orderRepository.existsByOrderNo(orderNo)) {
            throw new BizException("工单号 " + orderNo + " 已存在，不能重复开单");
        }
        Elevator elevator = requireElevator(req.getElevatorId());
        checkElevatorUsable(elevator);
        String cycle = requireCycle(req.getCycle());
        MaintenanceOrder order = new MaintenanceOrder();
        order.setOrderNo(orderNo);
        order.setPlanId(req.getPlanId());
        order.setElevatorId(elevator.getId());
        order.setUnitId(elevator.getUnitId());
        order.setBuildingId(elevator.getBuildingId());
        order.setOrderKind(ElevatorDict.KIND_NORMAL);
        order.setCycle(cycle);
        order.setPlanDate(req.getPlanDate() == null ? LocalDate.now() : req.getPlanDate());
        order.setTeam(trim(req.getTeam()));
        order.setWorker(trim(req.getWorker()));
        order.setStatus(ElevatorDict.P_PENDING);
        order.setRemark(trim(req.getRemark()));
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return toOrderView(order, new NameContext());
    }

    @Transactional
    public Views.OrderView executeOrder(Long id, MaintenanceDtos.ExecuteReq req) {
        MaintenanceOrder order = orderRepository.findById(id).orElseThrow(() -> new BizException("维保工单不存在：id=" + id));
        if (ElevatorDict.KIND_EMERGENCY.equals(order.getOrderKind())) {
            throw new BizException("抢修工单 " + order.getOrderNo() + " 由应急报修 " + order.getFaultNo()
                    + " 联动驱动，请到故障处置界面推进，不能按普通工单执行");
        }
        if (ElevatorDict.P_VOID.equals(order.getStatus())) {
            throw new BizException("工单 " + order.getOrderNo() + " 已作废，不能再执行");
        }
        if (ElevatorDict.P_DONE.equals(order.getStatus())) {
            throw new BizException("工单 " + order.getOrderNo() + " 已执行，不要重复执行");
        }
        elevatorRepository.findById(order.getElevatorId()).ifPresent(this::checkElevatorUsable);
        String result = req == null ? null : trim(req.getExecuteResult());
        if (result == null || result.isEmpty()) {
            throw new BizException("执行结果不能为空");
        }
        order.setExecuteResult(result);
        order.setActualDate(req.getActualDate() == null ? LocalDate.now() : req.getActualDate());
        order.setStatus(ElevatorDict.P_DONE);
        if (req.getWorker() != null && !req.getWorker().isBlank()) {
            order.setWorker(trim(req.getWorker()));
        }
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return toOrderView(order, new NameContext());
    }

    @Transactional
    public Views.OrderView delayOrder(Long id, MaintenanceDtos.DelayReq req) {
        MaintenanceOrder order = orderRepository.findById(id).orElseThrow(() -> new BizException("维保工单不存在：id=" + id));
        if (ElevatorDict.KIND_EMERGENCY.equals(order.getOrderKind())) {
            throw new BizException("抢修工单 " + order.getOrderNo() + " 不能延期，它随应急报修 " + order.getFaultNo() + " 闭环");
        }
        if (ElevatorDict.P_VOID.equals(order.getStatus())) {
            throw new BizException("已作废的工单不能再延期");
        }
        if (ElevatorDict.P_DONE.equals(order.getStatus())) {
            throw new BizException("已执行的工单不能再延期");
        }
        int days = req == null || req.getDays() == null ? 15 : req.getDays();
        if (days <= 0) {
            throw new BizException("顺延天数必须大于 0");
        }
        LocalDate base = order.getPlanDate() == null ? LocalDate.now() : order.getPlanDate();
        order.setPlanDate(base.plusDays(days));
        order.setStatus(ElevatorDict.P_DELAYED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return toOrderView(order, new NameContext());
    }

    @Transactional
    public Views.OrderView voidOrder(Long id) {
        MaintenanceOrder order = orderRepository.findById(id).orElseThrow(() -> new BizException("维保工单不存在：id=" + id));
        if (ElevatorDict.KIND_EMERGENCY.equals(order.getOrderKind())
                && !ElevatorDict.isFaultTerminal(order.getStatus())) {
            throw new BizException("抢修工单 " + order.getOrderNo() + " 正在处置中，不能删除/作废，"
                    + "需在故障处置界面闭环，或判定误报后随报修单撤销");
        }
        if (ElevatorDict.P_DONE.equals(order.getStatus())) {
            throw new BizException("已执行的工单不能作废");
        }
        order.setStatus(ElevatorDict.P_VOID);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        return toOrderView(order, new NameContext());
    }

    // ---------------- 内部 ----------------

    private Elevator requireElevator(Long elevatorId) {
        if (elevatorId == null) {
            throw new BizException("所属电梯不能为空");
        }
        return elevatorRepository.findById(elevatorId)
                .orElseThrow(() -> new BizException("电梯不存在：id=" + elevatorId));
    }

    private void checkElevatorUsable(Elevator elevator) {
        if (ElevatorDict.ST_OUT.equals(elevator.getStatus())) {
            throw new BizException("电梯 " + elevator.getElevatorNo() + " 已停用，不能排计划或开单");
        }
        if (ElevatorDict.ST_EMERGENCY.equals(elevator.getStatus())) {
            throw new BizException("电梯 " + elevator.getElevatorNo()
                    + " 正处于应急维修，不能新建普通维保计划或工单，待应急闭环后自动恢复");
        }
    }

    private String requireCycle(String cycle) {
        String value = cycle == null ? "" : trim(cycle).toUpperCase();
        if (!ElevatorDict.isValidCycle(value)) {
            throw new BizException("维保周期只能是 HALF_MONTH/MONTH/QUARTER/HALF_YEAR/YEAR，收到的是：" + cycle);
        }
        return value;
    }

    private String nextOrderNo() {
        long seq = orderRepository.count() + 1;
        return "MO-" + LocalDate.now().getYear() + "-" + String.format("%04d", seq);
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
                suspended,
                plan.getOccupiedReason(),
                plan.getPrevStatus(),
                plan.getRemark(),
                plan.getUpdatedAt());
    }

    private Views.OrderView toOrderView(MaintenanceOrder order, NameContext ctx) {
        String planNo = order.getPlanId() == null ? null
                : planRepository.findById(order.getPlanId()).map(MaintenancePlan::getPlanNo).orElse(null);
        boolean emergency = ElevatorDict.KIND_EMERGENCY.equals(order.getOrderKind());
        String statusName = emergency ? ElevatorDict.emergencyStatusName(order.getStatus())
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
                order.getOrderKind(),
                emergency ? "应急抢修" : "常规维保",
                order.getCycle(),
                order.getCycle() == null ? "" : ElevatorDict.cycleName(order.getCycle()),
                order.getPlanDate(),
                order.getTeam(),
                order.getWorker(),
                order.getExecuteResult(),
                order.getActualDate(),
                order.getStatus(),
                statusName,
                order.getFaultId(),
                order.getFaultNo(),
                order.getArriveTime(),
                order.getRemark(),
                order.getUpdatedAt());
    }

    /** 名称缓存：一次列表里楼宇/单元/电梯名只查一遍全量表 */
    private final class NameContext {

        private Map<Long, String> buildingName;
        private Map<Long, String> unitNo;
        private Map<Long, String> elevatorNo;

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

        private String elevatorNo(Long id) {
            if (elevatorNo == null) {
                elevatorNo = new HashMap<>();
                for (Elevator elevator : elevatorRepository.findAll()) {
                    elevatorNo.put(elevator.getId(), elevator.getElevatorNo());
                }
            }
            return elevatorNo.getOrDefault(id, "");
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

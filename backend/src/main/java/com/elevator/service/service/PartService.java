package com.elevator.service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.elevator.service.common.BizException;
import com.elevator.service.domain.Building;
import com.elevator.service.domain.Elevator;
import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.domain.MaintenanceOrder;
import com.elevator.service.domain.PartReplacement;
import com.elevator.service.dto.PartDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.repository.BuildingRepository;
import com.elevator.service.repository.ElevatorRepository;
import com.elevator.service.repository.FaultReportRepository;
import com.elevator.service.repository.MaintenanceOrderRepository;
import com.elevator.service.repository.PartReplacementRepository;

/**
 * 模块五：配件更换。
 *
 * <p>列表 / 汇总都经 {@link FilterScope}：不传楼宇时 {@code statusFilter} 过滤作废记录，
 * 传入楼宇时 {@code buildingFilter} 只统计这栋楼（作废记录会被过滤条件排除在 statusFilter 之外，
 * 但按楼宇查询时会随楼宇结果集一起出现，便于对账）。
 */
@Service
public class PartService {

    private final PartReplacementRepository partRepository;
    private final ElevatorRepository elevatorRepository;
    private final BuildingRepository buildingRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final FaultReportRepository faultRepository;
    private final FilterScope filterScope;

    public PartService(PartReplacementRepository partRepository,
                       ElevatorRepository elevatorRepository,
                       BuildingRepository buildingRepository,
                       MaintenanceOrderRepository orderRepository,
                       FaultReportRepository faultRepository,
                       FilterScope filterScope) {
        this.partRepository = partRepository;
        this.elevatorRepository = elevatorRepository;
        this.buildingRepository = buildingRepository;
        this.orderRepository = orderRepository;
        this.faultRepository = faultRepository;
        this.filterScope = filterScope;
    }

    @Transactional(readOnly = true)
    public List<Views.PartView> list(Long buildingId, Long elevatorId) {
        List<PartReplacement> parts = filterScope.withFilter(buildingId,
                partRepository::findAllByOrderByReplaceDateDescIdDesc);
        Map<Long, String> elevatorNo = new HashMap<>();
        for (Elevator elevator : elevatorRepository.findAll()) {
            elevatorNo.put(elevator.getId(), elevator.getElevatorNo());
        }
        Map<Long, String> buildingName = new HashMap<>();
        for (Building building : buildingRepository.findAll()) {
            buildingName.put(building.getId(), building.getName());
        }
        List<Views.PartView> list = new ArrayList<>();
        for (PartReplacement part : parts) {
            if (elevatorId != null && !elevatorId.equals(part.getElevatorId())) {
                continue;
            }
            list.add(toView(part, elevatorNo, buildingName));
        }
        return list;
    }

    /** 页面底部金额汇总：总条数、总数量、总金额、按供应商分组 */
    @Transactional(readOnly = true)
    public Views.PartSummaryView summary(Long buildingId) {
        List<PartReplacement> parts = filterScope.withFilter(buildingId,
                partRepository::findAllByOrderByReplaceDateDescIdDesc);
        long count = 0;
        long quantity = 0;
        BigDecimal total = BigDecimal.ZERO;
        Map<String, SupplierAgg> bySupplier = new LinkedHashMap<>();
        for (PartReplacement part : parts) {
            count++;
            quantity += part.getQuantity() == null ? 0 : part.getQuantity();
            total = total.add(part.getAmount() == null ? BigDecimal.ZERO : part.getAmount());
            String supplier = part.getSupplier() == null || part.getSupplier().isBlank() ? "未登记供应商" : part.getSupplier();
            bySupplier.computeIfAbsent(supplier, key -> new SupplierAgg()).accept(part);
        }
        List<Views.SupplierAmountView> suppliers = new ArrayList<>();
        for (Map.Entry<String, SupplierAgg> entry : bySupplier.entrySet()) {
            suppliers.add(new Views.SupplierAmountView(entry.getKey(), entry.getValue().count, entry.getValue().amount));
        }
        suppliers.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));
        return new Views.PartSummaryView(count, quantity, total.setScale(2, java.math.RoundingMode.HALF_UP), suppliers);
    }

    @Transactional
    public Views.PartView create(PartDtos.PartSaveReq req) {
        if (req.getElevatorId() == null) {
            throw new BizException("所属电梯不能为空");
        }
        Elevator elevator = elevatorRepository.findById(req.getElevatorId())
                .orElseThrow(() -> new BizException("电梯不存在：id=" + req.getElevatorId()));
        String partName = trim(req.getPartName());
        if (partName.isEmpty()) {
            throw new BizException("配件名称不能为空");
        }
        Integer quantity = req.getQuantity() == null ? 0 : req.getQuantity();
        BigDecimal unitPrice = req.getUnitPrice() == null ? BigDecimal.ZERO : req.getUnitPrice();
        if (quantity <= 0) {
            throw new BizException("更换数量必须大于 0");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("单价必须大于 0");
        }

        PartReplacement part = new PartReplacement();
        part.setElevatorId(elevator.getId());
        part.setUnitId(elevator.getUnitId());
        part.setBuildingId(elevator.getBuildingId());
        part.setPartName(partName);
        part.setSpec(trim(req.getSpec()));
        part.setQuantity(quantity);
        part.setUnitPrice(unitPrice);
        part.setAmount(ElevatorDict.amount(quantity, unitPrice));
        part.setReplaceDate(req.getReplaceDate() == null ? LocalDate.now() : req.getReplaceDate());
        part.setOperator(trim(req.getOperator()));
        part.setSupplier(trim(req.getSupplier()));
        part.setStatus("NORMAL");

        String refType = req.getRefType() == null ? "" : trim(req.getRefType()).toUpperCase();
        if (!refType.isEmpty() && req.getRefId() != null) {
            if ("ORDER".equals(refType)) {
                MaintenanceOrder order = orderRepository.findById(req.getRefId())
                        .orElseThrow(() -> new BizException("关联的维保工单不存在：id=" + req.getRefId()));
                part.setRefType("ORDER");
                part.setRefId(order.getId());
                part.setRefNo(order.getOrderNo());
            } else if ("FAULT".equals(refType)) {
                FaultReport report = faultRepository.findById(req.getRefId())
                        .orElseThrow(() -> new BizException("关联的报修单不存在：id=" + req.getRefId()));
                part.setRefType("FAULT");
                part.setRefId(report.getId());
                part.setRefNo(report.getFaultNo());
            } else {
                throw new BizException("关联单据类型只能是 ORDER 维保工单 / FAULT 故障报修，收到的是：" + req.getRefType());
            }
        }
        part.setUpdatedAt(LocalDateTime.now());
        partRepository.save(part);
        return toView(part, new HashMap<>(), new HashMap<>());
    }

    private Views.PartView toView(PartReplacement part, Map<Long, String> elevatorNo, Map<Long, String> buildingName) {
        String refTypeName = part.getRefType() == null ? "" : switch (part.getRefType()) {
            case "ORDER" -> "维保工单";
            case "FAULT" -> "故障报修";
            default -> "未关联";
        };
        return new Views.PartView(
                part.getId(),
                part.getPartName(),
                part.getSpec(),
                part.getQuantity(),
                part.getUnitPrice(),
                part.getAmount(),
                part.getReplaceDate(),
                part.getOperator(),
                part.getRefType(),
                refTypeName,
                part.getRefId(),
                part.getRefNo(),
                part.getSupplier(),
                part.getElevatorId(),
                elevatorNo.getOrDefault(part.getElevatorId(), ""),
                part.getBuildingId(),
                buildingName.getOrDefault(part.getBuildingId(), ""),
                part.getStatus(),
                ElevatorDict.partStatusName(part.getStatus()),
                part.getUpdatedAt());
    }

    private static final class SupplierAgg {

        private long count;
        private BigDecimal amount = BigDecimal.ZERO;

        private void accept(PartReplacement part) {
            count++;
            amount = amount.add(part.getAmount() == null ? BigDecimal.ZERO : part.getAmount());
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

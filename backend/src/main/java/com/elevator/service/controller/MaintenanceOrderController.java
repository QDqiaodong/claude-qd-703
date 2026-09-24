package com.elevator.service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elevator.service.common.ApiResponse;
import com.elevator.service.dto.MaintenanceDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.service.MaintenanceService;

import jakarta.validation.Valid;

/** 模块三：维保工单。 */
@RestController
@RequestMapping("/api/orders")
public class MaintenanceOrderController {

    private final MaintenanceService maintenanceService;

    public MaintenanceOrderController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /** 不传楼宇 → statusFilter 过滤作废工单；传楼宇 → buildingFilter 收口 */
    @GetMapping
    public ApiResponse<List<Views.OrderView>> list(
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "elevatorId", required = false) Long elevatorId) {
        return ApiResponse.success(maintenanceService.listOrders(buildingId, elevatorId));
    }

    @PostMapping
    public ApiResponse<Views.OrderView> create(@Valid @RequestBody MaintenanceDtos.OrderSaveReq req) {
        return ApiResponse.success("维保工单已开单", maintenanceService.createOrder(req));
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<Views.OrderView> execute(@PathVariable("id") Long id,
                                                @Valid @RequestBody MaintenanceDtos.ExecuteReq req) {
        return ApiResponse.success("工单已执行", maintenanceService.executeOrder(id, req));
    }

    @PostMapping("/{id}/delay")
    public ApiResponse<Views.OrderView> delay(@PathVariable("id") Long id,
                                              @RequestBody(required = false) MaintenanceDtos.DelayReq req) {
        return ApiResponse.success("工单已延期", maintenanceService.delayOrder(id, req));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<Views.OrderView> voidOrder(@PathVariable("id") Long id) {
        return ApiResponse.success("工单已作废", maintenanceService.voidOrder(id));
    }
}

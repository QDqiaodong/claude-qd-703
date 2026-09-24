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

/** 模块三：维保计划（甘特条数据源）。 */
@RestController
@RequestMapping("/api/plans")
public class MaintenancePlanController {

    private final MaintenanceService maintenanceService;

    public MaintenancePlanController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    /** 不传楼宇 → statusFilter 过滤作废计划；传楼宇 → buildingFilter 收口 */
    @GetMapping
    public ApiResponse<List<Views.PlanView>> list(
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "elevatorId", required = false) Long elevatorId) {
        return ApiResponse.success(maintenanceService.listPlans(buildingId, elevatorId));
    }

    @PostMapping
    public ApiResponse<Views.PlanView> create(@Valid @RequestBody MaintenanceDtos.PlanSaveReq req) {
        return ApiResponse.success("维保计划已排程", maintenanceService.createPlan(req));
    }

    /** 执行：计划置已执行并生成一张工单 */
    @PostMapping("/{id}/execute")
    public ApiResponse<Views.OrderView> execute(@PathVariable("id") Long id,
                                                @Valid @RequestBody MaintenanceDtos.ExecuteReq req) {
        return ApiResponse.success("计划已执行并生成工单", maintenanceService.executePlan(id, req));
    }

    @PostMapping("/{id}/delay")
    public ApiResponse<Views.PlanView> delay(@PathVariable("id") Long id,
                                             @RequestBody(required = false) MaintenanceDtos.DelayReq req) {
        return ApiResponse.success("计划已延期", maintenanceService.delayPlan(id, req));
    }

    @PostMapping("/{id}/void")
    public ApiResponse<Views.PlanView> voidPlan(@PathVariable("id") Long id) {
        return ApiResponse.success("计划已作废", maintenanceService.voidPlan(id));
    }
}

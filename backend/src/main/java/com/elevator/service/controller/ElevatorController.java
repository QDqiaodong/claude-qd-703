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
import com.elevator.service.dto.ElevatorDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.service.ElevatorService;

import jakarta.validation.Valid;

/** 模块二：电梯台账（过滤器开关对比接口在此）。 */
@RestController
@RequestMapping("/api/elevators")
public class ElevatorController {

    private final ElevatorService elevatorService;

    public ElevatorController(ElevatorService elevatorService) {
        this.elevatorService = elevatorService;
    }

    /** buildingId 为空 → statusFilter 过滤停用梯；有值 → buildingFilter 只看这栋楼 */
    @GetMapping
    public ApiResponse<List<Views.ElevatorView>> list(
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "unitId", required = false) Long unitId,
            @RequestParam(value = "elevatorId", required = false) Long elevatorId) {
        return ApiResponse.success(elevatorService.list(buildingId, unitId, elevatorId));
    }

    /**
     * 【过滤器开关对比】同一条查询方法 {@code findAllByOrderByElevatorNoAsc()}，
     * 开 buildingFilter 与不开，返回两个不同大小的结果集。
     */
    @GetMapping("/filter-compare")
    public ApiResponse<Views.FilterCompareView> filterCompare(
            @RequestParam(value = "buildingId", required = false) Long buildingId) {
        return ApiResponse.success(elevatorService.compareFilter(buildingId));
    }

    /** 井道剖面图数据源 */
    @GetMapping("/{id}/shaft")
    public ApiResponse<Views.ShaftView> shaft(@PathVariable("id") Long id) {
        return ApiResponse.success(elevatorService.shaft(id));
    }

    @PostMapping
    public ApiResponse<Views.ElevatorView> create(@Valid @RequestBody ElevatorDtos.ElevatorSaveReq req) {
        return ApiResponse.success("电梯已建档", elevatorService.create(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.ElevatorView> changeStatus(@PathVariable("id") Long id,
                                                        @Valid @RequestBody ElevatorDtos.StatusReq req) {
        return ApiResponse.success("电梯状态已更新", elevatorService.changeStatus(id, req.getStatus()));
    }

    /** 井道剖面图：让轿厢停靠到指定楼层 */
    @PostMapping("/{id}/dock")
    public ApiResponse<Views.ElevatorView> dock(@PathVariable("id") Long id,
                                                @Valid @RequestBody ElevatorDtos.DockReq req) {
        return ApiResponse.success("轿厢已停靠 " + req.getFloor() + " 层", elevatorService.dock(id, req.getFloor()));
    }
}

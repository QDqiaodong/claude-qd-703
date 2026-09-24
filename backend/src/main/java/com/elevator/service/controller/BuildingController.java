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
import com.elevator.service.dto.BuildingDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.service.BuildingService;

import jakarta.validation.Valid;

/** 模块一：楼宇（列表走 buildingFilter / statusFilter）。 */
@RestController
@RequestMapping("/api/buildings")
public class BuildingController {

    private final BuildingService buildingService;

    public BuildingController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    /** buildingId 为空 → statusFilter 只留在管楼宇；有值 → buildingFilter 收口到这一栋 */
    @GetMapping
    public ApiResponse<List<Views.BuildingView>> list(@RequestParam(value = "buildingId", required = false) Long buildingId) {
        return ApiResponse.success(buildingService.list(buildingId));
    }

    /** 左侧三级级联面板：楼宇 → 单元 → 电梯 */
    @GetMapping("/tree")
    public ApiResponse<List<Views.TreeBuildingView>> tree(@RequestParam(value = "buildingId", required = false) Long buildingId) {
        return ApiResponse.success(buildingService.tree(buildingId));
    }

    @PostMapping
    public ApiResponse<Views.BuildingView> create(@Valid @RequestBody BuildingDtos.BuildingSaveReq req) {
        return ApiResponse.success("楼宇已建档", buildingService.create(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.BuildingView> changeStatus(@PathVariable("id") Long id,
                                                        @Valid @RequestBody BuildingDtos.StatusReq req) {
        return ApiResponse.success("楼宇状态已更新", buildingService.changeStatus(id, req.getStatus()));
    }
}

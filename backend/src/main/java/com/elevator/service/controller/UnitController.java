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

/** 模块一：楼栋单元。 */
@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final BuildingService buildingService;

    public UnitController(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    @GetMapping
    public ApiResponse<List<Views.UnitView>> list(@RequestParam(value = "buildingId", required = false) Long buildingId) {
        return ApiResponse.success(buildingService.listUnits(buildingId));
    }

    @PostMapping
    public ApiResponse<Views.UnitView> create(@Valid @RequestBody BuildingDtos.UnitSaveReq req) {
        return ApiResponse.success("单元已建档", buildingService.createUnit(req));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<Views.UnitView> changeStatus(@PathVariable("id") Long id,
                                                    @Valid @RequestBody BuildingDtos.StatusReq req) {
        return ApiResponse.success("单元状态已更新", buildingService.changeUnitStatus(id, req.getStatus()));
    }
}

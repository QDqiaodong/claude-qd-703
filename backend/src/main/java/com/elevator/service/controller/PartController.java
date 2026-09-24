package com.elevator.service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elevator.service.common.ApiResponse;
import com.elevator.service.dto.PartDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.service.PartService;

import jakarta.validation.Valid;

/** 模块五：配件更换（含底部金额汇总）。 */
@RestController
@RequestMapping("/api/parts")
public class PartController {

    private final PartService partService;

    public PartController(PartService partService) {
        this.partService = partService;
    }

    /** 不传楼宇 → statusFilter 过滤作废记录；传楼宇 → buildingFilter 收口 */
    @GetMapping
    public ApiResponse<List<Views.PartView>> list(
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "elevatorId", required = false) Long elevatorId) {
        return ApiResponse.success(partService.list(buildingId, elevatorId));
    }

    /** 页面底部金额汇总：总条数 / 总数量 / 总金额 / 按供应商分组 */
    @GetMapping("/summary")
    public ApiResponse<Views.PartSummaryView> summary(
            @RequestParam(value = "buildingId", required = false) Long buildingId) {
        return ApiResponse.success(partService.summary(buildingId));
    }

    @PostMapping
    public ApiResponse<Views.PartView> create(@Valid @RequestBody PartDtos.PartSaveReq req) {
        return ApiResponse.success("配件更换已登记", partService.create(req));
    }
}

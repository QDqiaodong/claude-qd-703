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
import com.elevator.service.dto.FaultDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.service.FaultService;

import jakarta.validation.Valid;

/** 模块四：故障报修。 */
@RestController
@RequestMapping("/api/faults")
public class FaultController {

    private final FaultService faultService;

    public FaultController(FaultService faultService) {
        this.faultService = faultService;
    }

    /** 不传楼宇 → statusFilter 只留未闭环报修；传楼宇 → buildingFilter 含历史已修复单 */
    @GetMapping
    public ApiResponse<List<Views.FaultView>> list(
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "elevatorId", required = false) Long elevatorId) {
        return ApiResponse.success(faultService.list(buildingId, elevatorId));
    }

    @PostMapping
    public ApiResponse<Views.FaultView> create(@Valid @RequestBody FaultDtos.FaultSaveReq req) {
        return ApiResponse.success("报修单已登记", faultService.create(req));
    }

    /** 推进状态：待处理 → 处理中 → 已修复 */
    @PostMapping("/{id}/advance")
    public ApiResponse<Views.FaultView> advance(@PathVariable("id") Long id) {
        return ApiResponse.success("报修状态已推进", faultService.advance(id));
    }

    @PostMapping("/{id}/fix")
    public ApiResponse<Views.FaultView> fix(@PathVariable("id") Long id,
                                            @Valid @RequestBody FaultDtos.FixReq req) {
        return ApiResponse.success("报修单已闭环", faultService.fix(id, req));
    }
}

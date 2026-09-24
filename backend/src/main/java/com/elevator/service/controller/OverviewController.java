package com.elevator.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elevator.service.common.ApiResponse;
import com.elevator.service.dto.Views;
import com.elevator.service.service.OverviewService;

/** 顶栏总览计数器。 */
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;

    public OverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping
    public ApiResponse<Views.OverviewView> overview() {
        return ApiResponse.success(overviewService.overview());
    }
}

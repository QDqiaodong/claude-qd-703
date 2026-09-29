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
import com.elevator.service.domain.ElevatorDict;
import com.elevator.service.domain.FaultReport;
import com.elevator.service.dto.FaultDtos;
import com.elevator.service.dto.Views;
import com.elevator.service.service.EmergencyLinkService;
import com.elevator.service.service.FaultService;

import jakarta.validation.Valid;

/** 模块四：故障报修（含应急联动受理/处置/验收/撤销）。 */
@RestController
@RequestMapping("/api/faults")
public class FaultController {

    private final FaultService faultService;
    private final EmergencyLinkService emergencyLinkService;

    public FaultController(FaultService faultService, EmergencyLinkService emergencyLinkService) {
        this.faultService = faultService;
        this.emergencyLinkService = emergencyLinkService;
    }

    /** 不传楼宇 → statusFilter 只留未闭环报修；传楼宇 → buildingFilter 含历史已修复/已撤销单 */
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

    /** 普通故障推进：待处理 → 处理中 → 已修复（应急单请走 /accept 与 /close） */
    @PostMapping("/{id}/advance")
    public ApiResponse<Views.FaultView> advance(@PathVariable("id") Long id) {
        return ApiResponse.success("报修状态已推进", faultService.advance(id));
    }

    @PostMapping("/{id}/fix")
    public ApiResponse<Views.FaultView> fix(@PathVariable("id") Long id,
                                            @Valid @RequestBody FaultDtos.FixReq req) {
        return ApiResponse.success("报修单已闭环", faultService.fix(id, req));
    }

    // ---------------- 应急联动 ----------------

    /** 受理应急报修：自动生成/关联一张抢修工单，电梯进入应急维修并暂停在途计划 */
    @PostMapping("/{id}/accept")
    public ApiResponse<Views.FaultView> accept(@PathVariable("id") Long id,
                                               @RequestBody(required = false) FaultDtos.AcceptReq req) {
        FaultReport report = emergencyLinkService.accept(id, req);
        return ApiResponse.success("已受理并生成抢修工单 " + report.getOrderNo(), faultService.getViewById(report.getId()));
    }

    /** 处置：处理中 → 待验收（必填处理结果 + 实际到场时间） */
    @PostMapping("/{id}/process")
    public ApiResponse<Views.FaultView> process(@PathVariable("id") Long id,
                                                @Valid @RequestBody FaultDtos.ProcessReq req) {
        emergencyLinkService.process(id, req);
        return ApiResponse.success("处置结果已提交，进入待验收", faultService.getViewById(id));
    }

    /** 验收闭环：待验收 → 已修复（确认电梯恢复运行，解除占用、恢复暂停计划） */
    @PostMapping("/{id}/close")
    public ApiResponse<Views.FaultView> close(@PathVariable("id") Long id,
                                              @Valid @RequestBody FaultDtos.CloseReq req) {
        emergencyLinkService.close(id, req);
        return ApiResponse.success("验收通过，应急处置闭环，电梯已恢复运行", faultService.getViewById(id));
    }

    /** 误报撤销：必须填写撤销原因，进入独立终态「已撤销」 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Views.FaultView> cancel(@PathVariable("id") Long id,
                                               @Valid @RequestBody FaultDtos.CancelReq req) {
        emergencyLinkService.cancel(id, req);
        return ApiResponse.success("已按误报撤销（" + ElevatorDict.faultStatusName(ElevatorDict.F_CANCELLED) + "）",
                faultService.getViewById(id));
    }

    /** 处置操作记录（服务重启后仍可恢复查看） */
    @GetMapping("/{id}/logs")
    public ApiResponse<List<Views.FaultLogView>> logs(@PathVariable("id") Long id) {
        return ApiResponse.success(faultService.logs(id));
    }
}

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

/**
 * 模块四：故障报修 + 应急联动。
 *
 * <p>应急状态机：待处理 →（受理，自动建抢修单并占用电梯）→ 处理中
 * →（到场登记 / 送验收）→ 待验收 →（确认恢复运行，闭环）→ 已修复；
 * 误报单独走 已撤销（必须填原因）。
 */
@RestController
@RequestMapping("/api/faults")
public class FaultController {

    private final FaultService faultService;

    public FaultController(FaultService faultService) {
        this.faultService = faultService;
    }

    /**
     * 列表：
     * <ul>
     *   <li>不传楼宇：默认只展示未闭环应急项（scope=all 时回到全部未闭环报修）；</li>
     *   <li>传楼宇：该楼全部报修（含历史已修复/已撤销），仍可用 elevatorId 收口到单台电梯。</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<List<Views.FaultView>> list(
            @RequestParam(value = "buildingId", required = false) Long buildingId,
            @RequestParam(value = "elevatorId", required = false) Long elevatorId,
            @RequestParam(value = "scope", required = false) String scope) {
        return ApiResponse.success(faultService.list(buildingId, elevatorId, scope));
    }

    /** 故障详情：关联抢修工单 + 被暂停计划 + 操作记录 */
    @GetMapping("/{id}")
    public ApiResponse<Views.FaultDetailView> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(faultService.detail(id));
    }

    @PostMapping
    public ApiResponse<Views.FaultView> create(@Valid @RequestBody FaultDtos.FaultSaveReq req) {
        return ApiResponse.success("报修单已登记", faultService.create(req));
    }

    /** 普通故障推进：待处理 → 处理中 → 已修复（应急故障拒绝） */
    @PostMapping("/{id}/advance")
    public ApiResponse<Views.FaultView> advance(@PathVariable("id") Long id) {
        return ApiResponse.success("报修状态已推进", faultService.advance(id));
    }

    /** 普通故障直接闭环 */
    @PostMapping("/{id}/fix")
    public ApiResponse<Views.FaultView> fix(@PathVariable("id") Long id,
                                            @Valid @RequestBody FaultDtos.FixReq req) {
        return ApiResponse.success("报修单已闭环", faultService.fix(id, req));
    }

    /** 受理应急报修：生成/关联抢修工单，电梯进入应急维修 */
    @PostMapping("/{id}/accept")
    public ApiResponse<Views.FaultDetailView> accept(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) FaultDtos.AcceptReq req) {
        return ApiResponse.success("已受理，抢修工单已生成并关联", faultService.accept(id, req));
    }

    /** 登记实际到场信息（到场时间/说明） */
    @PostMapping("/{id}/arrive")
    public ApiResponse<Views.FaultDetailView> arrive(@PathVariable("id") Long id,
                                                     @RequestBody(required = false) FaultDtos.ArriveReq req) {
        return ApiResponse.success("实际到场信息已登记", faultService.arrive(id, req));
    }

    /** 抢修完成送验收：处理中 → 待验收 */
    @PostMapping("/{id}/verify")
    public ApiResponse<Views.FaultDetailView> verify(@PathVariable("id") Long id,
                                                     @Valid @RequestBody FaultDtos.VerifyReq req) {
        return ApiResponse.success("已提交验收", faultService.submitVerify(id, req.getHandleResult()));
    }

    /** 验收通过并闭环（必须确认电梯恢复运行） */
    @PostMapping("/{id}/close")
    public ApiResponse<Views.FaultDetailView> close(@PathVariable("id") Long id,
                                                    @Valid @RequestBody FaultDtos.FixReq req) {
        return ApiResponse.success("应急处置闭环完成，电梯已恢复运行", faultService.verifyAndFix(id, req));
    }

    /** 误报撤销：填写撤销原因，走单独终态 */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Views.FaultDetailView> cancel(@PathVariable("id") Long id,
                                                     @Valid @RequestBody FaultDtos.CancelReq req) {
        return ApiResponse.success("已按误报撤销", faultService.cancel(id, req));
    }
}

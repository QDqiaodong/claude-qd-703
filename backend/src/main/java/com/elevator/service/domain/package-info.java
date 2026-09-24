/**
 * 电梯维保（特种设备维保单位）领域实体包。
 *
 * <p>包级声明两个全局 Hibernate 过滤器（{@code @FilterDef}），供全部实体共用：
 * <ul>
 *   <li>{@code buildingFilter}：按楼宇收口，{@code building_id = :buildingId}（楼宇自身用 {@code id}）。
 *       级联面板选中某栋楼后，所有模块列表只剩这栋楼的数据。</li>
 *   <li>{@code statusFilter}：不传楼宇时的兜底过滤，只留有效数据
 *       （非停用电梯、非作废计划/工单、未修复报修、非作废配件记录）。</li>
 * </ul>
 * 同一个 repository 查询方法，开/关这两个过滤器会得到不同结果集，见
 * {@code ElevatorService#compareFilter}。
 */
@org.hibernate.annotations.FilterDefs({
        @org.hibernate.annotations.FilterDef(
                name = "buildingFilter",
                parameters = @org.hibernate.annotations.ParamDef(name = "buildingId", type = Long.class)),
        @org.hibernate.annotations.FilterDef(name = "statusFilter")
})
package com.elevator.service.domain;

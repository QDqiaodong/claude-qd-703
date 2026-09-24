package com.elevator.service.domain;

/** 全局过滤器名常量，实体上的 {@code @Filter} 与 Service 的 enable/disable 共用。 */
public final class FilterNames {

    /** 按楼宇收口：building_id = :buildingId（楼宇自身按 id） */
    public static final String BUILDING = "buildingFilter";

    /** 兜底状态过滤：只留有效数据（非停用 / 非作废 / 未修复） */
    public static final String STATUS = "statusFilter";

    /** buildingFilter 的参数名 */
    public static final String BUILDING_PARAM = "buildingId";

    private FilterNames() {
    }
}

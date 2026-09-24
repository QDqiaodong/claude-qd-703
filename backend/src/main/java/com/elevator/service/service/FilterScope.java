package com.elevator.service.service;

import java.util.function.Supplier;

import org.hibernate.Session;
import org.springframework.stereotype.Component;

import com.elevator.service.domain.FilterNames;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 全局 Hibernate 过滤器的开关工具。
 *
 * <p>所有列表查询都在 {@code withFilter(...)} 里执行：
 * <ul>
 *   <li>传了楼宇 id → 开 {@code buildingFilter}(building_id = :buildingId)，结果收口到这一栋楼；</li>
 *   <li>没传楼宇 → 开 {@code statusFilter}，只留有效数据（非停用电梯 / 非作废计划工单 / 未修复报修）。</li>
 * </ul>
 * 查询跑完立刻关掉，避免过滤器泄漏到同一事务里的其它查询。
 */
@Component
public class FilterScope {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 在过滤器作用域内执行查询。
     *
     * @param buildingId 楼宇 id，为 null 时改用状态过滤
     */
    public <T> T withFilter(Long buildingId, Supplier<T> query) {
        Session session = entityManager.unwrap(Session.class);
        boolean byBuilding = buildingId != null && buildingId > 0;
        String name = byBuilding ? FilterNames.BUILDING : FilterNames.STATUS;
        if (byBuilding) {
            session.enableFilter(name).setParameter(FilterNames.BUILDING_PARAM, buildingId);
        } else {
            session.enableFilter(name);
        }
        try {
            return query.get();
        } finally {
            session.disableFilter(name);
        }
    }
}

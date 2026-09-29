-- ============================================================================
-- 电梯维保（特种设备维保单位）管理平台 · 建表 + 种子数据
-- 库名 elevator_service，由 mysql 镜像的 docker-entrypoint 在【镜像构建期】执行一次。
--
-- 种子数据保证下面每条业务规则都能被真的触发（不是"理论上可以"）：
--   [F1] buildingFilter 按楼宇收口   → 选 BL-003 只看到 3 号楼的电梯/计划/报修/配件
--   [F2] statusFilter 兜底过滤       → 未选楼宇时列表里看不到停用梯(EL-2008-005)、
--                                      撤场楼(BL-012)、作废计划(MP-2026-0018)、已修复报修
--   [F3] 同一查询方法开关过滤器      → /api/elevators/filter-compare 给出两个不同大小结果集
--   [R1.1] 楼宇编号唯一              → BL-001 已存在
--   [R1.2] 单元层数不超楼宇层数      → 新建单元层数 > 楼宇层数会被拦
--   [R1.3] 撤场楼宇不能再加单元      → BL-012 = ARCHIVED
--   [R2.1] 电梯编号唯一              → EL-2008-001 已存在
--   [R2.2] 停用梯不能排计划/开单     → EL-2008-005 = OUT_OF_SERVICE
--   [R2.3] 层站数不超单元层数        → service 校验
--   [R2.4] 轿厢停靠层必须在层站数内  → service 校验
--   [R3.1] 计划状态机                → 待执行 → 已执行；可延期；可作废
--   [R3.2] 已执行计划不可再执行      → MP-2026-0004 = DONE
--   [R3.3] 已作废计划不可再执行      → MP-2026-0018 = VOID
--   [R4.1] 报修状态推进              → 待处理 → 处理中 → 已修复
--   [R4.2] 困人报修整行标红          → FR-2026-0002 / FR-2026-0007 = 困人
--   [R4.3] 到达超 30 分钟红线        → FR-2026-0007 arrive = 46 分钟
--   [R5.1] 金额 = 数量 × 单价        → service 计算，页面底部汇总
--   [R5.2] 关联单号可追溯            → ref_type = ORDER / FAULT
--   [R6.1] 应急联动状态机            → 困人/停梯 emergency=1：待受理→抢修中→待验收→已修复；误报=已撤销
--   [R6.2] 受理生成抢修单 EX-xxxx    → FR-2026-0007/0008 已受理，电梯 13/14=EMERGENCY_REPAIR(prev 可恢复)
--   [R6.3] 占用互斥                  → elevator_emergency_lock 唯一键：一梯同时只有一张有效抢修单
--   [R6.4] 计划保留并标占用原因       → MP-2026-0029/0030=SUSPENDED，闭环后恢复 PENDING/DELAYED
--   [R6.5] 历史闭环应急单             → EX-2026-0003/0004/0005 与 FR-0002/0005/0016 三方单号一致
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS elevator_service DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE elevator_service;

DROP TABLE IF EXISTS part_replacement;
DROP TABLE IF EXISTS fault_operation_log;
DROP TABLE IF EXISTS elevator_emergency_lock;
DROP TABLE IF EXISTS fault_report;
DROP TABLE IF EXISTS maintenance_order;
DROP TABLE IF EXISTS maintenance_plan;
DROP TABLE IF EXISTS elevator;
DROP TABLE IF EXISTS unit;
DROP TABLE IF EXISTS building;

-- ----------------------------------------------------------------------------
-- 模块一：楼宇与单元
-- ----------------------------------------------------------------------------
CREATE TABLE building (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    building_no      VARCHAR(16) NOT NULL COMMENT '楼宇编号',
    name             VARCHAR(64) NOT NULL COMMENT '楼宇名称',
    address          VARCHAR(128) NOT NULL COMMENT '地址',
    floor_count      INT         NOT NULL COMMENT '层数',
    build_year       INT         NOT NULL COMMENT '建成年代',
    property_contact VARCHAR(32) NULL COMMENT '物业联系人',
    property_phone   VARCHAR(32) NULL COMMENT '物业联系电话',
    status           VARCHAR(16) NOT NULL COMMENT 'ACTIVE 在管 / ARCHIVED 已撤场',
    updated_at       DATETIME    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_building_no (building_no),
    KEY idx_building_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '楼宇';

CREATE TABLE unit (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    unit_no        VARCHAR(24) NOT NULL COMMENT '单元编号',
    building_id    BIGINT      NOT NULL COMMENT '所属楼宇 id',
    floor_count    INT         NOT NULL COMMENT '单元层数',
    elevator_count INT         NOT NULL COMMENT '电梯数',
    note           VARCHAR(128) NULL COMMENT '备注',
    status         VARCHAR(16) NOT NULL COMMENT 'ACTIVE 在管 / ARCHIVED 已撤场',
    updated_at     DATETIME    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_unit_no (unit_no),
    KEY idx_unit_building (building_id),
    KEY idx_unit_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '楼栋单元';

-- ----------------------------------------------------------------------------
-- 模块二：电梯台账
-- ----------------------------------------------------------------------------
CREATE TABLE elevator (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    elevator_no       VARCHAR(24)  NOT NULL COMMENT '电梯编号（注册代码）',
    unit_id           BIGINT       NOT NULL COMMENT '所属单元 id',
    building_id       BIGINT       NOT NULL COMMENT '冗余楼宇 id，buildingFilter 直接作用在此列',
    brand             VARCHAR(32)  NOT NULL COMMENT '品牌',
    model             VARCHAR(32)  NOT NULL COMMENT '型号',
    rated_load        INT          NOT NULL COMMENT '额定载重（kg）',
    rated_speed       DECIMAL(5,2) NOT NULL COMMENT '额定速度（m/s）',
    floor_station     INT          NOT NULL COMMENT '层站数',
    install_date      DATE         NULL COMMENT '安装日期',
    last_inspect_date DATE         NULL COMMENT '上次年检日',
    next_inspect_date DATE         NULL COMMENT '下次年检日',
    maint_vendor      VARCHAR(64)  NULL COMMENT '维保单位',
    status            VARCHAR(16)  NOT NULL COMMENT 'RUNNING 运行 / OUT_OF_SERVICE 停用 / REPAIRING 维修 / INSPECTING 年检中 / EMERGENCY_REPAIR 应急维修',
    prev_status       VARCHAR(16)  NULL COMMENT '进入应急维修前的原状态，闭环后恢复',
    emergency_fault_id BIGINT      NULL COMMENT '当前占用该电梯的有效应急报修单 id',
    current_floor     INT          NULL COMMENT '当前停靠楼层',
    updated_at        DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_elevator_no (elevator_no),
    KEY idx_elevator_unit (unit_id),
    KEY idx_elevator_building (building_id),
    KEY idx_elevator_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电梯台账';

-- ----------------------------------------------------------------------------
-- 模块三：维保计划与工单
-- ----------------------------------------------------------------------------
CREATE TABLE maintenance_plan (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    plan_no     VARCHAR(24) NOT NULL COMMENT '计划编号',
    elevator_id BIGINT      NOT NULL COMMENT '电梯 id',
    unit_id     BIGINT      NOT NULL COMMENT '单元 id',
    building_id BIGINT      NOT NULL COMMENT '冗余楼宇 id',
    cycle       VARCHAR(16) NOT NULL COMMENT 'HALF_MONTH 半月 / MONTH 月 / QUARTER 季 / HALF_YEAR 半年 / YEAR 年',
    plan_date   DATE        NOT NULL COMMENT '计划执行日（甘特条落点）',
    team        VARCHAR(32) NULL COMMENT '维保班组',
    worker      VARCHAR(32) NULL COMMENT '维保员',
    status      VARCHAR(16) NOT NULL COMMENT 'PENDING 待执行 / DONE 已执行 / DELAYED 已延期 / SUSPENDED 应急暂停 / VOID 作废',
    occupied_by_fault_id BIGINT NULL COMMENT '被哪张应急报修占用',
    occupied_reason VARCHAR(128) NULL COMMENT '被占用原因（含应急报修单号）',
    prev_status VARCHAR(16) NULL COMMENT '暂停前原状态，闭环后恢复',
    remark      VARCHAR(128) NULL COMMENT '备注',
    updated_at  DATETIME    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_no (plan_no),
    KEY idx_plan_elevator (elevator_id),
    KEY idx_plan_building (building_id),
    KEY idx_plan_date (plan_date),
    KEY idx_plan_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '维保计划';

CREATE TABLE maintenance_order (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    order_no       VARCHAR(24) NOT NULL COMMENT '工单号（应急抢修单 EX-2026-xxxx）',
    plan_id        BIGINT      NULL COMMENT '来源计划 id，临时派工/应急抢修为空',
    elevator_id    BIGINT      NOT NULL COMMENT '电梯 id',
    unit_id        BIGINT      NOT NULL COMMENT '单元 id',
    building_id    BIGINT      NOT NULL COMMENT '冗余楼宇 id',
    order_kind     VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 常规维保 / EMERGENCY 应急抢修',
    cycle          VARCHAR(16) NULL COMMENT '维保周期（应急单可空）',
    plan_date      DATE        NULL COMMENT '计划执行日',
    team           VARCHAR(32) NULL COMMENT '维保班组',
    worker         VARCHAR(32) NULL COMMENT '维保员',
    execute_result VARCHAR(255) NULL COMMENT '执行结果',
    actual_date    DATE        NULL COMMENT '实际完成日',
    status         VARCHAR(16) NOT NULL COMMENT '常规：PENDING/DONE/DELAYED/VOID；应急抢修：PENDING/PROCESSING/WAIT_ACCEPT/FIXED/CANCELLED',
    fault_id       BIGINT      NULL COMMENT '应急抢修关联的故障报修单 id',
    fault_no       VARCHAR(24) NULL COMMENT '应急抢修关联的报修单号',
    arrive_time    DATETIME    NULL COMMENT '实际到场时间',
    remark         VARCHAR(128) NULL COMMENT '备注',
    updated_at     DATETIME    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_plan (plan_id),
    KEY idx_order_elevator (elevator_id),
    KEY idx_order_building (building_id),
    KEY idx_order_status (status),
    KEY idx_order_fault (fault_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '维保工单（含应急抢修单）';

-- ----------------------------------------------------------------------------
-- 模块四：故障报修
-- ----------------------------------------------------------------------------
CREATE TABLE fault_report (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    fault_no         VARCHAR(24) NOT NULL COMMENT '报修单号',
    elevator_id      BIGINT      NOT NULL COMMENT '电梯 id',
    unit_id          BIGINT      NOT NULL COMMENT '单元 id',
    building_id      BIGINT      NOT NULL COMMENT '冗余楼宇 id',
    fault_type       VARCHAR(16) NOT NULL COMMENT 'TRAPPED 困人 / NOISE 异响 / LEVELING 不平层 / STOPPED 停梯 / DOOR 门系统 / OTHER 其他',
    emergency        TINYINT     NOT NULL DEFAULT 0 COMMENT '是否应急联动故障（困人/停梯）：1 是 / 0 否',
    risk_level       VARCHAR(16) NULL COMMENT 'HIGH 高风险 / NORMAL 普通',
    report_time      DATETIME    NOT NULL COMMENT '报修时间',
    accept_time      DATETIME    NULL COMMENT '受理时间',
    assignee         VARCHAR(32) NULL COMMENT '当前负责人',
    response_minutes INT         NULL COMMENT '响应时长（分钟）',
    arrive_minutes   INT         NULL COMMENT '到达时长（分钟）',
    arrive_time      DATETIME    NULL COMMENT '实际到场时间（闭环必填）',
    fix_time         DATETIME    NULL COMMENT '修复时间',
    trapped          INT         NULL COMMENT '是否困人：1 是 / 0 否',
    handle_result    VARCHAR(255) NULL COMMENT '处理结果',
    cancel_reason    VARCHAR(255) NULL COMMENT '误报撤销原因',
    reporter         VARCHAR(32) NULL COMMENT '报修人',
    order_id         BIGINT      NULL COMMENT '关联抢修工单 id',
    order_no         VARCHAR(24) NULL COMMENT '关联抢修工单号',
    confirm_user     VARCHAR(32) NULL COMMENT '闭环验收人',
    status           VARCHAR(16) NOT NULL COMMENT 'PENDING 待处理 / PROCESSING 处理中 / WAIT_ACCEPT 待验收 / FIXED 已修复 / CANCELLED 已撤销',
    stage            VARCHAR(16) NULL COMMENT '处置阶段：待受理/抢修中/待验收/已修复/已撤销',
    updated_at       DATETIME    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_fault_no (fault_no),
    KEY idx_fault_elevator (elevator_id),
    KEY idx_fault_building (building_id),
    KEY idx_fault_status (status),
    KEY idx_fault_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '故障报修（含应急联动）';

-- ----------------------------------------------------------------------------
-- 模块四-扩展：电梯应急占用锁 + 应急处置操作记录
-- ----------------------------------------------------------------------------
CREATE TABLE elevator_emergency_lock (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    elevator_id BIGINT      NOT NULL COMMENT '被占用电梯 id',
    fault_id    BIGINT      NOT NULL COMMENT '当前有效应急报修单 id',
    fault_no    VARCHAR(24) NOT NULL COMMENT '应急报修单号',
    order_id    BIGINT      NULL COMMENT '抢修工单 id',
    order_no    VARCHAR(24) NULL COMMENT '抢修工单号',
    locked_by   VARCHAR(32) NULL COMMENT '加锁（受理）操作员',
    created_at  DATETIME    NOT NULL COMMENT '加锁时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lock_elevator (elevator_id),
    UNIQUE KEY uk_lock_fault (fault_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电梯应急占用锁（一台电梯最多一行）';

CREATE TABLE fault_operation_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    fault_id    BIGINT      NOT NULL COMMENT '报修单 id',
    fault_no    VARCHAR(24) NOT NULL COMMENT '报修单号',
    action      VARCHAR(16) NOT NULL COMMENT 'CREATE/ACCEPT/ADVANCE/CANCEL/CLOSE/REJECT',
    from_status VARCHAR(16) NULL COMMENT '操作前状态',
    to_status   VARCHAR(16) NULL COMMENT '操作后状态',
    operator    VARCHAR(32) NULL COMMENT '操作员',
    detail      VARCHAR(255) NULL COMMENT '操作说明',
    order_id    BIGINT      NULL COMMENT '关联抢修工单 id',
    order_no    VARCHAR(24) NULL COMMENT '关联抢修工单号',
    created_at  DATETIME    NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_fault (fault_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '应急处置操作记录（只追加）';

-- ----------------------------------------------------------------------------
-- 模块五：配件更换
-- ----------------------------------------------------------------------------
CREATE TABLE part_replacement (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    elevator_id  BIGINT        NOT NULL COMMENT '电梯 id',
    unit_id      BIGINT        NOT NULL COMMENT '单元 id',
    building_id  BIGINT        NOT NULL COMMENT '冗余楼宇 id',
    part_name    VARCHAR(64)   NOT NULL COMMENT '配件名称',
    spec         VARCHAR(64)   NULL COMMENT '规格型号',
    quantity     INT           NOT NULL COMMENT '数量',
    unit_price   DECIMAL(12,2) NOT NULL COMMENT '单价',
    amount       DECIMAL(12,2) NOT NULL COMMENT '金额 = 数量 × 单价',
    replace_date DATE          NULL COMMENT '更换日期',
    operator     VARCHAR(32)   NULL COMMENT '更换人',
    ref_type     VARCHAR(16)   NULL COMMENT 'ORDER 维保工单 / FAULT 故障报修',
    ref_id       BIGINT        NULL COMMENT '关联单据 id',
    ref_no       VARCHAR(24)   NULL COMMENT '关联单号',
    supplier     VARCHAR(64)   NULL COMMENT '供应商',
    status       VARCHAR(16)   NOT NULL COMMENT 'NORMAL 已登记 / VOID 作废',
    updated_at   DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_part_elevator (elevator_id),
    KEY idx_part_building (building_id),
    KEY idx_part_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '配件更换';

-- ============================================================================
-- 种子数据
-- ============================================================================

-- [R1.3] BL-012 已撤场；其余在管
INSERT INTO building (id, building_no, name, address, floor_count, build_year, property_contact, property_phone, status, updated_at) VALUES
    (1,  'BL-001', '锦绣华城 1 号楼',   '上海市浦东新区锦绣路 128 号',   32, 2008, '周敏', '13801001001', 'ACTIVE',   NOW()),
    (2,  'BL-002', '锦绣华城 2 号楼',   '上海市浦东新区锦绣路 130 号',   32, 2008, '周敏', '13801001001', 'ACTIVE',   NOW()),
    (3,  'BL-003', '中央商务广场 A 座',  '上海市静安区南京西路 456 号',    45, 2015, '陈立', '13801002002', 'ACTIVE',   NOW()),
    (4,  'BL-004', '中央商务广场 B 座',  '上海市静安区南京西路 460 号',    38, 2015, '陈立', '13801002002', 'ACTIVE',   NOW()),
    (5,  'BL-005', '滨江花园 3 幢',     '上海市杨浦区滨江大道 88 号',     18, 2003, '王芳', '13801003003', 'ACTIVE',   NOW()),
    (6,  'BL-006', '滨江花园 5 幢',     '上海市杨浦区滨江大道 90 号',     18, 2003, '王芳', '13801003003', 'ACTIVE',   NOW()),
    (7,  'BL-007', '科技园区研发楼',     '上海市徐汇区漕溪路 200 号',     12, 2019, '李强', '13801004004', 'ACTIVE',   NOW()),
    (8,  'BL-008', '市一院门诊楼',      '上海市黄浦区瑞金二路 197 号',    16, 2011, '赵雪', '13801005005', 'ACTIVE',   NOW()),
    (9,  'BL-009', '星河购物中心',      '上海市长宁区娄山关路 55 号',      6, 2013, '孙浩', '13801006006', 'ACTIVE',   NOW()),
    (10, 'BL-010', '莫干山路创意园',     '上海市普陀区莫干山路 50 号',      5, 1998, '吴桐', '13801007007', 'ACTIVE',   NOW()),
    (11, 'BL-011', '会展中心西馆',      '上海市浦东新区龙阳路 2345 号',    4, 2020, '郑楠', '13801008008', 'ACTIVE',   NOW()),
    (12, 'BL-012', '临江公寓 6 号楼',   '上海市虹口区东大名路 908 号',    26, 2006, '冯磊', '13801009009', 'ARCHIVED', NOW());

-- [R1.2] 单元层数不超过楼宇层数；17/18 号单元登记了电梯但台账里还没挂
INSERT INTO unit (id, unit_no, building_id, floor_count, elevator_count, note, status, updated_at) VALUES
    (1,  'U-BL001-01', 1,  32, 3, '住宅塔楼，客梯 + 消防梯', 'ACTIVE',   NOW()),
    (2,  'U-BL001-02', 1,  32, 2, '住宅塔楼', 'ACTIVE',   NOW()),
    (3,  'U-BL002-01', 2,  32, 3, '住宅塔楼', 'ACTIVE',   NOW()),
    (4,  'U-BL002-02', 2,  28, 2, '住宅塔楼', 'ACTIVE',   NOW()),
    (5,  'U-BL003-01', 3,  45, 4, '写字楼高区', 'ACTIVE',   NOW()),
    (6,  'U-BL003-02', 3,  45, 4, '写字楼低区', 'ACTIVE',   NOW()),
    (7,  'U-BL004-01', 4,  38, 3, '写字楼', 'ACTIVE',   NOW()),
    (8,  'U-BL005-01', 5,  18, 2, '住宅', 'ACTIVE',   NOW()),
    (9,  'U-BL005-02', 5,  18, 2, '住宅', 'ACTIVE',   NOW()),
    (10, 'U-BL006-01', 6,  18, 2, '住宅', 'ACTIVE',   NOW()),
    (11, 'U-BL007-01', 7,  12, 2, '研发楼', 'ACTIVE',   NOW()),
    (12, 'U-BL008-01', 8,  16, 3, '医用电梯 + 客梯', 'ACTIVE',   NOW()),
    (13, 'U-BL009-01', 9,   6, 2, '商场自动扶梯旁客梯', 'ACTIVE',   NOW()),
    (14, 'U-BL010-01', 10,  5, 1, '老厂房改造，货梯', 'ACTIVE',   NOW()),
    (15, 'U-BL011-01', 11,  4, 2, '会展重型货梯', 'ACTIVE',   NOW()),
    (16, 'U-BL012-01', 12, 26, 2, '已随楼宇撤场', 'ARCHIVED', NOW()),
    (17, 'U-BL003-03', 3,  45, 2, '写字楼裙房，台账待补', 'ACTIVE',   NOW()),
    (18, 'U-BL004-02', 4,  38, 2, '写字楼裙房，台账待补', 'ACTIVE',   NOW());

-- [R2.2] EL-2008-005 / EL-2003-021 / EL-2006-035 停用；年检中两台
-- [应急] EL-2015-013(13)/EL-2015-014(14) 已被 FR-2026-0007/0008 受理占用：
--        EMERGENCY_REPAIR（prev_status=REPAIRING），闭环后恢复维修态
INSERT INTO elevator (id, elevator_no, unit_id, building_id, brand, model, rated_load, rated_speed, floor_station, install_date, last_inspect_date, next_inspect_date, maint_vendor, status, prev_status, emergency_fault_id, current_floor, updated_at) VALUES
    (1,  'EL-2008-001', 1,  1,  '通力',   'MonoSpace 1000', 1000, 2.50, 32, '2008-06-18', '2025-06-20', DATE_ADD(NOW(), INTERVAL 240 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        12, NOW()),
    (2,  'EL-2008-002', 1,  1,  '通力',   'MonoSpace 800',   800, 2.50, 32, '2008-06-18', '2025-06-20', DATE_ADD(NOW(), INTERVAL 120 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         7, NOW()),
    (3,  'EL-2008-003', 1,  1,  '迅达',   'Schindler 5400', 1000, 3.00, 32, '2008-09-02', '2025-09-02', DATE_ADD(NOW(), INTERVAL 20 DAY),  '上海安捷电梯维保有限公司', 'REPAIRING', NULL, NULL,       1, NOW()),
    (4,  'EL-2008-004', 2,  1,  '通力',   'MonoSpace 1000', 1000, 2.50, 32, '2008-06-18', '2025-06-20', DATE_ADD(NOW(), INTERVAL 60 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         5, NOW()),
    (5,  'EL-2008-005', 2,  1,  '迅达',   'Schindler 3300',  800, 2.00, 32, '2008-06-18', '2025-01-15', DATE_SUB(NOW(), INTERVAL 15 DAY),  '上海安捷电梯维保有限公司', 'OUT_OF_SERVICE', NULL, NULL,  1, NOW()),
    (6,  'EL-2009-006', 3,  2,  '蒂升',   'TWIN 1350',      1350, 3.00, 32, '2009-03-11', '2025-03-11', DATE_ADD(NOW(), INTERVAL 90 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        21, NOW()),
    (7,  'EL-2009-007', 3,  2,  '蒂升',   'TWIN 1000',      1000, 2.50, 32, '2009-03-11', '2025-03-11', DATE_ADD(NOW(), INTERVAL 90 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         3, NOW()),
    (8,  'EL-2009-008', 3,  2,  '三菱',   'NEXWAY-S 1000',  1000, 2.50, 32, '2009-05-20', '2025-05-20', DATE_SUB(NOW(), INTERVAL 5 DAY),   '上海安捷电梯维保有限公司', 'INSPECTING', NULL, NULL,     16, NOW()),
    (9,  'EL-2009-009', 4,  2,  '三菱',   'NEXWAY-S 800',    800, 2.00, 28, '2009-05-20', '2025-05-20', DATE_ADD(NOW(), INTERVAL 150 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         9, NOW()),
    (10, 'EL-2009-010', 4,  2,  '通力',   'MonoSpace 800',   800, 2.00, 28, '2009-08-08', '2025-08-08', DATE_ADD(NOW(), INTERVAL 200 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         1, NOW()),
    (11, 'EL-2015-011', 5,  3,  '蒂升',   'TWIN 1600',      1600, 4.00, 45, '2015-11-02', '2025-11-02', DATE_ADD(NOW(), INTERVAL 300 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        33, NOW()),
    (12, 'EL-2015-012', 5,  3,  '蒂升',   'TWIN 1600',      1600, 4.00, 45, '2015-11-02', '2025-11-02', DATE_ADD(NOW(), INTERVAL 300 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         8, NOW()),
    (13, 'EL-2015-013', 5,  3,  '迅达',   'Schindler 7000', 1350, 3.50, 45, '2015-11-02', '2025-11-02', DATE_ADD(NOW(), INTERVAL 300 DAY), '上海安捷电梯维保有限公司', 'EMERGENCY_REPAIR', 'RUNNING', 7, 27, NOW()),
    (14, 'EL-2015-014', 5,  3,  '迅达',   'Schindler 7000', 1350, 3.50, 45, '2015-11-02', '2025-02-18', DATE_SUB(NOW(), INTERVAL 40 DAY),  '上海安捷电梯维保有限公司', 'EMERGENCY_REPAIR', 'REPAIRING', 8, 2, NOW()),
    (15, 'EL-2015-015', 6,  3,  '三菱',   'NEXWAY-S 1600',  1600, 4.00, 45, '2015-12-19', '2025-12-19', DATE_ADD(NOW(), INTERVAL 320 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        40, NOW()),
    (16, 'EL-2015-016', 6,  3,  '三菱',   'NEXWAY-S 1350',  1350, 3.50, 45, '2015-12-19', '2025-12-19', DATE_ADD(NOW(), INTERVAL 320 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        15, NOW()),
    (17, 'EL-2015-017', 7,  4,  '通力',   'MonoSpace 1350', 1350, 3.00, 38, '2015-07-07', '2025-07-07', DATE_ADD(NOW(), INTERVAL 180 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        22, NOW()),
    (18, 'EL-2015-018', 7,  4,  '通力',   'MonoSpace 1000', 1000, 2.50, 38, '2015-07-07', '2025-07-07', DATE_SUB(NOW(), INTERVAL 2 DAY),   '上海安捷电梯维保有限公司', 'INSPECTING', NULL, NULL,     30, NOW()),
    (19, 'EL-2015-019', 7,  4,  '蒂升',   'TWIN 1000',      1000, 2.50, 38, '2015-07-07', '2025-07-07', DATE_ADD(NOW(), INTERVAL 180 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         6, NOW()),
    (20, 'EL-2003-020', 8,  5,  '三菱',   'GPS-III 800',     800, 1.75, 18, '2003-10-15', '2025-10-15', DATE_ADD(NOW(), INTERVAL 45 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        11, NOW()),
    (21, 'EL-2003-021', 8,  5,  '三菱',   'GPS-III 800',     800, 1.75, 18, '2003-10-15', '2024-10-15', DATE_SUB(NOW(), INTERVAL 90 DAY),  '上海安捷电梯维保有限公司', 'OUT_OF_SERVICE', NULL, NULL,  1, NOW()),
    (22, 'EL-2003-022', 9,  5,  '通力',   'MonoSpace 1000', 1000, 2.00, 18, '2003-10-15', '2025-10-15', DATE_ADD(NOW(), INTERVAL 45 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        14, NOW()),
    (23, 'EL-2004-023', 10, 6,  '迅达',   'Schindler 3300',  800, 1.75, 18, '2004-04-22', '2025-04-22', DATE_ADD(NOW(), INTERVAL 30 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        17, NOW()),
    (24, 'EL-2004-024', 10, 6,  '迅达',   'Schindler 3300',  800, 1.75, 18, '2004-04-22', '2025-04-22', DATE_ADD(NOW(), INTERVAL 30 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         4, NOW()),
    (25, 'EL-2019-025', 11, 7,  '蒂升',   'TWIN 1000',      1000, 2.50, 12, '2019-09-09', '2025-09-09', DATE_ADD(NOW(), INTERVAL 210 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         9, NOW()),
    (26, 'EL-2019-026', 11, 7,  '蒂升',   'TWIN 800',        800, 2.00, 12, '2019-09-09', '2025-09-09', DATE_ADD(NOW(), INTERVAL 210 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         1, NOW()),
    (27, 'EL-2011-027', 12, 8,  '三菱',   'NEXWAY-S 1600',  1600, 2.50, 16, '2011-05-30', '2025-05-30', DATE_ADD(NOW(), INTERVAL 100 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,        12, NOW()),
    (28, 'EL-2011-028', 12, 8,  '三菱',   'NEXWAY-S 1600',  1600, 2.50, 16, '2011-05-30', '2025-05-30', DATE_ADD(NOW(), INTERVAL 100 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         5, NOW()),
    (29, 'EL-2011-029', 12, 8,  '通力',   'MonoSpace 1000', 1000, 2.00, 16, '2011-05-30', '2025-05-30', DATE_SUB(NOW(), INTERVAL 10 DAY),  '上海安捷电梯维保有限公司', 'REPAIRING', NULL, NULL,       3, NOW()),
    (30, 'EL-2013-030', 13, 9,  '迅达',   'Schindler 5400', 1000, 1.75,  6, '2013-12-01', '2025-12-01', DATE_ADD(NOW(), INTERVAL 260 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         4, NOW()),
    (31, 'EL-2013-031', 13, 9,  '迅达',   'Schindler 5400', 1000, 1.75,  6, '2013-12-01', '2025-12-01', DATE_ADD(NOW(), INTERVAL 260 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         2, NOW()),
    (32, 'EL-1998-032', 14, 10, '三菱',   'GPS-II 500',      500, 1.00,  5, '1998-08-08', '2025-08-08', DATE_ADD(NOW(), INTERVAL 15 DAY),  '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         3, NOW()),
    (33, 'EL-2020-033', 15, 11, '蒂升',   'TWIN 2000',      2000, 2.50,  4, '2020-10-01', '2025-10-01', DATE_ADD(NOW(), INTERVAL 350 DAY), '上海安捷电梯维保有限公司', 'RUNNING', NULL, NULL,         1, NOW()),
    (34, 'EL-2020-034', 15, 11, '蒂升',   'TWIN 2000',      2000, 2.50,  4, '2020-10-01', '2025-10-01', DATE_SUB(NOW(), INTERVAL 25 DAY),  '上海安捷电梯维保有限公司', 'INSPECTING', NULL, NULL,      2, NOW()),
    (35, 'EL-2006-035', 16, 12, '通力',   'MonoSpace 800',   800, 1.75, 26, '2006-02-14', '2024-02-14', DATE_SUB(NOW(), INTERVAL 200 DAY), '上海安捷电梯维保有限公司', 'OUT_OF_SERVICE', NULL, NULL,  1, NOW());

-- 计划执行日刻意落在最近 12 个月内，甘特条全部可见
INSERT INTO maintenance_plan (id, plan_no, elevator_id, unit_id, building_id, cycle, plan_date, team, worker, status, remark, updated_at) VALUES
    (1,  'MP-2026-0001', 1,  1,  1,  'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 11 MONTH), '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (2,  'MP-2026-0002', 1,  1,  1,  'MONTH',      DATE_SUB(NOW(), INTERVAL 10 MONTH), '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (3,  'MP-2026-0003', 1,  1,  1,  'QUARTER',    DATE_SUB(NOW(), INTERVAL 9 MONTH),  '维保一组', '刘志强', 'DONE',    '季度检查合格', NOW()),
    (4,  'MP-2026-0004', 1,  1,  1,  'HALF_YEAR',  DATE_SUB(NOW(), INTERVAL 8 MONTH),  '维保二组', '孙立勇', 'DONE',    '半年检，更换门刀滑块', NOW()),
    (5,  'MP-2026-0005', 1,  1,  1,  'MONTH',      DATE_SUB(NOW(), INTERVAL 7 MONTH),  '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (6,  'MP-2026-0006', 1,  1,  1,  'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 6 MONTH),  '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (7,  'MP-2026-0007', 1,  1,  1,  'QUARTER',    DATE_SUB(NOW(), INTERVAL 5 MONTH),  '维保二组', '孙立勇', 'DONE',    '季度检查合格', NOW()),
    (8,  'MP-2026-0008', 1,  1,  1,  'MONTH',      DATE_SUB(NOW(), INTERVAL 4 MONTH),  '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (9,  'MP-2026-0009', 1,  1,  1,  'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 3 MONTH),  '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (10, 'MP-2026-0010', 1,  1,  1,  'YEAR',       DATE_SUB(NOW(), INTERVAL 2 MONTH),  '维保三组', '周海涛', 'DONE',    '年度自检完成', NOW()),
    (11, 'MP-2026-0011', 1,  1,  1,  'MONTH',      DATE_SUB(NOW(), INTERVAL 1 MONTH),  '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (12, 'MP-2026-0012', 1,  1,  1,  'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 12 DAY),   '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (13, 'MP-2026-0013', 1,  1,  1,  'MONTH',      DATE_ADD(NOW(), INTERVAL 5 DAY),    '维保一组', '刘志强', 'PENDING', '待执行：月度保养', NOW()),
    (14, 'MP-2026-0014', 1,  1,  1,  'QUARTER',    DATE_ADD(NOW(), INTERVAL 1 MONTH),  '维保二组', '孙立勇', 'PENDING', '待执行：季度检查', NOW()),
    (15, 'MP-2026-0015', 1,  1,  1,  'HALF_YEAR',  DATE_ADD(NOW(), INTERVAL 2 MONTH),  '维保三组', '周海涛', 'PENDING', '待执行：半年检', NOW()),
    (16, 'MP-2026-0016', 2,  1,  1,  'MONTH',      DATE_SUB(NOW(), INTERVAL 20 DAY),   '维保一组', '刘志强', 'DELAYED', '等配件到货，顺延', NOW()),
    (17, 'MP-2026-0017', 2,  1,  1,  'QUARTER',    DATE_ADD(NOW(), INTERVAL 12 DAY),   '维保二组', '孙立勇', 'PENDING', '待执行：季度检查', NOW()),
    (18, 'MP-2026-0018', 4,  2,  1,  'MONTH',      DATE_SUB(NOW(), INTERVAL 45 DAY),   '维保一组', '刘志强', 'VOID',    '楼宇装修停梯，计划作废', NOW()),
    (19, 'MP-2026-0019', 6,  3,  2,  'MONTH',      DATE_SUB(NOW(), INTERVAL 18 DAY),   '维保二组', '孙立勇', 'DONE',    '例行保养，运行正常', NOW()),
    (20, 'MP-2026-0020', 6,  3,  2,  'HALF_MONTH', DATE_ADD(NOW(), INTERVAL 8 DAY),    '维保二组', '孙立勇', 'PENDING', '待执行：半月保养', NOW()),
    (21, 'MP-2026-0021', 8,  3,  2,  'YEAR',       DATE_SUB(NOW(), INTERVAL 6 DAY),    '维保三组', '周海涛', 'DONE',    '年检配合完成', NOW()),
    (22, 'MP-2026-0022', 11, 5,  3,  'MONTH',      DATE_SUB(NOW(), INTERVAL 22 DAY),   '维保三组', '周海涛', 'DONE',    '例行保养，运行正常', NOW()),
    (23, 'MP-2026-0023', 11, 5,  3,  'QUARTER',    DATE_ADD(NOW(), INTERVAL 16 DAY),   '维保三组', '周海涛', 'PENDING', '待执行：季度检查', NOW()),
    (24, 'MP-2026-0024', 15, 6,  3,  'MONTH',      DATE_SUB(NOW(), INTERVAL 9 DAY),    '维保一组', '刘志强', 'DONE',    '例行保养，运行正常', NOW()),
    (25, 'MP-2026-0025', 15, 6,  3,  'HALF_YEAR',  DATE_ADD(NOW(), INTERVAL 3 MONTH),  '维保二组', '孙立勇', 'PENDING', '待执行：半年检', NOW()),
    (26, 'MP-2026-0026', 20, 8,  5,  'MONTH',      DATE_SUB(NOW(), INTERVAL 30 DAY),   '维保一组', '刘志强', 'DELAYED', '业主装修，顺延', NOW()),
    (27, 'MP-2026-0027', 27, 12, 8,  'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 11 DAY),   '维保三组', '周海涛', 'DONE',    '医用梯半月保养', NOW()),
    (28, 'MP-2026-0028', 30, 13, 9,  'QUARTER',    DATE_ADD(NOW(), INTERVAL 20 DAY),   '维保二组', '孙立勇', 'PENDING', '商场梯季度检查', NOW());

-- [应急] 电梯 13/14 被 FR-2026-0007/0008 受理占用：未完成计划保留但置 SUSPENDED，标出占用原因
INSERT INTO maintenance_plan
    (id, plan_no, elevator_id, unit_id, building_id, cycle, plan_date, team, worker, status, occupied_by_fault_id, occupied_reason, prev_status, remark, updated_at) VALUES
    (29, 'MP-2026-0029', 13, 5, 3, 'HALF_MONTH', DATE_ADD(NOW(), INTERVAL 2 DAY),  '维保三组', '周海涛', 'SUSPENDED', 7,
     '应急抢修 FR-2026-0007（抢修工单 EX-2026-0001）占用电梯，计划暂停；闭环后自动恢复', 'PENDING', '半月保养，被应急占用暂停', NOW()),
    (30, 'MP-2026-0030', 14, 5, 3, 'QUARTER',    DATE_ADD(NOW(), INTERVAL 10 DAY), '维保三组', '周海涛', 'SUSPENDED', 8,
     '应急抢修 FR-2026-0008（抢修工单 EX-2026-0002）占用电梯，计划暂停；闭环后自动恢复', 'DELAYED', '季度检查已延期一次，再被应急占用暂停', NOW());

INSERT INTO maintenance_order (id, order_no, plan_id, elevator_id, unit_id, building_id, order_kind, cycle, plan_date, team, worker, execute_result, actual_date, status, remark, updated_at) VALUES
    (1,  'MO-2026-0001', 1,  1,  1,  1,  'NORMAL', 'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 11 MONTH), '维保一组', '刘志强', '导轨润滑、层门检查合格', DATE_SUB(NOW(), INTERVAL 11 MONTH), 'DONE',    '', NOW()),
    (2,  'MO-2026-0002', 2,  1,  1,  1,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 10 MONTH), '维保一组', '刘志强', '限速器、安全钳检查合格', DATE_SUB(NOW(), INTERVAL 10 MONTH), 'DONE',    '', NOW()),
    (3,  'MO-2026-0003', 3,  1,  1,  1,  'NORMAL', 'QUARTER',    DATE_SUB(NOW(), INTERVAL 9 MONTH),  '维保一组', '刘志强', '季度检查合格，轿厢照明更换', DATE_SUB(NOW(), INTERVAL 9 MONTH), 'DONE',    '', NOW()),
    (4,  'MO-2026-0004', 4,  1,  1,  1,  'NORMAL', 'HALF_YEAR',  DATE_SUB(NOW(), INTERVAL 8 MONTH),  '维保二组', '孙立勇', '半年检合格，更换门刀滑块', DATE_SUB(NOW(), INTERVAL 8 MONTH), 'DONE',    '', NOW()),
    (5,  'MO-2026-0005', 5,  1,  1,  1,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 7 MONTH),  '维保一组', '刘志强', '例行保养合格', DATE_SUB(NOW(), INTERVAL 7 MONTH), 'DONE',    '', NOW()),
    (6,  'MO-2026-0006', 6,  1,  1,  1,  'NORMAL', 'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 6 MONTH),  '维保一组', '刘志强', '例行保养合格', DATE_SUB(NOW(), INTERVAL 6 MONTH), 'DONE',    '', NOW()),
    (7,  'MO-2026-0007', 7,  1,  1,  1,  'NORMAL', 'QUARTER',    DATE_SUB(NOW(), INTERVAL 5 MONTH),  '维保二组', '孙立勇', '季度检查合格', DATE_SUB(NOW(), INTERVAL 5 MONTH), 'DONE',    '', NOW()),
    (8,  'MO-2026-0008', 8,  1,  1,  1,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 4 MONTH),  '维保一组', '刘志强', '例行保养合格', DATE_SUB(NOW(), INTERVAL 4 MONTH), 'DONE',    '', NOW()),
    (9,  'MO-2026-0009', 9,  1,  1,  1,  'NORMAL', 'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 3 MONTH),  '维保一组', '刘志强', '例行保养合格', DATE_SUB(NOW(), INTERVAL 3 MONTH), 'DONE',    '', NOW()),
    (10, 'MO-2026-0010', 10, 1,  1,  1,  'NORMAL', 'YEAR',       DATE_SUB(NOW(), INTERVAL 2 MONTH),  '维保三组', '周海涛', '年度自检完成，制动试验合格', DATE_SUB(NOW(), INTERVAL 2 MONTH), 'DONE',    '', NOW()),
    (11, 'MO-2026-0011', 11, 1,  1,  1,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 1 MONTH),  '维保一组', '刘志强', '例行保养合格', DATE_SUB(NOW(), INTERVAL 1 MONTH), 'DONE',    '', NOW()),
    (12, 'MO-2026-0012', 12, 1,  1,  1,  'NORMAL', 'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 12 DAY),   '维保一组', '刘志强', '例行保养合格，厅门地坎清理', DATE_SUB(NOW(), INTERVAL 12 DAY), 'DONE',    '', NOW()),
    (13, 'MO-2026-0013', NULL, 3,  1,  1,  'NORMAL', 'MONTH',      DATE_ADD(NOW(), INTERVAL 3 DAY),   '维保二组', '孙立勇', NULL, NULL, 'PENDING', '临时派工：曳引机异响复检', NOW()),
    (14, 'MO-2026-0014', NULL, 6,  3,  2,  'NORMAL', 'MONTH',      DATE_ADD(NOW(), INTERVAL 6 DAY),   '维保二组', '孙立勇', NULL, NULL, 'PENDING', '临时派工：季度复检', NOW()),
    (15, 'MO-2026-0015', 19, 6,  3,  2,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 18 DAY),   '维保二组', '孙立勇', '例行保养合格', DATE_SUB(NOW(), INTERVAL 18 DAY), 'DONE',    '', NOW()),
    (16, 'MO-2026-0016', 21, 8,  3,  2,  'NORMAL', 'YEAR',       DATE_SUB(NOW(), INTERVAL 6 DAY),    '维保三组', '周海涛', '年检配合完成', DATE_SUB(NOW(), INTERVAL 6 DAY), 'DONE',    '', NOW()),
    (17, 'MO-2026-0017', 22, 11, 5,  3,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 22 DAY),   '维保三组', '周海涛', '例行保养合格', DATE_SUB(NOW(), INTERVAL 22 DAY), 'DONE',    '', NOW()),
    (18, 'MO-2026-0018', 24, 15, 6,  3,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 9 DAY),    '维保一组', '刘志强', '例行保养合格', DATE_SUB(NOW(), INTERVAL 9 DAY), 'DONE',    '', NOW()),
    (19, 'MO-2026-0019', NULL, 14, 5,  3,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 4 DAY),   '维保三组', '周海涛', NULL, NULL, 'DELAYED', '缺件，等钢丝绳到货（后被应急占用暂停，见 MP/EX 单）', NOW()),
    (20, 'MO-2026-0020', 26, 20, 8,  5,  'NORMAL', 'MONTH',      DATE_SUB(NOW(), INTERVAL 30 DAY),   '维保一组', '刘志强', NULL, NULL, 'DELAYED', '业主装修延期', NOW()),
    (21, 'MO-2026-0021', NULL, 29, 12, 8, 'NORMAL', 'MONTH',      DATE_ADD(NOW(), INTERVAL 2 DAY),    '维保三组', '周海涛', NULL, NULL, 'PENDING', '医用梯优先派工', NOW()),
    (22, 'MO-2026-0022', 27, 27, 12, 8, 'NORMAL', 'HALF_MONTH', DATE_SUB(NOW(), INTERVAL 11 DAY),   '维保三组', '周海涛', '半月保养合格', DATE_SUB(NOW(), INTERVAL 11 DAY), 'DONE',    '', NOW()),
    (23, 'MO-2026-0023', NULL, 33, 15, 11, 'NORMAL', 'QUARTER',  DATE_ADD(NOW(), INTERVAL 9 DAY),   '维保二组', '孙立勇', NULL, NULL, 'PENDING', '会展前季度检查', NOW()),
    (24, 'MO-2026-0024', NULL, 32, 14, 10, 'NORMAL', 'MONTH',     DATE_SUB(NOW(), INTERVAL 15 DAY),  '维保一组', '刘志强', NULL, NULL, 'VOID',    '重复开单，作废', NOW());

-- [应急联动] 抢修工单：EX-2026-0001/0002 与未闭环的 FR-2026-0007/0008 一一对应（处理中）；
-- EX-2026-0003/0004/0005 为历史已闭环应急抢修单，证明故障/工单/电梯三处单号一致
INSERT INTO maintenance_order
    (id, order_no, plan_id, elevator_id, unit_id, building_id, order_kind, cycle, plan_date, team, worker, execute_result, actual_date, status, fault_id, fault_no, arrive_time, remark, updated_at) VALUES
    (25, 'EX-2026-0001', NULL, 13, 5, 3, 'EMERGENCY', NULL, CURDATE(), '应急抢修组', '周海涛', NULL, NULL, 'PROCESSING',
     7, 'FR-2026-0007', DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 58 MINUTE), '应急抢修：困人，已到场放人，抢修中', NOW()),
    (26, 'EX-2026-0002', NULL, 14, 5, 3, 'EMERGENCY', NULL, CURDATE(), '应急抢修组', '周海涛', NULL, NULL, 'PROCESSING',
     8, 'FR-2026-0008', DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 42 MINUTE), '应急抢修：停梯，已到场，抢修中', NOW()),
    (27, 'EX-2026-0003', NULL, 1, 1, 1, 'EMERGENCY', NULL, DATE_SUB(NOW(), INTERVAL 41 DAY), '应急抢修组', '孙立勇', '现场放人，检查门锁回路', DATE_SUB(NOW(), INTERVAL 41 DAY), 'FIXED',
     2, 'FR-2026-0002', DATE_ADD(DATE_SUB(NOW(), INTERVAL 41 DAY), INTERVAL 26 MINUTE), '应急抢修：困人，已闭环', NOW()),
    (28, 'EX-2026-0004', NULL, 8, 3, 2, 'EMERGENCY', NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), '应急抢修组', '周海涛', '变频器故障，更换控制板', DATE_SUB(NOW(), INTERVAL 5 DAY), 'FIXED',
     5, 'FR-2026-0005', DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 DAY), INTERVAL 21 MINUTE), '应急抢修：停梯，已闭环', NOW()),
    (29, 'EX-2026-0005', NULL, 33, 15, 11, 'EMERGENCY', NULL, DATE_SUB(NOW(), INTERVAL 25 DAY), '应急抢修组', '孙立勇', '安全回路恢复', DATE_SUB(NOW(), INTERVAL 25 DAY), 'FIXED',
     16, 'FR-2026-0016', DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL 49 MINUTE), '应急抢修：停梯，已闭环', NOW());

-- [R4.2] FR-2026-0002 / FR-2026-0007 困人；[R4.3] 0007 到达 46 分钟超红线
-- [应急] 困人/停梯 emergency=1：0007/0008 已受理（抢修中，关联 EX-2026-0001/0002，电梯应急维修），
--        0013 待受理；0002/0005/0016 为历史已闭环应急单。普通异响等 emergency=0 走原流程。
INSERT INTO fault_report
    (id, fault_no, elevator_id, unit_id, building_id, fault_type, emergency, risk_level,
     report_time, accept_time, assignee, response_minutes, arrive_minutes, arrive_time, fix_time,
     trapped, handle_result, cancel_reason, reporter, order_id, order_no, confirm_user, status, stage, updated_at) VALUES
    (1,  'FR-2026-0001', 1,  1,  1,  'NOISE',    0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 62 DAY), NULL, NULL, 6,  28, NULL, DATE_SUB(NOW(), INTERVAL 62 DAY), 0, '导轨支架紧固，异响消除', NULL, '物业周敏', NULL, NULL, NULL, 'FIXED', '已修复', NOW()),
    (2,  'FR-2026-0002', 1,  1,  1,  'TRAPPED',  1, 'HIGH',   DATE_SUB(NOW(), INTERVAL 41 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 41 DAY), INTERVAL 4 MINUTE), '孙立勇', 4, 22, DATE_ADD(DATE_SUB(NOW(), INTERVAL 41 DAY), INTERVAL 26 MINUTE), DATE_SUB(NOW(), INTERVAL 41 DAY), 1, '现场放人，检查门锁回路', NULL, '乘客王女士', 27, 'EX-2026-0003', '值班经理', 'FIXED', '已修复', NOW()),
    (3,  'FR-2026-0003', 3,  1,  1,  'LEVELING', 0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, NULL, 8,  35, NULL, NULL, 0, NULL, NULL, '物业周敏', NULL, NULL, NULL, 'PROCESSING', '处理中', NOW()),
    (4,  'FR-2026-0004', 6,  3,  2,  'DOOR',     0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 9 DAY),  NULL, NULL, 5,  26, NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), 0, '门刀滑块磨损，已更换', NULL, '物业陈立', NULL, NULL, NULL, 'FIXED', '已修复', NOW()),
    (5,  'FR-2026-0005', 8,  3,  2,  'STOPPED',  1, 'HIGH',   DATE_SUB(NOW(), INTERVAL 5 DAY),  DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 DAY), INTERVAL 3 MINUTE), '周海涛', 3, 18, DATE_ADD(DATE_SUB(NOW(), INTERVAL 5 DAY), INTERVAL 21 MINUTE), DATE_SUB(NOW(), INTERVAL 5 DAY), 1, '变频器故障，更换控制板', NULL, '物业陈立', 28, 'EX-2026-0004', '值班经理', 'FIXED', '已修复', NOW()),
    (6,  'FR-2026-0006', 11, 5,  3,  'NOISE',    0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 2 DAY),  NULL, NULL, 7,  24, NULL, NULL, 0, NULL, NULL, '物业陈立', NULL, NULL, NULL, 'PENDING', '待处理', NOW()),
    (7,  'FR-2026-0007', 13, 5,  3,  'TRAPPED',  1, 'HIGH',   DATE_SUB(NOW(), INTERVAL 1 DAY),  DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 12 MINUTE), '周海涛', 12, 46, DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 58 MINUTE), NULL, 1, NULL, NULL, '乘客李先生', 25, 'EX-2026-0001', NULL, 'PROCESSING', '抢修中', NOW()),
    (8,  'FR-2026-0008', 14, 5,  3,  'STOPPED',  1, 'HIGH',   DATE_SUB(NOW(), INTERVAL 12 HOUR), DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 9 MINUTE), '周海涛', 9, 33, DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 42 MINUTE), NULL, 1, NULL, NULL, '物业陈立', 26, 'EX-2026-0002', NULL, 'PROCESSING', '抢修中', NOW()),
    (9,  'FR-2026-0009', 15, 6,  3,  'LEVELING', 0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 30 DAY), NULL, NULL, 6,  29, NULL, DATE_SUB(NOW(), INTERVAL 30 DAY), 0, '平层感应器调整', NULL, '物业陈立', NULL, NULL, NULL, 'FIXED', '已修复', NOW()),
    (10, 'FR-2026-0010', 18, 7,  4,  'OTHER',    0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 8 DAY),  NULL, NULL, 10, 40, NULL, DATE_SUB(NOW(), INTERVAL 8 DAY), 0, '轿厢照明更换', NULL, '物业陈立', NULL, NULL, NULL, 'FIXED', '已修复', NOW()),
    (11, 'FR-2026-0011', 20, 8,  5,  'DOOR',     0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 3 DAY),  NULL, NULL, 6,  31, NULL, NULL, 0, NULL, NULL, '物业王芳', NULL, NULL, NULL, 'PENDING', '待处理', NOW()),
    (12, 'FR-2026-0012', 22, 9,  5,  'NOISE',    0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 21 DAY), NULL, NULL, 5,  27, NULL, DATE_SUB(NOW(), INTERVAL 21 DAY), 0, '门机皮带张紧', NULL, '物业王芳', NULL, NULL, NULL, 'FIXED', '已修复', NOW()),
    (13, 'FR-2026-0013', 27, 12, 8,  'TRAPPED',  1, 'HIGH',   DATE_SUB(NOW(), INTERVAL 6 HOUR), NULL, NULL, 4,  19, NULL, NULL, 1, NULL, NULL, '医护站', NULL, NULL, NULL, 'PENDING', '待受理', NOW()),
    (14, 'FR-2026-0014', 30, 13, 9,  'DOOR',     0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 17 DAY), NULL, NULL, 7,  25, NULL, DATE_SUB(NOW(), INTERVAL 17 DAY), 0, '光幕接线恢复', NULL, '商场孙浩', NULL, NULL, NULL, 'FIXED', '已修复', NOW()),
    (15, 'FR-2026-0015', 31, 13, 9,  'LEVELING', 0, 'NORMAL', DATE_SUB(NOW(), INTERVAL 4 DAY),  NULL, NULL, 8,  30, NULL, NULL, 0, NULL, NULL, '商场孙浩', NULL, NULL, NULL, 'PROCESSING', '处理中', NOW()),
    (16, 'FR-2026-0016', 33, 15, 11, 'STOPPED',  1, 'HIGH',   DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL 11 MINUTE), '孙立勇', 11, 38, DATE_ADD(DATE_SUB(NOW(), INTERVAL 25 DAY), INTERVAL 49 MINUTE), DATE_SUB(NOW(), INTERVAL 25 DAY), 1, '安全回路恢复', NULL, '会展郑楠', 29, 'EX-2026-0005', '值班经理', 'FIXED', '已修复', NOW());

-- [应急] 当前有效的电梯占用锁：电梯 13/14 各一行，与 FR/EX/电梯台账三方一致
INSERT INTO elevator_emergency_lock (id, elevator_id, fault_id, fault_no, order_id, order_no, locked_by, created_at) VALUES
    (1, 13, 7, 'FR-2026-0007', 25, 'EX-2026-0001', '值班员', DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 12 MINUTE)),
    (2, 14, 8, 'FR-2026-0008', 26, 'EX-2026-0002', '值班员', DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 9 MINUTE));

-- [应急] 未闭环应急报修的操作记录（只追加；服务重启后仍可追溯）
INSERT INTO fault_operation_log (id, fault_id, fault_no, action, from_status, to_status, operator, detail, order_id, order_no, created_at) VALUES
    (1, 7, 'FR-2026-0007', 'CREATE', NULL,       'PENDING',    '报修登记', '登记应急故障（困人/停梯），进入应急态', NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (2, 7, 'FR-2026-0007', 'ACCEPT', 'PENDING',  'PROCESSING', '值班员', '受理应急报修，生成抢修工单 EX-2026-0001，电梯进入应急维修，暂停在途计划', 25, 'EX-2026-0001', DATE_ADD(DATE_SUB(NOW(), INTERVAL 1 DAY), INTERVAL 12 MINUTE)),
    (3, 8, 'FR-2026-0008', 'CREATE', NULL,       'PENDING',    '报修登记', '登记应急故障（困人/停梯），进入应急态', NULL, NULL, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
    (4, 8, 'FR-2026-0008', 'ACCEPT', 'PENDING',  'PROCESSING', '值班员', '受理应急报修，生成抢修工单 EX-2026-0002，电梯进入应急维修，暂停在途计划', 26, 'EX-2026-0002', DATE_ADD(DATE_SUB(NOW(), INTERVAL 12 HOUR), INTERVAL 9 MINUTE)),
    (5, 13, 'FR-2026-0013', 'CREATE', NULL,      'PENDING',    '报修登记', '登记应急故障（困人/停梯），进入应急态，等待受理', NULL, NULL, DATE_SUB(NOW(), INTERVAL 6 HOUR));

-- [R5.1] amount = quantity × unit_price；[R5.2] ref_type = ORDER / FAULT
INSERT INTO part_replacement (id, elevator_id, unit_id, building_id, part_name, spec, quantity, unit_price, amount, replace_date, operator, ref_type, ref_id, ref_no, supplier, status, updated_at) VALUES
    (1,  1,  1,  1,  '门刀滑块',     'KONE-MD-32',    4,  68.00,  272.00,  DATE_SUB(NOW(), INTERVAL 8 MONTH), '刘志强', 'ORDER', 4,  'MO-2026-0004', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (2,  1,  1,  1,  '轿厢 LED 灯管', 'T8-18W',       6,  32.50,  195.00,  DATE_SUB(NOW(), INTERVAL 9 MONTH), '刘志强', 'ORDER', 3,  'MO-2026-0003', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (3,  1,  1,  1,  '厅门地坎条',   'AL-1200',      2,  145.00, 290.00,  DATE_SUB(NOW(), INTERVAL 12 DAY),  '刘志强', 'ORDER', 12, 'MO-2026-0012', '苏州金鼎机械', 'NORMAL', NOW()),
    (4,  1,  1,  1,  '门锁触点',     'DS-11',        3,  86.00,  258.00,  DATE_SUB(NOW(), INTERVAL 41 DAY),  '孙立勇', 'FAULT', 2,  'FR-2026-0002', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (5,  3,  1,  1,  '曳引轮轴承',   '6208-2RS',     2,  320.00, 640.00,  DATE_SUB(NOW(), INTERVAL 15 DAY),  '孙立勇', 'FAULT', 3,  'FR-2026-0003', '苏州金鼎机械', 'NORMAL', NOW()),
    (6,  6,  3,  2,  '门刀滑块',     'TWIN-MD-45',   4,  92.00,  368.00,  DATE_SUB(NOW(), INTERVAL 9 DAY),   '孙立勇', 'FAULT', 4,  'FR-2026-0004', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (7,  8,  3,  2,  '变频器控制板', 'SCH-5400-CPU', 1,  2850.00, 2850.00, DATE_SUB(NOW(), INTERVAL 5 DAY),  '周海涛', 'FAULT', 5,  'FR-2026-0005', '迅达（中国）配件中心', 'NORMAL', NOW()),
    (8,  8,  3,  2,  '制动器线圈',   'BRK-24V',      2,  410.00, 820.00,  DATE_SUB(NOW(), INTERVAL 6 DAY),   '周海涛', 'ORDER', 16, 'MO-2026-0016', '迅达（中国）配件中心', 'NORMAL', NOW()),
    (9,  11, 5,  3,  '钢丝绳',       'Φ13-8×19',    320, 12.80, 4096.00, DATE_SUB(NOW(), INTERVAL 22 DAY),  '周海涛', 'ORDER', 17, 'MO-2026-0017', '江苏通力绳缆厂', 'NORMAL', NOW()),
    (10, 13, 5,  3,  '限速器钢丝绳', 'Φ8-6×19',     180, 9.60,  1728.00, DATE_SUB(NOW(), INTERVAL 1 DAY),   '周海涛', 'FAULT', 7,  'FR-2026-0007', '江苏通力绳缆厂', 'NORMAL', NOW()),
    (11, 14, 5,  3,  '曳引钢丝绳',   'Φ13-8×19',    260, 12.80, 3328.00, DATE_SUB(NOW(), INTERVAL 4 DAY),   '周海涛', NULL, NULL, NULL, '江苏通力绳缆厂', 'NORMAL', NOW()),
    (12, 15, 6,  3,  '平层感应器',   'PMS-03',      2,  268.00, 536.00,  DATE_SUB(NOW(), INTERVAL 30 DAY),  '刘志强', 'FAULT', 9,  'FR-2026-0009', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (13, 18, 7,  4,  '轿厢 LED 灯管', 'T8-18W',     8,  32.50,  260.00,  DATE_SUB(NOW(), INTERVAL 8 DAY),   '孙立勇', 'FAULT', 10, 'FR-2026-0010', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (14, 20, 8,  5,  '门机皮带',     '5M-450',      2,  78.00,  156.00,  DATE_SUB(NOW(), INTERVAL 21 DAY),  '刘志强', 'FAULT', 12, 'FR-2026-0012', '苏州金鼎机械', 'NORMAL', NOW()),
    (15, 22, 9,  5,  '门机皮带',     '5M-450',      1,  78.00,  78.00,   DATE_SUB(NOW(), INTERVAL 21 DAY),  '刘志强', 'FAULT', 12, 'FR-2026-0012', '苏州金鼎机械', 'NORMAL', NOW()),
    (16, 27, 12, 8,  '应急电源电池', '12V-7Ah',     4,  186.00, 744.00,  DATE_SUB(NOW(), INTERVAL 6 HOUR),  '周海涛', 'FAULT', 13, 'FR-2026-0013', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (17, 30, 13, 9,  '光幕发射条',   'WECO-917A',   1,  760.00, 760.00,  DATE_SUB(NOW(), INTERVAL 17 DAY),  '孙立勇', 'FAULT', 14, 'FR-2026-0014', '宁波微科光电', 'NORMAL', NOW()),
    (18, 31, 13, 9,  '平层感应器',   'PMS-03',      1,  268.00, 268.00,  DATE_SUB(NOW(), INTERVAL 4 DAY),   '孙立勇', 'FAULT', 15, 'FR-2026-0015', '上海菱云配件有限公司', 'NORMAL', NOW()),
    (19, 33, 15, 11, '安全回路继电器', 'SSR-24V',   3,  124.00, 372.00,  DATE_SUB(NOW(), INTERVAL 25 DAY),  '孙立勇', 'FAULT', 16, 'FR-2026-0016', '苏州金鼎机械', 'NORMAL', NOW()),
    (20, 32, 14, 10, '蜗轮蜗杆油',   'CLP-220',     5,  96.00,  480.00,  DATE_SUB(NOW(), INTERVAL 15 DAY),  '刘志强', 'ORDER', 24, 'MO-2026-0024', '上海菱云配件有限公司', 'VOID',   NOW()),
    (21, 11, 5,  3,  '导靴衬板',     'GB-16K',      8,  45.00,  360.00,  DATE_SUB(NOW(), INTERVAL 3 MONTH), '周海涛', 'ORDER', 17, 'MO-2026-0017', '苏州金鼎机械', 'NORMAL', NOW()),
    (22, 1,  1,  1,  '缓冲器液压油', 'HL-46',       3,  88.00,  264.00,  DATE_SUB(NOW(), INTERVAL 2 MONTH), '刘志强', 'ORDER', 10, 'MO-2026-0010', '上海菱云配件有限公司', 'NORMAL', NOW());

SET FOREIGN_KEY_CHECKS = 1;

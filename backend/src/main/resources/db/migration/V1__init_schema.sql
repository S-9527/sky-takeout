-- =====================================================================
-- 苍穹外卖 v2 · V1__init_schema.sql
-- ---------------------------------------------------------------------
-- 语义来源:docs/01-domain.md(唯一真相)、docs/02-database.md(结构说明)
-- 目标环境:MySQL 8.4 / InnoDB / utf8mb4 / utf8mb4_0900_ai_ci
-- 约定:
--   * 主键统一 id BIGINT NOT NULL AUTO_INCREMENT
--   * 表名用单数;订单主表因 order 是 SQL 保留字,定名 orders
--   * 审计四件套 created_at / updated_at / created_by / updated_by 全表统一
--   * 不做软删除(无 is_deleted / deleted_at),禁用态由 status 表达
--   * 多值枚举存 VARCHAR 字符串字面量;二值启停标记按领域文档写 TINYINT 1/0
--   * 金额一律 BIGINT,列名以 _cents 结尾,单位=分
--   * 时间列 DATETIME,写库时区 Asia/Shanghai
-- 本文件由 Flyway 执行:已发布的迁移禁止修改,结构变更请新增 V3__xxx.sql
-- =====================================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ---------------------------------------------------------------------
-- 1. employee 员工(身份 Identity 上下文)
-- ---------------------------------------------------------------------
CREATE TABLE `employee` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
  `username`      VARCHAR(32)  NOT NULL                          COMMENT '登录名,全局唯一',
  `password_hash` VARCHAR(100) NOT NULL                          COMMENT 'BCrypt 密码哈希,不存明文',
  `name`          VARCHAR(32)  NOT NULL                          COMMENT '姓名',
  `phone`         VARCHAR(20)  NULL                              COMMENT '手机号',
  `role`          VARCHAR(16)  NOT NULL                          COMMENT '角色:ADMIN 管理员(超管) / STAFF 普通员工',
  `status`        TINYINT      NOT NULL DEFAULT 1                COMMENT '账号状态:1 启用 / 0 禁用',
  `last_login_at` DATETIME     NULL                              COMMENT '最近一次登录时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`    BIGINT       NULL                              COMMENT '创建人 employee.id,系统写入为 NULL',
  `updated_by`    BIGINT       NULL                              COMMENT '最后更新人 employee.id,系统写入为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_username` (`username`),
  CONSTRAINT `ck_employee_role`   CHECK (`role` IN ('ADMIN', 'STAFF')),
  CONSTRAINT `ck_employee_status` CHECK (`status` IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '员工(管理端账号)';

-- ---------------------------------------------------------------------
-- 2. customer 顾客(身份 Identity 上下文)
--    顾客没有密码:唯一登录方式是微信授权
-- ---------------------------------------------------------------------
CREATE TABLE `customer` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT           COMMENT '主键',
  `openid`        VARCHAR(64)  NOT NULL                          COMMENT '微信 openid,全局唯一',
  `nickname`      VARCHAR(64)  NULL                              COMMENT '微信昵称,微信可能不返回',
  `avatar_url`    VARCHAR(255) NULL                              COMMENT '头像地址',
  `phone`         VARCHAR(20)  NULL                              COMMENT '手机号,下单时可补',
  `status`        TINYINT      NOT NULL DEFAULT 1                COMMENT '账号状态:1 正常 / 0 封禁',
  `last_login_at` DATETIME     NULL                              COMMENT '最近一次登录时间',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`    BIGINT       NULL                              COMMENT '创建人 employee.id,微信自助注册为 NULL',
  `updated_by`    BIGINT       NULL                              COMMENT '最后更新人 employee.id,系统写入为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_openid` (`openid`),
  CONSTRAINT `ck_customer_status` CHECK (`status` IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '顾客(小程序用户)';

-- ---------------------------------------------------------------------
-- 3. user_address 地址簿(顾客资料 Profile 上下文)
--    "每顾客至多一条默认地址"由应用层在同一事务内先清空后置位保证
-- ---------------------------------------------------------------------
CREATE TABLE `user_address` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键',
  `customer_id` BIGINT       NOT NULL                            COMMENT '所属顾客 customer.id',
  `consignee`   VARCHAR(32)  NOT NULL                            COMMENT '收货人姓名',
  `phone`       VARCHAR(20)  NOT NULL                            COMMENT '收货人手机号',
  `province`    VARCHAR(32)  NOT NULL                            COMMENT '省',
  `city`        VARCHAR(32)  NOT NULL                            COMMENT '市',
  `district`    VARCHAR(32)  NOT NULL                            COMMENT '区/县',
  `detail`      VARCHAR(255) NOT NULL                            COMMENT '详细地址(街道门牌)',
  `label`       VARCHAR(16)  NULL                                COMMENT '地址标签,约定取值:家 / 公司 / 学校,允许自定义,可空',
  `is_default`  TINYINT      NOT NULL DEFAULT 0                  COMMENT '是否默认地址:1 是 / 0 否',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`  BIGINT       NULL                                COMMENT '创建人(顾客本人 customer.id 或后台 employee.id)',
  `updated_by`  BIGINT       NULL                                COMMENT '最后更新人',
  PRIMARY KEY (`id`),
  KEY `idx_user_address_customer` (`customer_id`, `is_default`),
  CONSTRAINT `fk_user_address_customer`
    FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `ck_user_address_is_default` CHECK (`is_default` IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '顾客收货地址簿';

-- ---------------------------------------------------------------------
-- 4. category 分类(商品 Catalog 上下文)
--    菜品分类与套餐分类同表,靠 type 区分;同类型下名称唯一
-- ---------------------------------------------------------------------
CREATE TABLE `category` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT               COMMENT '主键',
  `name`       VARCHAR(32) NOT NULL                              COMMENT '分类名称,同 type 内唯一',
  `type`       VARCHAR(16) NOT NULL                              COMMENT '分类类型:DISH 菜品分类 / SETMEAL 套餐分类',
  `sort_order` INT         NOT NULL DEFAULT 0                    COMMENT '排序值,越小越靠前',
  `status`     TINYINT     NOT NULL DEFAULT 1                    COMMENT '状态:1 启用 / 0 禁用',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` BIGINT      NULL                                  COMMENT '创建人 employee.id',
  `updated_by` BIGINT      NULL                                  COMMENT '最后更新人 employee.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_type_name` (`type`, `name`),
  KEY `idx_category_type_status_sort` (`type`, `status`, `sort_order`),
  CONSTRAINT `ck_category_type`   CHECK (`type` IN ('DISH', 'SETMEAL')),
  CONSTRAINT `ck_category_status` CHECK (`status` IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '商品分类(菜品/套餐)';

-- ---------------------------------------------------------------------
-- 5. dish 菜品(商品 Catalog 上下文)
-- ---------------------------------------------------------------------
CREATE TABLE `dish` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键',
  `category_id` BIGINT       NOT NULL                            COMMENT '所属分类 category.id,必须是 type=DISH 的分类(应用层保证)',
  `name`        VARCHAR(64)  NOT NULL                            COMMENT '菜品名称,同分类内唯一',
  `price_cents` BIGINT       NOT NULL                            COMMENT '售价,单位:分',
  `image_url`   VARCHAR(255) NULL                                COMMENT '图片地址',
  `description` VARCHAR(255) NULL                                COMMENT '描述',
  `status`      TINYINT      NOT NULL DEFAULT 0                  COMMENT '状态:1 起售 / 0 停售(新建默认停售,上架需显式操作)',
  `sort_order`  INT          NOT NULL DEFAULT 0                  COMMENT '排序值,越小越靠前',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`  BIGINT       NULL                                COMMENT '创建人 employee.id',
  `updated_by`  BIGINT       NULL                                COMMENT '最后更新人 employee.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dish_category_name` (`category_id`, `name`),
  KEY `idx_dish_category_status_sort` (`category_id`, `status`, `sort_order`),
  CONSTRAINT `fk_dish_category`
    FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_dish_status` CHECK (`status` IN (0, 1)),
  CONSTRAINT `ck_dish_price_cents` CHECK (`price_cents` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '菜品';

-- ---------------------------------------------------------------------
-- 6. dish_flavor 菜品口味(商品 Catalog 上下文)
--    options 存 JSON 数组,替代旧库逗号拼接字符串
--    无独立 sort_order 之外的状态;删除菜品连带删除口味
-- ---------------------------------------------------------------------
CREATE TABLE `dish_flavor` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT               COMMENT '主键',
  `dish_id`    BIGINT      NOT NULL                              COMMENT '所属菜品 dish.id',
  `name`       VARCHAR(32) NOT NULL                              COMMENT '口味维度名,如 辣度 / 忌口 / 甜度;同一菜品内唯一',
  `options`    JSON        NOT NULL                              COMMENT '选项数组,必须是 JSON 数组,如 ["不辣","微辣","中辣","重辣"]',
  `sort_order` INT         NOT NULL DEFAULT 0                    COMMENT '排序值,越小越靠前',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` BIGINT      NULL                                  COMMENT '创建人 employee.id',
  `updated_by` BIGINT      NULL                                  COMMENT '最后更新人 employee.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dish_flavor_dish_name` (`dish_id`, `name`),
  CONSTRAINT `fk_dish_flavor_dish`
    FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '菜品口味配置';

-- ---------------------------------------------------------------------
-- 7. setmeal 套餐(商品 Catalog 上下文)
--    套餐是独立商品:可有自己的图片、描述、手工定价
-- ---------------------------------------------------------------------
CREATE TABLE `setmeal` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT             COMMENT '主键',
  `category_id` BIGINT       NOT NULL                            COMMENT '所属分类 category.id,必须是 type=SETMEAL 的分类(应用层保证)',
  `name`        VARCHAR(64)  NOT NULL                            COMMENT '套餐名称,同分类内唯一',
  `price_cents` BIGINT       NOT NULL                            COMMENT '套餐售价(手工定价),单位:分',
  `image_url`   VARCHAR(255) NULL                                COMMENT '图片地址',
  `description` VARCHAR(255) NULL                                COMMENT '描述',
  `status`      TINYINT      NOT NULL DEFAULT 0                  COMMENT '状态:1 起售 / 0 停售',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`  BIGINT       NULL                                COMMENT '创建人 employee.id',
  `updated_by`  BIGINT       NULL                                COMMENT '最后更新人 employee.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setmeal_category_name` (`category_id`, `name`),
  KEY `idx_setmeal_category_status` (`category_id`, `status`),
  CONSTRAINT `fk_setmeal_category`
    FOREIGN KEY (`category_id`) REFERENCES `category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_setmeal_status` CHECK (`status` IN (0, 1)),
  CONSTRAINT `ck_setmeal_price_cents` CHECK (`price_cents` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '套餐';

-- ---------------------------------------------------------------------
-- 8. setmeal_item 套餐组成(商品 Catalog 上下文)
--    菜品不可被删(有套餐引用时 RESTRICT);删套餐连带删组成
-- ---------------------------------------------------------------------
CREATE TABLE `setmeal_item` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT                  COMMENT '主键',
  `setmeal_id` BIGINT   NOT NULL                                 COMMENT '所属套餐 setmeal.id',
  `dish_id`    BIGINT   NOT NULL                                 COMMENT '所含菜品 dish.id',
  `copies`     INT      NOT NULL DEFAULT 1                       COMMENT '份数,>=1',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` BIGINT   NULL                                     COMMENT '创建人 employee.id',
  `updated_by` BIGINT   NULL                                     COMMENT '最后更新人 employee.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setmeal_item_setmeal_dish` (`setmeal_id`, `dish_id`),
  KEY `idx_setmeal_item_dish` (`dish_id`),
  CONSTRAINT `fk_setmeal_item_setmeal`
    FOREIGN KEY (`setmeal_id`) REFERENCES `setmeal` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_setmeal_item_dish`
    FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_setmeal_item_copies` CHECK (`copies` >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '套餐组成明细';

-- ---------------------------------------------------------------------
-- 9. cart_item 购物车(购物车 Cart 上下文)
--    刻意不冗余商品名/图/价,读取时联表取实时值(领域文档 D4)
--
--    唯一键 NULL 陷阱的解法:
--      dish_id / setmeal_id 按类型二选一,另一个必为 NULL;而 MySQL 唯一索引
--      视多个 NULL 互不相等,直接对 (customer_id,item_type,dish_id,setmeal_id,
--      flavor_key) 建唯一键会完全失效(同一菜同口味可插入任意多行)。
--      因此新增两个生成列 dish_ref_id / setmeal_ref_id = IFNULL(x, 0),
--      唯一键建在生成列上,用 0 表示"不适用",使唯一性真正生效。
--      生成列由 MySQL 计算,应用层不可写,不会与真实列漂移。
--      必须用 VIRTUAL 而不是 STORED:MySQL 8.4 禁止"以带 ON DELETE CASCADE
--      外键的列作为基列的 STORED 生成列"(实测报 ERROR 1215),
--      而 VIRTUAL 生成列不受此限制,且同样可以承载 UNIQUE 索引。
-- ---------------------------------------------------------------------
CREATE TABLE `cart_item` (
  `id`              BIGINT      NOT NULL AUTO_INCREMENT          COMMENT '主键',
  `customer_id`     BIGINT      NOT NULL                         COMMENT '所属顾客 customer.id',
  `item_type`       VARCHAR(16) NOT NULL                         COMMENT '商品类型:DISH 菜品 / SETMEAL 套餐',
  `dish_id`         BIGINT      NULL                             COMMENT '菜品 dish.id,item_type=DISH 时必填',
  `setmeal_id`      BIGINT      NULL                             COMMENT '套餐 setmeal.id,item_type=SETMEAL 时必填',
  `quantity`        INT         NOT NULL DEFAULT 1               COMMENT '数量,>=1',
  `flavor_choice`   JSON        NULL                             COMMENT '顾客选中的口味,如 [{"name":"辣度","option":"微辣"}];未选为 NULL',
  `flavor_key`      VARCHAR(64) NOT NULL DEFAULT ''              COMMENT 'flavor_choice 的归一化哈希(稳定序),未选口味时为固定空串',
  `dish_ref_id`     BIGINT GENERATED ALWAYS AS (IFNULL(`dish_id`, 0)) VIRTUAL    COMMENT '唯一键归一化列:=IFNULL(dish_id,0),VIRTUAL 才能与 CASCADE 外键共存',
  `setmeal_ref_id`  BIGINT GENERATED ALWAYS AS (IFNULL(`setmeal_id`, 0)) VIRTUAL COMMENT '唯一键归一化列:=IFNULL(setmeal_id,0),VIRTUAL 才能与 CASCADE 外键共存',
  `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`      BIGINT      NULL                             COMMENT '创建人(customer.id)',
  `updated_by`      BIGINT      NULL                             COMMENT '最后更新人(customer.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_item_identity`
    (`customer_id`, `item_type`, `dish_ref_id`, `setmeal_ref_id`, `flavor_key`),
  KEY `idx_cart_item_dish` (`dish_id`),
  KEY `idx_cart_item_setmeal` (`setmeal_id`),
  CONSTRAINT `fk_cart_item_customer`
    FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_cart_item_dish`
    FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_cart_item_setmeal`
    FOREIGN KEY (`setmeal_id`) REFERENCES `setmeal` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `ck_cart_item_ref` CHECK (
    (`item_type` = 'DISH'     AND `dish_id`    IS NOT NULL AND `setmeal_id` IS NULL)
    OR
    (`item_type` = 'SETMEAL' AND `setmeal_id` IS NOT NULL AND `dish_id`    IS NULL)
  ),
  CONSTRAINT `ck_cart_item_item_type` CHECK (`item_type` IN ('DISH', 'SETMEAL')),
  CONSTRAINT `ck_cart_item_quantity`  CHECK (`quantity` >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '购物车行(同菜同口味唯一,加购即合并数量)';

-- ---------------------------------------------------------------------
-- 10. orders 订单(订单 Ordering 上下文)
--     表名用 orders:order 是 SQL 保留字
--     地址快照 + 状态时间戳:每个状态一列,不再复用语义混乱的四列
--     source_address_id 刻意不建外键:地址可被顾客删除,订单靠快照存活
-- ---------------------------------------------------------------------
CREATE TABLE `orders` (
  `id`                     BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
  `order_no`               VARCHAR(32)  NOT NULL                 COMMENT '业务单号,全局唯一,顾客可见',
  `customer_id`            BIGINT       NOT NULL                 COMMENT '下单顾客 customer.id',
  `status`                 VARCHAR(24)  NOT NULL DEFAULT 'PENDING_PAYMENT'
                                                                 COMMENT '订单状态:PENDING_PAYMENT/PENDING_ACCEPTANCE/ACCEPTED/DELIVERING/COMPLETED/CANCELLED',
  `total_amount_cents`     BIGINT       NOT NULL DEFAULT 0       COMMENT '商品合计,单位:分',
  `pack_amount_cents`      BIGINT       NOT NULL DEFAULT 0       COMMENT '打包费,单位:分',
  `delivery_amount_cents`  BIGINT       NOT NULL DEFAULT 0       COMMENT '配送费,单位:分',
  `discount_amount_cents`  BIGINT       NOT NULL DEFAULT 0       COMMENT '优惠金额,v2 预留,默认 0',
  `pay_amount_cents`       BIGINT       NOT NULL DEFAULT 0       COMMENT '实付 = total + pack + delivery - discount,单位:分',
  `pay_status`             VARCHAR(16)  NOT NULL DEFAULT 'UNPAID'
                                                                 COMMENT '支付状态:UNPAID/PAID/REFUNDED/PARTIAL_REFUNDED',
  `pay_method`             VARCHAR(16)  NULL                     COMMENT '支付方式:WECHAT 微信支付 / MOCK 模拟支付;未支付为 NULL',
  `consignee`              VARCHAR(32)  NOT NULL                 COMMENT '【地址快照】收货人',
  `phone`                  VARCHAR(20)  NOT NULL                 COMMENT '【地址快照】收货人手机号',
  `province`               VARCHAR(32)  NOT NULL                 COMMENT '【地址快照】省',
  `city`                   VARCHAR(32)  NOT NULL                 COMMENT '【地址快照】市',
  `district`               VARCHAR(32)  NOT NULL                 COMMENT '【地址快照】区/县',
  `detail`                 VARCHAR(255) NOT NULL                 COMMENT '【地址快照】详细地址',
  `source_address_id`      BIGINT       NULL                     COMMENT '下单时选用的 user_address.id,仅溯源,不建外键(地址可能已删除)',
  `remark`                 VARCHAR(255) NULL                     COMMENT '顾客备注',
  `tableware_count`        INT          NOT NULL DEFAULT 1       COMMENT '餐具份数,默认 1',
  `placed_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `estimated_delivery_at`  DATETIME     NULL                     COMMENT '预计送达时间',
  `paid_at`                DATETIME     NULL                     COMMENT '支付成功时间(首次进入 PAID)',
  `accepted_at`            DATETIME     NULL                     COMMENT '商家接单时间(首次进入 ACCEPTED)',
  `delivering_at`          DATETIME     NULL                     COMMENT '开始派送时间(首次进入 DELIVERING)',
  `completed_at`           DATETIME     NULL                     COMMENT '完成时间(首次进入 COMPLETED)',
  `cancelled_at`           DATETIME     NULL                     COMMENT '取消时间(首次进入 CANCELLED)',
  `cancel_side`            VARCHAR(16)  NULL                     COMMENT '取消方:CUSTOMER 顾客 / MERCHANT 商家 / SYSTEM 系统;未取消为 NULL',
  `cancel_reason`          VARCHAR(255) NULL                     COMMENT '取消原因;未取消为 NULL',
  `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`             BIGINT       NULL                     COMMENT '创建人(customer.id)',
  `updated_by`             BIGINT       NULL                     COMMENT '最后更新人(customer.id 或 employee.id)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  KEY `idx_orders_status_placed_at` (`status`, `placed_at`),
  KEY `idx_orders_customer_status_placed_at` (`customer_id`, `status`, `placed_at`),
  KEY `idx_orders_paid_at` (`paid_at`),
  CONSTRAINT `fk_orders_customer`
    FOREIGN KEY (`customer_id`) REFERENCES `customer` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_orders_status` CHECK (
    `status` IN ('PENDING_PAYMENT', 'PENDING_ACCEPTANCE', 'ACCEPTED', 'DELIVERING', 'COMPLETED', 'CANCELLED')
  ),
  CONSTRAINT `ck_orders_pay_status` CHECK (
    `pay_status` IN ('UNPAID', 'PAID', 'REFUNDED', 'PARTIAL_REFUNDED')
  ),
  CONSTRAINT `ck_orders_pay_method` CHECK (`pay_method` IS NULL OR `pay_method` IN ('WECHAT', 'MOCK')),
  CONSTRAINT `ck_orders_cancel_side` CHECK (`cancel_side` IS NULL OR `cancel_side` IN ('CUSTOMER', 'MERCHANT', 'SYSTEM')),
  CONSTRAINT `ck_orders_amount` CHECK (
    `total_amount_cents` >= 0 AND `pack_amount_cents` >= 0 AND `delivery_amount_cents` >= 0
    AND `discount_amount_cents` >= 0 AND `pay_amount_cents` >= 0
  ),
  CONSTRAINT `ck_orders_tableware_count` CHECK (`tableware_count` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单主表(含地址快照与各状态时间戳)';

-- ---------------------------------------------------------------------
-- 11. order_item 订单明细(订单 Ordering 上下文)
--     全系统唯一允许存快照的地方:订单是不可变凭证
--     dish_id / setmeal_id 可空 + ON DELETE SET NULL:商品真删后明细靠快照存活。
--     正因为会被置 NULL,这里刻意不加"类型与引用必须匹配"的 CHECK
--     (加了会让删除商品的操作直接失败,与 SET NULL 语义冲突)。
-- ---------------------------------------------------------------------
CREATE TABLE `order_item` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT       COMMENT '主键',
  `order_id`          BIGINT       NOT NULL                      COMMENT '所属订单 orders.id',
  `item_type`         VARCHAR(16)  NOT NULL                      COMMENT '商品类型:DISH 菜品 / SETMEAL 套餐',
  `dish_id`           BIGINT       NULL                          COMMENT '原菜品 dish.id,商品被删除后为 NULL(不可变快照仍完整)',
  `setmeal_id`        BIGINT       NULL                          COMMENT '原套餐 setmeal.id,商品被删除后为 NULL',
  `name_snapshot`     VARCHAR(64)  NOT NULL                      COMMENT '【快照】下单时商品名称',
  `image_snapshot`    VARCHAR(255) NULL                          COMMENT '【快照】下单时图片地址',
  `unit_price_cents`  BIGINT       NOT NULL                      COMMENT '【快照】下单时单价,单位:分',
  `quantity`          INT          NOT NULL                      COMMENT '【快照】数量,>=1',
  `amount_cents`      BIGINT       NOT NULL                      COMMENT '【快照】小计 = 单价 x 数量,单位:分',
  `flavor_snapshot`   JSON         NULL                          COMMENT '【快照】下单时选中的口味;未选为 NULL',
  `combo_snapshot`    JSON         NULL                          COMMENT '【快照】套餐内含菜品明细;菜品行(含套餐)为 NULL',
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`        BIGINT       NULL                          COMMENT '创建人(customer.id)',
  `updated_by`        BIGINT       NULL                          COMMENT '最后更新人(customer.id 或 employee.id)',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`),
  KEY `idx_order_item_dish` (`dish_id`),
  KEY `idx_order_item_setmeal` (`setmeal_id`),
  CONSTRAINT `fk_order_item_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_order_item_dish`
    FOREIGN KEY (`dish_id`) REFERENCES `dish` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `fk_order_item_setmeal`
    FOREIGN KEY (`setmeal_id`) REFERENCES `setmeal` (`id`) ON DELETE SET NULL ON UPDATE RESTRICT,
  CONSTRAINT `ck_order_item_item_type` CHECK (`item_type` IN ('DISH', 'SETMEAL')),
  CONSTRAINT `ck_order_item_quantity`  CHECK (`quantity` >= 1),
  CONSTRAINT `ck_order_item_amount`    CHECK (`unit_price_cents` >= 0 AND `amount_cents` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '订单明细(不可变快照)';

-- ---------------------------------------------------------------------
-- 12. payment 支付流水(订单 Ordering 上下文)
--     transaction_id 唯一 = 支付回调幂等键;唯一索引允许多个 NULL,
--     因此"多笔未完成的支付尝试"可以共存,而同一个平台单号只能落一行。
-- ---------------------------------------------------------------------
CREATE TABLE `payment` (
  `id`             BIGINT      NOT NULL AUTO_INCREMENT           COMMENT '主键',
  `order_id`       BIGINT      NOT NULL                          COMMENT '所属订单 orders.id',
  `order_no`       VARCHAR(32) NOT NULL                          COMMENT '冗余业务单号,回调只带单号时定位订单用',
  `channel`        VARCHAR(16) NOT NULL                          COMMENT '支付渠道:WECHAT 微信支付 / MOCK 模拟支付',
  `status`         VARCHAR(16) NOT NULL DEFAULT 'PENDING'        COMMENT '支付状态:PENDING 待支付 / SUCCESS 成功 / FAILED 失败 / CLOSED 已关闭',
  `amount_cents`   BIGINT      NOT NULL                          COMMENT '支付金额,单位:分',
  `transaction_id` VARCHAR(64) NULL                              COMMENT '支付平台单号,唯一(幂等键),未成功为 NULL',
  `prepay_id`      VARCHAR(64) NULL                              COMMENT '微信预支付交易会话标识',
  `paid_at`        DATETIME    NULL                              COMMENT '支付成功时间,未成功为 NULL',
  `raw_notify`     JSON        NULL                              COMMENT '支付回调原始报文,排障留痕',
  `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`     BIGINT      NULL                              COMMENT '创建人(customer.id);支付平台写入为 NULL',
  `updated_by`     BIGINT      NULL                              COMMENT '最后更新人;支付平台写入为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_transaction_id` (`transaction_id`),
  KEY `idx_payment_order` (`order_id`, `status`),
  KEY `idx_payment_order_no` (`order_no`),
  CONSTRAINT `fk_payment_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_payment_channel` CHECK (`channel` IN ('WECHAT', 'MOCK')),
  CONSTRAINT `ck_payment_status`  CHECK (`status` IN ('PENDING', 'SUCCESS', 'FAILED', 'CLOSED')),
  CONSTRAINT `ck_payment_amount`  CHECK (`amount_cents` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '支付流水';

-- ---------------------------------------------------------------------
-- 13. refund 退款流水(订单 Ordering 上下文)
--     v2 只覆盖"整单全额退",但仍独立成表以支持失败重试与回调重放
-- ---------------------------------------------------------------------
CREATE TABLE `refund` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT            COMMENT '主键',
  `order_id`     BIGINT       NOT NULL                           COMMENT '所属订单 orders.id',
  `payment_id`   BIGINT       NOT NULL                           COMMENT '被退款的支付流水 payment.id',
  `refund_no`    VARCHAR(32)  NOT NULL                           COMMENT '退款单号,全局唯一',
  `amount_cents` BIGINT       NOT NULL                           COMMENT '退款金额,单位:分',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'PENDING'         COMMENT '退款状态:PENDING 处理中 / SUCCESS 成功 / FAILED 失败',
  `reason`       VARCHAR(255) NULL                               COMMENT '退款原因描述',
  `reason_type`  VARCHAR(32)  NULL                               COMMENT '退款原因分类:MERCHANT_REJECT 商家拒单 / MERCHANT_CANCEL 商家取消 / CUSTOMER_APPLY 顾客申请 / OTHER 其它',
  `refunded_at`  DATETIME     NULL                               COMMENT '退款成功时间,未成功为 NULL',
  `raw_notify`   JSON         NULL                               COMMENT '退款回调原始报文,排障留痕',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by`   BIGINT       NULL                               COMMENT '发起人(employee.id / customer.id);系统写入为 NULL',
  `updated_by`   BIGINT       NULL                               COMMENT '最后更新人;支付平台写入为 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_refund_no` (`refund_no`),
  KEY `idx_refund_order` (`order_id`),
  KEY `idx_refund_payment` (`payment_id`),
  CONSTRAINT `fk_refund_order`
    FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_refund_payment`
    FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `ck_refund_status` CHECK (`status` IN ('PENDING', 'SUCCESS', 'FAILED')),
  CONSTRAINT `ck_refund_reason_type` CHECK (
    `reason_type` IS NULL OR `reason_type` IN ('MERCHANT_REJECT', 'MERCHANT_CANCEL', 'CUSTOMER_APPLY', 'OTHER')
  ),
  CONSTRAINT `ck_refund_amount` CHECK (`amount_cents` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '退款流水';

-- ---------------------------------------------------------------------
-- 14. shop_status 店铺营业状态(门店 Shop 上下文)
--     单行配置表:id 固定为 1,由应用层保证不新增第二行
--     打烊是显式状态,不由 open_time/close_time 推导(仅作展示)
-- ---------------------------------------------------------------------
CREATE TABLE `shop_status` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT              COMMENT '主键,固定为 1',
  `is_open`    TINYINT      NOT NULL DEFAULT 1                   COMMENT '营业状态:1 营业中 / 0 已打烊',
  `open_time`  TIME         NULL                                 COMMENT '每日开店时间,仅展示用',
  `close_time` TIME         NULL                                 COMMENT '每日打烊时间,仅展示用',
  `notice`     VARCHAR(255) NULL                                 COMMENT '店铺公告',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `created_by` BIGINT       NULL                                 COMMENT '创建人 employee.id',
  `updated_by` BIGINT       NULL                                 COMMENT '最后更新人 employee.id',
  PRIMARY KEY (`id`),
  CONSTRAINT `ck_shop_status_is_open` CHECK (`is_open` IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '店铺营业状态(单行配置)';

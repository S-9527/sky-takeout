-- 苍穹外卖数据库初始化脚本

-- 员工信息表
CREATE TABLE IF NOT EXISTS employee (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32) NOT NULL DEFAULT '' COMMENT '姓名',
    username    VARCHAR(32) NOT NULL DEFAULT '' COMMENT '用户名',
    password    VARCHAR(64) NOT NULL DEFAULT '' COMMENT '密码',
    phone       VARCHAR(11) NOT NULL DEFAULT '' COMMENT '手机号',
    sex         VARCHAR(2)  NOT NULL DEFAULT '0' COMMENT '性别',
    id_number   VARCHAR(18) NOT NULL DEFAULT '' COMMENT '身份证号',
    status      INT         NOT NULL DEFAULT '1' COMMENT '账号状态 1正常 0锁定',
    create_time DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME    DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT      DEFAULT NULL COMMENT '创建人',
    update_user BIGINT      DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    UNIQUE KEY idx_username (username)
) ENGINE = InnoDB AUTO_INCREMENT = 10 DEFAULT CHARSET = utf8mb4 COMMENT = '员工信息';

-- 分类表
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    type        INT         DEFAULT NULL COMMENT '类型 1菜品分类 2套餐分类',
    name        VARCHAR(32) NOT NULL COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT '0' COMMENT '顺序',
    status      INT         DEFAULT NULL COMMENT '分类状态 0禁用 1启用',
    create_time DATETIME    DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME    DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT      DEFAULT NULL COMMENT '创建人',
    update_user BIGINT      DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    KEY idx_type (type)
) ENGINE = InnoDB AUTO_INCREMENT = 16 DEFAULT CHARSET = utf8mb4 COMMENT = '菜品及套餐分类';

-- 菜品表
CREATE TABLE IF NOT EXISTS dish (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)  NOT NULL COMMENT '菜品名称',
    category_id BIGINT       NOT NULL COMMENT '菜品分类id',
    price       DECIMAL(10, 2) NULL COMMENT '菜品价格',
    image       VARCHAR(255) DEFAULT NULL COMMENT '图片',
    description VARCHAR(255) DEFAULT NULL COMMENT '描述信息',
    status      INT          DEFAULT '1' COMMENT '0停售 1起售',
    create_time DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME     DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT       DEFAULT NULL COMMENT '创建人',
    update_user BIGINT       DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id)
) ENGINE = InnoDB AUTO_INCREMENT = 50 DEFAULT CHARSET = utf8mb4 COMMENT = '菜品';

-- 菜品口味表
CREATE TABLE IF NOT EXISTS dish_flavor (
    id     BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    dish_id BIGINT     DEFAULT NULL COMMENT '菜品id',
    name   VARCHAR(32) DEFAULT NULL COMMENT '口味名称',
    value  VARCHAR(255) DEFAULT NULL COMMENT '口味数据list',
    PRIMARY KEY (id),
    KEY idx_dish_id (dish_id)
) ENGINE = InnoDB AUTO_INCREMENT = 98 DEFAULT CHARSET = utf8mb4 COMMENT = '菜品口味';

-- 套餐表
CREATE TABLE IF NOT EXISTS setmeal (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT        NOT NULL COMMENT '分类id',
    name        VARCHAR(32)   NOT NULL COMMENT '套餐名称',
    price       DECIMAL(10, 2) NULL COMMENT '套餐价格',
    status      INT           DEFAULT '1' COMMENT '状态 0停用 1启用',
    description VARCHAR(255)  DEFAULT NULL COMMENT '描述信息',
    image       VARCHAR(255)  DEFAULT NULL COMMENT '图片',
    create_time DATETIME      DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME      DEFAULT NULL COMMENT '更新时间',
    create_user BIGINT        DEFAULT NULL COMMENT '创建人',
    update_user BIGINT        DEFAULT NULL COMMENT '修改人',
    PRIMARY KEY (id),
    KEY idx_category_id (category_id)
) ENGINE = InnoDB AUTO_INCREMENT = 20 DEFAULT CHARSET = utf8mb4 COMMENT = '套餐';

-- 套餐菜品关系表
CREATE TABLE IF NOT EXISTS setmeal_dish (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    setmeal_id BIGINT       DEFAULT NULL COMMENT '套餐id',
    dish_id    BIGINT       DEFAULT NULL COMMENT '菜品id',
    name       VARCHAR(32)  DEFAULT NULL COMMENT '菜品名称(冗余字段)',
    price      DECIMAL(10, 2) DEFAULT NULL COMMENT '菜品原价',
    copies     INT          DEFAULT NULL COMMENT '份数',
    PRIMARY KEY (id),
    KEY idx_setmeal_id (setmeal_id)
) ENGINE = InnoDB AUTO_INCREMENT = 42 DEFAULT CHARSET = utf8mb4 COMMENT = '套餐菜品关系';

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid      VARCHAR(45)  DEFAULT NULL COMMENT '微信用户唯一标识',
    name        VARCHAR(32)  DEFAULT NULL COMMENT '姓名',
    phone       VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
    sex         VARCHAR(2)   DEFAULT NULL COMMENT '性别',
    id_number   VARCHAR(18)  DEFAULT NULL COMMENT '身份证号',
    avatar      VARCHAR(500) DEFAULT NULL COMMENT '头像',
    create_time DATETIME     DEFAULT NULL COMMENT '注册时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB AUTO_INCREMENT = 40 DEFAULT CHARSET = utf8mb4 COMMENT = '用户信息';

-- 购物车表
CREATE TABLE IF NOT EXISTS shopping_cart (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name         VARCHAR(32)  DEFAULT NULL COMMENT '商品名称',
    user_id      BIGINT       DEFAULT NULL COMMENT '用户id',
    dish_id      BIGINT       DEFAULT NULL COMMENT '菜品id',
    setmeal_id   BIGINT       DEFAULT NULL COMMENT '套餐id',
    dish_flavor  VARCHAR(50)  DEFAULT NULL COMMENT '口味',
    number       INT          DEFAULT '1' COMMENT '数量',
    amount       DECIMAL(10, 2) DEFAULT NULL COMMENT '金额',
    image        VARCHAR(255) DEFAULT NULL COMMENT '图片',
    create_time  DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB AUTO_INCREMENT = 4 DEFAULT CHARSET = utf8mb4 COMMENT = '购物车';

-- 地址簿表
CREATE TABLE IF NOT EXISTS address_book (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT      DEFAULT NULL COMMENT '用户id',
    consignee     VARCHAR(50) DEFAULT NULL COMMENT '收货人',
    sex           VARCHAR(2)  DEFAULT NULL COMMENT '性别',
    phone         VARCHAR(11) NOT NULL DEFAULT '' COMMENT '手机号',
    province_code VARCHAR(12) DEFAULT NULL COMMENT '省级区划编号',
    province_name VARCHAR(32) DEFAULT NULL COMMENT '省级名称',
    city_code     VARCHAR(12) DEFAULT NULL COMMENT '市级区划编号',
    city_name     VARCHAR(32) DEFAULT NULL COMMENT '市级名称',
    district_code VARCHAR(12) DEFAULT NULL COMMENT '区级区划编号',
    district_name VARCHAR(32) DEFAULT NULL COMMENT '区级名称',
    detail        VARCHAR(200) DEFAULT NULL COMMENT '详细地址',
    label         VARCHAR(100) DEFAULT NULL COMMENT '标签',
    is_default    TINYINT(1)  NOT NULL DEFAULT '0' COMMENT '默认地址 0否 1是',
    PRIMARY KEY (id)
) ENGINE = InnoDB AUTO_INCREMENT = 4 DEFAULT CHARSET = utf8mb4 COMMENT = '地址簿';

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id                     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    number                 VARCHAR(50)  DEFAULT NULL COMMENT '订单号',
    status                 INT          NOT NULL DEFAULT '1' COMMENT '订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消 7退款',
    user_id                BIGINT       NOT NULL DEFAULT '0' COMMENT '下单用户id',
    address_book_id        BIGINT       NOT NULL DEFAULT '0' COMMENT '地址id',
    order_time             DATETIME     NOT NULL COMMENT '下单时间',
    checkout_time          DATETIME     DEFAULT NULL COMMENT '结账时间',
    pay_method             INT          NOT NULL DEFAULT '1' COMMENT '支付方式 1微信 2支付宝',
    pay_status             TINYINT      NOT NULL DEFAULT '0' COMMENT '支付状态 0未支付 1已支付 2退款',
    amount                 DECIMAL(10, 2) NOT NULL DEFAULT '0.00' COMMENT '实收金额',
    remark                 VARCHAR(100) DEFAULT NULL COMMENT '备注',
    phone                  VARCHAR(11)  DEFAULT NULL COMMENT '手机号',
    address                VARCHAR(255) DEFAULT NULL COMMENT '地址',
    user_name              VARCHAR(32)  DEFAULT NULL COMMENT '用户名',
    consignee              VARCHAR(32)  DEFAULT NULL COMMENT '收货人',
    cancel_reason          VARCHAR(255) DEFAULT NULL COMMENT '订单取消原因',
    rejection_reason       VARCHAR(255) DEFAULT NULL COMMENT '订单拒绝原因',
    cancel_time            DATETIME     DEFAULT NULL COMMENT '订单取消时间',
    estimated_delivery_time DATETIME    DEFAULT NULL COMMENT '预计送达时间',
    delivery_status        TINYINT      NOT NULL DEFAULT '1' COMMENT '配送状态 1立即送出 0选择具体时间',
    delivery_time          DATETIME     DEFAULT NULL COMMENT '送达时间',
    pack_amount            INT          NOT NULL DEFAULT '0' COMMENT '打包费',
    tableware_number       INT          NOT NULL DEFAULT '1' COMMENT '餐具数量',
    tableware_status       TINYINT      NOT NULL DEFAULT '1' COMMENT '餐具数量状态 1按餐量提供 0选择具体数量',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_number (number)
) ENGINE = InnoDB AUTO_INCREMENT = 53 DEFAULT CHARSET = utf8mb4 COMMENT = '订单';

-- 订单明细表
CREATE TABLE IF NOT EXISTS order_detail (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)  DEFAULT NULL COMMENT '名称',
    order_id    BIGINT       NOT NULL DEFAULT '0' COMMENT '订单id',
    dish_id     BIGINT       DEFAULT NULL COMMENT '菜品id',
    setmeal_id  BIGINT       DEFAULT NULL COMMENT '套餐id',
    dish_flavor VARCHAR(50)  DEFAULT NULL COMMENT '口味',
    number      INT          NOT NULL DEFAULT '1' COMMENT '数量',
    amount      DECIMAL(10, 2) NOT NULL DEFAULT '0.00' COMMENT '金额',
    image       VARCHAR(255) DEFAULT NULL COMMENT '图片',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE = InnoDB AUTO_INCREMENT = 100 DEFAULT CHARSET = utf8mb4 COMMENT = '订单明细';

-- 苍穹外卖演示数据

-- 分类
INSERT INTO category (id, type, name, sort, status, create_time, update_time, create_user, update_user) VALUES
(1, 1, '热菜', 1, 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(2, 1, '凉菜', 2, 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(3, 1, '主食', 3, 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(4, 1, '酒水饮料', 4, 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(5, 2, '商务套餐', 5, 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(6, 2, '家庭套餐', 6, 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1);

-- 菜品
INSERT INTO dish (id, name, category_id, price, image, description, status, create_time, update_time, create_user, update_user) VALUES
(1, '宫保鸡丁', 1, 38.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/gongbaojiding.jpg', '经典川菜，鸡肉鲜嫩，花生香脆', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(2, '鱼香肉丝', 1, 32.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/yuxiangrousi.jpg', '酸甜微辣，下饭首选', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(3, '麻婆豆腐', 1, 28.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/mapodoufu.jpg', '麻辣鲜香，豆腐嫩滑', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(4, '红烧肉', 1, 48.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/hongshaorou.jpg', '肥而不腻，入口即化', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(5, '凉拌黄瓜', 2, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/liangbanhuanggua.jpg', '清爽开胃，爽脆可口', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(6, '拍黄瓜', 2, 16.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/paihuanggua.jpg', '蒜香浓郁，解腻好伴侣', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(7, '凉拌木耳', 2, 20.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/liangbanmuer.jpg', '爽脆营养，低脂健康', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(8, '蛋炒饭', 3, 22.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/danchaofan.jpg', '粒粒分明，烟火气十足', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(9, '番茄鸡蛋面', 3, 25.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/fanqiejidanmian.jpg', '汤鲜味美，营养家常', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(10, '重庆小面', 3, 20.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/chongqingxiaomian.jpg', '麻辣劲道，地道风味', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(11, '鲜榨橙汁', 4, 15.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/xianzhachengzhi.jpg', '100%鲜榨，维C满满', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(12, '可口可乐', 4, 8.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/kekoukele.jpg', '冰爽畅快', 1, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(13, '冰红茶', 4, 6.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/binghongcha.jpg', '消暑解渴', 0, '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1);

-- 菜品口味
INSERT INTO dish_flavor (id, dish_id, name, value) VALUES
(1, 1, '辣度', '["不辣","微辣","中辣","特辣"]'),
(2, 2, '口感', '["正常","偏软","偏硬"]'),
(3, 3, '辣度', '["微辣","中辣","特辣"]'),
(4, 4, '口味', '["偏甜","偏咸","正常"]'),
(5, 8, '份量', '["大份","小份"]'),
(6, 9, '份量', '["大份","小份"]'),
(7, 10, '辣度', '["不辣","微辣","中辣","特辣"]');

-- 套餐
INSERT INTO setmeal (id, category_id, name, price, status, description, image, create_time, update_time, create_user, update_user) VALUES
(1, 5, '商务双人餐', 99.00, 1, '超值二人工作餐', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/shangwushuangren.jpg', '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(2, 6, '家庭欢聚四人餐', 168.00, 1, '全家共享美味', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/jiatingjuji.jpg', '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1),
(3, 5, '单人便捷餐', 39.00, 1, '一人食也精彩', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/danrenbianjie.jpg', '2026-08-20 10:00:00', '2026-08-20 10:00:00', 1, 1);

-- 套餐-菜品关系
INSERT INTO setmeal_dish (id, setmeal_id, dish_id, name, price, copies) VALUES
(1, 1, 1, '宫保鸡丁', 38.00, 1),
(2, 1, 8, '蛋炒饭', 22.00, 2),
(3, 1, 11, '鲜榨橙汁', 15.00, 2),
(4, 2, 1, '宫保鸡丁', 38.00, 1),
(5, 2, 3, '麻婆豆腐', 28.00, 1),
(6, 2, 4, '红烧肉', 48.00, 1),
(7, 2, 8, '蛋炒饭', 22.00, 2),
(8, 2, 6, '拍黄瓜', 16.00, 1),
(9, 3, 10, '重庆小面', 20.00, 1),
(10, 3, 12, '可口可乐', 8.00, 1);

-- 用户
INSERT INTO `user` (id, openid, name, phone, sex, id_number, avatar, create_time) VALUES
(1, 'oX6Dsb1dBrYbRBtK0d3uZTcB3aUc', '张三', '13800138000', '1', '110101199001011234', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/avatar/zhangsan.jpg', '2026-08-25 09:31:00'),
(2, 'oX6Dsb1dBrYbRBtK0d3uZTcB3aUd', '李四', '13900139000', '0', '110101199202022345', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/avatar/lisi.jpg', '2026-08-28 14:02:00'),
(3, 'oX6Dsb1dBrYbRBtK0d3uZTcB3aUe', '王五', '13700137000', '1', '110101199303033456', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/avatar/wangwu.jpg', '2026-08-30 18:45:00'),
(4, 'oX6Dsb1dBrYbRBtK0d3uZTcB3aUf', '赵六', '13600136000', '0', '110101199404044567', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/avatar/zhaoliu.jpg', '2026-09-02 11:20:00'),
(5, 'oX6Dsb1dBrYbRBtK0d3uZTcB3aUg', '钱七', '13500135000', '1', '110101199505055678', 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/avatar/qianqi.jpg', '2026-09-05 12:08:00');

-- 地址簿
INSERT INTO address_book (id, user_id, consignee, sex, phone, province_code, province_name, city_code, city_name, district_code, district_name, detail, label, is_default) VALUES
(1, 1, '张三', '1', '13800138000', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '建国路88号SOHO现代城3号楼1201', '公司', 1),
(2, 1, '张三', '1', '13800138000', '110000', '北京市', '110100', '北京市', '110108', '海淀区', '中关村南大街5号院2号楼304', '家', 0),
(3, 2, '李四', '0', '13900139000', '310000', '上海市', '310100', '上海市', '310104', '徐汇区', '漕溪北路88号圣爱大厦1802', '家', 1),
(4, 3, '王五', '1', '13700137000', '440000', '广东省', '440100', '广州市', '440106', '天河区', '珠江新城华夏路30号富力中心2101', '公司', 1),
(5, 4, '赵六', '0', '13600136000', '440000', '广东省', '440300', '深圳市', '440304', '福田区', '深南大道6008号特区报业大厦1505', '家', 1),
(6, 5, '钱七', '1', '13500135000', '510000', '四川省', '510100', '成都市', '510104', '锦江区', '红星路三段99号银石广场1808', '家', 1);

-- 订单 (2026-08-31 ~ 2026-09-06, 覆盖多种状态)
INSERT INTO orders (id, number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount, remark, phone, address, user_name, consignee, cancel_reason, rejection_reason, cancel_time, estimated_delivery_time, delivery_status, delivery_time, pack_amount, tableware_number, tableware_status) VALUES
(1, '2026083100001', 5, 1, 2, '2026-08-31 11:32:10', '2026-08-31 11:32:25', 1, 1, 76.00, '多放辣椒', '13800138000', '中关村南大街5号院2号楼304', '张三', '张三', NULL, NULL, NULL, '2026-08-31 12:30:00', 1, '2026-08-31 12:18:43', 2, 1, 1),
(2, '2026083100002', 5, 2, 3, '2026-08-31 18:05:40', '2026-08-31 18:05:55', 1, 1, 168.00, NULL, '13900139000', '漕溪北路88号圣爱大厦1802', '李四', '李四', NULL, NULL, NULL, '2026-08-31 19:00:00', 1, '2026-08-31 18:52:11', 2, 2, 1),
(3, '2026090100003', 5, 3, 4, '2026-09-01 12:10:33', '2026-09-01 12:10:48', 1, 1, 38.00, NULL, '13700137000', '珠江新城华夏路30号富力中心2101', '王五', '王五', NULL, NULL, NULL, '2026-09-01 13:00:00', 1, '2026-09-01 12:55:02', 1, 1, 1),
(4, '2026090100004', 6, 1, 2, '2026-09-01 19:04:15', NULL, 1, 0, 48.00, NULL, '13800138000', '中关村南大街5号院2号楼304', '张三', '张三', '用户取消', NULL, '2026-09-01 19:10:33', '2026-09-01 20:00:00', 1, NULL, 1, 1, 1),
(5, '2026090200005', 5, 4, 5, '2026-09-02 11:26:50', '2026-09-02 11:27:06', 1, 1, 99.00, NULL, '13600136000', '深南大道6008号特区报业大厦1505', '赵六', '赵六', NULL, NULL, NULL, '2026-09-02 12:20:00', 1, '2026-09-02 12:11:38', 1, 1, 1),
(6, '2026090200006', 5, 2, 3, '2026-09-02 18:22:01', '2026-09-02 18:22:16', 1, 1, 56.00, '少油', '13900139000', '漕溪北路88号圣爱大厦1802', '李四', '李四', NULL, NULL, NULL, '2026-09-02 19:10:00', 1, '2026-09-02 18:59:21', 1, 2, 1),
(7, '2026090300007', 5, 1, 1, '2026-09-03 11:40:20', '2026-09-03 11:40:40', 1, 1, 57.00, NULL, '13800138000', '建国路88号SOHO现代城3号楼1201', '张三', '张三', NULL, NULL, NULL, '2026-09-03 12:30:00', 1, '2026-09-03 12:22:15', 2, 1, 1),
(8, '2026090300008', 5, 3, 4, '2026-09-03 17:58:35', '2026-09-03 17:58:52', 1, 1, 39.00, NULL, '13700137000', '珠江新城华夏路30号富力中心2101', '王五', '王五', NULL, NULL, NULL, '2026-09-03 18:40:00', 1, '2026-09-03 18:35:48', 1, 1, 1),
(9, '2026090400009', 5, 4, 5, '2026-09-04 11:18:45', '2026-09-04 11:19:00', 1, 1, 134.00, NULL, '13600136000', '深南大道6008号特区报业大厦1505', '赵六', '赵六', NULL, NULL, NULL, '2026-09-04 12:10:00', 1, '2026-09-04 12:03:29', 2, 2, 1),
(10, '2026090400010', 5, 1, 1, '2026-09-04 18:30:12', '2026-09-04 18:30:30', 1, 1, 90.00, NULL, '13800138000', '建国路88号SOHO现代城3号楼1201', '张三', '张三', NULL, NULL, NULL, '2026-09-04 19:20:00', 1, '2026-09-04 19:15:40', 1, 1, 1),
(11, '2026090500011', 5, 2, 3, '2026-09-05 12:00:55', '2026-09-05 12:01:10', 1, 1, 50.00, NULL, '13900139000', '漕溪北路88号圣爱大厦1802', '李四', '李四', NULL, NULL, NULL, '2026-09-05 12:50:00', 1, '2026-09-05 12:44:02', 1, 1, 1),
(12, '2026090500012', 5, 5, 6, '2026-09-05 18:45:20', '2026-09-05 18:45:40', 1, 1, 168.00, '不要香菜', '13500135000', '红星路三段99号银石广场1808', '钱七', '钱七', NULL, NULL, NULL, '2026-09-05 19:30:00', 1, '2026-09-05 19:22:36', 2, 2, 1),
(13, '2026090600013', 1, 1, 2, '2026-09-06 09:05:12', NULL, 1, 0, 46.00, NULL, '13800138000', '中关村南大街5号院2号楼304', '张三', '张三', NULL, NULL, NULL, '2026-09-06 10:00:00', 1, NULL, 1, 1, 1),
(14, '2026090600014', 4, 3, 4, '2026-09-06 10:15:30', '2026-09-06 10:15:50', 1, 1, 99.00, NULL, '13700137000', '珠江新城华夏路30号富力中心2101', '王五', '王五', NULL, NULL, NULL, '2026-09-06 11:00:00', 1, NULL, 1, 1, 1),
(15, '2026090600015', 2, 2, 3, '2026-09-06 10:40:08', '2026-09-06 10:40:25', 1, 1, 38.00, NULL, '13900139000', '漕溪北路88号圣爱大厦1802', '李四', '李四', NULL, NULL, NULL, '2026-09-06 11:20:00', 1, NULL, 1, 1, 1);

-- 订单明细
INSERT INTO order_detail (id, name, order_id, dish_id, setmeal_id, dish_flavor, number, amount, image) VALUES
(1, '宫保鸡丁', 1, 1, NULL, '中辣', 2, 76.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/gongbaojiding.jpg'),
(2, '家庭欢聚四人餐', 2, NULL, 2, NULL, 1, 168.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/jiatingjuji.jpg'),
(3, '宫保鸡丁', 3, 1, NULL, '微辣', 1, 38.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/gongbaojiding.jpg'),
(4, '红烧肉', 4, 4, NULL, '正常', 1, 48.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/hongshaorou.jpg'),
(5, '商务双人餐', 5, NULL, 1, NULL, 1, 99.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/shangwushuangren.jpg'),
(6, '重庆小面', 6, 10, NULL, '不辣', 2, 40.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/chongqingxiaomian.jpg'),
(7, '可口可乐', 6, 12, NULL, NULL, 2, 16.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/kekoukele.jpg'),
(8, '重庆小面', 7, 10, NULL, '特辣', 1, 20.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/chongqingxiaomian.jpg'),
(9, '鲜榨橙汁', 7, 11, NULL, NULL, 1, 15.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/xianzhachengzhi.jpg'),
(10, '蛋炒饭', 7, 8, NULL, '大份', 1, 22.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/danchaofan.jpg'),
(11, '单人便捷餐', 8, NULL, 3, NULL, 1, 39.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/danrenbianjie.jpg'),
(13, '宫保鸡丁', 9, 1, NULL, '特辣', 1, 38.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/gongbaojiding.jpg'),
(14, '麻婆豆腐', 9, 3, NULL, '中辣', 1, 28.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/mapodoufu.jpg'),
(15, '凉拌黄瓜', 9, 5, NULL, NULL, 1, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/liangbanhuanggua.jpg'),
(16, '鲜榨橙汁', 9, 11, NULL, NULL, 1, 15.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/xianzhachengzhi.jpg'),
(17, '蛋炒饭', 9, 8, NULL, '大份', 2, 35.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/danchaofan.jpg'),
(18, '宫保鸡丁', 10, 1, NULL, '微辣', 1, 38.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/gongbaojiding.jpg'),
(19, '鱼香肉丝', 10, 2, NULL, '正常', 1, 32.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/yuxiangrousi.jpg'),
(20, '凉拌木耳', 10, 7, NULL, NULL, 1, 20.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/liangbanmuer.jpg'),
(21, '番茄鸡蛋面', 11, 9, NULL, '小份', 2, 25.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/fanqiejidanmian.jpg'),
(22, '家庭欢聚四人餐', 12, NULL, 2, NULL, 1, 168.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/jiatingjuji.jpg'),
(23, '重庆小面', 13, 10, NULL, '中辣', 1, 20.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/chongqingxiaomian.jpg'),
(24, '凉拌黄瓜', 13, 5, NULL, NULL, 1, 18.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/liangbanhuanggua.jpg'),
(25, '蛋炒饭', 13, 8, NULL, '大份', 1, 22.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/danchaofan.jpg'),
(26, '商务双人餐', 14, NULL, 1, NULL, 1, 99.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/setmeal/shangwushuangren.jpg'),
(27, '宫保鸡丁', 15, 1, NULL, '中辣', 1, 38.00, 'https://sky-itcast.oss-cn-beijing.aliyuncs.com/dish/gongbaojiding.jpg');

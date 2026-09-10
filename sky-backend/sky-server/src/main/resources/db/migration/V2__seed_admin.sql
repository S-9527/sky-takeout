-- 初始化管理员账号 admin / 123456（BCrypt加密存储）
INSERT INTO employee (id, name, username, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user)
VALUES (1, '管理员', 'admin', '$2a$10$e./WtpvCl/1fClkIb1vGM.cY87zKJ0IS.ZVwbQFxUGwpiMKomEq6y', '13812345678', '1', '110101199003077118', 1, '2022-02-15 15:51:20', '2022-02-17 09:16:20', 10, 1);

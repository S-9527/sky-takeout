-- =====================================================================
-- 苍穹外卖 v2 · V3__align_seed_image_paths.sql
-- ---------------------------------------------------------------------
-- 把 V2 种子里的占位图片地址从 /img/** 对齐到本地存储路由 /files/**。
--
-- 背景:b9dd38c 直接改了 V2 的这 26 条 image_url,但 V2 当时
--   已经在开发库执行过(见 flyway_schema_history),于是有了两个后果:
--     1. 老库启动时 flyway:validate 报 "Migration checksum mismatch for version 2";
--     2. 老库里的 26 条 image_url 仍是 /img/**,改脚本并没有改到已经落库的数据。
--   按 docs/02-database.md §7.1 "已发布的迁移脚本不可修改",V2 已还原为
--   执行时的内容,路径对齐改由本迁移承担:fresh 库与老库最终状态一致。
--
-- 幂等:WHERE 限定 /img/ 前缀,重复执行不会二次改写,也不会碰到真实上传后的地址。
-- =====================================================================

SET NAMES utf8mb4;

UPDATE `dish`
SET `image_url` = REPLACE(`image_url`, '/img/', '/files/')
WHERE `image_url` LIKE '/img/%';

UPDATE `setmeal`
SET `image_url` = REPLACE(`image_url`, '/img/', '/files/')
WHERE `image_url` LIKE '/img/%';

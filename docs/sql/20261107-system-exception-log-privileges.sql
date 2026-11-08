-- SL-BUG-01：升级脚本若由运维角色执行，新表不会自动归应用角色所有。
-- 由表 owner 或具备授权能力的角色执行，app_role 必须是实际后端数据库角色。
-- psql -X -v ON_ERROR_STOP=1 -v app_role=gak_dev -f docs/sql/20261107-system-exception-log-privileges.sql
-- 连接参数由执行环境提供；脚本不读取凭据。重复执行安全，不改变 owner、已有授权或历史记录。
-- schema 与原升级脚本一致，由连接的 search_path 决定；执行前应确认目标库及 schema。
GRANT SELECT, INSERT ON TABLE gak_system_exception_log TO :"app_role";

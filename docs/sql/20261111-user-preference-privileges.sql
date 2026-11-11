\set ON_ERROR_STOP on
-- 不在生产库执行，除非用户另行授权；由表 owner 或有授权权限的角色执行。
-- 用法：psql -v ON_ERROR_STOP=1 -v app_role=实际应用角色 -f 本文件
-- CREATE TABLE IF NOT EXISTS 不会修复已有权限。本脚本可重复执行。
-- 应用通过 API 维护自己的偏好；恢复默认使用 UPDATE，不授 DELETE，也没有序列权限。
\if :{?app_role}
\else
DO $$ BEGIN RAISE EXCEPTION '必须用 -v app_role=实际应用角色 指定应用角色'; END $$;
\endif

-- psql 不在美元引号内插值；通过会话参数传递按 SQL 字面量转义的角色名。
SELECT set_config('gak.workbench_app_role', :'app_role', false) AS workbench_app_role \gset
DO $$
DECLARE
    application_role TEXT := current_setting('gak.workbench_app_role');
    application_role_oid OID;
    application_role_superuser BOOLEAN;
BEGIN
    IF lower(application_role) = 'public' THEN
        RAISE EXCEPTION 'app_role 不能为 public';
    END IF;
    SELECT oid, rolsuper INTO application_role_oid, application_role_superuser
    FROM pg_catalog.pg_roles WHERE rolname = application_role;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'app_role 指定的角色不存在：%', application_role;
    END IF;
    IF application_role_oid = (SELECT relowner FROM pg_catalog.pg_class
            WHERE oid = 'public.gak_user_preference'::regclass) THEN
        RAISE EXCEPTION 'app_role 不能为 public.gak_user_preference 的 owner';
    END IF;
    IF application_role_superuser THEN
        RAISE EXCEPTION 'app_role 不能为超级用户';
    END IF;
END $$;
GRANT SELECT, INSERT, UPDATE ON TABLE public.gak_user_preference TO :"app_role";

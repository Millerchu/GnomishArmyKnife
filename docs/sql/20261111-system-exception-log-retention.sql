-- 仅安装函数及授权，不删数据；必须由表属主或超级用户执行，全部变更在同一事务提交。
-- 模式 A（兼容）：函数属主 = 安装时的表属主（即使执行者是另一超级用户）。
-- psql -X -v ON_ERROR_STOP=1 -v app_role=gak_dev -f docs/sql/20261111-system-exception-log-retention.sql
-- 模式 B（推荐）：独立非超级用户 NOLOGIN 清理角色，只授予 public USAGE、目标表 SELECT/DELETE。
-- psql -X -v ON_ERROR_STOP=1 -v app_role=gak_dev -v cleanup_owner=gak_log_cleaner -f docs/sql/20261111-system-exception-log-retention.sql
-- 模式 B 执行者还须有创建角色、向清理角色授权及转移函数属主的权限；推荐由超级用户安装。
-- cleanup_owner 应为专用角色，无额外授权或角色成员关系；已有角色属性不合要求时失败。
-- app_role 必须存在，不能是表属主/cleanup_owner 或它们的成员；cleanup_owner 不能是表属主。
-- 模式 B 部署前须人工确认 app_role 的 rolcreaterole = f，且不是清理角色的成员、也不持有其 ADMIN。
-- B 会新建集群级角色并增加表授权；在 gak_dev/生产使用前需用户另行批准。
-- 同签名函数若被其他角色占用则拒绝替换。A 可重复执行；A -> B 和 B 同模式重跑幂等。
-- B -> A 须先由管理员将函数 OWNER 恢复为表属主，再运行 A；禁止默许接管其他属主的函数。
-- 切回 A 还必须执行下方回滚段的 REVOKE（表 SELECT/DELETE、schema USAGE），
-- 或确认无其他依赖后 DROP ROLE gak_log_cleaner；否则清理角色的表授权残留，快照与全新 A 不一致。
-- 不修改表属主，不授予 app_role 表级 DELETE/UPDATE；清理仍须应用另行显式开启。
-- 安装时固化表属主 OID，运行时先锁表再核验：数据库 owner 可通过 pg_database_owner
-- DROP public 内他人持有的表并重建；仅固定 search_path 无法阻止替换表的触发器提权。
-- 表属主角色 OID 比角色名稳定，不会受改名影响；合法转移表属主后必须重新安装。
-- search_path 仅 pg_catalog, pg_temp（显式最后）；表和系统目录/函数全部限定 schema。
-- 数据库 owner 角色（PG15+ 经 pg_database_owner 拥有 public）可以 DROP 清理函数并用同名函数替换。
-- 替换后的函数只能以该角色自身身份执行，不能提权；但伪函数可返回 0，使清理静默停止、Job 不报错。
-- 此时安装器重跑会因 "existing purge function has unexpected owner" 失败。
-- 部署前与日常巡检应使用权限快照核对函数属主与配置；安装后也应复核。以下均为只读查询：
-- SELECT p.oid::regprocedure AS fn, pg_catalog.pg_get_userbyid(p.proowner) AS owner, p.prosecdef, p.proconfig, p.proacl FROM pg_catalog.pg_proc p WHERE p.proname = 'gak_purge_system_exception_log' AND p.pronamespace = 'public'::regnamespace;
-- 预期恰好 1 行；owner 为 A 的表属主或 B 的 cleanup_owner，prosecdef = t，
-- proconfig 含 search_path=pg_catalog, pg_temp 和 lock_timeout=2s；proacl 中无 PUBLIC，app_role 有 X。
-- 模式 B 前置快照（将 <app_role>、<cleanup_owner> 替换为实际角色名）：
-- SELECT rolcreaterole FROM pg_catalog.pg_roles WHERE rolname = '<app_role>';
-- 预期恰好 1 行且 rolcreaterole = f；以下 member=应用角色、roleid=清理角色的记录应为 0 行（含 ADMIN）。
-- SELECT m.roleid, m.member, m.admin_option FROM pg_catalog.pg_auth_members m JOIN pg_catalog.pg_roles a ON a.oid = m.member JOIN pg_catalog.pg_roles c ON c.oid = m.roleid WHERE a.rolname = '<app_role>' AND c.rolname = '<cleanup_owner>';
-- app_role 有 EXECUTE 且无表级 DELETE/UPDATE；B 的 cleanup_owner 非超级用户/不可登录，
-- 仅有目标表 SELECT/DELETE、public USAGE（核查有效权限、ACL 和角色成员关系，无其他授权）。
-- 回滚（管理员执行，不恢复已删数据）：
-- DROP FUNCTION IF EXISTS public.gak_purge_system_exception_log(TIMESTAMP, INTEGER);
-- 模式 B 另执行（角色名按实际替换；确认专用角色无其他依赖）：
-- REVOKE SELECT, DELETE ON public.gak_system_exception_log FROM gak_log_cleaner;
-- REVOKE USAGE ON SCHEMA public FROM gak_log_cleaner;
-- DROP ROLE gak_log_cleaner;
-- 函数创建与撤销 PUBLIC 权限同事务，避免默认 EXECUTE 窗口；锁等待固定 2s，
-- statement_timeout 由外层事务配置。缺参数/前置检查失败均非零退出，事务回滚。
\set ON_ERROR_STOP on
\if :{?app_role}
\else
\echo '错误：缺少 app_role；请使用 -v app_role=<应用角色> 执行。Missing required app_role.'
-- 兼容不支持 \quit 退出码的 psql。
SELECT 1 / 0;
\endif
\if :{?cleanup_owner}
\set separate_cleanup_owner true
\set purge_cleanup_owner :cleanup_owner
\else
\set purge_cleanup_owner ''
\set separate_cleanup_owner false
\endif
BEGIN;
SET LOCAL search_path = pg_catalog, pg_temp;
-- 锁表会先检查 schema/表权限；仅查目录，保证无 public USAGE 的非属主也收到明确提示。
DO $installer_check$
DECLARE
    v_table_owner OID;
BEGIN
    SELECT c.relowner INTO v_table_owner
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND c.relname = 'gak_system_exception_log';
    IF v_table_owner IS NULL THEN
        RAISE EXCEPTION 'purge target table does not exist' USING ERRCODE = '42P01';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r
                   WHERE r.rolname = current_user AND (r.oid = v_table_owner OR r.rolsuper)) THEN
        RAISE EXCEPTION 'installer must be table owner or superuser' USING ERRCODE = '42501';
    END IF;
END;
$installer_check$;
-- 安装期间阻止 DROP、属主/结构变更，保证核验和固化 OID 来自同一张表。
LOCK TABLE public.gak_system_exception_log IN SHARE ROW EXCLUSIVE MODE;
-- 锁前检查只改善错误提示；锁后仍执行完整前置检查，防止检查与加锁之间的属主变更。
-- psql 不在 dollar-quoted 字符串内插值：先用 %L 安全生成 DO，再由 \gexec 执行。
SELECT pg_catalog.format($preflight$
DO $check$
DECLARE
    v_app_name CONSTANT TEXT := %1$L;
    v_cleanup_name CONSTANT TEXT := %2$L;
    v_separate_owner CONSTANT BOOLEAN := %3$L;
    v_table_owner OID;
    v_app_oid OID;
    v_cleanup_oid OID;
    v_function_owner OID;
BEGIN
    SELECT c.relowner INTO STRICT v_table_owner
    FROM pg_catalog.pg_class c
    WHERE c.oid = 'public.gak_system_exception_log'::pg_catalog.regclass;
    IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r
                   WHERE r.rolname = current_user AND (r.oid = v_table_owner OR r.rolsuper)) THEN
        RAISE EXCEPTION 'installer must be table owner or superuser' USING ERRCODE = '42501';
    END IF;
    SELECT r.oid INTO v_app_oid FROM pg_catalog.pg_roles r WHERE r.rolname = v_app_name;
    IF v_app_oid IS NULL THEN
        RAISE EXCEPTION 'app_role must name an existing role' USING ERRCODE = '42501';
    END IF;
    IF pg_catalog.pg_has_role(v_app_oid, v_table_owner, 'MEMBER') THEN
        RAISE EXCEPTION 'app_role must not be table owner or its member' USING ERRCODE = '42501';
    END IF;
    IF v_separate_owner THEN
        IF v_cleanup_name = '' OR v_cleanup_name = v_app_name THEN
            RAISE EXCEPTION 'cleanup_owner must be nonempty and different from app_role' USING ERRCODE = '42501';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.rolname = v_cleanup_name) THEN
            EXECUTE pg_catalog.format('CREATE ROLE %%I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS', v_cleanup_name);
        END IF;
        SELECT r.oid INTO STRICT v_cleanup_oid FROM pg_catalog.pg_roles r WHERE r.rolname = v_cleanup_name;
        IF v_cleanup_oid = v_table_owner THEN
            RAISE EXCEPTION 'cleanup_owner must not be table owner' USING ERRCODE = '42501';
        END IF;
        IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles r WHERE r.oid = v_cleanup_oid
                   AND (r.rolsuper OR r.rolcanlogin OR r.rolbypassrls OR r.rolcreaterole OR r.rolcreatedb)) THEN
            RAISE EXCEPTION 'cleanup_owner has unsafe role attributes' USING ERRCODE = '42501';
        END IF;
        IF pg_catalog.pg_has_role(v_app_oid, v_cleanup_oid, 'MEMBER') THEN
            RAISE EXCEPTION 'app_role must not be cleanup_owner or its member' USING ERRCODE = '42501';
        END IF;
    END IF;
    SELECT p.proowner INTO v_function_owner FROM pg_catalog.pg_proc p
    WHERE p.oid = pg_catalog.to_regprocedure('public.gak_purge_system_exception_log(timestamp,integer)');
    IF v_function_owner IS NOT NULL AND v_function_owner <> v_table_owner
       AND (NOT v_separate_owner OR v_function_owner <> v_cleanup_oid) THEN
        RAISE EXCEPTION 'existing purge function has unexpected owner' USING ERRCODE = '42501';
    END IF;
END;
$check$;
$preflight$, :'app_role', :'purge_cleanup_owner', :'separate_cleanup_owner')
\gexec
SELECT c.relowner AS purge_table_owner_oid, r.rolname AS purge_table_owner_name
FROM pg_catalog.pg_class c JOIN pg_catalog.pg_roles r ON r.oid = c.relowner
WHERE c.oid = 'public.gak_system_exception_log'::pg_catalog.regclass
\gset
-- %1$s 仅来自 pg_class.relowner 的数字 OID；%2$L 来自脚本内部布尔值。
SELECT pg_catalog.format($purge_definition$
CREATE OR REPLACE FUNCTION public.gak_purge_system_exception_log(p_cutoff TIMESTAMP, p_limit INTEGER)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
SET lock_timeout = '2s'
AS $purge_body$
DECLARE
    v_cutoff TIMESTAMP;
    v_deleted INTEGER;
BEGIN
    LOCK TABLE public.gak_system_exception_log IN ROW EXCLUSIVE MODE;
    -- 先锁再核验，阻止核验与 DELETE 之间 DROP/替换目标表。
    IF NOT EXISTS (
        SELECT 1 FROM pg_catalog.pg_class c
        WHERE c.oid = 'public.gak_system_exception_log'::pg_catalog.regclass
          AND c.relkind = 'r'
          AND c.relowner = %1$s::pg_catalog.oid
          AND (%2$L::pg_catalog.bool OR c.relowner = (
              SELECT r.oid FROM pg_catalog.pg_roles r WHERE r.rolname = current_user))
    ) THEN
        RAISE EXCEPTION 'purge target table failed ownership check' USING ERRCODE = '42501';
    END IF;
    -- 直接查 pg_trigger，包含 internal：外键 ON DELETE 的内部 RI 触发器也会执行代码
    -- 或级联到其他表。本日志表不应有任何触发器，也不应被外键引用，不能只看 relhastriggers。
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_trigger t
               WHERE t.tgrelid = 'public.gak_system_exception_log'::pg_catalog.regclass) THEN
        RAISE EXCEPTION 'purge target table must not have triggers (including internal)' USING ERRCODE = '42501';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_rewrite r
               WHERE r.ev_class = 'public.gak_system_exception_log'::pg_catalog.regclass) THEN
        RAISE EXCEPTION 'purge target table must not have rules' USING ERRCODE = '42501';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_class c
               WHERE c.oid = 'public.gak_system_exception_log'::pg_catalog.regclass
                 AND (c.relrowsecurity OR c.relforcerowsecurity)) THEN
        RAISE EXCEPTION 'purge target table must not use row security' USING ERRCODE = '42501';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_inherits i
               WHERE i.inhparent = 'public.gak_system_exception_log'::pg_catalog.regclass) THEN
        RAISE EXCEPTION 'purge target table must not have inheritance children' USING ERRCODE = '42501';
    END IF;
    IF p_cutoff IS NULL THEN
        RAISE EXCEPTION 'purge cutoff must not be null' USING ERRCODE = '22023';
    END IF;
    IF p_limit IS NULL OR p_limit < 1 OR p_limit > 1000 THEN
        RAISE EXCEPTION 'purge limit must be between 1 and 1000' USING ERRCODE = '22023';
    END IF;
    v_cutoff := LEAST(p_cutoff, (pg_catalog.now() AT TIME ZONE 'Asia/Shanghai') - INTERVAL '30 days');
    DELETE FROM public.gak_system_exception_log
    WHERE id IN (
        SELECT id FROM public.gak_system_exception_log
        WHERE occurred_at < v_cutoff
        ORDER BY occurred_at ASC, id ASC
        LIMIT p_limit
    );
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$purge_body$;
$purge_definition$, :'purge_table_owner_oid', :'separate_cleanup_owner')
\gexec
REVOKE ALL ON FUNCTION public.gak_purge_system_exception_log(TIMESTAMP, INTEGER) FROM PUBLIC;
\if :separate_cleanup_owner
REVOKE ALL ON SCHEMA public FROM :"cleanup_owner";
GRANT USAGE ON SCHEMA public TO :"cleanup_owner";
REVOKE ALL ON public.gak_system_exception_log FROM :"cleanup_owner";
GRANT SELECT, DELETE ON public.gak_system_exception_log TO :"cleanup_owner";
ALTER FUNCTION public.gak_purge_system_exception_log(TIMESTAMP, INTEGER) OWNER TO :"cleanup_owner";
\else
ALTER FUNCTION public.gak_purge_system_exception_log(TIMESTAMP, INTEGER) OWNER TO :"purge_table_owner_name";
\endif
GRANT EXECUTE ON FUNCTION public.gak_purge_system_exception_log(TIMESTAMP, INTEGER) TO :"app_role";
COMMIT;

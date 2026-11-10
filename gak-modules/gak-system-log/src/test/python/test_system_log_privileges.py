"""单用户 PG 权限回归及自起临时 PG 的 psql 脚本回归；不连接外部数据库。"""

import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


REPOSITORY = Path(__file__).resolve().parents[5]
SQL_DIRECTORY = REPOSITORY / "docs/sql"
POSTGRES_BIN = Path(os.environ.get("POSTGRES_BIN", "/usr/lib/postgresql/16/bin"))
RUNTIME_ROLE = "sl_runtime"


def retention_transaction(cleanup_owner=None):
    # 单用户 PG 执行同一前置检查/函数格式模板；psql 元命令与变量引用由真实 psql -f 回归覆盖。
    script = (SQL_DIRECTORY / "20261111-system-exception-log-retention.sql").read_text()
    installer_check = script.split("$installer_check$")[1]
    preflight = script.split("$preflight$")[1]
    definition = script.split("$purge_definition$")[1]
    separate = "true" if cleanup_owner else "false"
    if cleanup_owner not in (None, "sl_cleaner"):
        raise ValueError("SQL-only helper accepts only the fixed test cleanup role")
    cleaner = cleanup_owner or ""
    transaction = """
        BEGIN;
        SET LOCAL search_path = pg_catalog, pg_temp;
        DO $installer_check$%s$installer_check$;
        LOCK TABLE public.gak_system_exception_log IN SHARE ROW EXCLUSIVE MODE;
        DO $install$ BEGIN
          EXECUTE pg_catalog.format($preflight$%s$preflight$, 'sl_runtime', '%s', %s);
          EXECUTE pg_catalog.format($purge_definition$%s$purge_definition$,
            (SELECT relowner FROM pg_catalog.pg_class
             WHERE oid='public.gak_system_exception_log'::pg_catalog.regclass), %s);
        END $install$;
        REVOKE ALL ON FUNCTION public.gak_purge_system_exception_log(timestamp,integer) FROM PUBLIC;
    """ % (installer_check, preflight, cleaner, separate, definition, separate)
    if cleanup_owner:
        transaction += """
            GRANT USAGE ON SCHEMA public TO sl_cleaner;
            GRANT SELECT, DELETE ON public.gak_system_exception_log TO sl_cleaner;
            ALTER FUNCTION public.gak_purge_system_exception_log(timestamp,integer) OWNER TO sl_cleaner;
        """
    return transaction + """
        GRANT EXECUTE ON FUNCTION public.gak_purge_system_exception_log(timestamp,integer) TO sl_runtime;
        COMMIT;
    """


def rejected_call(message):
    return """
        DO $$ DECLARE failure_message text;
        BEGIN
          BEGIN
            PERFORM public.gak_purge_system_exception_log(TIMESTAMP '2100-01-01',500);
            RAISE EXCEPTION 'unsafe target accepted';
          EXCEPTION WHEN SQLSTATE '42501' THEN
            GET STACKED DIAGNOSTICS failure_message = MESSAGE_TEXT;
            IF failure_message NOT LIKE '%s' THEN
              RAISE EXCEPTION 'unexpected refusal: %%', failure_message;
            END IF;
          END;
        END $$;
    """ % message


def replacement_attack_sql(cleanup_owner):
    cleaner_grant = ("GRANT SELECT, DELETE ON public.gak_system_exception_log TO sl_cleaner;"
                     if cleanup_owner else "")
    return ("""
        BEGIN;
        SET LOCAL ROLE sl_runtime;
        DO $$ BEGIN
          IF NOT pg_catalog.pg_has_role(current_user,'pg_database_owner','MEMBER')
             OR NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE')
             OR NOT EXISTS (SELECT 1 FROM pg_catalog.pg_database
                            WHERE datname=current_database() AND datdba=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname=current_user))
             OR NOT EXISTS (SELECT 1 FROM pg_catalog.pg_namespace
                            WHERE nspname='public' AND nspowner='pg_database_owner'::pg_catalog.regrole)
             OR NOT EXISTS (SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_roles r ON r.oid=c.relowner
                            WHERE c.oid='public.gak_system_exception_log'::pg_catalog.regclass AND r.rolsuper) THEN
            RAISE EXCEPTION 'attack must use database/schema owner against superuser-owned table';
          END IF;
        END $$;
        DROP TABLE public.gak_system_exception_log;
        CREATE TABLE public.gak_system_exception_log(id bigint, occurred_at timestamp);
        CREATE TABLE public.sl_trigger_hits(executed_as name);
        CREATE SEQUENCE public.sl_trigger_calls;
        CREATE FUNCTION public.sl_attack_trigger() RETURNS trigger LANGUAGE plpgsql AS $trigger$
        BEGIN
          PERFORM pg_catalog.nextval('public.sl_trigger_calls');
          INSERT INTO public.sl_trigger_hits VALUES (current_user);
          RETURN OLD;
        END $trigger$;
        CREATE TRIGGER attack BEFORE DELETE ON public.gak_system_exception_log
            FOR EACH ROW EXECUTE FUNCTION public.sl_attack_trigger();
        INSERT INTO public.gak_system_exception_log VALUES (1,TIMESTAMP '2000-01-01');
        -- 正对照验证触发器确实可执行；序列不受异常子事务回滚影响，避免零命中的假阳性。
        DELETE FROM public.gak_system_exception_log;
        DO $$ BEGIN
          IF NOT (SELECT is_called FROM public.sl_trigger_calls)
             OR (SELECT count(*) FROM public.sl_trigger_hits)<>1 THEN
            RAISE EXCEPTION 'trigger positive control failed';
          END IF;
        END $$;
        TRUNCATE public.sl_trigger_hits;
        SELECT pg_catalog.setval('public.sl_trigger_calls',1,false);
        INSERT INTO public.gak_system_exception_log VALUES (1,TIMESTAMP '2000-01-01');
    """ + cleaner_grant + rejected_call('purge target table failed ownership check') + """
        DO $$ BEGIN
          IF (SELECT is_called FROM public.sl_trigger_calls)
             OR EXISTS (SELECT 1 FROM public.sl_trigger_hits)
             OR (SELECT count(*) FROM public.gak_system_exception_log)<>1 THEN
            RAISE EXCEPTION 'replacement table trigger executed or row deleted';
          END IF;
        END $$;
        ROLLBACK;
    """)


def unsafe_structure_cases(cleanup_owner):
    return {
        "trigger": ("""
            CREATE FUNCTION public.sl_owner_trigger() RETURNS trigger LANGUAGE plpgsql AS $trigger$
            BEGIN PERFORM pg_catalog.nextval('public.sl_guard_calls'); RETURN OLD; END $trigger$;
            CREATE TRIGGER owner_trigger BEFORE DELETE ON public.gak_system_exception_log
                FOR EACH ROW EXECUTE FUNCTION public.sl_owner_trigger();
        """, 'purge target table must not have triggers (including internal)'),
        "internal_trigger": ("""
            CREATE TABLE public.sl_referencing(id bigint REFERENCES public.gak_system_exception_log(id) ON DELETE CASCADE);
            INSERT INTO public.sl_referencing VALUES (1);
            DO $$ BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_trigger
                WHERE tgrelid='public.gak_system_exception_log'::pg_catalog.regclass AND tgisinternal) THEN
                RAISE EXCEPTION 'missing internal RI trigger';
              END IF;
            END $$;
        """, 'purge target table must not have triggers (including internal)'),
        "rule": ("CREATE RULE no_delete AS ON DELETE TO public.gak_system_exception_log DO INSTEAD NOTHING;",
                 'purge target table must not have rules'),
        "rls": ("ALTER TABLE public.gak_system_exception_log ENABLE ROW LEVEL SECURITY;",
                'purge target table must not use row security'),
        "force_rls": ("ALTER TABLE public.gak_system_exception_log FORCE ROW LEVEL SECURITY;",
                      'purge target table must not use row security'),
        "child": ("CREATE TABLE public.sl_child() INHERITS (public.gak_system_exception_log);"
                  + ("GRANT SELECT,DELETE ON public.sl_child TO sl_cleaner;" if cleanup_owner else ""),
                  'purge target table must not have inheritance children'),
    }


def unsafe_structure_sql(setup, message):
    return ("BEGIN; CREATE SEQUENCE public.sl_guard_calls; "
             "INSERT INTO public.gak_system_exception_log VALUES (1,TIMESTAMP '2000-01-01');"
             + setup + "SET LOCAL ROLE sl_runtime;" + rejected_call(message) + "RESET ROLE;" + """
        DO $$ BEGIN
          IF (SELECT is_called FROM public.sl_guard_calls)
             OR (SELECT count(*) FROM public.gak_system_exception_log)<>1 THEN
            RAISE EXCEPTION 'unsafe target executed code or deleted rows';
          END IF;
        END $$;
        ROLLBACK;
    """)


@unittest.skipUnless((POSTGRES_BIN / "postgres").exists(), "需要真实 PostgreSQL 16")
class SystemLogPrivilegesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="gak-sl-bug-01-")
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.cluster = Path(cls.temporary.name) / "pgdata"
        subprocess.run(
            [str(POSTGRES_BIN / "initdb"), "-D", str(cls.cluster), "--auth=trust",
             "--username=sl_ddl_owner", "--no-locale", "--encoding=UTF8"],
            check=True, capture_output=True, text=True,
        )
        cls.execute("""
            CREATE ROLE sl_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
            CREATE TABLE gak_user(id BIGINT PRIMARY KEY, username VARCHAR(64));
            CREATE TABLE gak_system_app(
                id BIGINT PRIMARY KEY, app_code VARCHAR(64) UNIQUE, app_name VARCHAR(64),
                route_path VARCHAR(128), category VARCHAR(64), data_source_mode VARCHAR(16),
                icon_type VARCHAR(16), icon_text VARCHAR(32), security_level VARCHAR(20),
                encryption_mode VARCHAR(20), enabled BOOLEAN, sort_no INTEGER,
                description VARCHAR(255), created_at TIMESTAMP, updated_at TIMESTAMP);
            CREATE TABLE gak_user_app_permission(
                id BIGINT PRIMARY KEY,user_id BIGINT,app_id BIGINT,app_code VARCHAR(64),
                granted BOOLEAN,granted_by BIGINT,granted_at TIMESTAMP,remark VARCHAR(255),
                created_at TIMESTAMP,updated_at TIMESTAMP);
        """)
        cls.upgrade = (SQL_DIRECTORY / "20261107-system-exception-log.sql").read_text()
        cls.grant = (SQL_DIRECTORY / "20261107-system-exception-log-privileges.sql").read_text()
        cls.grant = cls.grant.replace(':"app_role"', '"sl_runtime"')
        cls.execute(cls.upgrade)

    @classmethod
    def execute(cls, sql, *, denied=False):
        # 单用户模式按换行提交命令；合并源 SQL，保留字符串内的反斜杠转义。
        sql = " ".join(line for line in sql.splitlines() if not line.lstrip().startswith("--"))
        result = subprocess.run(
            [str(POSTGRES_BIN / "postgres"), "--single", "-D", str(cls.cluster),
             "-c", "log_error_verbosity=verbose", "postgres"],
            input=sql + "\n", capture_output=True, text=True, check=True,
        )
        if denied:
            if "42501:" not in result.stderr:
                raise AssertionError("未复现 PostgreSQL insufficient_privilege: " + result.stderr)
        elif "ERROR:" in result.stderr or "FATAL:" in result.stderr:
            raise AssertionError(result.stderr)
        return result.stdout

    def setUp(self):
        self.execute("REVOKE ALL ON gak_system_exception_log FROM sl_runtime; TRUNCATE gak_system_exception_log;")

    def test_count_detail_and_insert_fail_with_42501_before_grant(self):
        for statement in [
            "SELECT COUNT(*) FROM gak_system_exception_log",
            "SELECT id FROM gak_system_exception_log WHERE id=999999999999999999",
            "INSERT INTO gak_system_exception_log(id) VALUES(1)",
        ]:
            with self.subTest(statement=statement):
                self.execute("SET ROLE sl_runtime; " + statement + ";", denied=True)

    def test_rerunning_create_if_not_exists_does_not_repair_permissions(self):
        self.execute(self.upgrade)
        self.execute("SET ROLE sl_runtime; SELECT COUNT(*) FROM gak_system_exception_log;", denied=True)

    def test_grant_is_idempotent_and_only_adds_select_insert(self):
        self.execute(self.grant)
        self.execute(self.grant)
        self.execute("""
            SET ROLE sl_runtime;
            DO $$ BEGIN
              IF NOT has_table_privilege(current_user,'gak_system_exception_log','SELECT')
                 OR NOT has_table_privilege(current_user,'gak_system_exception_log','INSERT')
                 OR has_table_privilege(current_user,'gak_system_exception_log','UPDATE')
                 OR has_table_privilege(current_user,'gak_system_exception_log','DELETE') THEN
                RAISE EXCEPTION 'unexpected runtime privileges';
              END IF;
              IF (SELECT pg_get_userbyid(relowner)=current_user FROM pg_class
                  WHERE oid='gak_system_exception_log'::regclass) THEN
                RAISE EXCEPTION 'runtime role must not own the table';
              END IF;
            END $$;
        """)

    def test_existing_time_index_supports_cleanup_order(self):
        self.execute(self.grant)
        output = self.execute("""
            SET ROLE sl_runtime;
            SET enable_seqscan=off;
            EXPLAIN SELECT id FROM public.gak_system_exception_log
            WHERE occurred_at < (now() AT TIME ZONE 'Asia/Shanghai') - INTERVAL '30 days'
            ORDER BY occurred_at,id LIMIT 500;
        """)
        self.assertIn("idx_system_exception_log_occurred", output)
        # 小样本可能选择 Bitmap Index Scan + Sort，只确认既有索引可用，不锁定优化器计划。

    def test_retention_function_preserves_minimum_privileges_and_thirty_days(self):
        self.execute(self.grant)
        retention = retention_transaction()
        self.execute(retention)
        self.execute(retention)
        self.execute("""
            SET ROLE sl_runtime;
            DO $$ BEGIN
              IF NOT has_function_privilege(current_user,
                  'public.gak_purge_system_exception_log(timestamp,integer)','EXECUTE')
                 OR has_table_privilege(current_user,'gak_system_exception_log','DELETE')
                 OR has_table_privilege(current_user,'gak_system_exception_log','UPDATE') THEN
                RAISE EXCEPTION 'unexpected retention privileges';
              END IF;
              IF EXISTS (SELECT 1 FROM pg_proc p, LATERAL aclexplode(p.proacl) a
                  WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::regprocedure
                    AND a.grantee=0 AND a.privilege_type='EXECUTE') THEN
                RAISE EXCEPTION 'PUBLIC must not execute retention function';
              END IF;
              IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_proc p
                  WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::regprocedure
                    AND 'search_path=pg_catalog, pg_temp' = ANY(p.proconfig)) THEN
                RAISE EXCEPTION 'retention search_path must exclude public and put pg_temp last';
              END IF;
            END $$;
            INSERT INTO gak_system_exception_log
                (id,occurred_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated,created_at)
            VALUES
                (1,(now() AT TIME ZONE 'Asia/Shanghai')-INTERVAL '31 days','old','HTTP','Synthetic','synthetic','synthetic',false,now()),
                (2,(now() AT TIME ZONE 'Asia/Shanghai')-INTERVAL '29 days','recent','HTTP','Synthetic','synthetic','synthetic',false,now());
            DO $$ BEGIN
              IF public.gak_purge_system_exception_log(now() AT TIME ZONE 'Asia/Shanghai',500)<>1 THEN
                RAISE EXCEPTION 'must delete only expired row';
              END IF;
              IF (SELECT count(*) FROM gak_system_exception_log)<>1
                 OR NOT EXISTS (SELECT 1 FROM gak_system_exception_log WHERE id=2) THEN
                RAISE EXCEPTION 'thirty day floor violated';
              END IF;
            END $$;
        """)

    def guarded_minimal_fixture(self, cleanup_owner):
        installation = retention_transaction(cleanup_owner).rsplit("COMMIT;", 1)[0]
        return """
            BEGIN;
            DROP TABLE public.gak_system_exception_log;
            CREATE TABLE public.gak_system_exception_log(id bigint PRIMARY KEY, occurred_at timestamp);
            GRANT SELECT, INSERT ON public.gak_system_exception_log TO sl_runtime;
            ALTER DATABASE postgres OWNER TO sl_runtime;
        """ + installation

    def test_unsafe_structures_in_both_modes_without_socket(self):
        for cleanup_owner in (None, "sl_cleaner"):
            for variant, (setup, message) in unsafe_structure_cases(cleanup_owner).items():
                with self.subTest(cleanup_owner=cleanup_owner, variant=variant):
                    self.execute(self.guarded_minimal_fixture(cleanup_owner) + unsafe_structure_sql(setup, message))

    def test_database_owner_replacement_attack_in_both_modes_without_socket(self):
        for cleanup_owner in (None, "sl_cleaner"):
            with self.subTest(cleanup_owner=cleanup_owner):
                # 单用户 PG 也支持事务/SET ROLE；与 psql 用例复用同一攻击及断言。
                # 外层事务还回滚极简测试表、数据库 owner、安装角色和函数变更。
                self.execute(self.guarded_minimal_fixture(cleanup_owner) + replacement_attack_sql(cleanup_owner))

    def test_dedicated_cleanup_role_uses_same_guard_and_thirty_day_floor(self):
        self.execute(self.grant)
        # 与安装处于同一事务，结束回滚，避免向其他 SQL-only 用例遗留清理角色/函数属主。
        installation = retention_transaction("sl_cleaner").rsplit("COMMIT;", 1)[0]
        self.execute(installation + """
            DO $$ BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_roles r ON r.oid=p.proowner
                WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure
                  AND r.rolname='sl_cleaner' AND NOT r.rolsuper AND NOT r.rolcanlogin) THEN
                RAISE EXCEPTION 'invalid dedicated cleanup role';
              END IF;
            END $$;
            SET LOCAL ROLE sl_runtime;
            INSERT INTO public.gak_system_exception_log
                (id,occurred_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated,created_at)
            VALUES
                (1,(pg_catalog.now() AT TIME ZONE 'Asia/Shanghai')-INTERVAL '31 days','old','HTTP','Synthetic','synthetic','synthetic',false,now()),
                (2,(pg_catalog.now() AT TIME ZONE 'Asia/Shanghai')-INTERVAL '29 days','recent','HTTP','Synthetic','synthetic','synthetic',false,now());
            DO $$ DECLARE deleted_count integer;
            BEGIN
              -- 清理和状态断言须分成独立语句，后者才能读取删除后的快照。
              deleted_count := public.gak_purge_system_exception_log(TIMESTAMP '2100-01-01',500);
              IF deleted_count<>1 THEN
                RAISE EXCEPTION 'unexpected dedicated cleanup count';
              END IF;
              IF (SELECT count(*) FROM public.gak_system_exception_log)<>1
                 OR NOT EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=2) THEN
                RAISE EXCEPTION 'dedicated cleanup violated thirty day floor';
              END IF;
            END $$;
            RESET ROLE;
            ALTER TABLE public.gak_system_exception_log ENABLE ROW LEVEL SECURITY;
            SET LOCAL ROLE sl_runtime;
        """ + rejected_call('purge target table must not use row security') + "ROLLBACK;")

    def test_retention_ignores_objects_created_by_untrusted_application_role(self):
        self.execute(self.grant)
        self.execute(retention_transaction())
        # 对象和 CREATE 授权只在本事务中存在；同一事务的 now() 保证微秒级边界稳定。
        self.execute("""
            BEGIN;
            GRANT CREATE ON SCHEMA public TO sl_runtime;
            SET LOCAL ROLE sl_runtime;
            DO $$ BEGIN
              IF NOT pg_catalog.has_schema_privilege(current_user,'public','CREATE')
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_proc p
                     WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::regprocedure
                       AND p.proowner=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname=current_user))
                 OR EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname=current_user AND rolsuper) THEN
                RAISE EXCEPTION 'attacker must have public CREATE but must not own retention or be superuser';
              END IF;
            END $$;
            CREATE TABLE public.sl_retention_hijack_marker(called BOOLEAN);
            CREATE FUNCTION public.now() RETURNS pg_catalog.timestamptz LANGUAGE plpgsql AS $hijack$
            BEGIN
              INSERT INTO public.sl_retention_hijack_marker VALUES (true);
              RETURN TIMESTAMPTZ '2200-01-01 00:00:00+00';
            END;
            $hijack$;
            SET LOCAL search_path = public, pg_temp, pg_catalog;
            SELECT now();
            DO $$ BEGIN
              IF (SELECT pg_catalog.count(*) FROM public.sl_retention_hijack_marker)<>1 THEN
                RAISE EXCEPTION 'hijack marker control failed';
              END IF;
            END $$;
            TRUNCATE public.sl_retention_hijack_marker;
            CREATE TEMP TABLE gak_system_exception_log(id BIGINT, occurred_at TIMESTAMP);
            INSERT INTO pg_temp.gak_system_exception_log VALUES (99,TIMESTAMP '2000-01-01');
            DO $$ DECLARE
              cutoff TIMESTAMP := (pg_catalog.now() AT TIME ZONE 'Asia/Shanghai') - INTERVAL '30 days';
            BEGIN
              INSERT INTO public.gak_system_exception_log
                (id,occurred_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated,created_at)
              VALUES
                (1,cutoff-INTERVAL '1 microsecond','before','HTTP','Synthetic','synthetic','synthetic',false,cutoff),
                (2,cutoff,'equal','HTTP','Synthetic','synthetic','synthetic',false,cutoff),
                (3,cutoff+INTERVAL '1 microsecond','after','HTTP','Synthetic','synthetic','synthetic',false,cutoff);
              IF public.gak_purge_system_exception_log(cutoff+INTERVAL '30 days',500)<>1 THEN
                RAISE EXCEPTION 'hijack changed deleted count or thirty day floor';
              END IF;
              IF (SELECT pg_catalog.count(*) FROM public.gak_system_exception_log)<>2
                 OR NOT EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=2)
                 OR NOT EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=3) THEN
                RAISE EXCEPTION 'hijack changed strict boundary';
              END IF;
              IF public.gak_purge_system_exception_log(cutoff+INTERVAL '30 days',500)<>0 THEN
                RAISE EXCEPTION 'hijack changed empty batch result';
              END IF;
              IF EXISTS (SELECT 1 FROM public.sl_retention_hijack_marker) THEN
                RAISE EXCEPTION 'untrusted public.now was called';
              END IF;
              IF (SELECT pg_catalog.count(*) FROM pg_temp.gak_system_exception_log WHERE id=99)<>1 THEN
                RAISE EXCEPTION 'cleanup touched shadowing temporary table';
              END IF;
            END $$;
            ROLLBACK;
        """)

    def test_retention_strict_boundary_invalid_arguments_and_ordered_batches(self):
        self.execute(self.grant)
        self.execute(retention_transaction())
        self.execute("""
            SET ROLE sl_runtime;
            DO $$ DECLARE
              cutoff timestamp := (now() AT TIME ZONE 'Asia/Shanghai') - INTERVAL '30 days';
              invalid_limit integer;
              batch integer;
              deleted_count integer;
            BEGIN
              INSERT INTO gak_system_exception_log
                (id,occurred_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated,created_at)
              VALUES
                (1,cutoff-INTERVAL '1 microsecond','before','HTTP','Synthetic','synthetic','synthetic',false,cutoff),
                (2,cutoff,'equal','HTTP','Synthetic','synthetic','synthetic',false,cutoff),
                (3,cutoff+INTERVAL '1 microsecond','after','HTTP','Synthetic','synthetic','synthetic',false,cutoff);
              FOREACH invalid_limit IN ARRAY ARRAY[NULL,0,-1,1001] LOOP
                BEGIN
                  PERFORM public.gak_purge_system_exception_log(cutoff,invalid_limit);
                  RAISE EXCEPTION 'accepted invalid limit';
                EXCEPTION WHEN invalid_parameter_value THEN NULL;
                END;
              END LOOP;
              BEGIN
                PERFORM public.gak_purge_system_exception_log(NULL,500);
                RAISE EXCEPTION 'accepted null cutoff';
              EXCEPTION WHEN invalid_parameter_value THEN NULL;
              END;
              IF (SELECT count(*) FROM gak_system_exception_log)<>3 THEN
                RAISE EXCEPTION 'invalid arguments deleted rows';
              END IF;
              deleted_count := public.gak_purge_system_exception_log(cutoff,500);
              IF deleted_count<>1 THEN
                RAISE EXCEPTION 'unexpected strict boundary deletion count';
              END IF;
              IF EXISTS (SELECT 1 FROM gak_system_exception_log WHERE id=1)
                 OR NOT EXISTS (SELECT 1 FROM gak_system_exception_log WHERE id=2)
                 OR NOT EXISTS (SELECT 1 FROM gak_system_exception_log WHERE id=3) THEN
                RAISE EXCEPTION 'strict boundary violated';
              END IF;
              INSERT INTO gak_system_exception_log
                (id,occurred_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated,created_at)
              SELECT n+10,cutoff-INTERVAL '1 day','batch','HTTP','Synthetic','synthetic','synthetic',false,cutoff
              FROM generate_series(1,2500) n;
              FOR batch IN 1..5 LOOP
                IF public.gak_purge_system_exception_log(cutoff,500)<>500 THEN
                  RAISE EXCEPTION 'unexpected batch size';
                END IF;
                IF EXISTS (SELECT 1 FROM gak_system_exception_log WHERE id BETWEEN 11 AND batch*500+10) THEN
                  RAISE EXCEPTION 'delete order must be occurred_at,id ascending';
                END IF;
                IF (SELECT count(*) FROM gak_system_exception_log)<>2502-batch*500 THEN
                  RAISE EXCEPTION 'unexpected remaining batch rows';
                END IF;
              END LOOP;
              IF public.gak_purge_system_exception_log(cutoff,500)<>0 THEN
                RAISE EXCEPTION 'no expired rows must return zero';
              END IF;
            END $$;
        """)

    def test_synthetic_write_query_filters_order_and_missing_id_after_grant(self):
        self.execute(self.grant)
        output = self.execute(r"""
            SET ROLE sl_runtime;
            INSERT INTO gak_system_exception_log
              (id,occurred_at,trace_id,source,exception_type,summary,stack_trace,stack_truncated,
               http_method,request_path,created_at)
            VALUES
              (1,'2026-11-07 12:00:00','synthetic-trace','HTTP','java.lang.IllegalStateException',
               'Synthetic 100%_ Path\Marker','synthetic stack',FALSE,'GET','/synthetic/{id}','2026-11-07 12:00:00'),
              (2,'2026-11-07 12:00:00','other-trace','SCHEDULER','java.lang.IllegalArgumentException',
               'Synthetic 100ab PathXMarker','synthetic stack',TRUE,NULL,NULL,'2026-11-07 12:00:00');
            DO $$ BEGIN
              IF (SELECT COUNT(*) FROM gak_system_exception_log)<>2 THEN RAISE EXCEPTION 'count'; END IF;
              IF (SELECT COUNT(*) FROM gak_system_exception_log WHERE id=999999999999999999)<>0
                THEN RAISE EXCEPTION 'missing numeric id'; END IF;
              IF (SELECT COUNT(*) FROM gak_system_exception_log WHERE summary ILIKE '%100\%\_%' ESCAPE '\')<>1
                THEN RAISE EXCEPTION 'literal wildcard filter'; END IF;
              IF (SELECT COUNT(*) FROM gak_system_exception_log WHERE summary ILIKE '%pAtH\\mArKeR%' ESCAPE '\')<>1
                THEN RAISE EXCEPTION 'backslash and case filter'; END IF;
              IF (SELECT COUNT(*) FROM gak_system_exception_log WHERE exception_type='java.lang.IllegalStateException'
                  AND trace_id='synthetic-trace' AND occurred_at>='2026-11-07 12:00:00'
                  AND occurred_at<='2026-11-07 12:00:00')<>1 THEN RAISE EXCEPTION 'exact filters'; END IF;
            END $$;
            SELECT id,occurred_at,trace_id,source,exception_type,summary,http_method,request_path
              FROM gak_system_exception_log ORDER BY occurred_at DESC,id DESC LIMIT 100 OFFSET 0;
        """)
        self.assertEqual(["2", "1"], re.findall(r'\b1: id = "(\d+)"', output))


@unittest.skipUnless(all((POSTGRES_BIN / name).exists() for name in ("initdb", "pg_ctl", "psql")),
                     "需要 PostgreSQL 16 initdb/pg_ctl/psql")
class RetentionPsqlScriptTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="gak-sl-retention-psql-")
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.cluster = Path(cls.temporary.name) / "pgdata"
        cls.server_log = Path(cls.temporary.name) / "server.log"
        cls.socket_directory = Path(cls.temporary.name) / "socket"
        cls.socket_directory.mkdir()
        subprocess.run(
            [str(POSTGRES_BIN / "initdb"), "-D", str(cls.cluster), "--auth=trust",
             "--username=sl_ddl_owner", "--no-locale", "--encoding=UTF8"],
            check=True, capture_output=True, text=True,
        )
        # 仅监听测试独有的 Unix socket，不使用现有服务，也不开 TCP 端口。
        started = subprocess.run(
            [str(POSTGRES_BIN / "pg_ctl"), "-D", str(cls.cluster), "-l", str(cls.server_log),
             "-o", f"-k {cls.socket_directory} -p 55439 -c listen_addresses=''", "-w", "start"],
            capture_output=True, text=True,
        )
        if started.returncode:
            diagnostics = started.stdout + started.stderr + cls.server_log.read_text()
            if "Operation not permitted" in diagnostics or "Permission denied" in diagnostics:
                raise unittest.SkipTest("沙箱禁止临时 PG socket：" + diagnostics)
            raise AssertionError(diagnostics)
        cls.addClassCleanup(cls.stop_server)
        cls.psql("-v", "ON_ERROR_STOP=1", "-c", """
            CREATE ROLE sl_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
            CREATE ROLE sl_other NOLOGIN NOSUPERUSER;
            ALTER DATABASE postgres OWNER TO sl_runtime;
        """).check_returncode()

    @classmethod
    def stop_server(cls):
        subprocess.run([str(POSTGRES_BIN / "pg_ctl"), "-D", str(cls.cluster), "-m", "fast", "-w", "stop"],
                       check=True, capture_output=True, text=True)

    @classmethod
    def psql(cls, *arguments):
        # 清除会影响连接/角色/超时的外部 PG 环境；不依赖凭据或调用者的 ON_ERROR_STOP。
        environment = {key: value for key, value in os.environ.items() if not key.startswith("PG")}
        return subprocess.run(
            [str(POSTGRES_BIN / "psql"), "-X", "-h", str(cls.socket_directory), "-p", "55439",
             "-U", "sl_ddl_owner", "-d", "postgres", *arguments],
            env=environment, capture_output=True, text=True,
        )

    cleanup_owner = None

    def sql(self, statement):
        result = self.psql("-v", "ON_ERROR_STOP=1", "-At", "-c", statement)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        return result.stdout.strip()

    def setUp(self):
        self.sql("""
            DROP SCHEMA public CASCADE;
            CREATE SCHEMA public AUTHORIZATION pg_database_owner;
            CREATE TABLE public.gak_system_exception_log(id bigint PRIMARY KEY, occurred_at timestamp);
            GRANT USAGE ON SCHEMA public TO sl_runtime;
            GRANT SELECT, INSERT ON public.gak_system_exception_log TO sl_runtime;
            DO $$ BEGIN
              IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sl_cleaner') THEN
                DROP OWNED BY sl_cleaner;
                DROP ROLE sl_cleaner;
              END IF;
            END $$;
        """)

    def install(self, *, app_role=RUNTIME_ROLE, cleanup_owner=None, executor=None):
        arguments = ["-v", "app_role=" + app_role]
        cleaner = cleanup_owner if cleanup_owner is not None else self.cleanup_owner
        if cleaner is not None:
            arguments += ["-v", "cleanup_owner=" + cleaner]
        if executor:
            arguments += ["-c", "SET ROLE " + executor]
        return self.psql(*arguments, "-f", str(SQL_DIRECTORY / "20261111-system-exception-log-retention.sql"))

    def install_ok(self, **arguments):
        result = self.install(**arguments)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def assert_function_absent(self):
        self.assertEqual("t", self.sql("SELECT pg_catalog.to_regprocedure("
                                     "'public.gak_purge_system_exception_log(timestamp,integer)') IS NULL;"))

    def assert_install_rejected(self, message, **arguments):
        result = self.install(**arguments)
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn(message, result.stderr)

    def test_missing_role_exits_nonzero_without_creating_function(self):
        result = self.psql("-f", str(SQL_DIRECTORY / "20261111-system-exception-log-retention.sql"))
        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("-v app_role=<应用角色>", result.stdout)
        self.assert_function_absent()

    def test_empty_or_unknown_app_role_rolls_back(self):
        for role in ("", "sl_missing"):
            with self.subTest(role=role):
                self.assert_install_rejected("app_role must name an existing role", app_role=role)
                self.assert_function_absent()
                self.assertEqual("f", self.sql("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sl_cleaner');"))

    def test_valid_role_succeeds_idempotently_with_expected_privileges(self):
        self.install_ok()
        snapshot = self.sql("SELECT proowner, proacl, proconfig, prosecdef, prosrc FROM pg_catalog.pg_proc "
                            "WHERE oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure;")
        self.install_ok()
        self.assertEqual(snapshot, self.sql("SELECT proowner, proacl, proconfig, prosecdef, prosrc FROM pg_catalog.pg_proc "
                                          "WHERE oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure;"))
        expected_owner = self.cleanup_owner or "sl_ddl_owner"
        self.assertEqual("t|t|t|t|f|f|f", self.sql(f"""
            SELECT p.proowner=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='{expected_owner}'),
                   p.prosecdef, 'search_path=pg_catalog, pg_temp'=ANY(p.proconfig),
                   pg_catalog.has_function_privilege('sl_runtime',p.oid,'EXECUTE'),
                   pg_catalog.has_function_privilege('sl_other',p.oid,'EXECUTE'),
                   pg_catalog.has_table_privilege('sl_runtime','public.gak_system_exception_log','DELETE'),
                   EXISTS (SELECT 1 FROM pg_catalog.aclexplode(p.proacl) a WHERE a.grantee=0)
            FROM pg_catalog.pg_proc p
            WHERE p.oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure;
        """))
        if self.cleanup_owner:
            self.assertEqual("f|f|f|f|f|f|f", self.sql("""
                SELECT rolsuper,rolcanlogin,rolbypassrls,rolcreatedb,rolcreaterole,rolinherit,rolreplication
                FROM pg_catalog.pg_roles WHERE rolname='sl_cleaner';
            """))
            self.assertEqual("t|t|f|f|f|f|f|t|f", self.sql("""
                SELECT pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','SELECT'),
                       pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','DELETE'),
                       pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','INSERT'),
                       pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','UPDATE'),
                       pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','TRUNCATE'),
                       pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','REFERENCES'),
                       pg_catalog.has_table_privilege('sl_cleaner','public.gak_system_exception_log','TRIGGER'),
                       pg_catalog.has_schema_privilege('sl_cleaner','public','USAGE'),
                       pg_catalog.has_schema_privilege('sl_cleaner','public','CREATE');
            """))
            self.assertEqual("0", self.sql("SELECT count(*) FROM pg_catalog.pg_auth_members "
                                           "WHERE member=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='sl_cleaner');"))
            self.assertEqual("DELETE,SELECT", self.sql("""
                SELECT pg_catalog.string_agg(a.privilege_type,',' ORDER BY a.privilege_type)
                FROM pg_catalog.pg_class c, LATERAL pg_catalog.aclexplode(c.relacl) a
                WHERE c.oid='public.gak_system_exception_log'::pg_catalog.regclass
                  AND a.grantee=(SELECT oid FROM pg_catalog.pg_roles WHERE rolname='sl_cleaner');
            """))

    def test_superuser_installer_preserves_nonsuperuser_table_owner(self):
        self.sql("ALTER TABLE public.gak_system_exception_log OWNER TO sl_other;"
                 "GRANT USAGE ON SCHEMA public TO sl_other;")
        self.install_ok()
        expected_owner = self.cleanup_owner or 'sl_other'
        self.assertEqual(expected_owner, self.sql("SELECT pg_catalog.pg_get_userbyid(proowner) "
                                                "FROM pg_catalog.pg_proc WHERE "
                                                "oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure;"))
        self.assertEqual('0', self.sql("SET ROLE sl_runtime; SELECT public.gak_purge_system_exception_log(TIMESTAMP '2100-01-01',500);").splitlines()[-1])

    def test_strict_boundary_thirty_day_floor_arguments_and_batches(self):
        self.install_ok()
        self.sql("""
            BEGIN;
            SET LOCAL ROLE sl_runtime;
            DO $$ DECLARE
              cutoff timestamp := (pg_catalog.now() AT TIME ZONE 'Asia/Shanghai') - INTERVAL '30 days';
              invalid_limit integer;
              batch integer;
              deleted_count integer;
            BEGIN
              INSERT INTO public.gak_system_exception_log VALUES
                (1,cutoff-INTERVAL '1 microsecond'),(2,cutoff),(3,cutoff+INTERVAL '1 microsecond');
              FOREACH invalid_limit IN ARRAY ARRAY[NULL,0,-1,1001] LOOP
                BEGIN
                  PERFORM public.gak_purge_system_exception_log(cutoff,invalid_limit);
                  RAISE EXCEPTION 'accepted invalid limit';
                EXCEPTION WHEN invalid_parameter_value THEN NULL;
                END;
              END LOOP;
              BEGIN
                PERFORM public.gak_purge_system_exception_log(NULL,500);
                RAISE EXCEPTION 'accepted null cutoff';
              EXCEPTION WHEN invalid_parameter_value THEN NULL;
              END;
              IF (SELECT count(*) FROM public.gak_system_exception_log)<>3 THEN
                RAISE EXCEPTION 'invalid arguments deleted rows';
              END IF;
              -- 独立调用后再查表，避免与函数调用共享删除前快照。
              deleted_count := public.gak_purge_system_exception_log(cutoff+INTERVAL '30 days',500);
              IF deleted_count<>1 THEN
                RAISE EXCEPTION 'unexpected strict boundary deletion count';
              END IF;
              IF EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=1)
                 OR NOT EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=2)
                 OR NOT EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=3) THEN
                RAISE EXCEPTION 'strict boundary or thirty day floor violated';
              END IF;
              INSERT INTO public.gak_system_exception_log
              SELECT n+10,cutoff-INTERVAL '1 day' FROM pg_catalog.generate_series(1,2500) n;
              FOR batch IN 1..5 LOOP
                deleted_count := public.gak_purge_system_exception_log(cutoff,500);
                IF deleted_count<>500 THEN
                  RAISE EXCEPTION 'unexpected batch size';
                END IF;
                IF EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id BETWEEN 11 AND batch*500+10) THEN
                  RAISE EXCEPTION 'delete order must be occurred_at,id ascending';
                END IF;
                IF (SELECT count(*) FROM public.gak_system_exception_log)<>2502-batch*500 THEN
                  RAISE EXCEPTION 'unexpected remaining batch rows';
                END IF;
              END LOOP;
              IF public.gak_purge_system_exception_log(cutoff,500)<>0 THEN
                RAISE EXCEPTION 'empty batch must return zero';
              END IF;
            END $$;
            ROLLBACK;
        """)

    def test_database_owner_cannot_replace_table_and_execute_trigger_as_definer(self):
        self.install_ok()
        self.sql(replacement_attack_sql(self.cleanup_owner))

    def test_owner_created_unsafe_structures_are_rejected(self):
        self.install_ok()
        for variant, (setup, message) in unsafe_structure_cases(self.cleanup_owner).items():
            with self.subTest(variant=variant):
                self.sql(unsafe_structure_sql(setup, message))

    def test_search_path_hijack_and_temporary_shadow_are_ignored(self):
        self.install_ok()
        self.sql("""
            BEGIN;
            SET LOCAL ROLE sl_runtime;
            CREATE SEQUENCE public.sl_hijack_calls;
            CREATE FUNCTION public.now() RETURNS pg_catalog.timestamptz LANGUAGE plpgsql AS $hijack$
            BEGIN
              PERFORM pg_catalog.nextval('public.sl_hijack_calls');
              RETURN TIMESTAMPTZ '2200-01-01 00:00:00+00';
            END $hijack$;
            SET LOCAL search_path = public, pg_temp, pg_catalog;
            SELECT now();
            DO $$ BEGIN
              IF NOT (SELECT is_called FROM public.sl_hijack_calls) THEN
                RAISE EXCEPTION 'hijack positive control failed';
              END IF;
            END $$;
            SELECT pg_catalog.setval('public.sl_hijack_calls',1,false);
            CREATE TEMP TABLE gak_system_exception_log(id bigint, occurred_at timestamp);
            INSERT INTO pg_temp.gak_system_exception_log VALUES (99,TIMESTAMP '2000-01-01');
            INSERT INTO public.gak_system_exception_log VALUES
                (1,(pg_catalog.now() AT TIME ZONE 'Asia/Shanghai')-INTERVAL '31 days'),
                (2,(pg_catalog.now() AT TIME ZONE 'Asia/Shanghai')-INTERVAL '29 days');
            DO $$ DECLARE deleted_count integer;
            BEGIN
              deleted_count := public.gak_purge_system_exception_log(TIMESTAMP '2100-01-01',500);
              IF deleted_count<>1 THEN
                RAISE EXCEPTION 'unexpected deletion count under hijack';
              END IF;
              IF (SELECT is_called FROM public.sl_hijack_calls) THEN
                RAISE EXCEPTION 'untrusted public.now was called';
              END IF;
              IF (SELECT count(*) FROM pg_temp.gak_system_exception_log)<>1
                 OR NOT EXISTS (SELECT 1 FROM pg_temp.gak_system_exception_log WHERE id=99) THEN
                RAISE EXCEPTION 'cleanup touched shadowing temporary table';
              END IF;
              IF (SELECT count(*) FROM public.gak_system_exception_log)<>1
                 OR NOT EXISTS (SELECT 1 FROM public.gak_system_exception_log WHERE id=2) THEN
                RAISE EXCEPTION 'hijack changed remaining rows';
              END IF;
            END $$;
            ROLLBACK;
        """)

    def test_app_table_owner_and_members_are_rejected(self):
        self.assert_install_rejected('app_role must not be table owner or its member', app_role='sl_ddl_owner')
        self.assert_function_absent()
        self.sql('GRANT sl_ddl_owner TO sl_runtime;')
        try:
            self.assert_install_rejected('app_role must not be table owner or its member')
            self.assert_function_absent()
        finally:
            self.sql('REVOKE sl_ddl_owner FROM sl_runtime;')

    def test_occupied_function_is_not_silently_replaced(self):
        self.sql("""
            SET ROLE sl_runtime;
            CREATE FUNCTION public.gak_purge_system_exception_log(timestamp,integer)
                RETURNS integer LANGUAGE sql AS 'SELECT 42';
        """)
        self.assert_install_rejected('existing purge function has unexpected owner')
        self.assertEqual('sl_runtime|SELECT 42', self.sql("""
            SELECT pg_catalog.pg_get_userbyid(proowner),prosrc FROM pg_catalog.pg_proc
            WHERE oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure;
        """))
        self.assertEqual('f', self.sql("SELECT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname='sl_cleaner');"))

    def test_installer_must_be_table_owner_or_superuser(self):
        # 即使无 public USAGE，也须在锁表前给出明确提示；有权限时仍不能绕过属主检查。
        self.sql('GRANT SELECT,INSERT,UPDATE,DELETE ON public.gak_system_exception_log TO sl_other;')
        for schema_usage in (False, True):
            with self.subTest(schema_usage=schema_usage):
                if schema_usage:
                    self.sql('GRANT USAGE ON SCHEMA public TO sl_other;')
                self.assertEqual('t' if schema_usage else 'f', self.sql(
                    "SELECT pg_catalog.has_schema_privilege('sl_other','public','USAGE');"))
                self.assert_install_rejected('installer must be table owner or superuser', executor='sl_other')
                self.assert_function_absent()


class DedicatedOwnerRetentionPsqlScriptTest(RetentionPsqlScriptTest):
    cleanup_owner = 'sl_cleaner'

    def test_unsafe_cleanup_attributes_and_role_relationships_are_rejected(self):
        for attribute in ('SUPERUSER', 'LOGIN', 'BYPASSRLS', 'CREATEROLE', 'CREATEDB'):
            with self.subTest(attribute=attribute):
                self.sql('CREATE ROLE sl_cleaner ' + attribute + ';')
                try:
                    self.assert_install_rejected('cleanup_owner has unsafe role attributes')
                    self.assert_function_absent()
                finally:
                    self.sql('DROP ROLE sl_cleaner;')
        self.assert_install_rejected('cleanup_owner must be nonempty', cleanup_owner='')
        self.assert_install_rejected('cleanup_owner must be nonempty', cleanup_owner='sl_runtime')
        self.assert_install_rejected('cleanup_owner must not be table owner', cleanup_owner='sl_ddl_owner')
        self.sql('CREATE ROLE sl_cleaner NOLOGIN; GRANT sl_cleaner TO sl_runtime;')
        try:
            self.assert_install_rejected('app_role must not be cleanup_owner or its member')
            self.assert_function_absent()
        finally:
            self.sql('REVOKE sl_cleaner FROM sl_runtime;')

    def test_migration_from_table_owner_and_repeated_install(self):
        # 模式 A -> B；B -> A 的 I-S3 检查要求先显式恢复函数属主。
        result = self.psql('-v','app_role=sl_runtime','-f',str(SQL_DIRECTORY / '20261111-system-exception-log-retention.sql'))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.install_ok()
        self.install_ok()
        result = self.psql('-v','app_role=sl_runtime','-f',str(SQL_DIRECTORY / '20261111-system-exception-log-retention.sql'))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('existing purge function has unexpected owner', result.stderr)
        self.sql('ALTER FUNCTION public.gak_purge_system_exception_log(timestamp,integer) OWNER TO sl_ddl_owner;')
        result = self.psql('-v','app_role=sl_runtime','-f',str(SQL_DIRECTORY / '20261111-system-exception-log-retention.sql'))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_psql_role_values_are_quoted_as_values_and_identifiers(self):
        unusual_app = "sl_app'\";--"
        unusual_cleaner = "sl_cleaner'\";--"
        self.sql('CREATE ROLE "' + unusual_app.replace('"','""') + '" NOLOGIN;')
        self.install_ok(app_role=unusual_app, cleanup_owner=unusual_cleaner)
        self.assertEqual(unusual_cleaner, self.sql("SELECT pg_catalog.pg_get_userbyid(proowner) FROM pg_catalog.pg_proc "
                                                "WHERE oid='public.gak_purge_system_exception_log(timestamp,integer)'::pg_catalog.regprocedure;"))


if __name__ == "__main__":
    unittest.main(verbosity=2)

"""无套接字环境的真实 PostgreSQL 权限回归；不替代 JDBC / MyBatis 映射测试。"""

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


if __name__ == "__main__":
    unittest.main(verbosity=2)

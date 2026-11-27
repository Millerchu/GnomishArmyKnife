-- 2026-11-27 体检报告明细与核心指标关联，可重复执行。
CREATE TABLE IF NOT EXISTS gak_health_report_detail (
    report_id BIGINT PRIMARY KEY REFERENCES gak_health_report(id) ON DELETE CASCADE,
    record_id BIGINT REFERENCES gak_health_record(id) ON DELETE RESTRICT,
    revision INTEGER NOT NULL CHECK (revision >= 1),
    items JSONB NOT NULL CHECK (jsonb_typeof(items) = 'array' AND jsonb_array_length(items) <= 200),
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_health_report_detail_record_id
    ON gak_health_report_detail (record_id)
    WHERE record_id IS NOT NULL;

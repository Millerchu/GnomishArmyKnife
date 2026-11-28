-- 2026-11-29 健康核心记录扩展字段（腰围、iOS 日常活动/睡眠/生理指标与数据来源），可重复执行。
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS waist_cm NUMERIC(5, 1);
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS steps INTEGER;
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS active_energy_kcal INTEGER;
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS exercise_minutes INTEGER;
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS distance_km NUMERIC(6, 2);
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS sleep_hours NUMERIC(4, 1);
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS blood_oxygen NUMERIC(4, 1);
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS hrv_ms INTEGER;
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS vo2_max NUMERIC(4, 1);
ALTER TABLE gak_health_record ADD COLUMN IF NOT EXISTS source_type VARCHAR(24);

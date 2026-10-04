
-- 2026-10-04 就诊实际自付金额，历史记录保持空值；可重复执行。
ALTER TABLE gak_health_visit ADD COLUMN IF NOT EXISTS amount NUMERIC(12, 2);
ALTER TABLE gak_health_visit DROP CONSTRAINT IF EXISTS ck_health_visit_amount;
ALTER TABLE gak_health_visit ADD CONSTRAINT ck_health_visit_amount CHECK (amount >= 0);

INSERT INTO gak_data_dictionary_item (id, dictionary_id, dict_code, item_code, item_label, item_value, sort_no, status, is_default, description, created_at, updated_at, deleted)
SELECT 5793, dictionary.id, dictionary.dict_code, 'medical', '医疗', '医疗', 16, 'ENABLED', FALSE, '就诊实际自付支出', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM gak_data_dictionary dictionary
WHERE dictionary.dict_code = 'PERSONAL_BILLS_BILL_CATEGORY' AND dictionary.deleted = FALSE
AND NOT EXISTS (SELECT 1 FROM gak_data_dictionary_item item WHERE item.dictionary_id = dictionary.id AND item.item_value = '医疗' AND item.deleted = FALSE)
ON CONFLICT (id) DO NOTHING;

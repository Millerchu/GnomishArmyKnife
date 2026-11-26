-- Apply after personal bills and credit accounts have been initialized.
CREATE UNIQUE INDEX IF NOT EXISTS uk_personal_credit_account_owner_id
    ON gak_personal_credit_account (owner_user_id, id);

CREATE TABLE IF NOT EXISTS gak_personal_bank_card (
    id BIGINT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    card_type VARCHAR(8) NOT NULL CHECK (card_type IN ('DEBIT', 'CREDIT')),
    card_name VARCHAR(56) NOT NULL,
    institution VARCHAR(64) NOT NULL,
    last_four VARCHAR(4) CHECK (last_four ~ '^[0-9]{4}$'),
    credit_account_id BIGINT,
    note VARCHAR(160),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CHECK (card_type = 'CREDIT' OR credit_account_id IS NULL),
    FOREIGN KEY (owner_user_id, credit_account_id) REFERENCES gak_personal_credit_account (owner_user_id, id)
);

CREATE INDEX IF NOT EXISTS idx_personal_bank_card_owner
    ON gak_personal_bank_card (owner_user_id, archived, card_type, card_name, id);
CREATE INDEX IF NOT EXISTS idx_personal_bank_card_credit
    ON gak_personal_bank_card (owner_user_id, credit_account_id) WHERE credit_account_id IS NOT NULL;

ALTER TABLE gak_personal_bill ADD COLUMN IF NOT EXISTS bank_card_id BIGINT REFERENCES gak_personal_bank_card (id);
CREATE INDEX IF NOT EXISTS idx_personal_bill_bank_card ON gak_personal_bill (bank_card_id) WHERE bank_card_id IS NOT NULL;

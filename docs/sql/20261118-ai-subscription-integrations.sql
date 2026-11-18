-- 智订预算与显式账单关联；先执行 business.sql。可重复执行。
CREATE TABLE IF NOT EXISTS gak_ai_budget (
    owner_user_id BIGINT NOT NULL REFERENCES gak_user(id),
    budget_month VARCHAR(7) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK(amount > 0),
    threshold NUMERIC(3,2) NOT NULL CHECK(threshold > 0 AND threshold <= 1),
    PRIMARY KEY(owner_user_id,budget_month,currency)
);
CREATE TABLE IF NOT EXISTS gak_ai_bill_link (
    owner_user_id BIGINT NOT NULL REFERENCES gak_user(id),
    payment_id BIGINT PRIMARY KEY REFERENCES gak_ai_payment(id),
    bill_id BIGINT NOT NULL UNIQUE,
    generated BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_ai_bill_link_owner ON gak_ai_bill_link(owner_user_id,payment_id);

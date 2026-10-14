-- SA-03 企業體質檢視「AI 投資建議」快取表（DEC-068 後續實作）。
-- 逐字比照 V1__init_schema.sql 的 stock_ai_suggestion（DB-01 §4.8），獨立一張表，
-- 不與目標價頁的 AI 建議共用同一列——兩個功能各自的 prompt/內容完全不同。

CREATE TABLE stock_health_ai_suggestion (
    stock_id        VARCHAR(10) NOT NULL,
    suggestion_text TEXT,
    model_name      VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(50) NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by      VARCHAR(50) NOT NULL,
    CONSTRAINT pk_stock_health_ai_suggestion PRIMARY KEY (stock_id),
    CONSTRAINT fk_stock_health_ai_suggestion_stock FOREIGN KEY (stock_id) REFERENCES stock (stock_id)
);

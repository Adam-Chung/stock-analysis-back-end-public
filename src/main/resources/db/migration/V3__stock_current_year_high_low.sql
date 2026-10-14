-- Tracks the in-progress calendar year's running high/low on `stock`, since stock_price_yearly
-- only ever stores complete calendar years (DEC-24) and previously left the current year's price
-- action out of the 10年高/10年低 displayed on the target-price screen entirely.

ALTER TABLE stock
    ADD COLUMN current_year_high_price NUMERIC(12, 4),
    ADD COLUMN current_year_low_price  NUMERIC(12, 4);

ALTER TABLE stock_target_price_setting
    DROP CONSTRAINT chk_eps_avg_years;

ALTER TABLE stock_target_price_setting
    ADD CONSTRAINT chk_eps_avg_years
        CHECK (eps_avg_years IN (0, 3, 5, 10));

ALTER TABLE stock_target_price_setting
    DROP CONSTRAINT chk_bps_avg_years;

ALTER TABLE stock_target_price_setting
    ADD CONSTRAINT chk_bps_avg_years
        CHECK (bps_avg_years IN (0, 3, 5, 10));

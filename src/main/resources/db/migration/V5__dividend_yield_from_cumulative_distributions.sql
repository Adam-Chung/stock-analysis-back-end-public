-- Switch 十年平均殖利率 away from FinMind's TaiwanStockPER.dividend_yield (a TTM ratio that can
-- blend two calendar years and never accounted for stock dividends) to a self-computed
-- 還原殖利率 based on that year's actual cash+stock distributions (see docs/agent/DECISIONS.md).
--
-- stock_dividend can have zero, one, or multiple rows per calendar year (semi-annual/季配), so a
-- single year's total can't live on stock_valuation_yearly's one-row-per-year shape without an
-- extra join. Instead each stock_dividend row now carries a running, chronologically cumulative
-- total for its own year - the row with the latest announcement_date in a year always holds that
-- year's full total, so no app-level grouping/summing is needed at read time.
ALTER TABLE stock_dividend
    ADD COLUMN cumulative_cash_dividend  NUMERIC(10, 4),
    ADD COLUMN cumulative_stock_dividend NUMERIC(10, 4);

-- No longer written or read; the app now computes yield at request time from stock_price_yearly
-- + stock_dividend instead.
ALTER TABLE stock_valuation_yearly
    DROP COLUMN avg_dividend_yield;

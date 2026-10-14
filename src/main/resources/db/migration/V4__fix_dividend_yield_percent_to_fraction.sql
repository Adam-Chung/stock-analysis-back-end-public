-- FinMind's TaiwanStockPER.dividend_yield is a percentage number (e.g. 1.22 meaning 1.22%), not
-- the decimal fraction (0.0122) the sync code assumed when it first stored these rows - verified
-- against the live API 2026-07-30 (docs/agent/DECISIONS.md DEC-019). Every avg_dividend_yield
-- value synced before that fix is 100x too large, which inflated plusOneYearDividendPrice and
-- broke yearsToRecover/yearsOfProfitIfSold (both divide by this yield). One-time backfill; new
-- syncs after the code fix already store the correct fraction.
UPDATE stock_valuation_yearly
SET avg_dividend_yield = avg_dividend_yield / 100
WHERE avg_dividend_yield IS NOT NULL;

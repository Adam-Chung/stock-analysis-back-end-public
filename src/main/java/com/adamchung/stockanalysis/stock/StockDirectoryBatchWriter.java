package com.adamchung.stockanalysis.stock;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 對全市場股票目錄做 PostgreSQL 批次 upsert，避免每一列都各自往返一次 Neon。 */
@Repository
public class StockDirectoryBatchWriter {

    private static final int BATCH_SIZE = 500;
    private static final String UPSERT_SQL = """
            INSERT INTO stock (
                stock_id, stock_name, industry_category, market_type,
                created_at, created_by, updated_at, updated_by
            )
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?)
            ON CONFLICT (stock_id) DO UPDATE SET
                stock_name = EXCLUDED.stock_name,
                industry_category = EXCLUDED.industry_category,
                market_type = EXCLUDED.market_type,
                updated_at = CURRENT_TIMESTAMP,
                updated_by = EXCLUDED.updated_by
            """;

    private final JdbcTemplate jdbcTemplate;

    public StockDirectoryBatchWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 批次 upsert 全部股票目錄資料列。
     *
     * @param rows 股票基本資料列
     */
    public void upsertAll(List<FinMindStockInfoDto> rows) {
        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                rows,
                BATCH_SIZE,
                (statement, row) -> {
                    statement.setString(1, row.stockId());
                    statement.setString(2, row.stockName());
                    statement.setString(3, row.industryCategory());
                    statement.setString(4, row.type());
                    statement.setString(5, AuditActors.FINMIND_DIRECTORY_SYNC);
                    statement.setString(6, AuditActors.FINMIND_DIRECTORY_SYNC);
                });
    }
}

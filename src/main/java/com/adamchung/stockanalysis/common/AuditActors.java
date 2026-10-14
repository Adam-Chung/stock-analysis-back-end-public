package com.adamchung.stockanalysis.common;

/**
 * 稽核欄位（{@code created_by}／{@code updated_by}）用的角色識別字串，集中管理（DEC-081）。
 * 這輪套件重組前，這8個值分散在11個不同的 service/writer 類別裡各自宣告成
 * {@code private static final String}，其中3組（{@link #DEFAULT_USER}、
 * {@link #FINMIND_DIRECTORY_SYNC}、{@link #FINMIND_SYNC}）在不同類別裡各自宣告了完全
 * 相同的值——集中到這裡之後這幾組重複宣告就消失了，其餘沒有單一「擁有者」功能。
 */
public final class AuditActors {

    private AuditActors() {
    }

    /** 尚未有登入機制時，使用者手動編輯資料的預設佔位值。 */
    public static final String DEFAULT_USER = "default-user";

    /** 企業體質檢視「更新本季資料」按鈕觸發的 TWSE OpenAPI／銀行局外部同步。 */
    public static final String EXTERNAL_SYNC = "external-sync";

    /** 企業體質檢視的 FinMind 自動同步（財報三表／股利／月營收／籌碼變化）。 */
    public static final String FINMIND_HEALTHCHECK_SYNC = "finmind-healthcheck-sync";

    /** 股票目錄（代號/名稱/產業別）的 FinMind 同步。 */
    public static final String FINMIND_DIRECTORY_SYNC = "finmind-directory-sync";

    /** 目標價頁 AI 投資建議的 Groq 產生結果。 */
    public static final String GROQ_SUGGESTION = "groq-suggestion";

    /** 企業體質檢視頁 AI 投資建議的 Groq 產生結果。 */
    public static final String HEALTH_CHECK_AI_SUGGESTION = "health-check-ai-suggestion";

    /** 目標價功能的 FinMind 逐日→年度聚合同步（股價／估值／股利／財報）。 */
    public static final String FINMIND_SYNC = "finmind-sync";

    /** 企業體質檢視董監持股/質押、法人持股比、銀行監理指標的 TWSE/銀行局同步。 */
    public static final String EXTERNAL_DATA_SYNC = "external-data-sync";

}

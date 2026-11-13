# Security Policy

此 repository 是由 Private source of truth 產生、已排除 production state 的 source-available 公開版本，不是可直接部署的安全範本。

請勿在 Public issue、Pull Request、commit 或 log 中張貼密碼、API key、token、連線字串、個人資料或內部 URL。若發現疑似弱點，請使用 GitHub 的 Private Vulnerability Reporting；若該功能不可用，先透過 repository owner 的 GitHub profile 私下聯絡。

目前版本使用 Google ID token 驗證、伺服器端 session、HttpOnly cookie、CSRF 保護與使用者資料隔離，但這些機制不代表任何自行部署環境已安全完成設定。公開部署前仍須使用自己的 Google OAuth client，確認 HTTPS、cookie policy、CORS、CSRF、帳號停用、管理者權限、資料刪除與 rate limit 的實際行為，並重新執行 threat modeling、dependency scan、secret scan 與 integration test。

若憑證可能外洩，應先撤銷或 rotate，再評估 Git history、Actions logs、artifacts 與 cached content 的清理範圍。

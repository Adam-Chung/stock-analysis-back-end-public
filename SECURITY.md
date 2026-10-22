# Security Policy

此 repository 是由 Private source of truth 產生、已排除 production state 的 source-available 公開版本，不是可直接部署的安全範本。

請勿在 Public issue、Pull Request、commit 或 log 中張貼密碼、API key、token、連線字串、個人資料或內部 URL。若發現疑似弱點，請使用 GitHub 的 Private Vulnerability Reporting；若該功能不可用，先透過 repository owner 的 GitHub profile 私下聯絡。

已知限制：simple auth 為單一使用者原型，未採 password hashing、token hashing、到期時間、rotation、rate limiting 或完整 authorization。任何公開部署前都必須替換並重新進行 threat modeling、dependency scan、secret scan 與 integration test。

若憑證可能外洩，應先撤銷或 rotate，再評估 Git history、Actions logs、artifacts 與 cached content 的清理範圍。

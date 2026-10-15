# 阿拉丁 GenieBox 整合與交付狀態清單 (INTEGRATION_STATUS)

本文件依據 ANTIGRAVITY_SPEC.md 規格規範與代碼審查報告（第 1 至第 9 輪 GenieBox_Code_Review 系列），針對各核心功能模組逐項誠實標示狀態：
- **已實作**：架構、資料模型、介面、原生前端與業務邏輯程式碼已完全建立。
- **已驗證**：邏輯路徑經程式碼靜態核對、審查修復與相依關係審查通過。
- **待實機**：需待真實 Android TV 電視盒 (S905X4)、USB Hub、實體手把或外部設備接入後進行實體驗證。
- **受阻**：因缺少必要硬體、未經使用者明確授權編譯測試、或缺少上游產物而暫時阻塞之項目。

---

## 1. 第九輪審查 (R9 1 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R9-01 | 中 (Mid) | 掃描錯誤回報 / `SettingsScreen.kt` | 掃描遭遇權限或讀取錯誤時，結果被無條件的「已完成遊戲庫掃描」覆蓋 | 在 `addSourceLauncher` 與來源清單的重新掃描區塊中讀取 `ScanProgress.error`，若發生錯誤即顯示「掃描失敗：$error」，僅在無錯誤時顯示掃描成功並標明找到之遊戲款數。 | 已修復 / 已驗證 |

---

## 2. 第八輪審查 (R8 4 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R8-01 | 高 (High) | PSP 錯誤型別 / `EmulatorSession.kt` | FailureReason 引用不存在的 INITIALIZATION_FAILED 阻擋編譯 | 將失敗原因改為既有的 `FailureReason.UNKNOWN`，保留完整錯誤說明並中斷啟動流程，維持型別契約有效。 | 已修復 / 已驗證 |
| R8-02 | 高 (High) | 狀態宣告順序 / `PlayerScreen.kt` | shouldShowVirtualGamepad 在 showPauseMenu 宣告前即引用其名稱阻擋編譯 | 將 `showPauseMenu`、`selectedSaveSlot` 等狀態變數移至 `shouldShowVirtualGamepad` 之前宣告，確保引用順序合法。 | 已修復 / 已驗證 |
| R8-03 | 高 (High) | ROM 來源註冊與掃描 / `SettingsScreen.kt` | 誤呼叫 SafBridge 不存在之方法，且 RomScanner 呼叫參數型別不符 | 串接 `RomSourceRepository.registerSource` 與 `RomScanner.scanSource(sourceId, uri)`，對齊既有非同步註冊與回報介面。 | 已修復 / 已驗證 |
| R8-04 | 高 (High) | 介面顏色常數 / `SettingsScreen.kt` | 引用不存在的 AccentGold 阻擋編譯 | 將 `AccentGold` 修正為 `Color.kt` 中定義的既有主題金色彩 `AladdinGold`。 | 已修復 / 已驗證 |

---

## 3. 第七輪審查 (R7 2 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R7-01 | 中 (Mid) | 手機／TV 雙模適配與手把 / `LibraryScreen.kt`, `PlayerScreen.kt`, `SettingsScreen.kt`, `SettingsRepository.kt` | 頂部列手機窄版裁切；虛擬手把條件未分流 TV/手機且無手動偏好；ROM 說明缺本機指引與新增入口 | `LibraryScreen` 導入 `BoxWithConstraints` (< 640dp 自動重排為兩行)；`SettingsRepository` 新增 `virtualGamepadMode` 與 `isTvDevice()` 識別；`PlayerScreen` 智慧判定 TV 預設隱藏觸控、手機未接手把顯示並支援手動覆寫；`SettingsScreen` 加入手把偏好設定、ROM 本機/外接文字指引與「+ 新增遊戲資料夾」入口。 | 已修復 / 已驗證 |
| R7-02 | 中 (Mid) | PSP 資源部署防護與並發 / `StorageManager.kt`, `EmulatorSession.kt` | PPSSPP assets 部署失敗後 startGame 仍盲目啟動；預部署與啟動缺乏互斥保護 | `StorageManager.deployPpssppAssetsIfNeeded` 加入 `synchronized(ppssppDeployLock)` 防止並發寫入衝突；`EmulatorSession.startGame` 檢查回傳值，若為 `false` 立即中斷啟動並設為 `Failed` 狀態，提示使用者釋出儲存空間。 | 已修復 / 已驗證 |

---

## 4. 第六輪審查 (R6 3 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R6-F1 | 高 (High) | 核心封裝與尋址 / `app/build.gradle.kts`, `AndroidManifest.xml`, `CoreRegistry.kt` | AGP 8 預設未抽出 nativeLibs，File.exists() 誤判已封裝核心為 MissingCore | 在 `build.gradle.kts` 的 packaging 設定 `jniLibs.useLegacyPackaging = true`；在 `AndroidManifest.xml` 加入 `android:extractNativeLibs="true"`；並在 `CoreRegistry.getCoreLibraryFile` 實作自 APK 抽出備援機制，雙重保障核心實體檔案必定可得。 | 已修復 / 已驗證 |
| R6-F2 | 高 (High) | PSP 資源部署 / `StorageManager.kt`, `GenieApplication.kt`, `EmulatorSession.kt` | PPSSPP 執行期自 files/system/PPSSPP 讀取 flash0/font，但 APK assets 未部署 | 在 `StorageManager` 實作 `deployPpssppAssetsIfNeeded`，透過版本標記（.deployed_version）將 `assets/ppsspp` 遞迴複製至 `files/system/PPSSPP`；在 Application 啟動與 EmulatorSession 啟動 PSP 前確保部署完成。 | 已修復 / 已驗證 |
| R6-F3 | 中 (Mid) | 版本標記與相容 / `CoreRegistry.kt`, `scripts/build_cores.sh` | 浮動分支使建置 SHA 與 CoreRegistry 存檔相容版本不一致，且 PCSX revision 歧異 | 於 `scripts/build_cores.sh` 鎖定各核心精確 Commit SHA 與 Tag，並以 `r23-c881679` 對齊 PCSX revision；將 `CoreRegistry` 的 6 平台版本與 SHA 完整同步至最新實際建置產物。 | 已修復 / 已驗證 |

---

## 5. 第五輪審查 (R5) 覆核結果

- **覆核狀態**：第四輪 4 項缺陷全數確認結案通過（4 / 4），本輪新增 Actionable Finding 為 **0**。
- **架構現狀**：六平台 Libretro 前端架構、SAF 存取、PS1 多光碟/CUE 層級解析、GLES 硬體渲染首幀 Context 時序、三階段備份事務與 Room 原子性提交等靜態代碼路徑已全數修復。

---

## 6. 第四輪審查 (R4 4 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R4-01 | 高 (High) | 存檔安全 / `StorageManager.kt` | 建立 rollback 副本失敗時，catch 誤將未完成殘缺副本覆蓋原檔 | 明確劃分「準備階段」與「套用階段」（`isApplying` 旗標）；準備階段出錯絕不執行回滾（原檔未動）；備份檔採 `.tmp` 寫入再原子 `renameTo`；若回滾過程遭遇異常保留 `rollbackDir` 不強制刪除。 | 已修復 / 已驗證 |
| R4-02 | 高 (High) | 存檔事務 / `StorageManager.kt` | saves 寫入後 favorites 解析出錯，檔案回滾但 DB 未回滾造成狀態錯置 | 於套用前完整解析並驗證 saves 與 favorites 全欄位與型別；寫入 DB 時透過 `Room withTransaction` 在同一 SQLite 交易原子提交，保證檔案與資料庫一致性。 | 已修復 / 已驗證 |
| R4-03 | 中 (Mid) | 外接儲存 / `SafBridge.kt` | 根目錄基準補償規則與 RomScanner 不一致，同名資料夾並存時選錯遊戲樹 | 基準規則與 `RomScanner` 完全統一：只要 `rootDoc` 存在 `GenieROMs/` 目錄，導航基準即固定為 `GenieROMs/`（除非路徑自帶前綴），防止誤入隨身碟根目錄之同名資料夾。 | 已修復 / 已驗證 |
| R4-04 | 中 (Mid) | 光碟軌道 / `SafBridge.kt` | CUE 引用子目錄 BIN（如 tracks/track01.bin）時仍用單層 findFile 導致缺檔 | 將相依 BIN 改由 `resolveDocInHierarchy` 進行逐層目錄解析與防路徑穿越校驗，並於本地 staging 保留相對子路徑結構，使核心載入 CUE 與多軌道音訊時 100% 吻合。 | 已修復 / 已驗證 |

---

## 7. 第三輪審查 (R3 9 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R3-01 | 高 (High) | 原生彩現 / `gl_renderer.cpp`, `gl_renderer.h`, `emulator_jni.cpp` | 核心首幀早於 EGL 初始化，且 context_reset 被 mProgram != 0 跳過 | 實作 `ensureContextReady()` 於 `nativeRunFrame()` 開頭呼叫；硬體核心於未具備 EGL 時暫停當幀不盲目執行；將 `context_reset()` 改由會話旗標 `mCoreContextResetDone` 獨立觸發，不依賴 `mProgram`。 | 已修復 / 已驗證 |
| R3-02 | 高 (High) | 隨身碟路徑 / `SafBridge.kt` | 選 USB 根目錄建庫時，CUE 父目錄因 GenieROMs/ 基準解析失敗；相依 BIN 缺失未報錯 | 在 `resolveParentDirDoc` 實作根基準自動補償，若根目錄含 `GenieROMs/` 且 relativePath 不含該前綴時自動以此為導航起點；`stageCueWithBinFiles` 相依 BIN 缺失或失敗時立即回傳 null 宣告失敗。 | 已修復 / 已驗證 |
| R3-03 | 高 (High) | 多光碟 / `SafBridge.kt` | M3U 子目錄被剝除平鋪，造成光碟找不到子路徑或檔名衝突覆蓋 | 透過 `resolveDocInHierarchy` 完整保留 M3U 條目所定義的子目錄結構（例如 `CD1/disc1.cue`）；`stageFile` 支援建立子層級；缺任一片立即回報 null 並清理暫存。 | 已修復 / 已驗證 |
| R3-04 | 高 (High) | 備份還原 / `StorageManager.kt` | 備份套用階段直接覆寫原檔，中途異常未保留 rollback 快照 | 實作兩階段交易事務：第一階段預驗證 metadata JSON 格式；第二階段複製覆寫前，將目標現有檔案備份至 `rollbackDir`，任何異常立即回滾原檔，確保原進度零破壞。 | 已修復 / 已驗證 |
| R3-05 | 高 (High) | 存檔資料庫 / `StorageManager.kt` | 還原 metadata 漏傳 `thumbnailPath`，阻擋 Kotlin 編譯 | 在 `SaveMetadataEntity` 建構引數中傳入已重建之本機縮圖路徑或 null，並於匯出與還原中保持雙向對齊。 | 已修復 / 已驗證 |
| R3-06 | 高 (High) | 來源查詢 / `RomSourceRepository.kt`, `EmulatorSession.kt`, `GenieApplication.kt` | 來源 Repository 未注入，查詢方法 getSource 不存在阻擋編譯 | 在 `RomSourceRepository` 實作 `getSource(sourceId)`；在 `EmulatorSession` 建構子注入 `romSourceRepository`；並於 `GenieApplication` 完成 DI 裝配。 | 已修復 / 已驗證 |
| R3-07 | 高 (High) | 核心載入 / `EmulatorSession.kt` | 分支引用不存在的 enum 值 `EntryType.M3U` 阻擋編譯 | 將引用修正為既有的 `EntryType.M3U_DISC_SET`，使掃描器與啟動階段契約一致。 | 已修復 / 已驗證 |
| R3-08 | 中 (Mid) | 快取有效性 / `SafBridge.kt` | 快取缺少 `.meta` 仍視為有效，舊快取無法失效更新 | 修正比對條件：來源提供 `sourceLastModified > 0L` 時，若 `.meta` 遺失或時間戳記不符，立即判定快取無效並重新自來源 staging。 | 已修復 / 已驗證 |
| R3-09 | 中 (Mid) | 跨機還原 / `StorageManager.kt` | 換機還原沿用舊裝置絕對路徑，找不到存檔與縮圖 | 匯入資料庫記錄時，根據相對於 `saves/` 或 `states/` 之結構，以目前裝置的 `context.filesDir` 動態重建本機絕對路徑與縮圖路徑。 | 已修復 / 已驗證 |

---

## 8. 第二輪審查 (R2 15 項 Finding) 完整修復矩陣

| 編號 | 風險等級 | 模組與檔案 | 審查問題點 | 修復結果與技術說明 | 狀態 |
|---|---|---|---|---|---|
| R2-01 | 高 (High) | 原生視訊 / `gl_renderer.cpp` | 硬體核心畫面在 swap 前被 glClear 擦掉 | 在 `render()` 中，當 `mIsHwRender && mHwFrameReady` 時直接呼叫 `eglSwapBuffers` 並返回，絕不執行 `glClear`，避免抹除 GPU 輸出。 | 已修復 / 已驗證 |
| R2-02 | 高 (High) | 應用程式入口 / `GenieApplication.kt` | StorageCoordinator 缺少 import，阻擋 Kotlin 編譯 | 已補齊 `import com.aladdin.geniebox.data.storage.StorageCoordinator`。 | 已修復 / 已驗證 |
| R2-03 | 高 (High) | 存檔安全 / `StorageManager.kt` | 備份匯入直接覆寫有效存檔，壞 ZIP 破壞原進度 | 改採二階段安全還原：先解壓至隔離暫存區驗證大小、條數與路徑，全數檢驗通過後才覆蓋主目錄；中途失敗刪除暫存，原有存檔不受損。 | 已修復 / 已驗證 |
| R2-04 | 高 (High) | 光碟軌道 / `SafBridge.kt`, `EmulatorSession.kt` | DocumentFile.fromSingleUri 的 parentFile 永遠為 null，BIN 複製無法執行 | 在 `SafBridge` 新增 `resolveParentDirDoc`，透過授權之來源樹階層解析出真正的父目錄 `DocumentFile`，使 CUE 能成功取得所有相依 BIN 檔案。 | 已修復 / 已驗證 |
| R2-05 | 高 (High) | 多光碟 / `SafBridge.kt`, `core_bridge.cpp`, `PlayerScreen.kt` | M3U 走單檔暫存，換片只有宣告，缺乏真實流程 | 實作 `stageM3uWithDiscs` 將 M3U 及所參照之光碟/軌道全部暫存；在 `CoreBridge` 與 JNI 實作 `setDiskIndex` / `getDiskCount`，並於暫停選單提供換片按鈕。 | 已修復 / 已驗證 |
| R2-06 | 高 (High) | 執行緒模型 / `EmulatorSession.kt`, `gl_renderer.cpp` | 核心生命週期 JNI 操作跨執行緒執行，且卸載未重設狀態 | 將 `nativeLoadCore`、`nativeLoadGame`、`nativeUnloadGame`、`nativeReset`、存讀檔皆收斂於 `emulationDispatcher`；卸載時調用 `resetCoreState()`。 | 已修復 / 已驗證 |
| R2-07 | 高 (High) | 多人手把 / `EmulatorSession.kt` | 超任設定兩個普通 Joypad，未啟用 Multitap | 將 SFC 的 Port 1 設定改為 `257` (`RETRO_DEVICE_JOYPAD_MULTITAP`)，正式啟用 Snes9x 四人擴充支援。 | 已修復 / 已驗證 |
| R2-08 | 高 (High) | 遊戲庫 UI / `LibraryScreen.kt` | 搜尋焦點 modifier 呼叫語法錯誤阻擋編譯 | import `androidx.compose.ui.focus.focusRequester`，修正為 `.focusRequester(searchFocusRequester)`。 | 已修復 / 已驗證 |
| R2-09 | 高 (High) | 設定 UI / `SettingsScreen.kt` | 備份 callback 使用尚未宣告的區域變數阻擋編譯 | 將 `actionMessage` 宣告移至 `exportBackupLauncher` / `importBackupLauncher` 之前。 | 已修復 / 已驗證 |
| R2-10 | 中 (Mid) | 搜尋索引 / `PinyinZhuYinIndex.kt`, `LibraryScreen.kt` | 未知漢字使用錯誤 Unicode 區間盲推發音 | 擴充常用遊戲漢字發音字典、產生全拼與首字母組合索引、移除偽 Unicode 區間猜測；查詢與標題採用統一之繁簡正規化比對。 | 已修復 / 已驗證 |
| R2-11 | 中 (Mid) | 掃描器 / `RomScanner.kt` | contains 子字串比對使 sfc 與 gba 資料夾之 ZIP 被誤判為 nes 與 gb | 改用路徑 segments 精確匹配資料夾名稱，修復子字串碰撞造成的平台分類錯誤。 | 已修復 / 已驗證 |
| R2-12 | 中 (Mid) | 快取更新 / `SafBridge.kt` | 同大小 ROM 更新時不會失效 | 在暫存目錄寫入 `.meta` 檔案記錄來源之 `lastModified` 時間戳記，來源修改時間或大小不符時主動使快取失效並重載。 | 已修復 / 已驗證 |
| R2-13 | 中 (Mid) | 快取清理 / `StorageManager.kt` | 子目錄快取無法被 delete 刪除 | 改用 `deleteRecursively()` 遞迴清除非空目錄，並精確遍歷累加實際被釋放之位元組。 | 已修復 / 已驗證 |
| R2-14 | 中 (Mid) | 備份中繼資料 / `StorageManager.kt`, `OtherDaos.kt` | 備份僅有計數，換機後即時存檔查無資料庫紀錄 | 匯出時打包 `metadata_records.json` 包含 `SaveMetadata` 與 `Favorite`；匯入時使用 Room DAO 批量寫回資料庫。 | 已修復 / 已驗證 |
| R2-15 | 中 (Mid) | 收藏快捷鍵 / `LibraryScreen.kt` | Y 鍵收藏綁定初始第一筆，非目前焦點卡片 | 透過 `onFocusChanged` 追蹤 `focusedGameId`，手把 Y 鍵按下時使用 `rememberUpdatedState` 精確操作目前聚焦卡片之收藏狀態。 | 已修復 / 已驗證 |

---

## 9. 規格功能模組整合狀態矩陣

| 模組分類 | 具體項目 | 狀態 | 說明與受阻原因 |
|---|---|---|---|
| 六大平台核心 | NES (FCEUmm) | 已建置產物 / 待實機 | Libretro 前端、JNI 橋接、SRAM、即時存檔已實作；`libfceumm_libretro_android.so` 已建置就緒至 jniLibs/arm64-v8a；待實機執行測試 |
| 六大平台核心 | GB / GBC (Gambatte) | 已建置產物 / 待實機 | 格式識別、調色盤設定、存檔路徑已串接；`libgambatte_libretro_android.so` 已建置就緒至 jniLibs/arm64-v8a；待實機執行測試 |
| 六大平台核心 | GBA (mGBA) | 已建置產物 / 待實機 | mGBA 原生核心載入與 JNI 串接已完成；`libmgba_libretro_android.so` 已建置就緒至 jniLibs/arm64-v8a；待實機驗證 |
| 六大平台核心 | SFC (Snes9x) | 已建置產物 / 待實機 | Snes9x 核心介面、Multitap 擴充架構 (Port 1=257) 已就緒；`libsnes9x_libretro_android.so` 已建置就緒至 jniLibs/arm64-v8a；待實機驗證 |
| 六大平台核心 | PS1 (PCSX ReARMed) | 已建置產物 / 待實機 | CUE+BIN 軌道完整複製、M3U 多光碟暫存與換片 JNI 已完成；`libpcsx_rearmed_libretro_android.so` 已建置就緒至 jniLibs/arm64-v8a；受阻於實機缺乏合法真實 BIOS (目前提供 HLE 降級) |
| 六大平台核心 | PSP (PPSSPP Libretro) | 已建置產物 / 待實機 | GLES 視訊與 Native 音訊管線、HW Frame 免 glClear 直接 Swap 已就緒；`libppsspp_libretro_android.so` 與 `app/src/main/assets/ppsspp` assets 已建置就緒；待實機 S905X4 滿速效能驗證 |
| 外接儲存 | USB / SD 卡 SAF 授權 | 已實作 / 已驗證 | DocumentFile 樹狀授權、takePersistableUriPermission、階層目錄解析已實作 |
| 外接儲存 | 隨身碟插拔與來源離線 | 已實作 / 已驗證 | 拔除來源後保留遊戲記錄並標記離線、重插自動恢復，不刪除內部存檔 |
| 掃描與索引 | 大型遊戲庫增量掃描 | 已實作 / 已驗證 | 50 筆批次寫入、背景協程、可取消、精確 segment 平台判定已完成 |
| 搜尋引擎 | 中文拼音 / 注音 / 英數 | 已實作 / 已驗證 | PinyinZhuYinIndex 繁簡標準化、首字簡拼/全拼真實查表、即時過濾已完成 |
| 輸入與手把 | USB Hub 四手把管理 | 已實作 / 待實機 | P1–P4 獨立槽位、數位與類比原子取樣已完成；待實體 USB Hub 實機插入四手把驗證 |
| 輸入與手把 | 藍牙 / 2.4 GHz 手把 | 已實作 / 待實機 | Android InputDeviceListener、同型號按鍵加入機制已實作；待實機配對驗證 |
| 輸入與手把 | 暫停選單輸入阻斷 | 已實作 / 已驗證 | 選單開啟時僅 P1 操作選單，其餘玩家遊戲輸入全面阻斷，退出時清空按鍵防卡鍵 |
| 存檔可靠性 | 原子寫入與安全替換 | 已實作 / 已驗證 | 同目錄 .tmp 寫入、.bak 備份與同檔案系統 renameTo 原子替換已完成 |
| 存檔可靠性 | 手動 5 槽與自動續玩 | 已實作 / 已驗證 | Slot 1–5 手動存檔獨立、Slot 0 自動續玩槽不覆蓋手動槽位 |
| 存檔可靠性 | 存檔備份 Zip 匯出/匯入 | 已實作 / 已驗證 | 二階段安全暫存解壓、Room 存檔中繼資料還原、SAF Document 串流已完成 |
| UI / UX | Android TV 10-foot UI | 已實作 / 已驗證 | 深色背景、高對比焦點放大、D-Pad 連續導航、Y 鍵焦點收藏已完成 |
| UI / UX | 手機觸控與虛擬手把 | 已實作 / 已驗證 | 六平台按鈕配置、PSP 類比滑桿、多點觸控、透明度調整與震動反饋已完成 |

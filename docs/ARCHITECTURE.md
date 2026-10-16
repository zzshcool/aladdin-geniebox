# 阿拉丁 GenieBox 架構設計文件 (ARCHITECTURE)

## 1. 系統整體架構

阿拉丁 GenieBox 採單一 APK 封裝架構，主要包含四大層級：

```text
[UI 層 (Jetpack Compose)]
   -> TV 10-foot UI / Mobile 觸控與虛擬手把
   -> 焦點管理、按鍵映射提示、暫停選單、InputMode UI 導航隔離
         |
[領域與應用服務層 (Kotlin Flow / Coroutines)]
   -> EmulatorSession (專屬單線程調度器 EmuSessionThread、生命週期狀態機)
   -> InputManager (四玩家手把槽位、USB Hub、斷線處理、UI 快捷鍵)
   -> RomScanner (SAF 增量掃描、CUE+BIN 關聯、字典查表繁簡拼音注音索引)
   -> StorageCoordinator (全域外接隨身碟廣播與前景活躍度協調)
         |
[資料持久化與儲存層 (Room / SAF / Filesystem)]
   -> GenieDatabase (GameWithFavorite LEFT JOIN 視圖, RomSource, SaveMetadata)
   -> StorageManager (原子雙重替換、.bak 容錯自動還原、SAF 備份串流匯出匯入)
   -> SafBridge (DocumentFile 串流複製、gameId 目錄隔離、CUE+BIN 軌道同步 staging)
         |
[原生 Libretro 前端引擎 (C++17 / JNI)]
   -> CoreBridge (dlopen、動態符號綁定、Multitap 指派、RETRO_HW_FRAME_BUFFER_VALID 硬體幀分流)
   -> GLRenderer (Worker Thread 專屬 EGLContext、GLES 著色器、原始比例/整數/拉伸視口)
   -> AudioRingBuffer (有界雙聲道環狀緩衝、取樣率同步)
   -> InputManagerNative (原子取樣、P1-P4 獨立埠號輸入快照)
```

## 2. 執行緒與生命週期模型

### 2.1 執行緒責任劃分
1. **主執行緒 (Main Thread / UI)**：
   - 負責 Compose 元件組合、遙控器與手把事件分發 (`dispatchKeyEvent` / `dispatchGenericMotionEvent`)。
   - `InputMode.UI_NAVIGATION` 模式下手把事件保留給 Compose Focus Manager 與全域快捷鍵（Y 鍵收藏、X 鍵搜尋、L1/R1 切換分頁）。
   - 暫停選單顯示期間阻斷其餘玩家之遊戲輸入。
2. **模擬執行緒 (Emulation Dedicated Thread - `EmuSessionThread`)**：
   - 使用 `newSingleThreadContext("EmuSessionThread")` 建立專屬單線程調度器，消除跨執行緒資料競爭。
   - 負責 dlopen、retro_init、retro_load_game、nativeRunFrame、GLRenderer::render 與存檔生命週期。
   - 依核心回報之 FPS (如 50 Hz, 59.94 Hz, 60 Hz) 計算每幀奈秒週期，動態補償掉幀，嚴禁固定 60 次硬性 sleep。
3. **音訊串流執行緒 (Audio Thread - IO Dispatcher)**：
   - 獨立驅動 `AudioTrack` (MODE_STREAM)。
   - 每隔 2-20 ms 從 C++ 原生 `AudioRingBuffer` 取出雙聲道 16-bit PCM 資料刷入音訊裝置，嚴禁在原生回呼中直接做阻塞式 JNI。
4. **I/O 與掃描執行緒 (I/O Dispatcher)**：
   - 負責外接 USB/SD 目錄之 SAF 遍歷、檔案複製 Staging、ZIP 單檔校驗與 Room 資料庫批次寫入。

### 2.2 工作階段狀態機 (EmulationSessionState)
模擬工作階段透過 Mutex 序列化所有生命週期操作，防止併發崩潰：
- `Idle` -> `Preparing` (檢查 ABI、驗證核心 .so、檢查 BIOS、複製 ROM Staging 與 BIN 軌道)
- `Preparing` -> `Loading` (dlopen、retro_init、retro_load_game、載入 SRAM)
- `Loading` -> `Running` (啟動音訊軌與模擬迴圈)
- `Running` -> `Paused` (暫停迴圈與音訊，開啟選單，阻斷遊戲輸入)
- `Running/Paused` -> `Stopping` (flush SRAM、自動寫入 Auto-Resume 即時存檔；若存檔失敗則拋出異常阻斷卸載，防止資料遺失)
- 任何失敗階段 -> `Failed` (提供明確 FailureReason，不以假成功掩蓋)

## 3. 彩現管線與 EGL 執行緒隔離

1. **EGLContext 單一線程維護**：
   - 遵循 OpenGL ES / EGL 規範，EGL Display、Surface 與 Context 的建立及銷毀完全收斂於模擬背景執行緒 (`initEGLOnCurrentThread`)。
   - Android UI SurfaceHolder 回呼僅以 `setNativeWindow` 更新底層指標，絕不在 UI 執行緒初始化或銷毀 Context，防止跨線程漂移崩潰。
2. **軟硬體彩現分流**：
   - 軟體像素核心 (NES, GB, GBA, SFC)：原生回呼拷貝像素至 double buffer，經著色器轉換紋理。
   - 硬體彩現核心 (PS1, PSP)：偵測 `RETRO_HW_FRAME_BUFFER_VALID` (`(uintptr_t)-1`)，直接呼叫 `notifyHwFrameReady()`，由 GPU FBO 負責幀緩衝切換，嚴禁進入 CPU 記憶體複製路徑。

## 4. 資料可靠性與存檔保護

1. **目錄分工規範**：
   - `files/saves/<platform>/<game-id>/`：保存原生電池記憶卡 (SRAM/EEPROM)。
   - `files/states/<core-id>/<game-id>/`：保存即時存檔 (Slot 1-5 手動，Slot 0 自動續玩)。
   - `files/system/`：放置使用者匯入之 BIOS (如 scph5501.bin)。
   - `cache/rom-staging/<game-id>/`：依 gameId 隔離暫存外接來源檔案，避免多檔案或同名遊戲覆寫。
2. **雙重原子替換與崩潰恢復策略**：
   - 寫入存檔時，先寫入同目錄之 `.tmp` 檔案並強制 `sync()`。
   - 將既有存檔重命名為 `.bak`，再將 `.tmp` 重命名為目標存檔。
   - 替換成功後保留 `.bak` 作為備用；讀取時若主檔損壞或缺失，自動從同名 `.bak` 備份無損還原。
3. **備份與 SAF 串流**：
   - 支援 SAF `CreateDocument` 與 `OpenDocument`，直接讀寫 OutputStream / InputStream。
   - 打包 `saves/`、`states/` 並寫入 `manifest.json` 中繼資料；解壓時嚴格檢查防止路徑穿越 (`..`)。

## 5. 輸入與四玩家架構

1. **USB Hub 與裝置管理**：
   - Android 系統將每個手把分配獨立之 `InputDevice` 與 `deviceId`。
   - `InputManager` 維護 P1-P4 實體槽位。插入單支手把時自動指派至空槽位；拔除單支手把標記為離線，保留其槽位不打亂其餘玩家順序。
   - 支援 SFC 多人 Multitap 變數查詢與裝置指派，多玩家按鍵能分流至 Libretro port 0–3。
2. **UI 導航與遊戲輸入模式分流**：
   - `InputMode.UI_NAVIGATION`：首頁、收藏庫與設定畫面，手把按鍵直通 Android 焦點系統，支援 Y 鍵收藏與 X 鍵搜尋。
   - `InputMode.IN_GAME`：遊戲中所有玩家按鍵轉化為 Libretro 核心按鍵遮罩。
   - `InputMode.PAUSE_MENU`：暫停選單僅允許 P1 操作選單，其餘玩家輸入全數阻斷。

## 6. 遊戲操作與語言擴充（2026-10-16）

- **加速與聲音**：`SettingsRepository` 依遊戲保存 1×、1.25×、1.5×、1.75×、2×、3× 目標倍率；加速靜音偏好為共用設定，預設開啟。`EmulatorSession` 調整模擬幀週期，透過 `nativeRunFrame(presentFrame)` 控制是否呼叫呈現，讓核心執行幀與畫面呈現次數分開。保留聲音時，音訊執行緒依倍率抽取完整立體聲 PCM 取樣對，輸出取樣率維持不變，因此速度與音調會一起升高；未實作保留音調的時間伸縮。
- **存檔預覽與續玩**：`PlayerScreen` 等待進行中的模擬幀結束後，以 `PixelCopy` 擷取 `SurfaceView`，PNG 放在對應的 `states/<core-id>/<game-id>/`。`SaveRepository` 沿用既有時間與縮圖路徑欄位。槽位畫面核對檔案存在性及核心／格式版本；續玩選擇讀取 Slot 0，讀取失敗會回報錯誤。擷取截圖失敗不阻止存檔。
- **手把映射與斷線**：`InputManager` 在設定頁擷取實體按鈕，依裝置 descriptor 與玩家編號保存映射並快取；只套用於遊戲按鈕，方向軸與 UI 導航不變。遊戲中收到已指派手把的移除事件時，通知 `PlayerScreen` 暫停並切換選單輸入模式，不自動恢復遊戲。
- **語言資源**：預設繁體中文 `zh-TW`，另提供簡體中文 `zh-CN`（`values-zh-rCN`）與英文 `en`。`AppLanguage` 保存選擇並更新資源 Configuration，設定頁重建 Activity 套用；三種語言均提供對應的字串資源。

本節描述目前程式實作；本次新增功能尚未完成編譯與手機／S905X5M 實機驗證。

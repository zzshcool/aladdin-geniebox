# 阿拉丁 GenieBox (Aladdin GenieBox)

把喜歡的老遊戲，帶回掌心與客廳。

阿拉丁 GenieBox 是一款面向 **Amlogic S905X5M Android TV Box 與 Android 手機**的開源復古遊戲 App。從自己的遊戲資料夾建立收藏庫，透過封面、平台分類與搜尋，找到下一款想玩的遊戲。

模擬核心整合於單一 APK，不必另外安裝 RetroArch、PPSSPP 等 App。ROM 可從手機本機資料夾、USB 隨身碟或 SD 卡讀取，存檔與設定預設保存在裝置內部空間。

### 純淨，專注遊戲

- **無廣告、無內購、無須登入**：不以開屏廣告或促銷打斷遊玩。
- **離線使用**：使用者準備好遊戲檔案後，即可在本機整理與遊玩，不提供 ROM 下載或內建遊戲商店。
- **手機與電視各有合適的操作**：手機提供觸控、直橫向版面與虛擬手把；TV 提供焦點導航與實體手把操作。
- **自己的遊戲收藏庫**：平台分類、中文／拼音／注音搜尋、收藏、最近遊玩與本機封面配對。
- **保留遊戲進度**：提供即時存讀檔、自動續玩與備份功能；實際能力依核心與遊戲而異。

> **本 App 不附帶遊戲 ROM 或商業主機 BIOS，也不提供下載連結。** ROM 與必要的 BIOS 請由使用者自行準備，並確認具有使用權限。GenieBox 提供遊戲庫介面與內嵌模擬功能。

### 裝置需求與目前狀態

- Android 8.0（API 26）以上，系統必須支援 `arm64-v8a` 64 位元 App。
- TV 主要適配目標：Amlogic S905X5M Android TV Box；手機支援觸控與外接手把。
- 手機與 TV 介面以清楚導航、快速選遊戲及流暢操作為設計目標。實際流暢度與遊戲速度依裝置、散熱、核心、遊戲及渲染設定而異。
- 目前為開發中版本。已整合六個模擬核心，涵蓋下列平台；不代表全部遊戲或 S905X5M 實機相容性已完成驗證。PSP 尤其需要逐款實測，不保證所有遊戲滿速。

### 第一次使用

1. 安裝適合裝置的 APK，準備自己的 ROM；需要 BIOS 的平台另行準備對應檔案。
2. 在歡迎頁選擇遊戲資料夾，或從「設定」新增手機、USB 或 SD 卡目錄並授予存取權限。
3. 掃描完成後，從遊戲庫選擇遊戲。手機使用虛擬手把，TV 可搭配 USB、藍牙或 2.4 GHz 接收器手把。
4. 遊戲中點擊暫停圖示，或按返回鍵開啟選單；選擇「儲存並返回遊戲庫」即可換遊戲。

---

## 支援平台與核心

| 平台 ID | 平台名稱 | 模擬核心 | 支援格式 | 多人同玩擴充 |
|---|---|---|---|---|
| nes | 任天堂 NES / FC | FCEUmm | .nes | 支援 Four Score 4 人同玩 |
| gb | Game Boy / Color | Gambatte | .gb, .gbc | 單人模式 |
| gba | Game Boy Advance | mGBA | .gba | 單人模式 |
| sfc | 超任 Super Famicom | Snes9x | .sfc, .smc | 支援 Multitap 4 人同玩 |
| ps1 | PlayStation 1 | PCSX ReARMed | .cue + .bin, .chd, .m3u | 支援 Multitap 4 人同玩 |
| psp | PlayStation Portable | PPSSPP Libretro | .iso, .cso, .pbp | 原生單人模式 |

「任天堂」在此指 NES／FC；GB 與 GBC 共用核心。當前未整合 N64、NDS、3DS、GameCube、Wii、Switch 或 PS2。

表列格式與多人能力為目前整合範圍，實際可用性需依核心版本與遊戲確認。四人同玩僅適用支援的主機與遊戲；不包含 GB／GBA 通信連線或 PSP 無線對戰。

---

## 專案結構

```text
app/
  src/main/
    AndroidManifest.xml        # TV Leanback 與 Mobile 雙模宣告
    java/com/aladdin/geniebox/
      app/                     # Application 單例、MainActivity 入口、導航
      domain/model/            # 平台、遊戲、來源、核心能力、狀態模型
      data/db/                 # Room 資料庫 (Game, Source, Favorite, PlayHistory, Save, Controller)
      data/storage/            # StorageManager (原子存檔保護、備份)、SafBridge (SAF 串流、ZIP 保護)
      data/scanner/            # RomScanner (增量掃描、CUE 去重、封面關聯)、PinyinZhuYinIndex
      data/repository/         # 遊戲庫、外接來源、設定持久化、存檔資料倉儲
      emulator/                # NativeEmulator (JNI)、CoreRegistry、EmulatorSession
      input/                   # InputManager (四手把管理、Hub 斷線處理、按鍵加入)、RetroPadKey
      ui/                      # TV 10-foot 焦點高亮元件、深色主題、虛擬手把
      feature/setup/           # 首次設定、外接來源樹狀授權
      feature/library/         # 海報牆、分類標籤、中文拼音注音搜尋、TV 導航
      feature/player/          # SurfaceView 原生視訊彩現、暫停選單 (存讀檔/換片/退出)、HUD
      feature/settings/        # 手把指派/測試、來源管理、BIOS 檢查、影音濾鏡、存檔備份
    cpp/
      jni/                     # Kotlin 與 C++ 邊界 (emulator_jni.cpp)
      libretro/                # CoreBridge (動態符號綁定、Environment 回呼)
      video/                   # GLRenderer (EGL、GLES 著色器紋理彩現、比例縮放)
      audio/                   # AudioRingBuffer (立體聲環狀緩衝、取樣率同步)
      input/                   # InputManagerNative (四玩家一致性取樣)
      CMakeLists.txt
    res/                       # TV Banner、向量圖示、繁體中文字串、深色主題
scripts/                       # build_cores.sh (可重現核心建置腳本)
docs/                          # ARCHITECTURE.md, COMPATIBILITY.md, INTEGRATION_STATUS.md
third_party/                   # LICENSES.md (第三方開源授權聲明)
```

---

## ROM 資料夾整理

建議於手機可授權的資料夾、USB 隨身碟或 SD 卡建立以下結構，並在 App 選取 `GenieROMs` 目錄：

```text
GenieROMs/
  nes/      # 放置 .nes 檔案
  gb/       # 放置 .gb, .gbc 檔案
  gba/      # 放置 .gba 檔案
  sfc/      # 放置 .sfc, .smc 檔案
  ps1/      # 放置 .cue + .bin, .chd, .m3u
  psp/      # 放置 .iso, .cso
```

- 同目錄同主檔名之 `.png`、`.jpg`、`.webp` 自動關聯為遊戲封面。
- PS1 CUE 參照之 BIN 音軌由掃描器自動過濾，不造成海報牆重複。

---

## 控制器與 USB Hub 操作

1. **四手把管理**：提供 P1–P4 輸入管理，支援 USB、藍牙與 2.4 GHz 接收器手把。USB Hub 四手把與混接相容性需在目標裝置驗證，依遊戲、接收器、供電與系統而異。
2. **槽位指派**：依連線順序自動分配 P1–P4。同型號無法區分時，可在設定頁點擊「重新偵測」並按下手把任意鍵加入。
3. **操作按鍵對照**：
   - 【A / 確認】：啟動遊戲
   - 【Y】：切換加入/移除我的收藏
   - 【X】：焦點切換至搜尋欄位
   - 【L1 / R1】：快速切換分類標籤
   - 【Back / 返回鍵】：遊戲中開啟暫停選單 (阻斷遊戲輸入，防止誤觸)

---

## 存檔與資料安全

- 原生電池存檔 (SRAM / 記憶卡) 存放於內部 `files/saves/<platform>/<game-id>/`。
- 即時存檔存放於 `files/states/<core-id>/<game-id>/`，提供手動 1–5 槽與退出時自動續玩槽 (Slot 0)。
- 存檔寫入採用「暫存檔寫入 -> 備份舊檔 -> 原子替換」機制，有效防範中途斷電造成資料損壞。
- 可在設定中一鍵匯出包含存檔與收藏之 ZIP 完整備份。

---

## 開發者建置指令

設定 Android SDK、NDK 與專案使用的 JDK 後，可於專案根目錄執行以下指令：

```bash
# 1. 編譯六大平台原生 Libretro 核心 (.so 產物將輸出至 app/src/main/jniLibs/arm64-v8a)
bash scripts/build_cores.sh

# 2. 建置 Debug APK (支援 Android 電視盒側載)
./gradlew assembleDebug

# 3. 執行單元測試
./gradlew testDebugUnitTest
```

---

## 授權條款

本專案採用 **GNU General Public License v3.0 (GPL-3.0)** 授權開源。
各模擬核心版權與授權條款分屬其原始開發團隊，詳情請參閱 [third_party/LICENSES.md](third_party/LICENSES.md)。

---

## APK 發布頁介紹文案

以下適用於 GitHub Releases 或 APK 下載頁。APK 是安裝檔格式；若另行發布至應用商店，需依該平台要求準備素材與說明。

### 短介紹

無廣告的開源復古遊戲 App，為 Android 手機與 TV Box 設計。匯入自己的 ROM，用觸控或手把重溫經典。

### 完整介紹

**你的老遊戲，新的收藏方式。**

阿拉丁 GenieBox 將復古遊戲庫與模擬核心整合在同一個 App。無論是在手機上玩一關，還是接上客廳電視與手把，都能從自己的遊戲收藏開始。

- 支援 NES／FC、GB／GBC、GBA、超任 SFC／SNES、PS1 與 PSP。
- 讀取手機資料夾、USB 隨身碟與 SD 卡中的遊戲檔案。
- 手機虛擬手把與直橫向介面，TV 焦點導航與實體手把操作。
- 平台分類、搜尋、收藏與最近遊玩，快速找到想玩的遊戲。
- 本機存檔、即時存讀檔與備份，方便下次接著玩。
- 純淨無廣告、無內購、無須登入，開源且可離線使用。

以 Amlogic S905X5M TV Box 與 Android 手機為主要適配目標，專注清楚、順手的操作體驗。

**遊戲需自行準備：** 本 App 不包含 ROM、商業 BIOS 或遊戲下載服務，請使用自己有權使用的檔案。需要 Android 8.0 以上及支援 arm64-v8a 的系統；遊戲相容性與效能依裝置、核心和遊戲而異。

> 此文案描述產品功能與定位，不是相容性驗收證明。開發中版本發布時請保留測試版標示，列出版本變更、已知問題與實測裝置；未有測試結果前，不使用「所有遊戲滿速」或「所有手機與 TV 都非常流暢」等保證。

# 阿拉丁 GenieBox (Aladdin GenieBox)

阿拉丁 GenieBox 是一款專為 Android 電視盒（主目標 Amlogic S905X4）與 Android 手機打造的開源復古遊戲整合主控台。
支援在單一 APK 內部原生執行模擬核心，不依賴 RetroArch、PPSSPP 等外部 App。支援從外接 USB 隨身碟與 SD 卡讀取 ROM，存檔與設定安全儲存於內部空間。

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

## 外接儲存目錄規範

建議於 USB 隨身碟或 SD 卡建立標準結構：

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

1. **四手把管理**：支援 USB Hub 連接四支有線手把、四支藍牙手把、或 2.4 GHz 接收器混用。
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

## 建置與編譯指令 (待授權)

依專案規範，本環境預設不執行編譯或測試。若使用者需要進行編譯驗證，可於終端機執行以下指令：

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
各模擬核心版權分屬其原始開發團隊，詳情請參閱 [third_party/LICENSES.md](file:///d:/idea/aladdin-geniebox.git/third_party/LICENSES.md)。
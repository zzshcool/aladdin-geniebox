# 阿拉丁 GenieBox 相容性與設備規範 (COMPATIBILITY)

## 1. 目標硬體與設備規格

| 項目 | 主目標設備 | 次目標設備 |
|---|---|---|
| 晶片平台 | Amlogic S905X4 (Quad-core Cortex-A55) | Android 8.0+ 智慧型手機 |
| 記憶體 | 4 GB RAM | 3 GB+ RAM |
| 內部儲存 | 32 GB eMMC | 32 GB+ 儲存 |
| 圖形後端 | ARM Mali-G31 MP2 (支援 OpenGL ES 3.2 / Vulkan 1.1) | Adreno / Mali (GLES 3.0+) |
| 系統版本 | Android 11+ (TV 韌體) | Android 8.0 (API 26) 以上 |
| 主輸入裝置 | USB Hub 擴充手把 / 2.4 GHz 接收器 / 藍牙手把 / 遙控器 | 螢幕多點觸控虛擬手把 / 藍牙手把 |

## 2. 平台支援與副檔名相容表

| 平台 ID | 平台名稱 | 支援格式 | 核心名稱 | 多人擴充能力 |
|---|---|---|---|---|
| nes | 任天堂 NES / FC | .nes, .fds, .unf | FCEUmm | 支援 Four Score 4 人同玩 |
| gb | Game Boy / Color | .gb, .gbc, .dmg | Gambatte | 僅限單人 (不宣稱通信線多人) |
| gba | Game Boy Advance | .gba, .agb | mGBA | 僅限單人 (不宣稱通信線多人) |
| sfc | 超任 Super Famicom | .sfc, .smc, .fig | Snes9x | 支援 Multitap 4 人同玩 |
| ps1 | PlayStation 1 | .cue + .bin, .chd, .m3u, .pbp | PCSX ReARMed | 支援 Multitap 4 人同玩 (需遊戲支援) |
| psp | PlayStation Portable | .iso, .cso, .pbp | PPSSPP Libretro | 僅限單人 (不宣稱 Ad-hoc 多人) |

## 3. 已知限制與實施細節

1. **ZIP 壓縮檔案規範**：
   - 僅支援單一 NES、GB、GBC、GBA、SFC ROM 之 ZIP 包。
   - 解壓單檔限制最大 128 MB，防範 Zip Bomb 攻擊；偵測到目錄穿越 (..) 或多個 ROM 條目時直接拒絕載入。
   - PS1 與 PSP 光碟映像檔第一版不支援 ZIP 壓縮格式。
2. **PS1 多軌光碟與換片**：
   - 必須透過 `.cue` 啟動，掃描器自動隱藏 CUE 所參照之 `.bin` 軌道，避免海報牆重複條目。
   - 多片裝遊戲建議使用 `.m3u` 播放清單，共享同一邏輯遊戲存檔命名空間。
3. **PSP 效能預期**：
   - Amlogic S905X4 搭載 Mali-G31 GPU，預設採用原生 1x 解析度，不預設啟用高倍渲染或跳幀。不承諾全平台 PSP 遊戲皆達滿速 60 FPS。
4. **USB Hub 供電限制**：
   - 同一 USB Hub 接入 4 支震動手把與隨身碟時，建議使用具備獨立外接供電之 Active USB Hub，避免電壓驟降導致手把異常斷線。

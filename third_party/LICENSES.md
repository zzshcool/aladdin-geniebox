# 阿拉丁 GenieBox 第三方授權與核心來源宣告

本專案主體程式碼採用 **GNU General Public License v3.0 (GPL-3.0)** 授權開源。
各模擬核心及第三方庫之授權資訊如下表所示，離線封裝均恪守相應開源條款：

| 平台 | 核心名稱 | 鎖定版本 / Commit | 授權條款 | Upstream 來源 |
|---|---|---|---|---|
| 主專案 | 阿拉丁 GenieBox | 1.0.0 | GPL-3.0 | 本倉庫 |
| NES / FC | FCEUmm | b65fa7e8d6419842bfb34697ff71f654f59e1f57 | GPL-2.0 | https://github.com/libretro/libretro-fceumm |
| GB / GBC | Gambatte | 58a43b2e319119934e6c1df7c4df624021fe3a1d | GPL-2.0 | https://github.com/libretro/gambatte-libretro |
| GBA | mGBA | 0.10.3 (a0d68f7c11f7) | MPL-2.0 | https://github.com/mgba-emu/mgba |
| SFC | Snes9x | 1.62.3 (9df94fa9167e) | Snes9x License (非商業) | https://github.com/snes9xgit/snes9x |
| PS1 | PCSX ReARMed | r23 (e604f378aa44) | GPL-2.0 | https://github.com/libretro/pcsx_rearmed |
| PSP | PPSSPP Libretro | v1.17.1 (61e8cf0bbd6e) | GPL-2.0-or-later | https://github.com/hrydgard/ppsspp |

## 外部資源聲明
1. **BIOS 規範**：本專案不內建、不散布任何未授權商業 BIOS 韌體。PS1 支援使用者自行匯入合法備份之真實 BIOS (scph5501.bin, scph5500.bin, scph5502.bin)，缺失時自動降級至 HLE 模擬。
2. **ROM 政策**：專案完全離線運作，無網路下載、無外部遊戲庫來源，所有遊戲內容均來自使用者本機插入之 USB / SD 儲存設備。

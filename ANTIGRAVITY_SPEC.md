# 阿拉丁 GenieBox：Antigravity 完整開發規格

## 1. 任務與交付目標

你是本專案的實作工程師。請在 `aladdin-geniebox` Repository 建立完整 Android 專案骨架，並實作可逐步整合真實模擬核心的完整產品流程。不要只交付 UI 展示、設計文件或零散程式碼。

產品目標：使用者把 ROM 放在 USB 隨身碟或 SD 卡，插入 Android 電視盒，以 USB、藍牙或 2.4 GHz 接收器手把選擇遊戲並直接遊玩。所有模擬器在同一 APK 內執行；存檔、設定與索引儲存在內部儲存空間。另支援 Android 手機觸控操作。

一次完成各主要功能的架構、資料模型、介面與可操作畫面，並盡可能完成真實串接。不能以假成功、空方法、硬編碼展示資料或 TODO 取代執行路徑。無法完成的整合必須顯示明確的不可用狀態，並在交付文件列出缺少的產物與重現步驟。

本規格定義功能需求；實際編輯範圍仍須遵守 Repository 的 AGENTS.md。開始前先讀取現況，列出預計新增與修改的精確檔案；若 AGENTS.md 要求檔案授權，先取得對應授權，不得把此規格解讀為任意修改共用模組或設定的許可。

## 2. 產品邊界

- 專案名稱：阿拉丁 GenieBox / Aladdin GenieBox。
- 授權：主專案 GPL-3.0；第三方核心與依賴逐一檢查相容性並保存聲明。
- 主設備：Amlogic S905X4、4 GB RAM、32 GB 儲存的 Android 電視盒。
- 次設備：Android 手機。
- 第一版以側載 APK 為交付方式，不以 Google Play 上架為先決條件。
- 單一 APK，執行時不依賴 RetroArch、PPSSPP、DuckStation 或其他外部 App。
- 核心與必要合法資源隨 APK 封裝，不在背景下載可執行核心。
- 無廣告、內購、帳號、遙測、自動 ROM 下載或背景網路請求。
- 不附帶商業 ROM 或未授權 BIOS；離線完成建庫、搜尋、封面顯示與遊玩。
- ROM 留在外接儲存；必要的相容性暫存可使用內部快取，但不得默默完整匯入整個遊戲庫。
- 遊戲存檔一律預設寫入 App 內部持久儲存，不能寫進 cache。

## 3. 支援平台與核心選型

「任天堂」在本版指 NES／FC，不包含 N64、NDS、3DS、GameCube、Wii 或 Switch。

| 平台 ID | 平台 | 初選 Libretro 核心 | 第一版格式 |
|---|---|---|---|
| nes | 任天堂 NES / FC | FCEUmm | .nes |
| gb | Game Boy / Game Boy Color | Gambatte | .gb、.gbc |
| gba | Game Boy Advance | mGBA | .gba |
| sfc | Super Nintendo / Super Famicom | Snes9x | .sfc、.smc |
| ps1 | PlayStation 1 | PCSX ReARMed | .cue + .bin、.chd、.m3u |
| psp | PlayStation Portable | PPSSPP Libretro | .iso、.cso、.pbp |

以上為選型起點。整合前逐一確認固定版本對 Android ABI、格式、圖形後端、授權與建置方式的實際支援。若初選不適合，記錄具體證據與替代方案，不得偷偷移除平台。

ZIP 支援先限定單一 NES／GB／GBC／GBA／SFC ROM，具備解壓大小、檔案數量與路徑穿越保護。不將任意壓縮包都當作遊戲。PS1／PSP 第一版不支援 ZIP／7z／RAR。

PSP 納入完整啟動、輸入、影音、存檔與設定架構，但不承諾 S905X4 所有 PSP 遊戲滿速。預設原生解析度、保守設定，不預設啟用高倍渲染或跳幀。

## 4. 技術方向

- Kotlin、AndroidX、Coroutines、Flow、Room、DataStore。
- UI 採 Jetpack Compose；TV 使用適合遙控器與焦點操作的元件，手機採觸控元件，共享領域資料與用例。
- 原生端 C++／JNI／Android NDK／CMake；核心採固定版本的原始碼建置或來源可追溯的固定產物。
- ABI 第一版為 arm64-v8a。啟動時檢查設備 ABI；64 位元 CPU 不代表韌體一定支援 64 位元 App。
- 最低支援 Android 8.0（API 26），minSdk 固定為 26；compileSdk、targetSdk、AGP、Kotlin、NDK 等選擇相容的穩定組合並固定版本，交付時記錄實際版本。依賴與核心整合須維持 API 26 相容性，較新版本 API 需做版本判斷並提供相容處理，不得自行提高最低版本。
- 音訊優先 Oboe 管理 AAudio 與必要的相容性後端，避免在 UI 執行緒處理音訊。
- SurfaceView + EGL／OpenGL ES；軟體像素輸出與核心硬體渲染兩種路徑都需有真實設計。
- 不預先限定所有核心只能使用 GLES 2.0；依實際核心需求協商並檢查設備能力。
- 採簡單分層，避免為每個動作建立無意義的 pass-through 類別。
- 不強制拆成大量 Gradle modules；可先用單一 app module 加清楚的 package 邊界，原生端獨立組織。

## 5. 必備專案骨架

以下是責任邊界示意，可配合現有專案調整實際命名，但不可遺漏功能。

```text
app/
  src/main/
    AndroidManifest.xml
    java/<package>/
      app/                  # Application、入口、依賴組裝、導航
      domain/               # 平台、遊戲、來源、核心能力、遊玩狀態
      data/db/              # Room entities、DAO、migration
      data/storage/         # SAF、來源識別、原生檔案橋接、快取
      data/scanner/         # 增量掃描、格式識別、播放清單、封面
      data/repository/      # 遊戲庫、收藏、設定、存檔
      emulator/             # session、JNI、核心註冊、能力與錯誤
      input/                # 手把、按鍵映射、多玩家、觸控
      feature/setup/        # 首次設定、來源與 BIOS 匯入
      feature/library/      # 海報牆、分類、搜尋、收藏、最近遊玩
      feature/player/       # Surface、暫停選單、虛擬手把
      feature/settings/     # 儲存、控制器、影音、核心、備份、關於
      ui/                   # 主題、TV／Mobile 共用元件
    cpp/
      jni/                  # Kotlin／C++ 邊界與生命週期
      libretro/             # API bridge、environment callbacks
      video/                # EGL、像素格式、硬體 framebuffer
      audio/                # 串流、ring buffer、重採樣與同步
      input/                # 核心輸入快照
      storage/              # VFS／native path 相容橋接
      CMakeLists.txt
    assets/                 # 合法封裝資源、核心 metadata、離線聲明
    res/                    # 字串、圖示、TV banner
  src/test/
  src/androidTest/
scripts/                    # 可重現核心取得與建置流程
docs/                       # 架構、操作、相容性、驗收與整合狀態
third_party/                # 來源／版本／授權資訊
```

需要交付 Gradle wrapper、必要建置設定與版本管理方式；不得包含本機 SDK 絕對路徑、簽章私鑰或個人憑證。

## 6. ROM 來源與檔案存取

標準目錄：

```text
GenieROMs/
  nes/
  gb/
  gbc/       # 可選別名，併入 GB/GBC 分類
  gba/
  sfc/
  ps1/
  psp/
```

- 第一次透過系統資料夾選擇器取得授權並保存可持久化權限。
- 不把 ACTION_MEDIA_MOUNTED 等同於已獲讀取權限。
- TV 韌體可能缺少可用的系統選擇器；需偵測並清楚說明無法授權的原因，不得默默要求 root 或假設任意絕對路徑可讀。
- 支援 USB、SD 卡、多來源、來源改名、移除來源與重新授權。
- 初次啟動、回到前景與掛載事件均需協調來源狀態；避免同一來源重複掃描。
- ROM 對外以 URI／相對路徑描述，不假設所有文件都有可用的真實檔案路徑。
- 先驗證選定核心的 VFS 支援。需要 seek、原生路徑或旁檔時，提供實際可用的檔案橋接或有上限的暫存。
- PS1 CUE、BIN、M3U 相依檔案要維持相對路徑關係；拒絕越出授權來源的路徑。
- PSP／PS1 大檔案不得整個讀入記憶體。
- 暫存前檢查剩餘容量，顯示準備進度、允許取消；清除快取不得刪除存檔。
- 來源拔除後保留遊戲 metadata、收藏與歷史，顯示離線，停用啟動；重新插入後恢復。
- 來源掃描失敗、授權過期或暫時離線，不可當作遊戲全部被刪除。

## 7. 掃描、索引與資料模型

最低資料模型：

- Game：穩定 ID、標題、平台、來源 ID、來源內相對位置、文件 URI、內容入口類型、大小、修改資訊、封面參照、搜尋索引、可用狀態。
- RomSource：穩定來源 ID、使用者名稱、樹狀 URI、權限與連線狀態、最後成功掃描時間。
- Favorite：遊戲 ID 與加入時間。
- PlayHistory：最後啟動、遊玩時間、最後使用核心。
- SaveMetadata：遊戲 ID、核心 ID／版本、格式版本、類型、槽位、時間、縮圖位置。
- ControllerProfile：映射、軸向、死區、玩家與可辨識裝置資訊。
- CoreDescriptor：固定版本、支援 ABI、格式、影音需求、資源需求與能力。

重要規則：

- 不以當下掛載絕對路徑作為唯一遊戲 ID；同一來源重插需保持收藏與存檔關聯。
- 同名不同來源／不同版本不能自動共用存檔。移動與重新命名後不能保證自動辨識時，提供明確重新關聯流程。
- 不在每次掃描時完整雜湊大型光碟映像；必要的指紋計算按需執行。
- 增量掃描、批次寫入、可取消、有進度；不阻塞主執行緒。
- PS1 CUE 的 BIN 音軌不顯示成多個遊戲；M3U 成員可合併為單一入口，保留換片資訊。
- 封面匹配同目錄同檔名的 png／jpg／jpeg／webp，無封面提供平台佔位圖。
- 解碼縮圖而非原尺寸海報，限制圖片快取，避免 4 GB 設備 OOM。
- 缺檔、空檔、壞檔、未知格式、缺旁檔需記錄可理解的結果。
- 資料庫 schema 有版本；更新不得以 destructive migration 默默清除收藏與歷史。

## 8. 搜尋與遊戲庫

- 分類：全部、NES、GB/GBC、GBA、SFC、PS1、PSP、收藏、最近遊玩。
- 中文名稱、英數名稱、拼音全拼／首字母與注音簡拼即時過濾。
- 繁簡字形正規化；拼音與注音各有索引，多音字採可控策略並記錄限制。
- TV 提供遙控器可操作的搜尋輸入介面，仍支援系統輸入法與實體鍵盤。
- 大量遊戲採分頁或適當的 lazy loading；搜尋取消過期查詢，不得卡住焦點導航。
- 同名遊戲顯示來源／版本資訊以供辨識。
- 收藏即時更新，離線遊戲仍可查看與取消收藏。
- 第一次無資料、掃描中、搜尋無結果、來源離線與錯誤各有獨立狀態。

## 9. TV 與手機 UI/UX

TV 為主要體驗：

- 10-foot UI、大字、清楚對比、安全邊界，不依賴滑鼠。
- 每個可操作元件有可辨識焦點，卡片放大與輕量高亮，不讓持續動畫拖慢列表。
- D-Pad 長按可連續移動；更新列表後維持合理焦點。
- A／確認啟動、Y 收藏、X 搜尋、L1／R1 切換分類，提供設定與實際按鍵提示。
- 返回遊戲庫時恢復分類、搜尋條件、捲動位置與選中遊戲。
- 首次設定、授權、錯誤、設定、暫停選單均能以遙控器操作。
- Android TV launcher 入口、banner、遙控器必要能力宣告完整。

手機：

- 觸控海報網格，直橫向與系統視窗尺寸變化有正確佈局。
- 遊戲橫向為預設；明確處理旋轉與 Surface 重建，不能旋轉就重啟核心。
- 虛擬手把多點觸控，支援同時方向與多按鍵、按鍵大小／透明度／位置調整、觸覺回饋開關。
- 適配六平台按鍵，含 PSP 類比滑桿與肩鍵；PS1 類比操作依核心能力呈現。
- 實體手把接入後可自動隱藏虛擬手把，亦可手動覆寫。

視覺方向：深色背景、清楚平台分類、封面為主、適量金色作品牌重點，動畫短而穩定。不得為了視覺效果在首頁加入網路背景影片或過度模糊。

## 10. 手把與輸入

- USB HID、藍牙、2.4 GHz USB 接收器，使用 Android InputDevice／KeyEvent／MotionEvent。
- 支援 D-Pad、類比軸、hat、扳機、肩鍵、Start／Select 與按鍵映射。
- 至少同時管理四支手把，明確指定 P1／P2／P3／P4；支援 USB Hub 連接四支有線手把，或四支相容的無線手把，以及有線／無線混用。
- 四支實體手把可連接，不代表所有平台與 ROM 都支援四人同玩。NES 四人需驗證核心與 Four Score 等擴充模式；SFC、PS1 需串接核心實際支援的 Multitap／控制器埠設定，且遊戲本身支援才啟用。GB／GBC／GBA 的通信線與 PSP 的無線連線對戰屬另一項模擬功能，不因四支手把接入而自動成立，第一版不得宣稱支援。
- 不依賴不穩定的 deviceId 作永久識別；處理重插、重連與相同型號多支手把。
- 遊戲輸入、UI 輸入與快捷鍵互斥，暫停選單操作不得同時傳入遊戲。
- 手把斷線暫停並提示，可重新指派或恢復；失去焦點時釋放所有按鍵，避免卡鍵。
- 按鍵映射與死區可保存，支援還原預設。
- 遙控器 Back 與可設定手把快捷組合開啟暫停選單；不能使用會頻繁誤觸的組合。
- 震動依設備與核心能力提供，不支持時正常降級。

### 10.1 USB Hub 與四手把硬體情境

- 驗收包括同一 USB Hub 上四支手把與 ROM 隨身碟同時使用；同型號四支、多品牌混用與多個無線接收器均需列入相容性矩陣。
- 不假設單一接收器天然支援四支手把；必須確認接收器與 Android 驅動能提供四組可區分的輸入裝置／通道。若只回報單一合併輸入，顯示限制，不得把同一訊號複製成四位玩家。
- 相同型號／descriptor 不保證可永久區分，無可靠唯一資訊時採「按鍵加入玩家」或人工重新指派，不得僅用 descriptor 當唯一鍵。
- 玩家管理畫面顯示四個槽位、連線狀態、輸入測試、重新排序與震動辨識（設備支援時）。P1 預設控制暫停選單，其他玩家的遊戲輸入在選單期間停止送出。
- 拔掉單支不重排其他玩家；拔掉整個 Hub 時暫停並清除全部按鍵狀態，重接後提供確認／重新加入，不可任意交換玩家。
- 四路輸入分別保存狀態與映射，在同一模擬幀一致取樣，不能互相覆寫或因四支接入就建立四個模擬迴圈。
- 記錄實機 Hub 供電與相容性；四支手把加外接儲存需評估獨立供電 Hub。App 不宣稱能精準偵測 USB 供電不足，異常斷線時提供檢查供電／接收器／接線提示。

### 10.2 無線與 Wi-Fi 的界線

- 藍牙手把、專用 2.4 GHz USB 接收器與真正的 Wi-Fi／LAN 手把是不同傳輸方式；不得將「2.4 GHz」直接標成 Wi-Fi。
- 若四支無線手把最終由 Android 回報為標準 InputDevice，沿用四玩家輸入路徑，實際連線數與延遲需依盒子韌體及手把驗證，不保證所有藍牙組合都可用。
- 真正透過 Wi-Fi 傳送按鍵的手把需要已知協定、相容驅動或配套控制端，不能承諾任意 Wi-Fi 手把直接連上就能使用。交付相容性表，明確標記傳輸方式與型號。
- 骨架預留本機 InputDevice 與 LAN 輸入 adapter 共用的玩家事件介面。Wi-Fi 手把具體型號／協定未提供前，不虛構可用實作；交付時明列協定整合待確認，不讓此缺項阻塞 USB／藍牙／接收器四手把路徑。
- 未來若實作 LAN adapter，僅在使用者開啟配對時啟用，包含配對授權、四個獨立 session、序號與過期封包處理、心跳／斷線判定及超時釋放按鍵；不得持續接受未配對裝置輸入。
- 如加入 LAN 功能，必要網路權限與本機連線行為需明列；不得擴張為背景公網連線、帳號或遠端遊戲串流。

## 11. 原生模擬器引擎

建立真實 Libretro frontend，而非只做 dlopen：

- 載入與驗證核心 API，設定 environment、video、audio、input callbacks。
- 正確呼叫初始化、系統資訊、載入遊戲、run、serialize、unserialize、unload 與 deinit。
- 依核心回報能力處理 core options、controller info、input descriptors、disk control、VFS、目錄、像素格式、logging 與硬體渲染。
- 未支援的 environment command 明確回傳不支援，不得一律回 true。
- 系統資源、save 與 assets 目錄都指向規劃好的可存取位置。
- 軟體渲染支援核心實際使用的 RGB565、XRGB8888 等格式、pitch、畫面尺寸與方向。
- 硬體渲染支援 framebuffer、context 建立／重建／銷毀與核心回呼；不假設 UI 持有的 GL context 可隨意跨執行緒使用。
- 音訊採低延遲串流、重採樣與有界 ring buffer；音訊 callback 不做磁碟 I/O、阻塞 JNI 呼叫或不受控配置。
- 依核心回報 FPS／取樣率同步，支援 50 Hz、59.94 Hz 等；不得固定每秒呼叫 retro_run 60 次。
- 將模擬執行、UI、音訊 callback 的狀態共享與所有權明確化。
- 不允許在核心執行中併發 unload／save／load state；所有 session 操作序列化。
- Session 狀態至少包含 Idle、Preparing、Loading、Running、Paused、Stopping、Failed。
- Surface 消失、App 背景、音訊焦點變更、HDMI／耳機路由變更與程序重建均有清楚流程。
- 核心原生 crash 不能以 Kotlin try/catch 假裝可攔截。評估同 APK 的獨立 :emulation process 隔離；若採用，UI 與模擬程序透過明確 IPC 溝通、避免每幀傳輸影像，並處理程序死亡與恢復。
- 遊戲庫資料庫由明確單一責任方管理，避免多程序直接競寫。

## 12. PSP 與 PS1 專項

PSP：

- 固定 PPSSPP Libretro 與相符 assets 版本，檢查必要字型／語言／flash0 等資源的封裝位置與授權。
- 遊戲內存檔與 memory stick 目錄位於內部持久儲存。
- 可調渲染解析度、音量與經驗證的核心選項；設定依平台／遊戲保存。
- 不預設開啟高風險加速 hack；不提供尚未實作的連線對戰按鈕。

PS1：

- BIOS 匯入、檔案資訊與相容性檢查，清楚顯示目前使用 BIOS／HLE 的實際狀態。
- BIOS 不足時不能只顯示黑畫面；按核心需求阻止啟動或提供明確相容性說明。
- CUE／BIN、多片 M3U、換片與記憶卡持久化皆需串接。
- 預設每款遊戲獨立記憶卡命名空間；多片同一遊戲共享同一邏輯遊戲存檔。

## 13. 內部存檔與資料可靠性

內部目錄責任示意：

```text
files/
  saves/<platform>/<game-id>/      # 原生存檔／記憶卡
  states/<core-id>/<game-id>/      # 即時存檔與 metadata
  system/                         # 使用者匯入 BIOS、必要資源
  core-assets/                    # 安裝後部署的合法核心資源
  screenshots/
cache/
  rom-staging/                    # 有界且可清除的相容性暫存
  thumbnails/
```

- 原生存檔、手動即時存檔、自動續玩分開管理。
- 手動至少 5 個槽位；自動槽位不覆寫手動槽位。
- 只對核心實際支援 serialize 的平台提供即時存檔與續玩。
- metadata 包含核心版本、狀態格式版本與遊戲識別；不相容時提示，不盲目載入。
- 成功寫入以暫存檔＋同檔案系統原子替換等策略保護舊資料，並保留必要備份。
- 原生 SRAM／記憶卡按核心機制安全保存；正常暫停／退出時 flush，不能只依賴 Activity.onDestroy。
- App 被殺、斷電與 native crash 不保證能保存當下進度，但不得先破壞已成功保存的檔案。
- 提供匯出／匯入備份，包含存檔、收藏與設定；匯入先驗證，再提供衝突選擇。
- ROM 不包含在預設備份中；移除遊戲來源不連帶刪除存檔。
- 顯示內部容量與快取使用量；低容量時清楚告知哪個寫入失敗，不回報假成功。
- 說明解除安裝會刪除 App 私有資料，提供手動備份入口。

## 14. 遊玩流程與設定

完整流程：首次設定 → 授權來源 → 背景建庫 → 選遊戲 → 檢查來源／核心／資源 → 必要準備 → 遊戲 → 暫停 → 返回原焦點。

暫停選單：繼續、存檔、讀檔、重新開始、換片（有能力才顯示）、控制器設定、遊戲影音設定、離開。

- 返回鍵先暫停，避免直接離開。
- 離開時先嘗試安全保存，失敗需提供重試與明確離開選項。
- 自動續玩成功存在時提供「繼續／重新開始」，不能偷偷載入舊狀態。
- 遊戲中關閉螢幕休眠，離開後釋放。
- 畫面比例提供原始比例、整數縮放與滿版，預設不拉伸。
- 濾鏡先提供最近鄰／雙線性；進階 shader、rewind、run-ahead、作弊與網路對戰不在第一版必要範圍。
- 全域 → 平台 → 遊戲設定有明確覆寫順序，可還原預設。

## 15. 效能與錯誤處理

- 首頁以 1080p 等級 UI 與合理尺寸資源設計，避免 4K 桌面導致不必要的高記憶體／GPU 負擔。
- 核心效能依遊戲原始速度衡量，不能以 UI 60 FPS 代表模擬滿速。
- 提供可關閉的 FPS、模擬速度、音訊 underrun 等診斷資訊；預設不佔畫面。
- 針對缺核心、ABI 不符、缺資源、無權限、來源離線、壞 ROM、低容量、圖形能力不符提供不同錯誤。
- 診斷紀錄保留本機且有大小上限，使用者主動匯出，避免記錄 ROM 內容或不必要的個人路徑資訊。
- 所有載入畫面都有實際狀態，耗時準備可取消；不能無限轉圈。

## 16. 建置、核心供應鏈與授權

- 每個核心記錄 upstream URL、commit／tag、授權、建置旗標、ABI、依賴與產物 checksum。
- scripts 提供可重現的核心取得／建置／封裝流程，不以未追蹤的個人 .so 為唯一來源。
- 清楚區分建置時可能需要下載依賴，與 App 執行時完全離線。
- PPSSPP assets 與原生相依庫一起納入版本與授權管理。
- 檢查共享 libc++、SONAME、原生符號與多核心相依衝突；不隨意合併不同來源同名 .so。
- 對選定 SDK／NDK 的原生打包、頁面大小與裝置相容性要求作實際檢查並記錄結果。
- APK 內提供離線開源聲明，Repository 保留對應來源與建置說明。
- Debug APK 可供側載；Release 簽章由使用者管理，不能提交私鑰。

## 17. 驗收矩陣

交付驗收案例與預期結果。遵守 Repository「未明確要求不執行測試或編譯」的規則；可準備測試與指令，但實際執行須有使用者明確授權。沒有設備或合法測試 ROM 時，標示未驗證，不得聲稱通過。

| 項目 | 必要結果 |
|---|---|
| 六平台 | 每平台都有真實核心整合路徑、格式識別、啟動與退出；未完成部分明確列出 |
| 外接來源 | USB／SD、首次授權、重插、離線、重新授權與多來源可處理 |
| 大型遊戲庫 | 以 5,000 筆 metadata 情境檢查列表、搜尋、快取與掃描設計 |
| 手把 | 遙控器、USB Hub 四手把、四支無線手把、有線／無線混用、P1–P4 指派、重連與獨立映射 |
| Hub 共用 | 四支手把與 ROM 隨身碟同時使用、整個 Hub 拔插、供電與來源斷線處理 |
| 四人遊戲 | 以支援四人的 NES／SFC／PS1 合法測試遊戲驗證擴充模式與四路輸入；單人平台不宣稱四人 |
| Wi-Fi | 依已知型號／協定驗證；未指定協定時只交付 adapter 介面與明確整合狀態，不冒充支援 |
| 存檔 | 正常退出／重啟後原生存檔可讀；即時存檔依核心能力運作 |
| PS1 | CUE 多軌不重複顯示，M3U 換片，多片共享遊戲存檔 |
| PSP | 真實畫面、音效、輸入、資源與內部 memory stick 存檔 |
| 生命週期 | 背景／前景、Surface 重建、手把斷線、音訊路由與程序死亡 |
| 異常 | 缺 BIOS、壞 ROM、拔除裝置、空間不足均有可恢復提示 |
| TV 操作 | 全流程不需要滑鼠／觸控，退出恢復原焦點 |
| 手機操作 | 多點觸控、類比滑桿、實體手把切換與畫面重建 |
| 離線 | 關閉網路後仍可完整使用已封裝功能 |
| 長時間 | 實機持續遊玩時記錄溫度／降頻現象、記憶體趨勢與音訊穩定性 |

S905X4 實機結果按遊戲分別記錄，尤其 PSP 不以單款遊戲推論全平台滿速。無實機時可交付骨架與整合產物，但效能與外接設備相容性必須保持「待驗證」。

## 18. 最終交付清單

1. 完整 Android 專案、可理解的模組責任與所有主要畫面。
2. 六平台核心註冊、真實 JNI frontend、影音／輸入／存檔／檔案橋接實作。
3. Room schema、來源掃描、收藏、中文搜尋與設定持久化。
4. TV／Mobile 操作與完整 loading／empty／offline／error 狀態。
5. 核心來源鎖定、建置／封裝腳本、第三方授權清單。
6. README：建置環境、安裝、ROM 目錄、授權、BIOS、手把與備份方式。
7. docs/ARCHITECTURE.md：執行緒、程序、資料流、核心能力與儲存決策。
8. docs/COMPATIBILITY.md：設備、平台、格式、核心版本與已知限制。
9. docs/INTEGRATION_STATUS.md：逐項標示已實作／已驗證／待實機／受阻，不用單一百分比掩蓋缺項。
10. 驗收案例與待授權執行的建置／測試指令；有實際執行才提供結果。

完成時列出實際修改檔案，確認未超出授權範圍且保留原有使用者修改。不得只回覆「專案骨架已完成」而未說明六個核心是否能真正啟動。

## 19. 技術核對來源

- Android ABI：https://developer.android.com/ndk/guides/abis
- Android SAF：https://developer.android.com/training/data-storage/shared/documents-files
- Android 手把輸入：https://developer.android.com/games/sdk/game-controller/controller-input
- Android 多手把：https://developer.android.com/games/sdk/game-controller/multiple-controllers
- Libretro frontend／core API：https://docs.libretro.com/development/cores/developing-cores/
- Libretro 硬體渲染：https://docs.libretro.com/development/cores/opengl-cores/
- Libretro 動態同步：https://docs.libretro.com/guides/ratecontrol.pdf
- Libretro 核心授權：https://docs.libretro.com/development/licenses/
- PCSX ReARMed：https://docs.libretro.com/library/pcsx_rearmed/
- PPSSPP Libretro：https://docs.libretro.com/library/ppsspp/
- PPSSPP 建置：https://github.com/hrydgard/ppsspp/blob/master/docs/building.md

以上來源是實作時的核對入口；以實際選定的固定版本程式碼與文件為準，不假設 upstream 最新版本永遠與鎖定版本一致。

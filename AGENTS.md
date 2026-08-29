# AGENTS.md

本檔案提供給之後參與此專案的人類與 AI 協作者使用。目標是用最少的探索成本快速理解專案現況，並在每次修改專案或回答與專案相關的問題後，持續更新這份文件。

## 維護規則

1. 只要有修改專案程式、設定、文件、資源或測試，應同步檢查本檔是否需要更新。
2. 只要有回答與此專案實作、架構、限制、待修問題相關的問題，也應把新確認的資訊補進本檔。
3. 不確定的資訊不要寫成既定事實，請明確標示為「推測」、「待確認」或「觀察到的現象」。
4. 更新時優先維護以下內容：
   - 專案用途與核心功能
   - 執行與建置方式
   - 主要架構與責任分工
   - 已知風險、限制與注意事項
   - 後續協作約定

## 專案概覽

- 專案名稱：`Fake Location`
- 類型：單模組 Android App
- 目標：提供可持續運作的模擬定位功能，並支援較真實的漂移、海拔、自動停止與自動啟動等設定
- 主要技術：
  - Kotlin
  - Jetpack Compose
  - Android ViewModel
  - DataStore Preferences
  - MapLibre Native Android SDK
  - Google Play Services Location

### 地圖套件

- 目前地圖使用 `org.maplibre.gl:android-sdk:12.3.1`
- 相依設定位置：
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
- 主要使用位置：
  - `app/src/main/java/com/rsps1008/fxxklocation/ui/screen/MainScreen.kt`
- 目前使用方式：
  - 在 Compose `AndroidView` 中包裝 MapLibre 的 `MapView`
  - 目前改為內嵌的 MapLibre style JSON，底圖來源是 OpenStreetMap raster tiles：`https://tile.openstreetmap.org/{z}/{x}/{y}.png`
  - 以 `Marker` 顯示目標位置
  - 以地圖點擊事件更新選定座標

## 建置與執行

### 基本環境

- `compileSdk = 37`
- `targetSdk = 36`
- `minSdk = 28`
- Java / Kotlin JVM target：`11`
- Android Gradle Plugin：`9.3.2`
- Kotlin：`2.4.10`
- AGP 9 已內建 Kotlin；不要再套用 `org.jetbrains.kotlin.android`，Compose compiler plugin 仍由版本目錄管理。
- AGP 9 下 Kotlin JVM 目標使用 `kotlin { compilerOptions { ... } }`，不再使用已移除的 `kotlinOptions` DSL。
- Compose 使用的 `LocalLifecycleOwner` 來自 `androidx.lifecycle:lifecycle-runtime-compose`；Material 返回圖示使用 AutoMirrored 版本。
- MapLibre 13.6.0 的舊 annotations Marker API 目前仍被地圖標記流程使用，編譯時會產生 deprecated warnings，待改用新版 annotation/plugin API 時再一併調整。

### 常用指令

- 建置 Debug：`.\gradlew.bat assembleDebug`
- 執行單元測試：`.\gradlew.bat testDebugUnitTest`
- 執行儀器測試：`.\gradlew.bat connectedDebugAndroidTest`

### 使用前置條件

依目前程式與 README 可知，App 正常使用前需要：

1. 開啟開發人員選項。
2. 將此 App 設為 mock location app。
3. 授予定位權限。
4. Android 13+ 需授予通知權限。
5. 建議關閉或略過電池最佳化，避免前景服務被系統回收。
6. GPS 需開啟。

## 目前功能整理

- 在主畫面以地圖選擇目標位置。
- 可在主畫面搜尋地標或地址，搜尋結果只會平移地圖視窗，不會改動紅色選定目標點；目前搜尋屬於近似搜尋，標頭會顯示「搜尋地點（不支援精準搜尋）」，框內提示則保留「輸入地標或地址」。
- 可點擊「Locate Me」取得目前真實位置並同步更新海拔。
- 啟動前景服務持續送出 mock location。
- 可設定隨機漫步漂移，每 2 秒沿著上一個位置附近小步移動，並維持在設定的漂移半徑內，同時更新微幅海拔變化。
- 隨機漂移的更新節奏固定是每 2 秒一次；每次步長上限為 `max(radius * 0.18, 2m)`，實際步長再落在 `35% ~ 100%` 的上限區間內，因此若漂移半徑是預設 `10m`，每次大約走 `0.7m ~ 2m`，海拔則每次額外隨機漂移約 `±0.2m`。
- 可選擇手動海拔或抓取真實海拔。
- 可選擇是否使用 Google Play Services 取得真實位置。
- 可設定自動停止時間。
- 可設定 App 啟動時自動以最後位置恢復 mock。
- 以常駐通知顯示執行中狀態與自動停止倒數。
- 主畫面按下開始時，若缺位置／通知權限會依序走 OS runtime permission；接著會先走忽略電池最佳化的 OS request，最後才檢查 GPS / mock app 是否仍缺，並視結果決定是否跳出「需要權限」對話框。
- 主畫面那個阻擋對話框目前文案已改成「需要完成系統設定」，內容會明確列出位置、通知、電池最佳化、GPS 與模擬應用程式等必要條件。
- App 會依系統語言自動套用語系；若系統語言為任何中文，會統一映射成繁體中文。設定頁不再提供中文／英文切換。
- App 目前已補上日文與韓文字串資源；若系統語言為日文或韓文，會直接套用對應語系，其餘語言維持預設英文。
- 系統語言同步要直接讀 `LocaleManagerCompat.getSystemLocales(context)`，不要讀 `context.resources.configuration.locales` 或只看 `LocaleList.getDefault()`；否則 App 先前套過的語系會把後續切換黏住，重開也不會跟著變。現在同步點放在 `MainActivity.attachBaseContext()`、`onCreate()` 和 `onResume()`。
- `MainViewModel` 的 toast 訊息已改為字串資源，會跟著目前套用的語系同步切換。
- 當正在虛擬定位時，主畫面右下角的 `Locate Me` 按鈕會以灰階樣式顯示；按下後仍會以目前系統回報的位置更新地圖。一旦停止虛擬定位，ViewModel 會立刻嘗試抓取真實定位與高度，並回寫到 camera / altitude 狀態。停止後再按 `Locate Me` 時，會主動向系統請求 current location，而不是只讀快取。
- 當主畫面尚未啟動虛擬定位時，下方的 `X` 停止按鈕會維持灰階外觀，且點擊不會有反應；只有 mock 正在執行時才會真的停止。
- 主畫面底部的勾勾／叉叉操作列保留額外底距與 navigation bar 安全區，按鈕本體改為長方形大按鈕，避免 `LargeFloatingActionButton` 造成圓形外觀，也避免在某些解析度下直接貼齊螢幕底部。
- 主畫面現在另外會顯示一個藍色的 current location 標記，用來表示系統目前回報的位置；紅色標記則維持代表使用者選定的虛擬目標點。
- 主畫面地圖不會再因為 `selectedLocation` 或 current location 更新而自動把鏡頭拉回去；鏡頭只會在使用者按 `Locate Me` 時主動 recenter。
- 進入主畫面時，地圖會固定先以 `selectedLocation` 作為初始鏡頭位置；若沒有手動釘選過位置，則會落在 `selectedLocation` 的預設值，也就是台北 101。`current location` 仍會保留為藍色標記，但不再主導初始鏡頭。
- 初始鏡頭現在要等 `MainViewModel` 把儲存的最後釘選位置載入完成後才會套用，避免畫面先以 101 定位後又鎖住不回到真正的釘選點。
- 當 mock 正在執行時，`MainViewModel` 會避免再去刷新真實 current location，以免把 current location 狀態覆寫回真實位置；此時 `Locate Me` 會優先使用已知的 mock/current 快照，而不是重新抓真實定位。
- 設定頁提供快速跳轉到系統權限／開發者選項／電池最佳化頁面。
- 設定頁最下方加入了隱私政策入口，會開啟 `https://rsps1008.github.io/FxxkLocation/privacy-policy/`。
- 目前可 runtime request 的權限包含位置與通知；這兩項會先走 Android runtime permission，只有在被 OS 拒絕後才跳 App 設定頁。電池最佳化與 mock app 指定屬於系統設定流程，不是 runtime permission，但也會優先走對應的 OS 頁面，再視情況回到 App 設定頁。
- 主畫面開始模擬時，電池最佳化會先嘗試 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 的 OS request；返回 App 後會在 `onResume` 再檢查一次，若裝置不支援或使用者拒絕，才改導到設定頁，且只有在電池條件處理完後才會檢查 GPS / mock app。
- 通知權限在 Android 13+ 會優先走 OS 層級的 runtime permission request；只有在使用者拒絕或無法直接請求時，才回到 App 設定頁當 fallback。
- 主畫面按下開始後，若缺位置權限會先請求位置權限，接著再自動請求通知權限，不需要再按一次開始；若仍缺系統設定類條件，才會跳出「需要權限」對話框並導到設定頁，而不是直接打開 App OS 設定頁。
- 權限頁面有時在剛回到 App 時會短暫維持舊狀態，因此 `checkStatus()` 會搭配一個短延遲的第二次刷新，避免剛調好權限卻仍顯示「修復」。
- `isIgnoringBatteryOptimizations` 在部分裝置上回寫更慢，現在有額外的延遲刷新專門補這一項，避免使用者回來後仍看到舊的「修復」狀態。

## 專案結構

### 入口與導航

- `app/src/main/java/com/rsps1008/fxxklocation/MainActivity.kt`
  - Compose 入口。
  - 目前只有兩個畫面：`main` 與 `settings`。
  - 目前 `MainActivity` 使用 `AppCompatActivity`，搭配 `Theme.FxxkLocation` 的 `Theme.AppCompat.DayNight.NoActionBar` parent，讓 App 內語言切換可以正常運作。

### UI 層

- `app/src/main/java/com/rsps1008/fxxklocation/ui/screen/MainScreen.kt`
  - 顯示 MapLibre 地圖。
  - 點地圖可更新目標座標。
  - 地圖與 `Locate Me` 按鈕之間有一個直接貼在背景上的地標搜尋列；標頭與框內提示分開顯示，搜尋結果會透過 OpenStreetMap Nominatim 取得座標，並只移動地圖鏡頭，不會變更 `selectedLocation`。
  - 可啟動／停止 mock。
  - 會在 `ON_RESUME` 時重新檢查系統狀態。
  - 右下角定位按鈕在虛擬定位時會以灰階樣式顯示；按下後只負責將地圖鏡頭拉回「裝置目前位置」並放大到約 `zoom 16.5`，行為接近 Google Maps 的 recenter 按鈕；它不會修改使用者手動選定的 `selectedLocation`。非 mock 狀態下會主動抓 current location，讓系統顯示定位中的提示。
  - 下方停止按鈕在 mock 尚未啟動時會是灰階外觀且不會有反應；只有 mock 正在執行時才可停止。
  - 地圖上另外會顯示一個藍色 current location 標記，和紅色目標點分開顯示。
  - 地圖鏡頭不會因狀態更新自動回正，只有定位按鈕會主動 recenter。
  - 進畫面時會固定以 `selectedLocation` 當作初始鏡頭位置；`current location` 只保留為藍色標記，不再主導第一次開圖的位置。

- `app/src/main/java/com/rsps1008/fxxklocation/ui/screen/SettingsScreen.kt`
  - 管理漂移、海拔、Google 服務、自動停止、自動啟動等設定。
  - 顯示系統狀態並提供修復入口。
  - 漂移半徑、手動海拔與自動停止分鐘數使用各自獨立的本地文字草稿；只有失焦、IME Done、短暫 debounce 或畫面 dispose 時才提交可解析值，避免每次按鍵都寫入 DataStore。
  - 輸入草稿使用 `rememberSaveable` 保存；DataStore 外部回讀在編輯或待確認寫入期間不會覆蓋文字，確認完成也保留使用者原本的暫時格式。

- `app/src/test/java/com/rsps1008/fxxklocation/ui/screen/DeferredSettingInputTest.kt`
  - 覆蓋暫時字串、外部值同步隔離、configuration restore 的 dirty 狀態，以及失焦提交去重。

### 對外網站文件

- `docs/index.html`
  - GitHub Pages 風格的專案首頁。
- 目前內容用來介紹 Fake Location 的核心功能、使用前置條件與隱私政策入口。
- `docs/privacy-policy/index.html`
  - 對外公開的隱私權政策頁面。
  - 目前內容以「資料主要保存在裝置上、沒有自有後端」為主軸，並說明定位、通知與 Google Play Services 相關使用情境。

### ViewModel 層

- `app/src/main/java/com/rsps1008/fxxklocation/viewmodel/MainViewModel.kt`
  - 管理主畫面狀態。
  - 讀取最後定位。
  - 啟停 `MockLocationService`。
  - 處理自動啟動、定位自己、刷新真實海拔。
  - `hasLoadedInitialSelectedLocation` 以 StateFlow 提供給 `MainScreen` 判斷初始鏡頭是否可以套用；這個狀態必須在讀完儲存的最後釘選點與高度之後才會設為 `true`。
  - 自動啟動會等待初始位置讀取與首次系統狀態檢查的明確 readiness，不再依賴固定延遲；若 DataStore 初始讀取失敗會安全結束等待。啟動前會核對 DataStore 的 `isMocking`、程序內 mock 狀態及原有必要條件，並以程序內 atomic claim 避免多個 ViewModel 重複送出啟動請求。
  - `centerMapOnCurrentLocation()` 在 mock 狀態下只使用目前已知的 mock/current 快照；非 mock 狀態下會主動請求 current location。整個流程都不會覆寫 `selectedLocation`，且只有定位失敗時才提示訊息。
  - 使用字串資源發送 toast 訊息，避免語言切換後還有硬編碼英文提示。
  - 當 mock 狀態從 `true` 切到 `false` 時，會自動啟動一輪真實定位查詢，優先 current location，失敗才 fallback 到 last known。
  - 若程序內仍有 mock 即時位置，會忽略已排隊但較晚回來的真實定位回呼，避免覆寫 mock UI 狀態、camera 或 DataStore 快照。

- `app/src/main/java/com/rsps1008/fxxklocation/viewmodel/SettingsViewModel.kt`
  - 將 `SettingsStore` 中的設定轉成 UI 可觀察狀態。
  - 更新設定值。
  - 在需要時刷新真實海拔。
  - 海拔刷新完成後只有在程序內沒有新的停止請求時才會恢復 mock，避免停止期間的延遲定位回呼把服務重新啟動。

### 資料與設定層

- `app/src/main/java/com/rsps1008/fxxklocation/data/store/SettingsStore.kt`
  - 使用 DataStore Preferences 保存設定與最後位置。
  - 提供 `setCurrentLocationAndStopMocking()`，讓停止時的最後位置與 `isMocking=false` 以單一交易保存。
  - 目前保存內容包含：
    - 是否啟用漂移
    - 漂移半徑
    - 是否使用 Google Play Services
    - 是否使用真實海拔
    - 最後經緯度
    - 最後海拔
    - 是否正在 mock
    - 自動停止與分鐘數
    - 啟動自動恢復

- `app/src/main/java/com/rsps1008/fxxklocation/data/model/LocationData.kt`
  - 單純的定位資料模型，包含經度、緯度、海拔。

- `app/src/main/java/com/rsps1008/fxxklocation/data/state/MockLocationRuntimeState.kt`
  - 提供 mock 服務與主畫面 ViewModel 之間的程序內即時位置 StateFlow，以及不需等待 DataStore 的 stop-request 標記；程序重建時仍由 DataStore 快照作為 fallback。

- `app/src/main/java/com/rsps1008/fxxklocation/data/store/CurrentLocationSnapshotPolicy.kt`
  - 控制目前 mock 位置的 DataStore 快照頻率，使用單調時間並支援每次服務啟動重新計算週期。

### 定位與系統整合

- `app/src/main/java/com/rsps1008/fxxklocation/service/MockLocationService.kt`
  - 前景服務核心。
  - 接收 start / stop / pause / resume action。
  - 啟動後持續推送 mock location。
  - mock provider 仍每 2 秒更新；目前位置會先透過 `MockLocationRuntimeState` 在程序內即時同步給主畫面，再以 DataStore 初次、每 30 秒一次及停止時最後一次的節奏保存快照。
  - 停止時會以單一 DataStore transaction 同時保存最後位置並清除 `isMocking`，避免 ViewModel 提前寫入狀態造成競態。
  - 服務內的設定觀察採用 supervisor scope，各觀察支線會記錄例外而不直接留下未清理的 mock provider；停止寫入以 `stopSelfResult(startId)` 避免舊停止請求終止較新的啟動。
  - 服務控制與 UI/通知狀態仍在 `Dispatchers.Main`；漂移計算移到 `Dispatchers.Default`，所有 mock provider 的 start / stop / pause / resume / location update 則透過單一序列背景 dispatcher 與 `Mutex` 排隊，維持 provider 操作順序。
  - `onDestroy()` 會先取消服務 scope，再以一次性的背景清理工作移除 provider；清理工作完成後會自行取消，避免在主執行緒執行 LocationManager IPC。
  - 內含自動停止倒數與通知更新邏輯。

- `app/src/main/java/com/rsps1008/fxxklocation/location/MockLocationManager.kt`
  - 建立與移除 test providers。
  - 對多個 provider 寫入 mock location。
  - 可產生隨機漫步式漂移位置，並限制在指定半徑內。
  - `startMock()`、`stopMock()` 與 `updateMockLocation()` 以 `@Synchronized` 保護，讓背景 provider 操作與服務銷毀時的清理不會同時進入 LocationManager。
  - `startMock()` 只有在 provider 成功建立並啟用後才加入 `activeProviders`；`updateMockLocation()` 只更新這些成功 provider，`stopMock()` 會清空清單，下一次啟動則重新逐一偵測。
  - provider 初始化失敗會記錄一次警告並略過該 provider，不會在每次兩秒更新週期重複呼叫失敗的 provider。

- `app/src/main/java/com/rsps1008/fxxklocation/util/SystemCheckUtil.kt`
  - 檢查定位／通知權限、GPS、mock app、電池最佳化狀態。
  - 提供跳轉系統設定頁的方法。
  - 對 Xiaomi / Redmi / Poco 有額外電池最佳化跳轉嘗試。

- `app/src/main/java/com/rsps1008/fxxklocation/util/LanguageHelper.kt`
  - 集中處理 App 語系與系統語言映射。
  - 目前觀察到的規則是：系統若為任何中文語系，就套用繁體中文；否則沿用預設語系。

## 權限與 Manifest 重點

`app/src/main/AndroidManifest.xml` 目前包含：

- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_MOCK_LOCATION`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `POST_NOTIFICATIONS`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `WRITE_EXTERNAL_STORAGE`（僅 `maxSdkVersion=32`）
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

另有一個未匯出的 `MockLocationService` 前景服務。

## 已知觀察與風險

1. `README.md` 的繁體中文段落目前顯示亂碼，推測是檔案編碼有問題。
2. `app/src/main/res/values-zh-rTW/strings.xml` 目前內容也有明顯亂碼，且看起來 XML 本身可能已損壞，後續若要正式支援繁中顯示，建議優先修復。
3. `MockLocationManager` 與部分註解內有亂碼註解，閱讀維護成本較高。
4. 已補上目前位置快照節流策略與程序內即時狀態的單元測試；前景服務與 mock provider 的完整生命週期仍未有儀器測試驗證，本次依需求不使用實機驗證。
5. `WRITE_EXTERNAL_STORAGE` 對目前 Android 新版本價值有限，未來可評估是否仍需要保留。
6. 地圖已從 osmdroid 改為 MapLibre；若未來要加入離線圖資、自訂 style 或更完整 annotation 功能，應優先沿用 MapLibre 生態系而不是混用兩套地圖 SDK。
7. 目前 MapLibre `MapView` 是包在 Compose `AndroidView` 內，初始化時需要主動補 `onCreate`、`onStart`、`onResume`，否則首次進畫面可能只看到藍色底而沒有實際底圖。
8. `demotiles.maplibre.org` 的 demo style 會依賴遠端 glyph 與向量樣式資源；若模擬器或網路環境無法穩定連線，容易只剩藍底。現階段已改為較單純的 OSM raster style 以降低這類問題。
9. 若 log 出現 `Unable to resolve host "tile.openstreetmap.org"`，代表目前執行裝置或模擬器的 DNS / 網路環境無法解析外部圖磚網域。這種情況下 MapLibre 會只顯示空白底圖，屬於環境連線問題，不是地圖選點邏輯本身失效。
10. 主畫面的地標搜尋會依賴 `nominatim.openstreetmap.org`；如果該服務或網路不可用，搜尋只會失敗，不會影響既有 mock 流程。
11. `docs/index.html` 與 `docs/privacy-policy/index.html` 屬於對外公開頁面，內容需要隨 App 實際功能同步更新，尤其是資料儲存、定位使用與第三方服務說明。
12. 目前程式碼沒有看到付費、訂閱、廣告、遊戲或自有後端；因此使用者貼出的 Brazil / Japan / Korea 商家或付款類要求，多半只會在之後加入 IAP、付費版本或開發者帳戶條件符合時才會真正觸發。Vietnam 的網遊授權規定則與目前這個 mock location App 無直接關聯。
12. 近期已將對外品牌改為 `Fake Location`；若要送審或重新上架，請同步確認 app 名稱、網站標題、隱私權頁、截圖與任何商店 metadata 都沒有殘留不雅字樣。

## 2026-08-29 靜態優化巡檢與實作補充

以下是本次巡檢確認到的優化候選與目前實作狀態：

1. `MockLocationService` 已改成每 2 秒維持 mock provider 與程序內即時位置同步；漂移設定以 Flow 觀察，DataStore 位置快照則節流為每 30 秒一次，並在停止時補寫最後值；相關狀態 Flow 也會略過相同值的重複通知。第 1 項 dispatcher 優化已完成：漂移計算使用 `Dispatchers.Default`，provider 操作使用序列化背景 dispatcher，並以 `Mutex`／`@Synchronized` 保護停止、重啟與銷毀順序。
2. `MockLocationManager` 的 active provider 優化已完成：固定四個 provider 仍依原順序嘗試建立，但只有成功建立並啟用者會進入 `activeProviders`；更新只遍歷成功清單，停止清空，初始化失敗則記錄原因。
3. `MainViewModel` 的自動啟動 readiness 優化已完成：初始位置／高度載入與首次系統狀態檢查以 StateFlow 表示，移除固定延遲並處理失敗終止；自動啟動仍保留原本四個必要條件與不檢查 GPS 的既有行為，並以 DataStore／程序內狀態與 atomic claim 避免重複啟動。
4. 設定頁數字輸入的 DataStore 寫入優化已完成：三個欄位各自維護可保存的本地草稿，通過原有解析規則後於失焦、IME Done、短暫 debounce 或 dispose 提交，並保留編輯中的暫時字串與外部回讀隔離。
5. 位置查詢與套用邏輯在 `MainViewModel` 多處重複；停止模擬後的成功路徑已移除重複更新 current location 的寫入，但查詢與 fallback 流程仍可後續統一；`currentLocations` 已移除，部分 helper 仍應一併確認是否可移除。
6. `MainScreen` 的 MapLibre `MapView` 生命週期同時由 `AndroidView` factory 與 `DisposableEffect` 管理，並且使用已棄用的 Marker Annotation API；後續可先統一生命週期所有權，再評估改用目前支援的 annotation／source API。
7. 目前建置工具鏈為 AGP 9.3.2、Gradle 9.7.1、Kotlin 2.4.10；本次修改後 `testDebugUnitTest` 與 `assembleDebug` 成功，`lintDebug` 仍受專案既有 12 個 errors 與 26 個 warnings 阻擋。
8. `minSdk` 目前文件與 `app/build.gradle.kts` 都應以 28 為準；lint 仍會因此指出既有 API 30／31 呼叫需要版本防護。

## 建議的後續維護方向

1. 修正 `README.md` 與繁體中文字串的編碼／內容。
2. 為 `SettingsStore`、漂移演算法與服務啟停流程補上測試。
3. 釐清 `fused` test provider 的相容性策略，並在文件中記錄不同裝置品牌的行為差異。
4. 若未來加入更多畫面或模組，需在本檔補上新的責任分層與資料流。

## 協作約定

- 修改 UI 時，請同步確認字串資源是否需要一起更新。
- 修改定位、權限、前景服務行為時，請同步更新本檔的「權限與 Manifest 重點」與「已知觀察與風險」。
- 修改設定欄位時，請同步更新 `SettingsStore` 說明與已保存欄位列表。
- 若新增或修改提示字串，請確認英文與中文資源都已補齊，避免系統語系切換後出現半套文案。
- 若發現裝置相容性問題，請記錄：
  - 裝置品牌 / 型號
  - Android 版本
  - 問題症狀
  - 是否與電池最佳化、mock app 指定或 Google Play Services 有關

## 本次整理依據

本檔內容目前依據以下檔案整理：

- `README.md`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/rsps1008/fxxklocation/**/*.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rTW/strings.xml`

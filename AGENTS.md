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

- 專案名稱：`Fxxk Location`
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

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 31`
- Java / Kotlin JVM target：`11`
- Android Gradle Plugin：`8.13.2`
- Kotlin：`2.0.21`

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
- 可選擇手動海拔或抓取真實海拔。
- 可選擇是否使用 Google Play Services 取得真實位置。
- 可設定自動停止時間。
- 可設定 App 啟動時自動以最後位置恢復 mock。
- 以常駐通知顯示執行中狀態與自動停止倒數。
- 主畫面按下開始時，若缺位置／通知權限會依序走 OS runtime permission；接著會先走忽略電池最佳化的 OS request，最後才檢查 GPS / mock app 是否仍缺，並視結果決定是否跳出「需要權限」對話框。
- 主畫面那個阻擋對話框目前文案已改成「需要完成系統設定」，內容會明確列出位置、通知、電池最佳化、GPS 與模擬應用程式等必要條件。
- App 會依系統語言自動套用語系；若系統語言為任何中文，會統一映射成繁體中文。設定頁不再提供中文／英文切換。
- 系統語言同步要直接讀 `LocaleManagerCompat.getSystemLocales(context)`，不要讀 `context.resources.configuration.locales` 或只看 `LocaleList.getDefault()`；否則 App 先前套過的語系會把後續切換黏住，重開也不會跟著變。現在同步點放在 `MainActivity.attachBaseContext()`、`onCreate()` 和 `onResume()`。
- `MainViewModel` 的 toast 訊息已改為字串資源，會跟著目前套用的語系同步切換。
- 當正在虛擬定位時，主畫面右下角的 `Locate Me` 按鈕會以灰階樣式顯示；按下後仍會以目前系統回報的位置更新地圖。一旦停止虛擬定位，ViewModel 會立刻嘗試抓取真實定位與高度，並回寫到 camera / altitude 狀態。停止後再按 `Locate Me` 時，會主動向系統請求 current location，而不是只讀快取。
- 當主畫面尚未啟動虛擬定位時，下方的 `X` 停止按鈕會維持灰階外觀，且點擊不會有反應；只有 mock 正在執行時才會真的停止。
- 主畫面底部的勾勾／叉叉操作列保留額外底距與 navigation bar 安全區，按鈕本體改為長方形小按鈕，避免 `LargeFloatingActionButton` 造成圓形外觀，也避免在某些解析度下直接貼齊螢幕底部。
- 主畫面現在另外會顯示一個藍色的 current location 標記，用來表示系統目前回報的位置；紅色標記則維持代表使用者選定的虛擬目標點。
- 主畫面地圖不會再因為 `selectedLocation` 或 current location 更新而自動把鏡頭拉回去；鏡頭只會在使用者按 `Locate Me` 時主動 recenter。
- 進入主畫面時，地圖會固定先以 `selectedLocation` 作為初始鏡頭位置；若沒有手動釘選過位置，則會落在 `selectedLocation` 的預設值，也就是台北 101。`current location` 仍會保留為藍色標記，但不再主導初始鏡頭。
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

### 對外網站文件

- `docs/index.html`
  - GitHub Pages 風格的專案首頁。
  - 目前內容用來介紹 FxxkLocation 的核心功能、使用前置條件與隱私政策入口。
- `docs/privacy-policy/index.html`
  - 對外公開的隱私權政策頁面。
  - 目前內容以「資料主要保存在裝置上、沒有自有後端」為主軸，並說明定位、通知與 Google Play Services 相關使用情境。

### ViewModel 層

- `app/src/main/java/com/rsps1008/fxxklocation/viewmodel/MainViewModel.kt`
  - 管理主畫面狀態。
  - 讀取最後定位。
  - 啟停 `MockLocationService`。
  - 處理自動啟動、定位自己、刷新真實海拔。
  - `centerMapOnCurrentLocation()` 在 mock 狀態下只使用目前已知的 mock/current 快照；非 mock 狀態下會主動請求 current location。整個流程都不會覆寫 `selectedLocation`，且只有定位失敗時才提示訊息。
  - 使用字串資源發送 toast 訊息，避免語言切換後還有硬編碼英文提示。
  - 當 mock 狀態從 `true` 切到 `false` 時，會自動啟動一輪真實定位查詢，優先 current location，失敗才 fallback 到 last known。

- `app/src/main/java/com/rsps1008/fxxklocation/viewmodel/SettingsViewModel.kt`
  - 將 `SettingsStore` 中的設定轉成 UI 可觀察狀態。
  - 更新設定值。
  - 在需要時刷新真實海拔。

### 資料與設定層

- `app/src/main/java/com/rsps1008/fxxklocation/data/store/SettingsStore.kt`
  - 使用 DataStore Preferences 保存設定與最後位置。
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

### 定位與系統整合

- `app/src/main/java/com/rsps1008/fxxklocation/service/MockLocationService.kt`
  - 前景服務核心。
  - 接收 start / stop / pause / resume action。
  - 啟動後持續推送 mock location。
  - 內含自動停止倒數與通知更新邏輯。

- `app/src/main/java/com/rsps1008/fxxklocation/location/MockLocationManager.kt`
  - 建立與移除 test providers。
  - 對多個 provider 寫入 mock location。
  - 可產生隨機漫步式漂移位置，並限制在指定半徑內。

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
4. 專案目前測試檔仍是範本檔，尚未看到與核心 mock 邏輯對應的測試。
5. `WRITE_EXTERNAL_STORAGE` 對目前 Android 新版本價值有限，未來可評估是否仍需要保留。
6. 地圖已從 osmdroid 改為 MapLibre；若未來要加入離線圖資、自訂 style 或更完整 annotation 功能，應優先沿用 MapLibre 生態系而不是混用兩套地圖 SDK。
7. 目前 MapLibre `MapView` 是包在 Compose `AndroidView` 內，初始化時需要主動補 `onCreate`、`onStart`、`onResume`，否則首次進畫面可能只看到藍色底而沒有實際底圖。
8. `demotiles.maplibre.org` 的 demo style 會依賴遠端 glyph 與向量樣式資源；若模擬器或網路環境無法穩定連線，容易只剩藍底。現階段已改為較單純的 OSM raster style 以降低這類問題。
9. 若 log 出現 `Unable to resolve host "tile.openstreetmap.org"`，代表目前執行裝置或模擬器的 DNS / 網路環境無法解析外部圖磚網域。這種情況下 MapLibre 會只顯示空白底圖，屬於環境連線問題，不是地圖選點邏輯本身失效。
10. 主畫面的地標搜尋會依賴 `nominatim.openstreetmap.org`；如果該服務或網路不可用，搜尋只會失敗，不會影響既有 mock 流程。
11. `docs/index.html` 與 `docs/privacy-policy/index.html` 屬於對外公開頁面，內容需要隨 App 實際功能同步更新，尤其是資料儲存、定位使用與第三方服務說明。

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

# 交付前UI検証（実機なし検証の標準化）— ミガケル

本人の決定（2026-11-23）による全プロジェクト標準。このVMにはKVMが無く
エミュレータは実用不可のため、画面検証はJVM上で行う。正本ルールは
`~/AGENTS.md` の「Pre-delivery UI verification standard — no emulator」。

## 構成

- **Robolectric 4.16.1**：実Activity（MainActivity）を起動し、実レイアウト
  パスを通す。`@GraphicsMode(NATIVE)`、`@Config(sdk=35, w393dp-h915dp)`。
- **Roborazzi 1.40.0**：スクリーンショット golden 照合。
  - 注意：1.40 の `RoborazziRule`（CaptureType の LastImage 等）は
    Espresso/Compose 前提で、素の View 撮影では
    "captureRoot is required for LastImage" で全件失敗する。
    **ルールは使わず、core の `View.captureRoboImage(file, options)` を
    直接呼ぶ**のが正解（もち守りの先行事例と一致）。
  - golden は `app/src/test/screenshots/` に置き、リポジトリにコミットする。
  - 照合の changeThreshold は 2%（炎バディのアニメーションの揺らぎ吸収用。
    真っ白画面は約100%差分なので検知は弱まらない）。
- **オフライン実行**：テストJVMはこのVMではネットワークに出られないため、
  Robolectric の android-all を `.local/robolectric-deps/` に事前取得し、
  `robolectric.offline=true` + `robolectric.dependency.dir` で読む
  （SDK 35 用は `android-all-instrumented-15-robolectric-13954326-i7.jar`、
  Robolectric 4.16.1 の DefaultSdkProvider が要求する版）。
  依存解決自体は通常どおりローカルミラー経由（build.sh）。
- テストJVMのタイムゾーンは Asia/Tokyo に固定（app/build.gradle.kts）。

## テスト内容（MainActivityScreenTest、6件）

全件に**構造アサート**（真っ白画面ガード）が入る：decorView の高さ>0、
ScrollView の高さ>0、ページの子要素>0 かつ高さ>0、期待テキストの存在。
これは v0.4.2 の障害形状（土台の子が height 0 に潰れて背景だけ表示）の
再発検知が目的。

| テスト | 画面 | golden |
|---|---|---|
| onboardingEntry_rendersNonBlank | オンボーディング入口（showOnboarding） | ✅ onboarding_entry.png |
| todayScreen_rendersNonBlank | 今日（showMain） | 構造のみ |
| todayScreen_firstRun_showsIntroCard | 今日＋初回カード | 構造のみ |
| mirrorScreen_rendersNonBlank | 鏡 | 構造のみ |
| guideScreen_rendersNonBlank | 図鑑 | ✅ guide.png |
| meScreen_rendersNonBlank | わたし | ✅ me.png |

**golden を付けない理由**：`LocalDate.now()`（java.time）は Robolectric の
シャドウ対象外で実時計を読むため、日付を描く画面（今日・鏡）は golden が
日替わりで壊れる。時計固定（スケジューラ advanceTo）は java.time に効か
ないことを実測で確認済み（2026-11-23）。これらの画面は構造アサートで守る。

## 実行方法

- 通常の交付前チェック：`./build.sh testDebugUnitTest assembleDebug`
  （golden は照合モードで走る）
- golden の再記録：`ROBORAZZI_RECORD=1 ./build.sh testDebugUnitTest`
  （画面を作り替えた版では必ず再記録し、画像を目視確認してからコミット）

build.sh は全プロジェクト共通のビルド直列化ラッパー
（`~/workspace/tools/with-build-lock.sh`）経由で実行される
（ビルド直列化ルール、2026-11-23）。

## 初回結果（2026-11-23）

- 全18件合格（画面6＋ロジック11＋プール1）、golden 3枚を目視確認済み
  （オンボーディング入口／図鑑／わたし — 黒×金×炎デザインで描画、
  崩れ・見切れなし）。
- 身体感覚が要る確認（実機の色味・触感）は従来どおり実機待ち行列。

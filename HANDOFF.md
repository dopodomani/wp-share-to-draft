# 引き継ぎ事項

## 現在の状態

- Android・WordPressの最新変更は `79e38b4`（Chromeの`リンク: URL を含む`定型件名を除外）です。
- `main`にはタイトルモード（`AUTO` / `FIRST_LINE`）、共有本文1行目のタイトル化、WordPress側のタイトル重複除去が統合済みです。
- `main` は `origin/main` と同期済みで、既存のCI（Android CI、WordPress CI、Documentation CI）が設定されています。
- Pixel 9a（USBデバッグ許可済み）でFIRST_LINE設定とChrome共有の実機確認済みです。
- APKの生成先は `android/app/build/outputs/apk/debug/app-debug.apk` です。

## 別PCでの開始手順

```bash
git fetch origin
git checkout main
git pull --ff-only
```

AndroidビルドにはJDK 17とAndroid SDKが必要です。端末検証時はUSB接続、USBデバッグ許可、`adb devices` で端末が `device` 状態であることを確認してください。

Androidのローカル検証:

```powershell
cd android
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"
.\gradlew.bat :core:test :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

`IntentParserTest`を含むAndroidユニットテストは成功済みです。Androidビルド時にSDK XML version 4の警告が出る場合がありますが、ビルド結果には影響していません。

## 引き継がれない環境

- `Documents/Work/git/local-wordpress/` はリポジトリ外のローカルWordPress環境です。必要なら `wordpress-plugin/scripts/setup-local-sqlite-env.sh` を再実行してください。
- Android Studioのエミュレータ選択などのローカル設定、実機のUSB許可設定はPCごとに再構築が必要です。
- `.claude/settings.local.json` はこのPC固有の未追跡ファイルで、コミット・共有しません。

## 継続作業

- WordPress PHPUnitは、このPCではPHPの`mbstring`拡張不足で実行できない場合があります。別PCまたはCIで`wordpress-plugin/vendor/bin/phpunit -c wordpress-plugin/phpunit.xml.dist`を実行してください。
- WordPress側の変更を先にデプロイしてから、タイトルモード対応APKを実機へ配布してください。
- Dependabotの更新PRが発生した場合は、CI結果と変更内容を確認してからマージしてください。
- WordPressプラグイン配布物はRelease用CIで生成されます。公開前にZIP展開後のプラグインロード検証とAPKチェックサムを確認してください。

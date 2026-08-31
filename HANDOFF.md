# 引き継ぎ事項

## 現在の状態

- Androidの最新変更は `feat/app-icon` のコミット `2be61dd`（adaptive app icon）です。
- `main` は `origin/main` と同期済みで、既存のCI（Android CI、WordPress CI、Documentation CI）が設定されています。
- Pixel 9a（USBデバッグ許可済み）へデバッグAPKをインストール・起動済みです。
- APKの生成先は `android/app/build/outputs/apk/debug/app-debug.apk` です。

## 別PCでの開始手順

```bash
git fetch origin
git checkout main
git pull --ff-only
```

AndroidビルドにはJDK 17とAndroid SDKが必要です。端末検証時はUSB接続、USBデバッグ許可、`adb devices` で端末が `device` 状態であることを確認してください。

## 引き継がれない環境

- `Documents/Work/git/local-wordpress/` はリポジトリ外のローカルWordPress環境です。必要なら `wordpress-plugin/scripts/setup-local-sqlite-env.sh` を再実行してください。
- Android Studioのエミュレータ選択などのローカル設定、実機のUSB許可設定はPCごとに再構築が必要です。
- `.claude/settings.local.json` はこのPC固有の未追跡ファイルで、コミット・共有しません。

## 継続作業

- Dependabotの更新PRが発生した場合は、CI結果と変更内容を確認してからマージしてください。
- WordPressプラグイン配布物はRelease用CIで生成されます。公開前にZIP展開後のプラグインロード検証とAPKチェックサムを確認してください。

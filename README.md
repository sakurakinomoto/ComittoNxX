# ComittoNxX改造版

<img src="app/src/main/res/drawable-hdpi/comittonxx.png" width="100" align="right" alt="logo">

ComittoNxX改造版はComittoNxXオリジナルをフォークしたオープンソースの画像ビュワーです.
基本動作はComittoNxXを受け継いでいます.
対応バージョンはAndroid5.0以上15までです.

- 対応する電子書籍ファイルの形式：  
  PDF、EPUB、TEXT、青空文庫  

- 対応する画像ファイルの形式：  
  JPEG、GIF、PNG、WebP、AVIF、HEIF(Android 8.0以上)、JXL  

- 対応する圧縮ファイルの形式：  
  ZIP、RAR  

- Windowsファイル共有(SMB)に対応

<!-- 
> [!IMPORTANT]
> 更新を再開しました.
-->

<!--
> [!NOTE]
> 掲示板への投稿が規制されたためバージョン更新の告知を停止しています.
-->

## 注意事項

- 32bit版アプリや32bitOSを使用している場合は使用メモリサイズを少なめにしてください.  
- SMBの接続情報にドメインを使用すると初回接続が遅くなります.  
- SMB上のRAR圧縮形式ファイルの構造解析は時間がかかります.サムネイル作成時にファイル名を解析する場合にはZIP圧縮形式の使用を推奨します.  

## 既知の不具合

- アプリ内ヘルプの内容が古いです.  
- Android 10 でローカルファイルの削除と名前の変更ができません.  
- しおりのまとめて削除が動作しません.  

## ダウンロード

[Releases](https://github.com/sakurakinomoto/ComittoNxX/releases) よりご利用ください.  

#### [NxD](https://github.com/Kdroidwin/cnxd/tree/cnxd) からの修正点

- PNG形式で表示できないファイルがあったのを修正.  
- AVIF形式画像の表示に対応.  
- HEIF形式画像の表示に対応.(Android 8.0以上)  
- JXL形式画像の表示に対応.  
- PDF形式電子書籍の表示に対応.  
- EPUB形式電子書籍の本文テキストの表示に対応.  

## ビルド

アプリをビルドするには [Git](https://git-scm.com/)、[Ninja](https://ninja-build.org/)、[Meson](https://mesonbuild.com/)、[pkg-config](https://www.freedesktop.org/wiki/Software/pkg-config/)、[NASM](https://www.nasm.us/) のインストールが必要です.  
[Android Studio](https://developer.android.com/studio/install) を利用するか、gradlewコマンドでアプリをビルドしてください.  

<details><summary><b>ビルドに必要なファイルの作成手順</b></summary>
<p>

###### 署名の作成

キーストアファイルを作成して保存します.  
Android studio の場合は [Build] > [Generate Signed Bundle/APK] から作成します.

###### signingConfigs/release.gradle の作成

プロジェクトルートに signingConfigs というフォルダを作成します.  
signingConfigs の中に release.gradle というファイルを作成します.

```gradle
signingConfigs {
    release {
        storePassword '${署名ファイルのパスワード}'
        keyPassword '${鍵のパスワード}'
        storeFile file('${署名のファイル名}')
        keyAlias '${鍵のエイリアス}'
    }
}
```
</details>

## ライセンス

LICENSE ファイルに記載されたライセンスに基づきます.  

本ソースは、[ComittoNおよびComittoNxN](https://docs.google.com/open?id=0Bzx6UxEo3Pg0SXNIQVdRVnVqemM)、[ComittoNxM](https://www.axfc.net/u/3792235)、[ComittoNxT](https://www.axfc.net/u/3978158)、[ComittoNxA](https://github.com/ComittoNxA/ComittoNxA/tree/1.65A20)、[ComittoNxAC](https://www.axfc.net/u/4059552)、[ComittoNxD](https://github.com/Kdroidwin/cnxd/tree/cnxd) のソースを元にしています.  

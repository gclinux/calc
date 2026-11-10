Shelter
===

> **本仓库是 Shelter 的「科学计算器」伪装改造版。** 桌面图标、应用名称和启动界面都表现为一台普通计算器，
> 真正的隔离空间（Shelter）藏在密码入口之后。隐藏入口的完整使用方法见
> [计算器伪装版使用说明](#计算器伪装版使用说明)。

Shelter is a Free and Open-Source (FOSS) app that leverages the "Work Profile" feature of Android to provide an isolated space that you can install or clone apps into.

Downloads
===

- 本改造版的正式安装包见本仓库的 **GitHub Releases**（自有签名，`ShelterCalc-v*-release.apk`）。
- [F-Droid](https://f-droid.org/app/net.typeblog.shelter) (Signed by F-Droid) — **上游官方版，不是本改造版**
- Custom F-Droid Repository (Signed by PeterCxy, contains latest development versions):
  - [Click here](fdroidrepos://fdroid.typeblog.net?fingerprint=1A7E446C491C80BC2F83844A26387887990F97F2F379AE7B109679FEAE3DBC8C) to add from your phone
  - Or scan the following QR-code:  
  ![](fdroid_custom_repo.png)
  - Or setup manually:
    - Url: https://fdroid.typeblog.net
    - Fingerprint: `1A 7E 44 6C 49 1C 80 BC 2F 83 84 4A 26 38 78 87 99 0F 97 F2 F3 79 AE 7B 10 96 79 FE AE 3D BC 8C`

You cannot switch between versions listed above that have different signature without uninstalling Shelter first. **This also applies to this disguised build**: it uses its own signing certificate and cannot be installed over the F-Droid / official Shelter release.

计算器伪装版使用说明
===

## 它看起来是什么

- 桌面图标是计算器，应用名默认为 `科学计算器`。名称已适配原项目的全部语言，例如繁体中文 `科學計算機`、日文 `関数電卓`、德文 `Wissenschaftlicher Rechner`；未单独提供译文的语言回退到默认的中文名。
- 打开后是一台**可以正常使用的计算器**：`+ - × ÷`、`±` 正负号、`%` 百分比、小数点、`AC` 清除、`=` 求值，以及连续运算（`7 + 8 = 15`）。
- 右上角齿轮（设定）按钮点开后有**三个选项**：
  1. `按键提示音` — 开关，默认开启；
  2. `检查更新` — 伪装项，只会提示「已是最新版本」；
  3. `关于` — 显示应用信息。

## 首次使用：设置初始密码

1. 打开 app，点右上角齿轮打开设定面板。
2. 保持面板不关闭，在 **1.5 秒内连续点击 5 次「检查更新」**（每次点击间隔不要超过 1.5 秒，否则计数会重置）。
3. 因为尚未设置密码，此时会弹出 **「设置初始密码」** 对话框，输入两遍数字密码并确认。
4. 密码以 **MD5 哈希** 保存在本机的 `SharedPreferences`（key `calc_password_hash`），**不保存明文**，长度上限 12 位。

## 解锁进入 Shelter

1. 同样在设定面板里 **1.5 秒内连点 5 次「检查更新」**。
2. 界面**仍然是计算器的外观**，但已切换为密码输入模式：按数字键会以 `•` 圆点显示；`+ - × ÷`、`±`、`%`、`=` 在这个模式下不会做计算，按 `AC` 则退出密码模式、回到普通计算器。
3. 输入密码后，**点击左上角的标题** —— 简体中文为 `计算`，繁体中文为 `計算`，英文为 `Calculator`。
4. 密码正确即解锁进入 Shelter 原有界面；密码错误会提示并清空，可以重新输入。

## 重新上锁

- 在 Shelter 界面按**返回键** → 回到干净的计算器。
- 或按 Home 后从桌面重新点开图标 → 启动 Activity 使用 `singleTask` + `clearTaskOnLaunch`，会清掉上层所有 Shelter 页面，总是回到计算器，未输完的密码也会被清空。
- 每次重新进入都需要再输入一次密码。

## 注意事项（务必阅读）

- **MD5 不是加密**。无盐 MD5 可被彩虹表 / 暴力破解，它只用于「防随手翻看」，不提供真正的数据加密保护。不要复用重要密码，也不要用本工具保护高敏感内容；真正的隔离依赖 Android 工作资料（Work Profile）本身。
- 密码只存在本机，**没有找回入口**。忘记密码只能清除应用数据；而卸载 / 清除 Shelter 会连带移除整个工作资料及其中已安装的 app 与数据。
- 首次解锁会进入 Shelter 的工作资料设置向导。设备不支持工作资料（部分模拟器）时，安装失败后会降级进入「主用户」应用列表，不会反复回到计算器。
- 本改造版是**自有签名**，与 F-Droid / 官方 Shelter 签名不同，**无法覆盖安装官方版**。切换版本必须先移除工作资料、关闭设备管理员再卸载原版，工作资料里的 app 与数据无法无损保留。

Features
===

- Installing apps inside a work profile for isolation
- "Freeze" apps inside the work profile to prevent them from running or being woken up when you are not actively using them
- Installing two copies of the same app on the same device
- Calculator disguise with a hidden password entry (see above)

构建与产物
===

## 环境

- JDK 21（Android Studio JBR 可用）、Android SDK 35 / Build Tools 35.0.0、Gradle Wrapper 8.7。
- 首次构建需初始化子模块：`git submodule update --init --recursive`。
- 在 `local.properties` 中指定 SDK 路径，并可选配置正式签名（该文件已被 `.gitignore` 忽略）：

  ```properties
  sdk.dir=C:/path/to/Android/Sdk
  calc.release.keystore=C:/path/to/shelter-calc-release.jks
  calc.release.storePassword=<store password>
  calc.release.keyAlias=shelter-calc
  calc.release.keyPassword=<key password>
  ```

  **切勿把 keystore 文件或密码提交进仓库。** 未配置签名时，`assembleRelease` 仍能构建，但产出的是未签名包，无法安装。

## 命令（Windows 用 `gradlew.bat`）

```bash
./gradlew assembleDebug       # 调试包（Android Debug 签名）
./gradlew assembleRelease     # 正式 APK（自有签名）
./gradlew bundleRelease       # 正式 AAB（应用商店）
```

## 产物

| 文件 | 签名 | 用途 |
| --- | --- | --- |
| `ShelterCalc-v<版本>-release.apk` | 自有 release 证书 | 侧载安装 / GitHub Release，可长期升级 |
| `ShelterCalc-v<版本>-release.aab` | 自有 release 证书 | 应用商店上传 |
| `ShelterCalc-debug.apk` | Android Debug 证书 | 本地开发与调试，signature 与正式包不兼容 |

正式包的版本号由 `gradle.properties` 中的 `calcVersionCode` / `calcVersionName` 控制（与上游基于 git tag 的逻辑分离）。**每次发布请递增 `calcVersionCode`，并始终使用同一个 keystore**；换签名会导致用户必须卸载重装，工作资料会一并丢失。

Discussion & Support
===

- [Mailing List](https://lists.sr.ht/~petercxy/shelter) （上游项目）
- Matrix Chat Room: #shelter:neo.angry.im

__The GitHub Issue list and pull requests are not checked regularly. Please use the mailing list instead.__

Caveats & Known Issues
===

- Some caveats and known issues are discussed during the setup process of Shelter. __Please read through text in the setup wizard carefully__.
- Shelter is only as safe as the Work Profile implementation of the Android OS you are using. For details, see <https://support.google.com/work/android/answer/6191949?hl=en>
- The calculator disguise is obfuscation, not encryption. See [注意事项](#注意事项务必阅读) above.

State of the Project, Feature Requests, etc.
===

Since Shelter simply makes use of the Work Profile APIs exposed by Android, there is a limited set of features that are possible to implement via the app. As we do not intend on leveraging (or "abusing") adb privileges, the features of Shelter can only be a strict subset of the exposed, unprivileged APIs.

As a result, we do not intend on adding a lot of new features to Shelter going forward, unless there is to be big changes in the capabilities of work profile APIs. Shelter is currently in an effective **maintenance mode**. Nevertheless, the author is still committed to regularly **adapting Shelter to all new Android versions as soon as possible after they are released** -- this includes upgrading the target SDK level, adapting to any new features or restrictions introduced by the new Android version, updating all dependencies, and so on. The author still relies on Shelter for his daily life, so Shelter will **not** become abandonware in the forseeable future.

Contributing
===

- [Weblate](https://weblate.typeblog.net/projects/shelter/shelter/) for contributing translations
- Sponsor me on [Patreon](https://www.patreon.com/PeterCxy)

<a href="http://weblate.typeblog.net/engage/shelter/?utm_source=widget">
  <img src="http://weblate.typeblog.net/widgets/shelter/-/shelter/multi-auto.svg" alt="Translation status" />
</a>

Uninstalling
===

To uninstall Shelter, please delete the work profile first in Settings -> Accounts, and then uninstall the Shelter app normally.

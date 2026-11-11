Shelter
===

> **本仓库是 Shelter 的「科学计算器」伪装改造版。** 桌面图标、应用名称和启动界面都表现为一台普通计算器，
> 真正的隔离空间（Shelter）藏在密码入口之后。隐藏入口的完整使用方法见
> [计算器伪装版使用说明](#计算器伪装版使用说明)。

Shelter is a Free and Open-Source (FOSS) app that leverages the "Work Profile" feature of Android to provide an isolated space that you can install or clone apps into.
Shelter(庇护所) 是一款免费且开源的应用程序，它利用了 Android 系统的“工作模式”功能，能够提供一个独立的空间，用户可以在其中安装或克隆及冻结应用程序。

计算器伪装版使用说明
===

## 它看起来是什么

- 桌面图标是灰色圆角方块里的黑色计算器机身：深棕屏幕、两排白／橙色圆键、底部白色长条键配橙色圆键。应用名默认为 `科学计算器`，已适配原项目的全部语言（繁体中文 `科學計算機`、日文 `関数電卓`、德文 `Wissenschaftlicher Rechner` 等）；未单独提供译文的语言回退到默认的中文名。
- 界面为近白底、圆形按键的极简计算器：左上角是带红色下划线的标题（繁体 `計算`），右上角是 `+` 与六边形齿轮图标，显示区右对齐大字。按键共 6 行：`mc / m+ / m− / mr`、`AC / ⌫ / ± / ÷`、`7 8 9 ×`、`4 5 6 −`、`1 2 3 +`、`% 0 . =`；数字键为浅灰圆、运算符列为浅粉圆红字、`=` 为红色实心圆。界面使用固定浅色主题，不受系统深色模式或 Material You 取色影响。
- 它是一台**可以正常使用的计算器**：`+ - × ÷`、`±` 正负号、`%` 百分比、小数点、`AC` 清除、`⌫` 退格、`=` 求值、连续运算（`7 + 8 = 15`），以及 `mc / m+ / m− / mr` 记忆键（`m+` / `m−` 后状态行显示 `M = ...`）。
- 右上角齿轮（设定）按钮点开后有**三个选项**：
  1. `按键提示音` — 开关，默认开启；
  2. `检查更新` — 伪装项，只会提示「已是最新版本」；
  3. `关于` — 显示应用信息。

## 首次使用：设置初始密码

1. 打开 app，点右上角齿轮打开设定面板。
2. 保持面板不关闭，在 **1.5 秒内连续点击 5 次「检查更新」**（每次点击间隔不要超过 1.5 秒，否则计数会重置）。
3. 因为尚未设置密码，此时会弹出 **「设置初始密码」** 对话框，输入数字密码并确认。
4. 密码以 **MD5 哈希** 保存在本机的 `SharedPreferences`（key `calc_password_hash`），**不保存明文**，长度上限 12 位。

## 解锁进入 Shelter

1. 同样在设定面板里 **1.5 秒内连点 5 次「检查更新」**。
2. 界面**仍然是计算器的外观**，但已切换为密码输入模式：按数字键会以 `•` 圆点显示，`⌫` 会删掉最后一个圆点；`+ - × ÷`、`±`、`%`、`=` 和记忆键在这个模式下不会做计算，按 `AC` 则退出密码模式、回到普通计算器。
3. 输入密码后，**点击左上角的标题** —— 简体中文为 `计算`，繁体中文为 `計算`，英文为 `Calculator`。
4. 密码正确即解锁进入 Shelter 原有界面；密码错误会提示并清空，可以重新输入。

### 过程图片说明
<img width="1600" height="900" alt="image" src="https://github.com/user-attachments/assets/f94b87c3-ca35-49a0-be5b-5ac83488c6d9" />
<img width="1600" height="900" alt="image" src="https://github.com/user-attachments/assets/56f25f73-c1bc-4166-a6ce-4374c7e04a9b" />
<img width="1600" height="900" alt="image" src="https://github.com/user-attachments/assets/722f7b74-f660-4d29-b6e8-ac2235fc1c43" />
现在进入了主界面,你可以克隆,冻结和解冻你的应用了.

## 重新上锁

- 在 Shelter 界面按**返回键** → 回到干净的计算器。
- 或按 Home 后从桌面重新点开图标 → 启动 Activity 使用 `singleTask` + `clearTaskOnLaunch`，会清掉上层所有 Shelter 页面，总是回到计算器，未输完的密码也会被清空。
- 每次重新进入都需要再输入一次密码。

## 注意事项（务必阅读）

- **MD5 不是加密**。无盐 MD5 可被彩虹表 / 暴力破解，它只用于「防随手翻看」，不提供真正的数据加密保护。不要复用重要密码，也不要用本工具保护高敏感内容；真正的隔离依赖 Android 工作资料（Work Profile）本身。
- 密码只存在本机，**没有找回入口**。忘记密码只能清除应用数据；而卸载 / 清除 Shelter 会连带移除整个工作资料及其中已安装的 app 与数据。
- **只要工作资料还没创建成功，每次解锁都会进入 Shelter 的设置向导**，并走到最后一步真正尝试创建 Android 工作资料（`ACTION_PROVISION_MANAGED_PROFILE`），不会静默跳过。
- **创建失败 / 被取消**：会捕获失败并弹出「工作资料创建失败」提示。点「临时进入主页」后，本次会话只显示「主用户」单页应用列表（部分模拟器不支持工作资料时常见），隔离 / 双开 / 冻结功能不可用。此时**不写入任何「已完成设置」或「跳过引导」的持久化标记**，所以**下次启动 / 下次解锁依旧会重新进入向导**，直到创建成功为止。弹窗中的「重新运行向导」只在当前会话重跑一次，同样不改变下次启动的引导行为。临时主页下依赖工作资料的菜单项（冻结全部、设置、安装 APK 到工作资料等）会自动隐藏，避免误触崩溃。
- **创建成功**：`Utility.isWorkProfileAvailable()` 会持久化 `has_setup=true`，此后**每次解锁都直接进入应用管理列表页**，不再出现向导。成功与失败的分界只取决于工作资料是否真的存在，不受用户在弹窗里点了什么影响。
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

Uninstalling
===

To uninstall Shelter, please delete the work profile first in Settings -> Accounts, and then uninstall the Shelter app normally.

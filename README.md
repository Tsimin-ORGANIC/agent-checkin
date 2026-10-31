# Agent 签到

一个面向 Android 15 的非官方个人签到工具，支持 **WorkBuddy** 和 **Qoder**：

- 在内置 WebView 中完成官方网页登录。
- 登录成功后立即执行一次当日签到。
- 之后通过 WorkManager 按北京时间自动签到，打开 App 时也会补签。
- 凭证只保存在本机；优先使用 Android Keystore 加密存储。
- 支持手动填写 Qoder Personal Access Token（PAT）。

> 本项目不提供账号、不托管服务端，也不会把凭证上传到第三方服务器。自动化操作可能违反平台服务条款，存在账号受限风险，请只用于自己的账号。

## 构建方式：GitHub Actions

项目不需要在本地安装 Android SDK，APK 由 GitHub Actions 构建。

### 1. 创建 GitHub 仓库并上传项目

在 GitHub 新建一个空仓库，然后在本目录执行：

```bash
git init
git add .
git commit -m "Initial Android check-in app"
git branch -M main
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

推送后打开仓库的 **Actions** 页面，等待 `Build Android APK` 完成。

### 2. 下载 APK

构建完成后，在对应运行记录的 **Artifacts** 中下载：

- `agent-checkin-debug`：使用 Android 调试签名，可直接安装。
- `agent-checkin-release`：只有配置了签名密钥时才会生成。

工作流文件：`.github/workflows/android.yml`。它使用 JDK 17、Gradle 8.9、Android SDK 35。

### 3. 可选：生成已签名的 Release APK

不配置也能用 debug APK。如果要长期使用 release 版本，先生成密钥：

```bash
keytool -genkeypair -v \
  -keystore release.jks \
  -alias agentcheckin \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

把 `release.jks` 转成 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content keystore.b64
```

然后在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 添加：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | `keystore.b64` 的完整内容（单行） |
| `KEYSTORE_PASSWORD` | 生成密钥时设置的密码 |
| `KEY_ALIAS` | `agentcheckin`（或你使用的 alias） |
| `KEY_PASSWORD` | 密钥密码 |

不要把 `release.jks` 或密码提交到仓库。`.gitignore` 已排除密钥文件。

## 使用

1. 安装 APK 并打开，阅读风险提示后点击「我已了解」。
2. 在首页点击 WorkBuddy 的「登录并签到」，在内置网页完成登录。
3. 点击 Qoder 的「登录并签到」，完成网页登录。App 会尝试用网页会话自动创建 PAT；失败时可点击「手动 PAT」粘贴 `pt-` 开头的令牌。
4. 登录成功后会自动签到一次。之后首页可点击「立即签到」或「一键签到」。
5. 在右上角设置中修改自动签到时间和开关。

默认时间（北京时间）：

- WorkBuddy：09:00
- Qoder：10:05（每日活动通常 10:00 后开放）

Android 的省电策略可能延迟后台任务。若发现没有按时签到，请在系统设置中允许本应用后台运行，或手动打开 App 触发补签。

## 安全说明

- WorkBuddy 的 access/refresh token 与 Qoder 的 PAT/jobToken 保存在应用私有存储中。
- 正常情况下使用 `EncryptedSharedPreferences`（Android Keystore AES256）。
- 如果设备初始化加密存储失败，会退回到应用私有 SharedPreferences，并在设置页显示警告。
- 日志中的 JWT、PAT 等敏感内容会被脱敏；最多保留 50 条。
- 退出登录只删除本机凭证。Qoder 建议同时到账号设置中撤销名为「Android 自动签到」的 PAT。

## 已知限制

- 这是非官方工具，平台接口或网页结构变化可能导致功能失效。
- Qoder 自动创建 PAT 依赖登录后的网页会话，接口可能要求额外验证；因此保留了手动 PAT 入口。
- 自动签到由 WorkManager 调度，系统进入深度省电或强制停止应用时可能延迟。
- 请不要用于多账号、代签、批量注册或任何超出个人账号范围的用途。

## 项目结构

```
app/src/main/java/com/agentcheckin/android/
├── MainActivity.kt
├── AgentCheckinApp.kt
├── checkin/CheckinRepository.kt
├── data/                     # 设置、模型、加密存储
├── network/                  # WorkBuddy / Qoder API
├── notification/Notifier.kt
├── ui/                       # Compose 界面与 ViewModel
├── util/                     # 时间、JSON、脱敏
└── work/                     # WorkManager 调度与 Worker
.github/workflows/android.yml # GitHub Actions 构建
```

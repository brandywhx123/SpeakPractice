# 中英文发音训练助手 (EnCnVoice)

**English version: [README.en.md](README.en.md)**

一款使用 Kotlin 开发的 Android 发音训练应用：播放标准发音 → 用户跟读 → 语音识别 → 基于编辑距离算法评分，并以**上下双栏波形图**直观对比标准发音与用户发音。支持英文与中文双语训练。

## 功能特性

- 🔊 **标准发音播放**：基于 Android 原生 `TextToSpeech`，英文使用 `Locale.US`，中文使用 `Locale.SIMPLIFIED_CHINESE`
- 🎤 **语音识别评分**：通过 `RecognizerIntent` 调用系统语音识别（英文 `en-US` / 中文 `zh-CN`）
- 📏 **Levenshtein 编辑距离算法**：动态规划计算识别文本与标准文本的字符级差异，输出正确率百分比，中英文通用
- 🌊 **声音波形对比**：结果区下方上下双栏波形图（上栏蓝色=标准发音，下栏绿色=用户发音），文本一致时两栏波形完全相同
- 🌐 **双语一键切换**：RadioGroup 切换英文 / 中文训练模式，TTS 与识别语言联动
- 📱 **紧凑单屏布局**：全部功能在一屏内完整显示，无需滚动
- 🔐 **运行时权限**：Android 6.0+ 动态申请 `RECORD_AUDIO` 录音权限

## 技术栈

| 项 | 版本 / 说明 |
|---|---|
| 开发语言 | Kotlin 2.0.21 |
| 最低 SDK | Android 7.0（API 24） |
| 目标 / 编译 SDK | API 36 |
| 构建工具 | Gradle 8.9 + Android Gradle Plugin 8.7.2 |
| JDK | 17 |
| UI | Material Components 1.12.0 + 自定义 View |
| 语音合成 | Android `TextToSpeech` |
| 语音识别 | `RecognizerIntent`（系统语音识别服务） |

## 项目结构

```
EnCnVoice/
├── settings.gradle                    # Gradle 项目配置
├── build.gradle                       # 项目级构建脚本
├── gradle.properties                  # Gradle 属性
├── gradlew / gradlew.bat              # Gradle Wrapper 启动脚本
├── gradle/wrapper/
│   ├── gradle-wrapper.jar             # Wrapper JAR（已纳入版本控制）
│   └── gradle-wrapper.properties      # Gradle 8.9 配置
└── app/
    ├── build.gradle                   # 应用级构建脚本（compileSdk 36）
    └── src/main/
        ├── AndroidManifest.xml        # 权限声明与 Activity 注册
        ├── java/com/example/pronunciationassistant/
        │   ├── MainActivity.kt        # 核心逻辑：TTS + 语音识别 + 编辑距离评分
        │   └── WaveformView.kt        # 自定义声音波形绘制 View
        └── res/
            ├── layout/activity_main.xml   # 紧凑垂直布局
            ├── values/strings.xml         # 字符串资源
            └── mipmap/                     # 矢量应用图标
```

## 工作原理

### 1. TTS 标准发音

`TextToSpeech` 异步初始化（`OnInitListener` 回调），播放时根据所选语言动态调用 `setLanguage()`，再用 `speak(text, QUEUE_FLUSH, ...)` 朗读。

### 2. 语音识别（STT）

创建 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`，配置语言模型与语言代码（`en-US` / `zh-CN`），通过 `startActivityForResult` 启动系统识别界面，在 `onActivityResult` 中取出置信度最高的识别结果。该服务依赖云端识别，**需要联网**。

### 3. Levenshtein 编辑距离评分

通过动态规划计算将识别文本转换为标准文本所需的最少单字符操作（插入 / 删除 / 替换）次数：

```
正确率 = (1 - 编辑距离 / 标准文本长度) × 100%
```

算法基于 Unicode 字符级，中文字符按字符数计算，因此同一套实现同时支持中英文。

### 4. 波形可视化

TTS 合成的 PCM 流与 `RecognizerIntent` 独占的麦克风均无法直接采样，因此采用**基于文本字符的确定性合成算法**生成可视化波形（字符码点 + 正弦包络 + 高频细节）：相同文本生成完全相同的波形，识别有误时对应位置出现可见差异，便于直观对比。

## 使用流程

1. 选择训练语言（英文 / 中文）
2. 输入待练习内容，如 `Hello World` 或 `你好世界`
3. 点击 **播放标准音**：TTS 朗读，同时上栏显示标准波形
4. 点击 **开始发音训练**：授权麦克风后跟读
5. 识别完成后查看结果区正确率，并对比上下两栏波形

结果示例：

```
标准文本：Hello World
识别结果：Hello Word
编辑距离：1
差异百分比：9.1%
发音正确率：90.9%
```

## 构建与运行

### 环境要求

- JDK 17+（Android Studio 自带 JBR 即可）
- Android SDK Platform 36
- Android Studio（推荐 Koala 或更新版本）

### 命令行构建（Windows PowerShell）

```powershell
# 如系统默认 JDK 低于 17，请先指向 Android Studio 自带 JBR
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 构建 debug APK
.\gradlew.bat assembleDebug
```

产物路径：`app\build\outputs\apk\debug\app-debug.apk`

macOS / Linux：

```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Android Studio

1. `File → Open` 选择本项目目录
2. 等待 Gradle Sync 完成
3. 连接设备或启动模拟器，点击 **Run**

> 注意：`local.properties`（本机 SDK 路径）已被 gitignore 忽略，在新机器上首次打开项目时 Android Studio 会自动生成。

## 权限说明

| 权限 | 用途 | 申请方式 |
|---|---|---|
| `RECORD_AUDIO` | 麦克风录音，语音识别必需 | 运行时动态申请 |
| `INTERNET` | 云端语音识别服务联网 | 安装时授权 |
| `ACCESS_NETWORK_STATE` | 检测网络可用性 | 安装时授权 |

## 已知限制

1. 语音识别依赖设备上的系统语音识别服务（通常为云端），**离线不可用**；部分国产设备可能未内置 Google 语音识别服务。
2. 部分设备未预装中文 TTS 语音数据，需在系统设置中下载语音包。
3. 编辑距离只对比发音内容的文字差异，不分析声纹、音色、语调。
4. 波形为基于文本的确定性可视化，非真实 PCM 音频波形。

## 许可证

本项目仅用于学习与教学用途，可自由参考与修改。

---

*更多实现细节（含完整算法推导与逐行注释）见源码 [MainActivity.kt](app/src/main/java/com/example/pronunciationassistant/MainActivity.kt)。*

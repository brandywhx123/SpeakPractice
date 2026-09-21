package com.example.pronunciationassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import java.util.Locale

/**
 * MainActivity - 中英文发音训练助手主界面
 *
 * 核心功能流程:
 * 1. 用户通过顶部 RadioGroup 选择训练语言 (英文 / 中文)
 * 2. 在输入框输入待练习内容（英文如 "Hello World"，中文如 "你好世界"）
 * 3. 点击 "播放标准音" -> 通过 TTS 播放对应语言的标准发音
 * 4. 点击 "开始发音训练" -> 调用语音识别 (SpeechRecognizer) 录音并转为文本
 * 5. 将识别文本与标准文本通过 Levenshtein 编辑距离算法进行对比
 * 6. 在结果区显示发音正确率 (差异百分比)
 *
 * 技术实现:
 * - TTS (TextToSpeech): Android 原生语音合成，根据语言选择动态切换 Locale
 *   - 英文: Locale.US
 *   - 中文: Locale.SIMPLIFIED_CHINESE
 * - STT (SpeechRecognizer / RecognizerIntent): Android 原生语音识别，将用户语音转为文本
 *   - 英文识别: EXTRA_LANGUAGE = "en-US"
 *   - 中文识别: EXTRA_LANGUAGE = "zh-CN"
 * - 文本对比: Levenshtein Distance (编辑距离) 算法
 *   正确率 = 1 - (编辑距离 / 标准文本长度)
 *   该算法基于字符级别，对中英文均适用（中文字符按 Unicode 字符计算）
 *
 * 注意: 本应用仅对比 "发音内容" 的正确性，不涉及声纹/音色分析。
 */
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // ==================== 视图控件 ====================
    private lateinit var etInput: TextInputEditText      // 文本输入框
    private lateinit var btnPlay: MaterialButton          // 播放标准音按钮
    private lateinit var btnTrain: MaterialButton         // 开始发音训练按钮
    private lateinit var tvResult: MaterialTextView        // 结果显示区
    private lateinit var rgLanguage: RadioGroup           // 语言选择单选组 (英文/中文)
    private lateinit var waveformStandard: WaveformView  // 标准发音波形显示区 (上栏)
    private lateinit var waveformUser: WaveformView       // 用户发音波形显示区 (下栏)

    // ==================== TTS 引擎 ====================
    // TextToSpeech: Android 原生语音合成引擎
    // - 通过 OnInitListener 回调获知初始化状态
    // - 调用 speak() 即可朗读文本，输出到手机扬声器
    private lateinit var tts: TextToSpeech

    // ==================== 请求码常量 ====================
    companion object {
        // 录音权限动态申请的请求码
        private const val REQUEST_RECORD_AUDIO = 100
        // 语音识别 startActivityForResult 的请求码
        // 用于在 onActivityResult 中区分返回的是语音识别结果
        private const val REQUEST_SPEECH_RECOGNITION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()        // 初始化视图控件
        initTTS()          // 初始化 TTS 语音合成引擎
        setupListeners()   // 设置按钮点击事件
    }

    /**
     * 初始化视图控件: 通过 ID 绑定 XML 布局中的控件
     */
    private fun initViews() {
        etInput = findViewById(R.id.etInput)
        btnPlay = findViewById(R.id.btnPlay)
        btnTrain = findViewById(R.id.btnTrain)
        tvResult = findViewById(R.id.tvResult)
        rgLanguage = findViewById(R.id.rgLanguage)
        waveformStandard = findViewById(R.id.waveformStandard)
        waveformUser = findViewById(R.id.waveformUser)
        // 上栏(标准发音)用蓝色，下栏(用户发音)用绿色，便于视觉区分
        waveformStandard.setBarColor(Color.parseColor("#2196F3"))
        waveformUser.setBarColor(Color.parseColor("#4CAF50"))
    }

    /**
     * 初始化 TTS (TextToSpeech) 引擎
     *
     * TextToSpeech 的初始化是异步的:
     * - 构造函数传入 OnInitListener (本 Activity 实现了该接口)
     * - 引擎准备就绪后会回调 onInit() 方法
     * - 在 onInit 成功后设置语言为英语 (Locale.US)
     */
    private fun initTTS() {
        // 创建 TTS 实例，第二个参数为初始化回调 (this 实现了 OnInitListener)
        tts = TextToSpeech(this, this)
    }

    /**
     * 设置按钮点击事件监听器
     */
    private fun setupListeners() {
        // ----- 播放标准音按钮 -----
        // 点击后: 获取输入框文本 -> 根据语言选择动态切换 TTS 语言 -> 朗读标准发音
        btnPlay.setOnClickListener {
            val standardText = getInputText()
            if (standardText.isEmpty()) {
                showToast("请输入待练习内容")
                return@setOnClickListener
            }
            // 根据用户选择的语言动态切换 TTS 语言引擎
            // - 英文: Locale.US (美国英语)
            // - 中文: Locale.SIMPLIFIED_CHINESE (简体中文)
            // setLanguage 可在运行时多次调用，无需重新初始化 TTS
            val ttsLocale = if (isChineseSelected()) Locale.SIMPLIFIED_CHINESE else Locale.US
            val langResult = tts.setLanguage(ttsLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                showToast("当前设备不支持该语言的 TTS 语音数据")
                return@setOnClickListener
            }
            // 调用 TTS 朗读文本
            // - QUEUE_FLUSH: 清空当前播放队列，立即播放新文本
            // - null: 不使用 UtteranceProgressListener
            // - 最后一个 null: 不指定 utteranceId
            tts.speak(standardText, TextToSpeech.QUEUE_FLUSH, null, null)

            // ---- 生成标准发音波形并显示到上栏 ----
            // TTS 合成的 PCM 音频流不对外暴露，采用基于文本字符的确定性合成算法
            // 生成可视化波形: 相同文本 -> 相同波形，便于与下栏视觉对比
            val standardWaveform = generateWaveformFromText(standardText)
            waveformStandard.setWaveform(standardWaveform)

            showToast("正在播放标准音...")
        }

        // ----- 开始发音训练按钮 -----
        // 点击后: 检查录音权限 -> 启动语音识别 -> 录音并转为文本 -> 与标准文本对比
        btnTrain.setOnClickListener {
            val standardText = getInputText()
            if (standardText.isEmpty()) {
                showToast("请先输入英文内容")
                return@setOnClickListener
            }
            // 检查并申请录音权限 (Android 6.0+ 需动态申请危险权限)
            checkAndRequestRecordAudioPermission()
        }
    }

    /**
     * TTS 初始化完成回调
     *
     * @param status 初始化状态: TextToSpeech.SUCCESS 或 TextToSpeech.ERROR
     *
     * 逻辑说明:
     * - 只有初始化成功 (SUCCESS) 时才能设置语言和进行朗读
     * - 初始化时设置默认语言为英语 (Locale.US)
     * - 实际播放时会根据用户语言选择动态调用 setLanguage 切换
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // 设置 TTS 默认语言为美国英语 (用户播放时可动态切换)
            tts.setLanguage(Locale.US)
        } else {
            showToast("TTS 初始化失败")
        }
    }

    // ==================== 录音权限管理 ====================

    /**
     * 检查并申请录音权限 (RECORD_AUDIO)
     *
     * 权限管理逻辑:
     * - RECORD_AUDIO 属于 "危险权限" (Dangerous Permission)
     * - Android 6.0 (API 23) 起，危险权限必须在运行时动态申请
     * - 如果已授权 -> 直接启动语音识别
     * - 如果未授权 -> 调用 requestPermissions 弹出系统授权对话框
     *
     * 用户授权结果会在 onRequestPermissionsResult 回调中处理
     */
    private fun checkAndRequestRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 已有录音权限，直接启动语音识别
            startSpeechRecognition()
        } else {
            // 未授权，请求录音权限
            // ActivityCompat.requestPermissions 会弹出系统权限申请对话框
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    /**
     * 录音权限申请结果回调
     *
     * @param requestCode  请求码，用于区分是哪个权限请求
     * @param permissions 申请的权限数组
     * @param grantResults 每个权限的授权结果 (GRANTED / DENIED)
     *
     * 逻辑:
     * - 如果用户授权 -> 启动语音识别
     * - 如果用户拒绝 -> 提示需要录音权限才能训练
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 用户授权，启动语音识别
                    startSpeechRecognition()
                } else {
                    showToast("需要录音权限才能进行发音训练")
                }
            }
        }
    }

    // ==================== 语音识别 (STT) ====================

    /**
     * 启动语音识别
     *
     * 使用 Android 原生 RecognizerIntent 实现语音转文字:
     *
     * 核心原理:
     * 1. 创建 Intent，Action 为 ACTION_RECOGNIZE_SPEECH
     * 2. 配置识别参数:
     *    - EXTRA_LANGUAGE_MODEL: 语言模型 (自由文本模式 LANGUAGE_MODEL_FREE_FORM)
     *    - EXTRA_LANGUAGE: 识别语言 (英语 en-US)
     *    - EXTRA_PROMPT: 录音界面提示语
     * 3. startActivityForResult 启动系统语音识别 Activity
     * 4. 用户对着麦克风朗读后，系统返回识别结果
     * 5. 在 onActivityResult 中获取识别文本并执行对比
     *
     * 注意: RecognizerIntent 依赖设备上的语音识别服务（通常为云端 Google 语音识别），
     *       因此需要网络连接。如果设备未安装语音识别服务，会抛出 ActivityNotFoundException。
     */
    private fun startSpeechRecognition() {
        // 提示用户开始朗读
        tvResult.text = "请开始朗读..."

        // 根据用户选择的语言，确定语音识别语言代码
        // - 英文: "en-US" (美国英语)
        // - 中文: "zh-CN" (简体中文)
        val recognitionLang = if (isChineseSelected()) "zh-CN" else "en-US"

        try {
            // 创建语音识别 Intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                // 语言模型: 自由文本模式，适合朗读句子或单词
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                // 识别语言: 根据用户选择动态切换 (en-US / zh-CN)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLang)
                // 设置最多返回 1 条识别结果（取置信度最高的一条）
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // 录音界面的提示语
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请朗读输入框中的内容")
            }
            // 启动语音识别 Activity，等待结果回调
            startActivityForResult(intent, REQUEST_SPEECH_RECOGNITION)
        } catch (e: Exception) {
            // 设备可能未安装语音识别服务
            showToast("设备不支持语音识别，请检查是否安装了语音输入服务")
            tvResult.text = "语音识别不可用"
        }
    }

    /**
     * 语音识别结果回调
     *
     * @param requestCode 请求码，区分是哪个 startActivityForResult 返回的
     * @param resultCode  结果码: RESULT_OK 表示识别成功
     * @param data        包含识别结果的 Intent
     *
     * 语音识别结果获取逻辑:
     * 1. 检查 requestCode == REQUEST_SPEECH_RECOGNITION 且 resultCode == RESULT_OK
     * 2. 从 data 中提取 EXTRA_RESULTS (ArrayList<String>)
     *    - 系统返回按置信度排序的多个候选文本，取第一条 (最佳匹配)
     * 3. 将识别文本与输入框标准文本进行 Levenshtein 编辑距离对比
     *    - 算法基于字符级别，中英文均适用
     *    - 中文按 Unicode 字符计算 (如 "你好" 长度为 2)
     * 4. 计算正确率并在结果区显示
     *
     * 示例 (英文):
     *   标准文本: "Hello World" (长度 11)
     *   识别文本: "Hello Word"  (缺少一个字母 'l')
     *   编辑距离: 1, 正确率 = (1 - 1/11) × 100 ≈ 90.9%
     *
     * 示例 (中文):
     *   标准文本: "你好世界" (长度 4)
     *   识别文本: "你好世纪" (最后一个字不同)
     *   编辑距离: 1, 正确率 = (1 - 1/4) × 100 = 75.0%
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SPEECH_RECOGNITION) {
            if (resultCode == RESULT_OK && data != null) {
                // 获取语音识别结果列表
                val results: ArrayList<String>? =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)

                if (results != null && results.isNotEmpty()) {
                    // 取置信度最高的第一条识别结果
                    val recognizedText = results[0]
                    val standardText = getInputText()

                    // ---- 文本对比核心逻辑 ----
                    // 调用 Levenshtein 编辑距离算法，计算两段文本的差异
                    val editDistance = levenshteinDistance(
                        standardText.lowercase(),  // 标准文本 (转小写，消除大小写差异)
                        recognizedText.lowercase()  // 识别文本 (转小写)
                    )

                    // 计算发音正确率
                    // 公式: 正确率 = 1 - (编辑距离 / 标准文本长度)
                    val accuracy = calculateAccuracy(editDistance, standardText.length)

                    // 显示对比结果
                    tvResult.text = buildString {
                        append("标准文本：$standardText\n")
                        append("识别结果：$recognizedText\n")
                        append("编辑距离：$editDistance\n")
                        append("差异百分比：${"%.1f".format(100.0 - accuracy)}%\n")
                        append("发音正确率：${"%.1f".format(accuracy)}%")
                    }

                    // ---- 生成用户发音波形并显示到下栏 ----
                    // 基于识别文本生成可视化波形，与上栏标准波形视觉对比。
                    // 文本完全一致时，上下两栏波形完全相同。
                    val userWaveform = generateWaveformFromText(recognizedText)
                    waveformUser.setWaveform(userWaveform)
                } else {
                    tvResult.text = "未能识别语音，请重试"
                }
            } else {
                tvResult.text = "语音识别失败，请重试"
            }
        }
    }

    // ==================== 文本对比算法: Levenshtein 编辑距离 ====================

    /**
     * Levenshtein Distance (编辑距离) 算法
     *
     * 算法原理:
     * 编辑距离是指将字符串 s1 转换成 s2 所需的最少单字符编辑操作次数。
     * 允许的三种编辑操作:
     *   1. 插入 (Insert)  : 在 s1 中插入一个字符
     *   2. 删除 (Delete)  : 从 s1 中删除一个字符
     *   3. 替换 (Replace) : 将 s1 中的一个字符替换为另一个字符
     *
     * 动态规划 (DP) 实现:
     * - 构建二维 DP 表 dp[i][j]，表示 s1[0..i-1] 与 s2[0..j-1] 的编辑距离
     * - 状态转移方程:
     *     如果 s1[i-1] == s2[j-1]: dp[i][j] = dp[i-1][j-1]  (字符相同，无需操作)
     *     否则: dp[i][j] = 1 + min(dp[i-1][j],    // 删除 s1[i-1]
     *                               dp[i][j-1],    // 插入 s2[j-1]
     *                               dp[i-1][j-1])  // 替换 s1[i-1] 为 s2[j-1]
     * - 边界条件:
     *     dp[i][0] = i  (s2 为空，需删除 i 个字符)
     *     dp[0][j] = j  (s1 为空，需插入 j 个字符)
     * - 最终结果: dp[m][n]，其中 m = s1.length, n = s2.length
     *
     * 时间复杂度: O(m × n)
     * 空间复杂度: O(m × n)
     *
     * @param s1 标准文本 (用户输入的原文)
     * @param s2 识别文本 (语音识别返回的结果)
     * @return 编辑距离 (最少编辑操作次数)
     *
     * 示例:
     *   s1 = "hello world"  (标准文本)
     *   s2 = "hello word"   (识别文本，少了字母 'l')
     *   编辑距离 = 1 (需插入一个 'l')
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length  // 标准文本长度
        val n = s2.length  // 识别文本长度

        // 特殊情况处理: 任一字符串为空，编辑距离等于另一字符串长度
        if (m == 0) return n
        if (n == 0) return m

        // 构建 DP 二维数组 dp[m+1][n+1]
        // dp[i][j] 表示 s1 的前 i 个字符 与 s2 的前 j 个字符 之间的编辑距离
        val dp = Array(m + 1) { IntArray(n + 1) }

        // 初始化边界条件
        for (i in 0..m) dp[i][0] = i  // s2 为空时，s1[0..i-1] 需要删除 i 个字符
        for (j in 0..n) dp[0][j] = j  // s1 为空时，需要插入 j 个字符以匹配 s2

        // 填充 DP 表
        for (i in 1..m) {
            for (j in 1..n) {
                if (s1[i - 1] == s2[j - 1]) {
                    // 当前字符相同，无需编辑操作，继承左上角值
                    dp[i][j] = dp[i - 1][j - 1]
                } else {
                    // 当前字符不同，取三种操作的最小值 + 1
                    dp[i][j] = 1 + minOf(
                        dp[i - 1][j],      // 删除操作: 删除 s1[i-1]
                        dp[i][j - 1],      // 插入操作: 插入 s2[j-1] 到 s1
                        dp[i - 1][j - 1]   // 替换操作: 将 s1[i-1] 替换为 s2[j-1]
                    )
                }
            }
        }

        // dp[m][n] 即为最终的编辑距离
        return dp[m][n]
    }

    /**
     * 计算发音正确率
     *
     * 公式: 正确率 = (1 - 编辑距离 / 标准文本长度) × 100%
     *
     * @param editDistance  识别文本与标准文本的 Levenshtein 编辑距离
     * @param standardLength 标准文本长度
     * @return 正确率百分比 (0.0 ~ 100.0)，结果不会低于 0%
     *
     * 示例:
     *   标准文本 "Hello World" (长度 11)
     *   识别文本 "Hello Word"  (编辑距离 1)
     *   正确率 = (1 - 1/11) × 100 ≈ 90.9%
     */
    private fun calculateAccuracy(editDistance: Int, standardLength: Int): Double {
        if (standardLength == 0) return 0.0
        val accuracy = (1.0 - editDistance.toDouble() / standardLength) * 100.0
        // 确保正确率在 0~100 范围内 (识别文本可能比标准文本长很多，导致编辑距离 > 标准长度)
        return accuracy.coerceIn(0.0, 100.0)
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取输入框文本 (去除首尾空白)
     */
    private fun getInputText(): String {
        return etInput.text?.toString()?.trim() ?: ""
    }

    /**
     * 根据文本内容生成确定性的合成波形数据
     *
     * 设计说明:
     * - TTS 引擎内部合成的 PCM 音频流不对外暴露，且 RecognizerIntent 启动的
     *   系统识别 Activity 独占麦克风，外部无法并行采样。
     * - 因此本应用采用"基于文本字符的确定性合成"方式生成可视化波形:
     *     1) 用字符的 Unicode 码点填充初始振幅采样点 (中英文均适用)
     *     2) 应用正弦包络模拟自然语音的"中间高、两端低"特征
     *     3) 叠加高频细节，让波形呈现齿状外观
     * - 同一段文本生成的波形完全一致:
     *     * 标准文本与识别文本一致 -> 上下两栏波形完全相同
     *     * 识别有误 -> 上下两栏在对应字符处出现可见差异
     *
     * @param text 输入文本（中英文均可）
     * @return 振幅数组，每个元素取值 0.0 ~ 1.0
     */
    private fun generateWaveformFromText(text: String): FloatArray {
        val sampleCount = 128  // 采样点数量
        if (text.isEmpty()) return FloatArray(sampleCount)

        // 第一阶段: 用字符 Unicode 值生成初始振幅
        val raw = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val charIndex = i % text.length
            val charCode = text[charIndex].code
            // 取 Unicode 低 8 位归一化，避免波形过度受高位影响
            raw[i] = (charCode % 256) / 256f
        }

        // 第二阶段: 应用包络 + 高频细节，得到最终波形
        val result = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val t = i.toFloat() / sampleCount
            // 正弦包络: 中间凸起 (1.0)，两端衰减到 0.3
            val envelope = 0.3f + 0.7f * Math.sin(Math.PI * t).toFloat()
            // 高频细节: 与文本长度相关，让不同文本波形差异更明显
            val detail = (0.5f + 0.5f * Math.sin(i * 0.7 + text.length)).toFloat()
            result[i] = (raw[i] * envelope * detail).coerceIn(0f, 1f)
        }
        return result
    }

    /**
     * 判断当前是否选择了中文训练模式
     *
     * 通过检查 RadioGroup 中选中的 RadioButton ID 来判断:
     * - R.id.rbChinese 被选中 -> 返回 true (中文模式)
     * - R.id.rbEnglish 被选中 -> 返回 false (英文模式)
     *
     * 此方法用于:
     * - TTS 播放时选择 Locale (US / SIMPLIFIED_CHINESE)
     * - 语音识别时选择 EXTRA_LANGUAGE (en-US / zh-CN)
     */
    private fun isChineseSelected(): Boolean {
        return rgLanguage.checkedRadioButtonId == R.id.rbChinese
    }

    /**
     * 显示 Toast 提示
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ==================== 生命周期管理 ====================

    /**
     * Activity 销毁时释放 TTS 资源
     * - 停止正在播放的语音
     * - 关闭 TTS 引擎，释放底层资源
     */
    override fun onDestroy() {
        if (this::tts.isInitialized) {
            tts.stop()     // 停止当前播放
            tts.shutdown() // 释放 TTS 引擎资源
        }
        super.onDestroy()
    }
}

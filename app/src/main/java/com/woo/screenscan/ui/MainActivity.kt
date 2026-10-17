package com.woo.screenscan.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.woo.screenscan.R
import com.woo.screenscan.camera.CameraBinder
import com.woo.screenscan.camera.OcrLanguage
import com.woo.screenscan.camera.TextAnalyzer
import com.woo.screenscan.databinding.ActivityMainBinding
import com.woo.screenscan.ocr.FrameVerdict
import com.woo.screenscan.update.UpdateViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraBinder: CameraBinder
    private lateinit var exporter: TextExporter
    private lateinit var updatePrompter: UpdatePrompter
    private val viewModel: MainViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private var updateMessage: String? = null
    private var feedbackText: String? = null
    private var lastQuality = 0f

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else toast(R.string.camera_permission_denied)
    }

    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TEXT)) { uri ->
        if (uri == null) return@registerForActivityResult
        val ok = exporter.save(uri, outputText())
        toast(if (ok) R.string.saved else R.string.save_failed)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        exporter = TextExporter(this)
        cameraBinder = CameraBinder(this, binding.preview, viewModel.analysisExecutor)
        updatePrompter = UpdatePrompter(this, updateViewModel) { message ->
            updateMessage = message
            updateStatus()
        }

        applySystemBarInsets()
        bindButtons()
        bindGestures()
        observeViewModel()
        ensureCameraPermission()
        updateViewModel.checkOnLaunch(UpdatePrompter.installedVersion(this))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val settings = viewModel.currentSettings
        menu.findItem(R.id.action_engine_korean).isChecked = settings.language == OcrLanguage.KOREAN
        menu.findItem(R.id.action_strip_line_numbers).isChecked = settings.stripLineNumbers
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_share -> withText { exporter.share(it) }
        R.id.action_save -> withText { createDocument.launch(getString(R.string.default_file_name)) }
        R.id.action_engine_korean -> toggleLanguage()
        R.id.action_strip_line_numbers -> toggleSetting { it.copy(stripLineNumbers = !it.stripLineNumbers) }
        R.id.action_check_update -> checkForUpdate()
        else -> super.onOptionsItemSelected(item)
    }

    private fun checkForUpdate(): Boolean {
        updateViewModel.check(UpdatePrompter.installedVersion(this), silent = false)
        return true
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
    }

    private fun bindButtons() = with(binding) {
        scanButton.setOnClickListener { toggleScanning() }
        captureButton.setOnClickListener {
            commitEditsIfStopped()
            viewModel.captureNow()
        }
        undoButton.setOnClickListener {
            commitEditsIfStopped()
            viewModel.undo()
        }
        clearButton.setOnClickListener {
            commitEditsIfStopped()
            viewModel.clear()
        }
        copyButton.setOnClickListener {
            withText {
                exporter.copy(it)
                toast(R.string.copied)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindGestures() {
        var pinched = false
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                pinched = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                cameraBinder.zoomBy(detector.scaleFactor)
                return true
            }
        })
        binding.preview.setOnTouchListener { view, event ->
            scaleDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> pinched = false
                MotionEvent.ACTION_UP -> if (!pinched) {
                    cameraBinder.focusAt(event.x, event.y)
                    view.performClick()
                }
            }
            true
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            if (binding.output.text.toString() != state.text) {
                binding.output.setText(state.text)
                binding.output.setSelection(state.text.length)
            }
            updateStatus()
        }
        viewModel.settings.observe(this) { settings ->
            binding.scanButton.setText(if (settings.scanning) R.string.scan_stop else R.string.scan_start)
            binding.output.isFocusable = !settings.scanning
            binding.output.isFocusableInTouchMode = !settings.scanning
            updateStatus()
            invalidateOptionsMenu()
        }
        viewModel.lastFrame.observe(this) { frame ->
            binding.overlay.update(frame)
            lastQuality = frame.quality.confidence
            updateStatus()
        }
        viewModel.feedback.observe(this) { feedback ->
            feedbackText = describe(feedback)
            updateStatus()
        }
        updateViewModel.state.observe(this) { state -> updatePrompter.render(state) }
    }

    private fun describe(feedback: MainViewModel.Feedback): String = when (feedback) {
        is MainViewModel.Feedback.Added ->
            if (feedback.lines > 0) getString(R.string.feedback_added, feedback.lines) else getString(R.string.feedback_unchanged)
        MainViewModel.Feedback.NoOverlap -> getString(R.string.feedback_no_overlap)
        MainViewModel.Feedback.Unchanged -> getString(R.string.feedback_unchanged)
        is MainViewModel.Feedback.Rejected -> when (feedback.verdict) {
            FrameVerdict.BLURRY -> getString(R.string.feedback_blurry)
            FrameVerdict.TILTED -> getString(R.string.feedback_tilted, viewModel.lastFrame.value?.quality?.angle ?: 0f)
            FrameVerdict.EMPTY -> getString(R.string.feedback_empty)
            FrameVerdict.OK -> ""
        }
    }

    private fun updateStatus() {
        val pending = updateMessage
        if (pending != null) {
            binding.status.text = pending
            return
        }
        val settings = viewModel.currentSettings
        val lineCount = viewModel.state.value?.lines?.size ?: 0
        val mode = getString(if (settings.scanning) R.string.status_scanning else R.string.status_stopped)
        val version = UpdatePrompter.installedVersion(this)
        val base = getString(R.string.status_format, lineCount, mode, lastQuality, version)
        val feedback = feedbackText
        binding.status.text = if (feedback.isNullOrEmpty()) base else "$base · $feedback"
    }

    private fun toggleScanning() {
        commitEditsIfStopped()
        viewModel.updateSettings { it.copy(scanning = !it.scanning) }
    }

    /** Edits are only possible while stopped; push them into the model before anything else changes it. */
    private fun commitEditsIfStopped() {
        if (!viewModel.currentSettings.scanning) viewModel.replaceText(outputText())
    }

    private fun toggleLanguage(): Boolean = toggleSetting { settings ->
        val next = if (settings.language == OcrLanguage.KOREAN) OcrLanguage.LATIN else OcrLanguage.KOREAN
        settings.copy(language = next)
    }

    private fun toggleSetting(transform: (MainViewModel.Settings) -> MainViewModel.Settings): Boolean {
        viewModel.updateSettings(transform)
        return true
    }

    private fun withText(action: (String) -> Unit): Boolean {
        val text = outputText()
        if (text.isBlank()) toast(R.string.nothing_to_export) else action(text)
        return true
    }

    private fun outputText(): String = binding.output.text.toString()

    private fun ensureCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else requestCamera.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val analyzer = TextAnalyzer(
            executor = viewModel.analysisExecutor,
            recognizer = { viewModel.engine.recognizer(viewModel.currentSettings.language) },
            options = { viewModel.extractOptions },
            onFrame = viewModel::onFrame,
        )
        cameraBinder.start(analyzer)
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val MIME_TEXT = "text/plain"
    }
}

package com.woo.screenscan.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.woo.screenscan.camera.OcrEngine
import com.woo.screenscan.camera.OcrLanguage
import com.woo.screenscan.ocr.AccumulatorState
import com.woo.screenscan.ocr.ExtractOptions
import com.woo.screenscan.ocr.Frame
import com.woo.screenscan.ocr.FrameStability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    data class Settings(
        val autoMode: Boolean = true,
        val paused: Boolean = false,
        val language: OcrLanguage = OcrLanguage.KOREAN,
        val stripLineNumbers: Boolean = false,
    )

    val engine = OcrEngine()

    private val _state = MutableLiveData(AccumulatorState())
    val state: LiveData<AccumulatorState> = _state

    private val _settings = MutableLiveData(Settings())
    val settings: LiveData<Settings> = _settings

    private val _lastFrame = MutableLiveData<Frame>()
    val lastFrame: LiveData<Frame> = _lastFrame

    private val stateLock = Mutex()

    @Volatile
    private var latestFrame: Frame? = null

    // Only touched from the single analysis thread.
    private var pendingKey: List<String>? = null
    private var lastMergedKey: List<String>? = null

    val currentSettings: Settings get() = _settings.value ?: Settings()
    val extractOptions: ExtractOptions get() = ExtractOptions(currentSettings.stripLineNumbers)

    /** Called on the analysis thread for every recognized frame. */
    fun onFrame(frame: Frame) {
        latestFrame = frame
        _lastFrame.postValue(frame)
        val settings = currentSettings
        if (!settings.autoMode || settings.paused) return

        val key = FrameStability.key(frame)
        if (key.isEmpty()) {
            pendingKey = null
            return
        }
        val stable = FrameStability.isSame(pendingKey, key)
        pendingKey = key
        if (!stable || FrameStability.isSame(lastMergedKey, key)) return
        lastMergedKey = key
        mergeFrame(frame)
    }

    fun captureNow() {
        latestFrame?.let(::mergeFrame)
    }

    fun undo() = update { it.undo() }

    fun clear() = update { it.clear() }

    fun replaceText(text: String) = update { it.replaceWith(text) }

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.value = transform(currentSettings)
    }

    private fun mergeFrame(frame: Frame) {
        viewModelScope.launch {
            stateLock.withLock {
                val current = _state.value ?: AccumulatorState()
                val merged = withContext(Dispatchers.Default) { current.merge(frame.lines) }
                if (merged !== current) _state.value = merged
            }
        }
    }

    private fun update(transform: (AccumulatorState) -> AccumulatorState) {
        viewModelScope.launch {
            stateLock.withLock {
                val current = _state.value ?: AccumulatorState()
                val next = transform(current)
                lastMergedKey = null
                if (next !== current) _state.value = next
            }
        }
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}

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
import com.woo.screenscan.ocr.FrameMerger
import com.woo.screenscan.ocr.FrameStability
import com.woo.screenscan.ocr.FrameVerdict
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    /** What happened to the most recent frame, for the status line. */
    sealed interface Feedback {
        data class Added(val lines: Int) : Feedback
        data object NoOverlap : Feedback
        data object Unchanged : Feedback
        data class Rejected(val verdict: FrameVerdict, val confidence: Float) : Feedback
    }

    val engine = OcrEngine()

    /** Single thread for recognition callbacks; outlives Activity recreation. */
    val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val _state = MutableLiveData(AccumulatorState())
    val state: LiveData<AccumulatorState> = _state

    private val _settings = MutableLiveData(Settings())
    val settings: LiveData<Settings> = _settings

    private val _lastFrame = MutableLiveData<Frame>()
    val lastFrame: LiveData<Frame> = _lastFrame

    private val _feedback = MutableLiveData<Feedback>()
    val feedback: LiveData<Feedback> = _feedback

    private val stateLock = Mutex()

    @Volatile
    private var latestFrame: Frame? = null

    @Volatile
    private var lastMergedKey: List<String>? = null

    val currentSettings: Settings get() = _settings.value ?: Settings()
    val extractOptions: ExtractOptions get() = ExtractOptions(currentSettings.stripLineNumbers)

    /** Called on the analysis thread for every recognized frame. */
    fun onFrame(frame: Frame) {
        latestFrame = frame
        _lastFrame.postValue(frame)
        val settings = currentSettings
        if (!settings.autoMode || settings.paused) return

        if (!frame.quality.accepted) {
            _feedback.postValue(Feedback.Rejected(frame.quality.verdict, frame.quality.confidence))
            return
        }
        val key = FrameStability.key(frame)
        if (key.isEmpty() || FrameStability.isSame(lastMergedKey, key)) return
        lastMergedKey = key
        mergeFrame(frame, requireOverlap = true)
    }

    /** Manual capture always appends, even when nothing overlaps. */
    fun captureNow() {
        latestFrame?.let { mergeFrame(it, requireOverlap = false) }
    }

    /** Undo must not be re-applied by the still-visible scene, so the scene stays "handled". */
    fun undo() = update(rearm = false) { it.undo() }

    fun clear() = update(rearm = false) { it.clear() }

    fun replaceText(text: String) = update(rearm = true) { it.replaceWith(text) }

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.value = transform(currentSettings)
    }

    private fun mergeFrame(frame: Frame, requireOverlap: Boolean) {
        viewModelScope.launch {
            stateLock.withLock {
                val current = _state.value ?: AccumulatorState()
                val merged = withContext(Dispatchers.Default) { current.merge(frame.lines, requireOverlap) }
                if (merged.state !== current) _state.value = merged.state
                _feedback.value = when (val result = merged.result) {
                    is FrameMerger.Result.Appended -> Feedback.Added(result.added)
                    is FrameMerger.Result.NoOverlap -> Feedback.NoOverlap
                    is FrameMerger.Result.Unchanged -> Feedback.Unchanged
                }
            }
        }
    }

    private fun update(rearm: Boolean, transform: (AccumulatorState) -> AccumulatorState) {
        viewModelScope.launch {
            stateLock.withLock {
                val current = _state.value ?: AccumulatorState()
                val next = transform(current)
                lastMergedKey = if (rearm) null else latestFrame?.let(FrameStability::key)
                if (next !== current) _state.value = next
            }
        }
    }

    override fun onCleared() {
        engine.close()
        analysisExecutor.shutdown()
        super.onCleared()
    }
}

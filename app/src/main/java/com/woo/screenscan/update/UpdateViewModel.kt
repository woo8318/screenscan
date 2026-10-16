package com.woo.screenscan.update

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File

/** Drives the check → download → install flow and exposes it as a single state. */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface State {
        data object Idle : State
        data object UpToDate : State
        data class Available(val release: ReleaseInfo) : State
        data class Downloading(val percent: Int) : State
        data class Ready(val file: File) : State
        data class Failed(val silent: Boolean) : State
    }

    private val checker = UpdateChecker(REPO)
    private val installer = UpdateInstaller(application)

    private val _state = MutableLiveData<State>(State.Idle)
    val state: LiveData<State> = _state

    private var checkedAutomatically = false

    /** Silent check on launch; dev builds never nag. */
    fun checkOnLaunch(installedVersion: String) {
        if (checkedAutomatically || AppVersion.isDev(installedVersion)) return
        checkedAutomatically = true
        check(installedVersion, silent = true)
    }

    fun check(installedVersion: String, silent: Boolean) {
        viewModelScope.launch {
            _state.value = try {
                val release = checker.latest()
                when {
                    release == null -> if (silent) State.Idle else State.UpToDate
                    AppVersion.isNewer(release.version, installedVersion) -> State.Available(release)
                    else -> if (silent) State.Idle else State.UpToDate
                }
            } catch (error: Exception) {
                Log.w(TAG, "Update check failed", error)
                State.Failed(silent)
            }
        }
    }

    fun download(release: ReleaseInfo) {
        viewModelScope.launch {
            _state.value = State.Downloading(0)
            // Progress arrives via postValue from the IO thread, so the final state must be posted
            // too; a setValue here could be overwritten by a still-pending progress post.
            val result = try {
                val file = installer.download(release.apkUrl) { percent -> _state.postValue(State.Downloading(percent)) }
                State.Ready(file)
            } catch (error: Exception) {
                Log.w(TAG, "Update download failed", error)
                State.Failed(silent = false)
            }
            _state.postValue(result)
        }
    }

    fun install(file: File) {
        installer.install(file)
        _state.value = State.Idle
    }

    fun dismiss() {
        _state.value = State.Idle
    }

    private companion object {
        const val TAG = "UpdateViewModel"
        const val REPO = "woo8318/screenscan"
    }
}

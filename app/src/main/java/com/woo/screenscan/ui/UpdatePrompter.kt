package com.woo.screenscan.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.woo.screenscan.R
import com.woo.screenscan.update.UpdateViewModel

/** Renders update states as a dialog, toasts and a status line. */
class UpdatePrompter(
    private val context: Context,
    private val viewModel: UpdateViewModel,
    private val showStatus: (String?) -> Unit,
) {
    fun render(state: UpdateViewModel.State) {
        when (state) {
            UpdateViewModel.State.Idle -> showStatus(null)
            UpdateViewModel.State.UpToDate -> {
                toast(R.string.update_up_to_date)
                viewModel.dismiss()
            }
            is UpdateViewModel.State.Available -> {
                showStatus(null)
                promptUpdate(state)
            }
            is UpdateViewModel.State.Downloading ->
                showStatus(context.getString(R.string.update_downloading, state.percent))
            is UpdateViewModel.State.Ready -> {
                showStatus(null)
                viewModel.install(state.file)
            }
            is UpdateViewModel.State.Failed -> {
                showStatus(null)
                if (!state.silent) toast(R.string.update_failed)
                viewModel.dismiss()
            }
        }
    }

    private fun promptUpdate(state: UpdateViewModel.State.Available) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_available_title)
            .setMessage(context.getString(R.string.update_available_message, state.release.version, installedVersion(context)))
            .setPositiveButton(R.string.update_now) { _, _ -> viewModel.download(state.release) }
            .setNegativeButton(R.string.update_later) { _, _ -> viewModel.dismiss() }
            .setOnCancelListener { viewModel.dismiss() }
            .show()
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun installedVersion(context: Context): String {
            val manager = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(context.packageName, 0)
            }
            return info.versionName ?: "0.0.0-dev"
        }
    }
}

package com.woo.screenscan.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import com.woo.screenscan.R

/** Copy, share and save helpers for the captured text. */
class TextExporter(private val context: Context) {

    fun copy(text: String) {
        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    }

    fun share(text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType(MIME_TEXT)
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
    }

    /** Writes [text] to [uri]; returns false when the write fails. */
    fun save(uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } != null
    } catch (error: Exception) {
        Log.w(TAG, "Saving text failed", error)
        false
    }

    private companion object {
        const val TAG = "TextExporter"
        const val MIME_TEXT = "text/plain"
    }
}

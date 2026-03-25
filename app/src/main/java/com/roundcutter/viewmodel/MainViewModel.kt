package com.roundcutter.viewmodel

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.roundcutter.model.Clip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val current: Int, val total: Int) : ExportState()
    data class Done(val path: String, val count: Int) : ExportState()
    data class Error(val message: String) : ExportState()
}

class MainViewModel : ViewModel() {

    private val _clips = MutableStateFlow<List<Clip>>(emptyList())
    val clips: StateFlow<List<Clip>> = _clips.asStateFlow()

    private val _inPoint = MutableStateFlow<Long?>(null)
    val inPoint: StateFlow<Long?> = _inPoint.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** Persists across config changes (rotation) */
    private val _videoUri = MutableStateFlow<Uri?>(null)
    val videoUri: StateFlow<Uri?> = _videoUri.asStateFlow()

    fun setVideoUri(uri: Uri) {
        _videoUri.value = uri
    }

    fun setInPoint(ms: Long) {
        _inPoint.value = ms
    }

    fun setOutPoint(ms: Long) {
        val inMs = _inPoint.value ?: return
        if (ms <= inMs) return
        val roundNumber = _clips.value.size + 1
        val clip = Clip(
            id = UUID.randomUUID(),
            name = "Round $roundNumber",
            startMs = inMs,
            endMs = ms
        )
        _clips.update { it + clip }
        _inPoint.value = null
    }

    fun removeClip(id: UUID) {
        _clips.update { list ->
            list.filter { it.id != id }
                .mapIndexed { index, clip -> clip.copy(name = "Round ${index + 1}") }
        }
    }

    fun exportAll(context: Context, videoUri: Uri) {
        val clipsList = _clips.value
        if (clipsList.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "RoundCutter"
            )
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                _exportState.value = ExportState.Error("Cannot create output directory: ${outputDir.absolutePath}")
                return@launch
            }

            _exportState.value = ExportState.Exporting(0, clipsList.size)

            val pfd = try {
                context.contentResolver.openFileDescriptor(videoUri, "r")
            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Cannot open video: ${e.message}")
                return@launch
            }

            if (pfd == null) {
                _exportState.value = ExportState.Error("Cannot open video file")
                return@launch
            }

            try {
                val inputPath = "/proc/self/fd/${pfd.fd}"
                var exportSuccess = true

                for ((index, clip) in clipsList.withIndex()) {
                    _exportState.value = ExportState.Exporting(index + 1, clipsList.size)

                    val safeName = clip.name.replace(" ", "_")
                    val outputFile = File(outputDir, "${safeName}_${System.currentTimeMillis()}.mp4")
                    val startSec = clip.startMs / 1000.0
                    val endSec = clip.endMs / 1000.0

                    val command = "-i $inputPath -ss $startSec -to $endSec -c copy -avoid_negative_ts make_zero ${outputFile.absolutePath}"
                    val session = FFmpegKit.execute(command)

                    if (!ReturnCode.isSuccess(session.returnCode)) {
                        _exportState.value = ExportState.Error("Failed to export ${clip.name}")
                        exportSuccess = false
                        break
                    }

                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(outputFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                }

                if (exportSuccess) {
                    _exportState.value = ExportState.Done(outputDir.absolutePath, clipsList.size)
                }
            } finally {
                pfd.close()
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
}

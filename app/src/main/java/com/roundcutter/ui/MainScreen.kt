package com.roundcutter.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.roundcutter.model.Clip
import com.roundcutter.viewmodel.ExportState
import com.roundcutter.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val clips by viewModel.clips.collectAsState()
    val inPoint by viewModel.inPoint.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var playerPosition by remember { mutableLongStateOf(0L) }
    var playerDuration by remember { mutableLongStateOf(1L) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }
    val lastSeekTime = remember { longArrayOf(0L) }

    val player = remember {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Poll player position every 100ms
    LaunchedEffect(player) {
        while (true) {
            val pos = player.currentPosition
            val dur = player.duration.takeIf { it > 0L } ?: 1L
            playerPosition = pos
            playerDuration = dur
            if (!isDragging) {
                sliderValue = pos.toFloat()
            }
            delay(100)
        }
    }

    // Handle export state changes
    LaunchedEffect(exportState) {
        when (val state = exportState) {
            is ExportState.Done -> {
                snackbarHostState.showSnackbar("Saved ${state.count} clips to Movies/RoundCutter/")
                viewModel.resetExportState()
            }
            is ExportState.Error -> {
                snackbarHostState.showSnackbar("Error: ${state.message}")
                viewModel.resetExportState()
            }
            else -> {}
        }
    }

    // Runtime permission request
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result noted */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Video picker
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            videoUri = it
            player.setMediaItem(MediaItem.fromUri(it))
            player.prepare()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        if (videoUri == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                    Text("Select Video")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // ── Video Player (top ~40%) ───────────────────────────────
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clickable {
                            if (player.isPlaying) player.pause() else player.play()
                        }
                )

                // ── Seek / Scrub bar ──────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { value ->
                            sliderValue = value
                            isDragging = true
                            val now = System.currentTimeMillis()
                            if (now - lastSeekTime[0] >= 50L) {
                                lastSeekTime[0] = now
                                player.seekTo(value.toLong())
                            }
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            player.seekTo(sliderValue.toLong())
                        },
                        valueRange = 0f..playerDuration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Clip-marker strip below the slider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            clips.forEach { clip ->
                                val startFrac = clip.startMs.toFloat() / playerDuration
                                val endFrac = clip.endMs.toFloat() / playerDuration
                                drawRect(
                                    color = Color(0xFFFF6B00),
                                    topLeft = Offset(startFrac * w, 0f),
                                    size = Size((endFrac - startFrac) * w, h)
                                )
                            }
                            inPoint?.let { ip ->
                                val x = (ip.toFloat() / playerDuration) * w
                                drawLine(
                                    color = Color(0xFF00EE44),
                                    start = Offset(x, 0f),
                                    end = Offset(x, h),
                                    strokeWidth = 3f
                                )
                            }
                        }
                    }
                }

                // ── Time display + frame-step controls ────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playerPosition),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { player.seekTo((player.currentPosition - 1000L).coerceAtLeast(0L)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("◀1s", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(
                            onClick = { player.seekTo((player.currentPosition - 33L).coerceAtLeast(0L)) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("◀F", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(
                            onClick = { player.seekTo(player.currentPosition + 33L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("F▶", style = MaterialTheme.typography.labelSmall) }
                        FilledTonalButton(
                            onClick = { player.seekTo(player.currentPosition + 1000L) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) { Text("1s▶", style = MaterialTheme.typography.labelSmall) }
                    }
                }

                // ── Mark In / Out buttons ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.setInPoint(player.currentPosition) },
                        colors = if (inPoint != null)
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF00AA44))
                        else
                            ButtonDefaults.buttonColors()
                    ) { Text("SET IN") }

                    Button(
                        onClick = { viewModel.setOutPoint(player.currentPosition) },
                        enabled = inPoint != null
                    ) { Text("SET OUT") }

                    if (inPoint != null) {
                        Text(
                            text = "IN: ${formatTime(inPoint!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // ── Clips list ────────────────────────────────────────────
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(clips, key = { it.id }) { clip ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    viewModel.removeClip(clip.id)
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFCC0000))
                                        .padding(end = 16.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Text("Delete", color = Color.White)
                                }
                            }
                        ) {
                            ClipRow(
                                clip = clip,
                                onDelete = { viewModel.removeClip(clip.id) },
                                onClick = { player.seekTo(clip.startMs) }
                            )
                        }
                        HorizontalDivider()
                    }
                }

                // ── Export button (sticky bottom) ─────────────────────────
                val isExporting = exportState is ExportState.Exporting
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isExporting) {
                        val progress = exportState as ExportState.Exporting
                        Text(
                            text = "Exporting ${progress.current}/${progress.total}...",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress.current.toFloat() / progress.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { videoUri?.let { viewModel.exportAll(context, it) } },
                        enabled = clips.isNotEmpty() && !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export All (${clips.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipRow(clip: Clip, onDelete: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = clip.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "${formatTimeShort(clip.startMs)} → ${formatTimeShort(clip.endMs)}",
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = formatDuration(clip.durationMs),
            modifier = Modifier.weight(0.8f),
            style = MaterialTheme.typography.bodySmall
        )
        IconButton(onClick = onDelete) {
            Text("✕")
        }
    }
}

private fun formatTime(ms: Long): String {
    val h = ms / 3_600_000L
    val m = (ms % 3_600_000L) / 60_000L
    val s = (ms % 60_000L) / 1_000L
    val ms3 = ms % 1_000L
    return "%02d:%02d:%02d.%03d".format(h, m, s, ms3)
}

private fun formatTimeShort(ms: Long): String {
    val m = ms / 60_000L
    val s = (ms % 60_000L) / 1_000L
    return "%02d:%02d".format(m, s)
}

private fun formatDuration(ms: Long): String {
    val m = ms / 60_000L
    val s = (ms % 60_000L) / 1_000L
    return "%d:%02d".format(m, s)
}

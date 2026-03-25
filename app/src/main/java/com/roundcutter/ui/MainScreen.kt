package com.roundcutter.ui

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val clips by viewModel.clips.collectAsState()
    val inPoint by viewModel.inPoint.collectAsState()
    val exportState by viewModel.exportState.collectAsState()

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var playerPosition by remember { mutableLongStateOf(0L) }
    var playerDuration by remember { mutableLongStateOf(1L) }
    var isDragging by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    val player = remember {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setSeekParameters(SeekParameters.EXACT)
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Poll player position
    LaunchedEffect(player) {
        while (true) {
            val dur = player.duration.takeIf { it > 0L } ?: 1L
            playerPosition = player.currentPosition
            playerDuration = dur
            if (!isDragging) {
                scrubPosition = playerPosition.toFloat() / playerDuration.toFloat()
            }
            delay(100)
        }
    }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* noted */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

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
            // ---------- Shared composable lambdas ----------

            val videoPlayer: @Composable (Modifier) -> Unit = { mod ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = mod.clickable {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                )
            }

            val controls: @Composable (Modifier) -> Unit = { mod ->
                Column(modifier = mod) {
                    // ── Adaptive scrub bar ─────────────────────────
                    AdaptiveScrubBar(
                        position = scrubPosition,
                        duration = playerDuration,
                        clips = clips,
                        inPoint = inPoint,
                        onScrubStart = { isDragging = true },
                        onScrubMove = { fraction ->
                            scrubPosition = fraction.coerceIn(0f, 1f)
                            player.seekTo((scrubPosition * playerDuration).toLong())
                        },
                        onScrubEnd = {
                            isDragging = false
                            player.seekTo((scrubPosition * playerDuration).toLong())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // ── Time display ──────────────────────────────
                    Text(
                        text = formatTime(playerPosition),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(4.dp))

                    // ── Jump controls ─────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { player.seekTo((player.currentPosition - 180_000L).coerceAtLeast(0L)) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) { Text("◀3m", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { player.seekTo((player.currentPosition - 1000L).coerceAtLeast(0L)) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) { Text("◀1s", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { player.seekTo((player.currentPosition - 33L).coerceAtLeast(0L)) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) { Text("◀F", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { player.seekTo(player.currentPosition + 33L) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) { Text("F▶", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = { player.seekTo(player.currentPosition + 1000L) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) { Text("1s▶", style = MaterialTheme.typography.labelSmall) }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalButton(
                            onClick = {
                                player.seekTo(
                                    (player.currentPosition + 180_000L)
                                        .coerceAtMost(playerDuration)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                        ) { Text("3m▶", style = MaterialTheme.typography.labelSmall) }
                    }

                    Spacer(Modifier.height(4.dp))

                    // ── Mark In / Out ─────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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

                    Spacer(Modifier.height(4.dp))

                    // ── Clips list ────────────────────────────────
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
                                    ) { Text("Delete", color = Color.White) }
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

                    // ── Export ─────────────────────────────────────
                    val isExporting = exportState is ExportState.Exporting
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isExporting) {
                            val progress = exportState as ExportState.Exporting
                            Text(
                                "Exporting ${progress.current}/${progress.total}...",
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
                        ) { Text("Export All (${clips.size})") }
                    }
                }
            }

            // ---------- Layout switch ----------

            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    videoPlayer(
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(16f / 9f)
                    )
                    controls(
                        Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    videoPlayer(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                    controls(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Adaptive Scrub Bar
//  - Normal drag: full-speed scrubbing across the timeline
//  - Slow/hovering drag: automatically enters fine mode (10×
//    precision) so tiny finger movements = small time changes
//  - Visual indicator shows current mode (FINE / SCRUB)
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AdaptiveScrubBar(
    position: Float,           // 0..1 fraction
    duration: Long,            // total ms
    clips: List<Clip>,
    inPoint: Long?,
    onScrubStart: () -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFineMode by remember { mutableStateOf(false) }

    // Velocity tracking state — reset per drag
    val velocitySamples = remember { mutableListOf<Float>() }
    val FINE_THRESHOLD_PX = 4f          // avg px/sample below this → fine mode
    val FINE_SENSITIVITY = 0.1f         // 10× slower in fine mode
    val SAMPLE_WINDOW = 6               // last N samples for avg velocity

    Column(modifier = modifier) {
        // Mode label
        if (isFineMode) {
            Text(
                text = "FINE SCRUB · 10× precision",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF00EE44),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                textAlign = TextAlign.Center
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            velocitySamples.clear()
                            isFineMode = false
                            onScrubStart()
                        },
                        onDragEnd = {
                            velocitySamples.clear()
                            isFineMode = false
                            onScrubEnd()
                        },
                        onDragCancel = {
                            velocitySamples.clear()
                            isFineMode = false
                            onScrubEnd()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()

                            // Track velocity
                            velocitySamples.add(abs(dragAmount))
                            if (velocitySamples.size > SAMPLE_WINDOW) {
                                velocitySamples.removeAt(0)
                            }

                            // Compute average speed
                            val avgSpeed = if (velocitySamples.size >= 3) {
                                velocitySamples.average().toFloat()
                            } else {
                                Float.MAX_VALUE // not enough data yet → normal mode
                            }

                            // Switch modes based on drag speed
                            isFineMode = avgSpeed < FINE_THRESHOLD_PX

                            // Apply sensitivity scaling
                            val scale = if (isFineMode) FINE_SENSITIVITY else 1f
                            val barWidth = size.width.toFloat()
                            val deltaFraction = (dragAmount / barWidth) * scale
                            val newPosition = (position + deltaFraction).coerceIn(0f, 1f)

                            onScrubMove(newPosition)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Background track
                drawRect(Color(0xFF2A2A2A))

                // Clip markers (orange segments)
                clips.forEach { clip ->
                    val startFrac = clip.startMs.toFloat() / duration
                    val endFrac = clip.endMs.toFloat() / duration
                    drawRect(
                        color = Color(0x66FF6B00),
                        topLeft = Offset(startFrac * w, 0f),
                        size = Size((endFrac - startFrac) * w, h)
                    )
                }

                // In-point marker (green line)
                inPoint?.let { ip ->
                    val x = (ip.toFloat() / duration) * w
                    drawLine(
                        color = Color(0xFF00EE44),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 3f
                    )
                }

                // Playhead (white line + handle)
                val px = position * w
                drawLine(
                    color = Color.White,
                    start = Offset(px, 0f),
                    end = Offset(px, h),
                    strokeWidth = 3f
                )
                drawCircle(
                    color = if (isFineMode) Color(0xFF00EE44) else Color.White,
                    radius = 8f,
                    center = Offset(px, h / 2f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════

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

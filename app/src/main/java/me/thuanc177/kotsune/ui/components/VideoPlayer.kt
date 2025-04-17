package me.thuanc177.kotsune.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

// Define TAG constant for debugging
private const val TAG = "VideoPlayerDebug"

// Extension function to find activity from context
fun Context.findActivity(): Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@UnstableApi
@Composable
fun VideoPlayer(
    streamUrl: String,
    title: String = "",
    subtitleUrls: List<Pair<String, String>> = emptyList(),
    qualityOptions: List<Pair<String, String>> = emptyList(),
    onBackPress: () -> Unit,
    customHeaders: Map<String, String> = mapOf(),
    initialPosition: Long = 0L,
    onPositionChanged: (Long) -> Unit = {},
    onServerError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()

    // State variables
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playWhenReady by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var bufferedProgress by remember { mutableStateOf(0f) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var selectedSubtitle by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSeekingForward by remember { mutableStateOf(false) }
    var isSeekingBackward by remember { mutableStateOf(false) }
    var autoSwitchCountdown by remember { mutableStateOf(3) }

    // Track selector for handling subtitles
    val trackSelector = remember { DefaultTrackSelector(context) }

    // Manage controls visibility
    val hideControlsJob = remember { mutableStateOf<Job?>(null) }

    fun resetControlsTimer() {
        hideControlsJob.value?.cancel()
        hideControlsJob.value = coroutineScope.launch {
            delay(5000)
            controlsVisible = false
        }
    }

    // Reset the controls timer when they become visible
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            resetControlsTimer()
        }
    }

    // Configuration change detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Keep screen on during playback
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Handle countdown for auto server switch
    LaunchedEffect(playerError, autoSwitchCountdown) {
        if (playerError != null && autoSwitchCountdown > 0) {
            delay(1000)
            autoSwitchCountdown -= 1

            if (autoSwitchCountdown == 0) {
                // Auto switch to next server
                onServerError()
                playerError = null
                autoSwitchCountdown = 3
            }
        }
    }

    // Handle system UI visibility in fullscreen
    DisposableEffect(isFullScreen) {
        if (isFullScreen) {
            activity?.window?.decorView?.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        onDispose {
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    // Back handler for fullscreen
    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    // Create and prepare player
    LaunchedEffect(streamUrl) {
        if (player == null || streamUrl != player?.currentMediaItem?.localConfiguration?.uri.toString()) {
            player?.release()

            try {
                player = createPlayer(
                    context = context,
                    url = streamUrl,
                    trackSelector = trackSelector,
                    startPosition = initialPosition,
                    subtitleUrls = subtitleUrls,
                    customHeaders = customHeaders,
                    onPlayerError = { error ->
                        playerError = "Error loading media. Switching servers in ${autoSwitchCountdown}s"
                        autoSwitchCountdown = 3
                    }
                )
                player?.prepare()
                if (playWhenReady) {
                    player?.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating player", e)
                playerError = "Error loading media. Switching servers in ${autoSwitchCountdown}s"
                autoSwitchCountdown = 3
            }
        }
    }

    // Update progress and buffer state
    LaunchedEffect(player) {
        while (isActive && player != null) {
            delay(500)
            player?.let { exoPlayer ->
                if (!exoPlayer.isPlayingAd) {
                    currentTime = exoPlayer.currentPosition
                    onPositionChanged(currentTime)
                    totalDuration = exoPlayer.duration.coerceAtLeast(1)
                    progress = (currentTime.toFloat() / totalDuration).coerceIn(0f, 1f)
                    bufferedProgress = (exoPlayer.bufferedPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Handle lifecycle events
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hideControlsJob.value?.cancel()
            currentTime = player?.currentPosition ?: 0
            player?.release()
            player = null
        }
    }

    // Adjust fullscreen based on orientation automatically
    LaunchedEffect(isLandscape) {
        isFullScreen = isLandscape
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .run {
                if (isFullScreen) {
                    fillMaxSize()
                } else {
                    aspectRatio(16f / 9f)
                }
            }
            .background(Color.Black)
    ) {
        // Main video view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Use RESIZE_MODE_FIT for consistent aspect ratio
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = null
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        // Show/hide controls on tap
                        controlsVisible = !controlsVisible
                        if (controlsVisible) resetControlsTimer()
                    }
                )
        )

        // Error message
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = playerError ?: "An error occurred",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Button(
                        onClick = {
                            onServerError()
                            playerError = null
                            autoSwitchCountdown = 3
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Switch Server Now")
                    }
                }
            }
        }

        // Double tap areas for seeking (only active when no error is showing)
        if (playerError == null) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Left side - rewind
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    player?.let { exoPlayer ->
                                        isSeekingBackward = true
                                        val newPosition = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                        exoPlayer.seekTo(newPosition)
                                        coroutineScope.launch {
                                            delay(500)
                                            isSeekingBackward = false
                                        }
                                    }
                                }
                            )
                        }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                // Show/hide controls on tap
                                controlsVisible = !controlsVisible
                                if (controlsVisible) resetControlsTimer()
                            }
                        )
                )

                // Right side - forward
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    player?.let { exoPlayer ->
                                        isSeekingForward = true
                                        val newPosition = (exoPlayer.currentPosition + 10000).coerceAtMost(totalDuration)
                                        exoPlayer.seekTo(newPosition)
                                        coroutineScope.launch {
                                            delay(500)
                                            isSeekingForward = false
                                        }
                                    }
                                }
                            )
                        }
                )
            }
        }

        // Seeking indicators
        if (isSeekingForward) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        if (isSeekingBackward) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Center play/pause button (always visible when controls are visible)
        if (controlsVisible && playerError == null) {
            IconButton(
                onClick = {
                    playWhenReady = !playWhenReady
                    if (playWhenReady) {
                        player?.play()
                    } else {
                        player?.pause()
                    }
                    resetControlsTimer()
                },
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .align(Alignment.Center)
            ) {
                Icon(
                    imageVector = if (playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playWhenReady) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible && playerError == null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                // Top controls - simplified, removed title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .padding(8.dp), // Reduced padding
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackPress,
                        modifier = Modifier.size(36.dp) // Smaller button
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.padding(end = 4.dp) // Reduced padding
                    ) {
                        Text(
                            text = "${formatTime(currentTime)} / ${formatTime(totalDuration)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall // Smaller text
                        )

                        IconButton(
                            onClick = { showSettingsMenu = true },
                            modifier = Modifier.size(36.dp) // Smaller button
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom controls - moved closer to the bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 4.dp) // Reduced padding
                ) {
                    // Progress bar with buffering indicator - more compact
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp) // Smaller height
                    ) {
                        // Background track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp) // Thinner track
                                .align(Alignment.Center)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        // Buffer indicator
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferedProgress)
                                .height(3.dp) // Thinner track
                                .align(Alignment.CenterStart)
                                .background(Color.White.copy(alpha = 0.5f))
                        )

                        // Actual progress slider
                        Slider(
                            value = progress,
                            onValueChange = { newProgress ->
                                progress = newProgress
                                controlsVisible = true
                                resetControlsTimer()
                            },
                            onValueChangeFinished = {
                                player?.seekTo((progress * totalDuration).toLong())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.Transparent
                            )
                        )
                    }

                    // Bottom row with playback controls - more compact
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp), // Reduced padding
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                isFullScreen = !isFullScreen
                            },
                            modifier = Modifier.size(36.dp) // Smaller button
                        ) {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullScreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Settings dialog
        if (showSettingsMenu) {
            Dialog(onDismissRequest = {
                showSettingsMenu = false
                resetControlsTimer()
            }) {
                Surface(
                    modifier = Modifier
                        .wrapContentHeight()
                        .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Quality selection
                        if (qualityOptions.isNotEmpty()) {
                            Text(
                                text = "Quality",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            LazyRow(
                                contentPadding = PaddingValues(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(qualityOptions) { (quality, url) ->
                                    FilterChip(
                                        selected = selectedQuality == quality,
                                        onClick = {
                                            if (selectedQuality != quality) {
                                                selectedQuality = quality
                                                player?.let { exoPlayer ->
                                                    val position = exoPlayer.currentPosition
                                                    playWhenReady = !exoPlayer.isPlaying
                                                    player?.release()
                                                    player = createPlayer(
                                                        context = context,
                                                        url = url,
                                                        trackSelector = trackSelector,
                                                        startPosition = position,
                                                        subtitleUrls = subtitleUrls,
                                                        customHeaders = customHeaders
                                                    )
                                                    player?.prepare()
                                                    if (playWhenReady) {
                                                        player?.play()
                                                    }
                                                }
                                            }
                                        },
                                        label = { Text(quality) },
                                        leadingIcon = if (selectedQuality == quality) {
                                            { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Subtitle selection
                        if (subtitleUrls.isNotEmpty()) {
                            Text(
                                text = "Subtitles",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 16.dp),
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = selectedSubtitle == null,
                                        onClick = {
                                            selectedSubtitle = null
                                            // Disable subtitles
                                            trackSelector.parameters = trackSelector.buildUponParameters()
                                                .setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                                .build()
                                        },
                                        label = { Text("Off") },
                                        leadingIcon = if (selectedSubtitle == null) {
                                            { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                        } else null
                                    )
                                }

                                items(subtitleUrls) { (language, url) ->
                                    FilterChip(
                                        selected = selectedSubtitle == language,
                                        onClick = {
                                            selectedSubtitle = language
                                            // Enable subtitles by recreating player with the subtitle
                                            player?.let { exoPlayer ->
                                                val position = exoPlayer.currentPosition
                                                playWhenReady = exoPlayer.isPlaying
                                                player?.release()
                                                player = createPlayer(
                                                    context = context,
                                                    url = streamUrl,
                                                    trackSelector = trackSelector,
                                                    startPosition = position,
                                                    subtitleUrls = listOf(language to url),
                                                    customHeaders = customHeaders
                                                )
                                                player?.prepare()
                                                if (playWhenReady) {
                                                    player?.play()
                                                }
                                            }
                                        },
                                        label = { Text(language) },
                                        leadingIcon = if (selectedSubtitle == language) {
                                            { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                        } else null
                                    )
                                }
                            }
                        }

                        // Playback speed
                        Text(
                            text = "Playback Speed",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyRow(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            items(speeds) { speed ->
                                FilterChip(
                                    selected = playbackSpeed == speed,
                                    onClick = {
                                        playbackSpeed = speed
                                        player?.setPlaybackSpeed(speed)
                                    },
                                    label = { Text(if (speed == 1.0f) "Normal" else "${speed}x") },
                                    leadingIcon = if (playbackSpeed == speed) {
                                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                                    } else null
                                )
                            }
                        }

                        // Close button
                        Button(
                            onClick = {
                                showSettingsMenu = false
                                resetControlsTimer()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun SettingsMenu(
    qualityOptions: List<Pair<String, String>>,
    subtitleOptions: List<Pair<String, String>>,
    player: ExoPlayer?,
    currentQuality: String,
    currentSubtitle: String?,
    currentPlaybackSpeed: Float,
    onQualitySelected: (String) -> Unit,
    onSubtitleSelected: (String?) -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Quality section
                if (qualityOptions.isNotEmpty()) {
                    Text(
                        text = "Quality",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(qualityOptions) { (quality, _) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onQualitySelected(quality) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = quality,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                if (quality == currentQuality) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                }

                // Subtitles section
                if (subtitleOptions.isNotEmpty()) {
                    Text(
                        text = "Subtitles",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSubtitleSelected(null) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Off",
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                if (currentSubtitle == null) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        items(subtitleOptions) { (language, _) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSubtitleSelected(language) }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = language,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                if (language == currentSubtitle) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                }

                // Playback speed section
                Text(
                    text = "Playback Speed",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    val speedOptions = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

                    items(speedOptions) { speed ->
                        FilterChip(
                            selected = speed == currentPlaybackSpeed,
                            onClick = { onPlaybackSpeedChanged(speed) },
                            label = {
                                Text(
                                    text = if (speed == 1.0f) "Normal" else "${speed}x",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = if (speed == currentPlaybackSpeed) {
                                { Icon(Icons.Default.Check, "Selected") }
                            } else null
                        )
                    }
                }

                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// Extension functions for ExoPlayer
private fun ExoPlayer.seekForward() {
    val newPosition = this.currentPosition + 10.seconds.inWholeMilliseconds
    if (newPosition < (this.duration ?: 0)) {
        this.seekTo(newPosition)
    }
}

private fun ExoPlayer.seekBack() {
    val newPosition = (this.currentPosition - 10.seconds.inWholeMilliseconds)
        .coerceAtLeast(0)
    this.seekTo(newPosition)
}

@UnstableApi
private fun createPlayer(
    context: Context,
    url: String,
    trackSelector: DefaultTrackSelector,
    startPosition: Long = 0,
    subtitleUrls: List<Pair<String, String>> = emptyList(),
    onPlayerError: (PlaybackException) -> Unit = {},
    customHeaders: Map<String, String> = mapOf()
): ExoPlayer {
    Log.d(TAG, "Creating player with URL: $url")
    Log.d(TAG, "Using custom headers: $customHeaders")

    try {
        // Create player with improved error handling
        val player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .build()

        // Create data source factory with custom headers
        val dataSourceFactory = DefaultDataSource.Factory(context).apply {
            // Add headers to all requests
            val defaultHttpDataSourceFactory =
                this as? androidx.media3.datasource.DefaultHttpDataSource.Factory
            defaultHttpDataSourceFactory?.setDefaultRequestProperties(customHeaders)
            defaultHttpDataSourceFactory?.setConnectTimeoutMs(30000) // 30 seconds
            defaultHttpDataSourceFactory?.setReadTimeoutMs(30000) // 30 seconds
            Log.d(TAG, "Created data source factory with headers: $customHeaders")
        }

        // Create media source based on URL type
        val mediaSource = when {
            url.endsWith(".m3u8") -> {
                Log.d(TAG, "Creating HLS media source for m3u8 stream")
                HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            else -> {
                Log.d(TAG, "Creating Progressive media source")
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
        }

        // Add subtitle tracks if available
        if (subtitleUrls.isNotEmpty()) {
            val mediaSources = mutableListOf<MediaSource>(mediaSource)
            subtitleUrls.forEach { (language, subtitleUrl) ->
                try {
                    val subtitleSource = MediaItem.SubtitleConfiguration.Builder(
                        Uri.parse(subtitleUrl)
                    )
                        .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(language)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        .setSubtitleConfigurations(listOf(subtitleSource))
                        .build()

                    player.setMediaItem(mediaItem)
                    Log.d(TAG, "Added subtitle track: $language")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding subtitle: ${e.message}")
                }
            }

            if (mediaSources.size > 1) {
                val mediaSourceFactory = object : MediaSource.Factory {
                    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                        return androidx.media3.exoplayer.source.MergingMediaSource(
                            *mediaSources.toTypedArray()
                        )
                    }

                    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
                        TODO("Not yet implemented")
                    }

                    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
                        TODO("Not yet implemented")
                    }

                    override fun getSupportedTypes(): IntArray {
                        return intArrayOf(C.CONTENT_TYPE_OTHER)
                    }
                }

                val mergedMediaSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                player.setMediaSource(mergedMediaSource)
            } else {
                player.setMediaSource(mediaSource)
            }
        } else {
            player.setMediaSource(mediaSource)
        }

        // Add error listener
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}")
                error.cause?.let {
                    Log.e(TAG, "Caused by: ${it.javaClass.simpleName}: ${it.message}")
                    if (it is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        Log.e(TAG, "HTTP Error details: ${it.responseCode}, ${it.responseMessage}")
                    }
                }
                onPlayerError(error)
            }

            override fun onPlaybackStateChanged(state: Int) {
                val stateString = when (state) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Player state changed to: $stateString")
            }
        })

        // Set initial position if needed
        if (startPosition > 0) {
            Log.d(TAG, "Setting initial position to: $startPosition ms")
            player.seekTo(startPosition)
        }

        return player
    } catch (e: Exception) {
        Log.e(TAG, "Error creating player", e)
        throw e
    }
}
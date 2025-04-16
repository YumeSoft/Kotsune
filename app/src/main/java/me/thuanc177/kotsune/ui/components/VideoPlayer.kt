package me.thuanc177.kotsune.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.text.toFloat
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
    subtitleUrls: List<Pair<String, String>> = emptyList(),
    qualityOptions: List<Pair<String, String>> = emptyList(),
    onBackPress: () -> Unit,
    customHeaders: Map<String, String> = mapOf(),
    initialPosition: Long = 0L,
    onPositionChanged: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // State variables
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playWhenReady by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    val hideControlsTimer = remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var bufferedProgress by remember { mutableStateOf(0f) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var selectedSubtitle by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Track selector for handling subtitles
    val trackSelector = remember { DefaultTrackSelector(context) }

    // Configuration change listener
    val configuration = LocalConfiguration.current
    configuration.screenHeightDp.dp
    configuration.screenWidthDp.dp

    // Handle orientation changes
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Adjust fullscreen based on orientation automatically
    LaunchedEffect(isLandscape) {
        isFullScreen = isLandscape
    }

    LaunchedEffect(player) {
        while (isActive && player != null) {
            delay(1000)
            player?.let { exoPlayer ->
                if (exoPlayer.isPlaying) {
                    currentTime = exoPlayer.currentPosition
                    totalDuration = exoPlayer.duration.coerceAtLeast(1)
                    progress = (currentTime.toFloat() / totalDuration).coerceIn(0f, 1f)
                    bufferedProgress = (exoPlayer.bufferedPosition.toFloat() / totalDuration).coerceIn(0f, 1f)

                    // Report position back
                    onPositionChanged(exoPlayer.currentPosition)
                }
            }
        }
    }

    LaunchedEffect(streamUrl) {
        // Don't recreate player unnecessarily
        if (player == null || streamUrl != player?.currentMediaItem?.localConfiguration?.uri.toString()) {
            player?.release()

            player = createPlayer(
                context = context,
                url = streamUrl,
                trackSelector = trackSelector,
                startPosition = initialPosition, // Use the initial position
                subtitleUrls = subtitleUrls,
                customHeaders = customHeaders
            )
            player?.prepare()
            player?.play()
        }
    }

    // Dispose player when leaving the screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player?.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                player?.play()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hideControlsTimer.value?.cancel()
            player?.release()
            player = null
        }
    }

    // Update progress and time periodically
    LaunchedEffect(player) {
        while (isActive && player != null) {
            delay(1000)
            player?.let { exoPlayer ->
                if (exoPlayer.isPlaying) {
                    currentTime = exoPlayer.currentPosition
                    totalDuration = exoPlayer.duration.coerceAtLeast(1)
                    progress = (currentTime.toFloat() / totalDuration).coerceIn(0f, 1f)
                    bufferedProgress = (exoPlayer.bufferedPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
                }
            }
        }
    }

    // Reset controls visibility timer when controls are shown
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            hideControlsTimer.value?.cancel()
            hideControlsTimer.value = coroutineScope.launch {
                delay(5000)
                controlsVisible = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .run {
                if (isFullScreen) {
                    fillMaxSize()
                        .systemBarsPadding() // Handle system bars in fullscreen
                } else {
                    aspectRatio(16f / 9f)
                }
            }
            .background(Color.Black)
            .clickable {
                controlsVisible = !controlsVisible
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                // Update player if needed
                view.player = player

                // Handle resize mode based on fullscreen state
                view.resizeMode = if (isFullScreen) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                // Top controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPress) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = formatTime(currentTime) + " / " + formatTime(totalDuration),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    IconButton(
                        onClick = { showSettingsMenu = true }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }

                // Center play/pause button
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            player?.let {
                                if (it.isPlaying) {
                                    it.pause()
                                } else {
                                    it.play()
                                }
                                playWhenReady = it.isPlaying
                            }
                        },
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (player?.isPlaying == true)
                                Icons.Default.Pause
                            else
                                Icons.Default.PlayArrow,
                            contentDescription = if (player?.isPlaying == true) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    // Progress bar with buffering indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp) // Make the touch target larger
                    ) {
                        // Buffered progress
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(bufferedProgress)
                                .height(4.dp)
                                .align(Alignment.Center)
                                .background(Color.White.copy(alpha = 0.5f))
                        )

                        // Actual slider for seeking
                        Slider(
                            value = progress,
                            onValueChange = { newProgress ->
                                progress = newProgress
                            },
                            onValueChangeFinished = {
                                player?.seekTo((totalDuration * progress).toLong())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom row with controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Playback controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    player?.seekBack()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = {
                                    player?.seekForward()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White
                                )
                            }
                        }

                        // Right-side controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Fullscreen button - moved to bottom right
                            IconButton(
                                onClick = {
                                    isFullScreen = !isFullScreen
                                    // Toggle device orientation if needed
                                    val activity = context.findActivity() as? Activity
                                    activity?.requestedOrientation = if (isFullScreen) {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen)
                                        Icons.Default.FullscreenExit
                                    else
                                        Icons.Default.Fullscreen,
                                    contentDescription = if (isFullScreen)
                                        "Exit fullscreen"
                                    else
                                        "Enter fullscreen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings menu
        if (showSettingsMenu) {
            SettingsMenu(
                qualityOptions = qualityOptions,
                subtitleOptions = subtitleUrls,
                player = player,
                currentQuality = selectedQuality,
                onQualitySelected = { quality ->
                    selectedQuality = quality
                    coroutineScope.launch {
                        val selectedQualityUrl = qualityOptions.find { it.first == quality }?.second
                        if (selectedQualityUrl != null && selectedQualityUrl != streamUrl) {
                            val currentPosition = player?.currentPosition ?: 0
                            player?.pause()
                            player?.release()

                            player = createPlayer(
                                context = context,
                                url = selectedQualityUrl,
                                trackSelector = trackSelector,
                                startPosition = currentPosition,
                                subtitleUrls = subtitleUrls,
                                customHeaders = customHeaders
                            )
                            player?.prepare()
                            player?.play()
                        }
                    }
                    showSettingsMenu = false
                },
                currentSubtitle = selectedSubtitle,
                onSubtitleSelected = { subtitle ->
                    selectedSubtitle = subtitle
                    // Add subtitle selection logic here
                },
                currentPlaybackSpeed = playbackSpeed,
                onPlaybackSpeedChanged = { speed ->
                    playbackSpeed = speed
                    player?.setPlaybackSpeed(speed)
                },
                onDismiss = { showSettingsMenu = false }
            )
        }

        // Error display
        playerError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        )
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            // Retry playback
                            playerError = null
                            coroutineScope.launch {
                                player?.release()
                                player = createPlayer(
                                    context = context,
                                    url = streamUrl,
                                    trackSelector = trackSelector,
                                    subtitleUrls = subtitleUrls,
                                    customHeaders = customHeaders
                                )
                                player?.prepare()
                                player?.play()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
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
    // Log the URL and headers for debugging
    Log.d(TAG, "Creating player with URL: $url")
    Log.d(TAG, "Using custom headers: $customHeaders")

    return ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()
        .apply {
            // Create data source factory with custom headers and detailed logging
            val dataSourceFactory = DefaultDataSource.Factory(context).apply {
                // Add headers to all requests
                val defaultHttpDataSourceFactory =
                    this as? androidx.media3.datasource.DefaultHttpDataSource.Factory
                defaultHttpDataSourceFactory?.setDefaultRequestProperties(customHeaders)

                // Add extra logging
                defaultHttpDataSourceFactory?.setConnectTimeoutMs(30000) // 30 seconds
                defaultHttpDataSourceFactory?.setReadTimeoutMs(30000) // 30 seconds

                // Log when the data source is created
                Log.d(TAG, "Created data source factory with headers: $customHeaders")
            }

            // Create media source based on URL type with logging
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

            // Add more detailed error listener
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e(TAG, "Player error: ${error.message}")

                    // Log the cause for more details
                    error.cause?.let {
                        Log.e(TAG, "Caused by: ${it.javaClass.simpleName}: ${it.message}")

                        // For HTTP errors, log more details
                        if (it is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                            Log.e(
                                TAG,
                                "HTTP Error details: ${it.responseCode}, ${it.responseMessage}"
                            )
                            Log.e(TAG, "Request headers: ${it.headerFields}")
                            Log.e(TAG, "Data spec: ${it.dataSpec}")
                        }

                        // Log the stacktrace
                        Log.e(TAG, "Stack trace: ${it.stackTraceToString()}")
                    }

                    onPlayerError(error)
                }

                // Add state change logging
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

            // Log preparing media source
            Log.d(TAG, "Preparing media source")
            setMediaSource(mediaSource)

            // Set initial position if needed for seeking during quality change
            if (startPosition > 0) {
                Log.d(TAG, "Setting initial position to: $startPosition ms")
                seekTo(startPosition)
            }
        }
}
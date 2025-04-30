package me.thuanc177.kotsune.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import kotlinx.coroutines.delay
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

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    streamUrl: String,
    title: String,
    subtitleUrls: List<Pair<String, String>> = emptyList(),
    qualityOptions: List<Pair<String, String>> = emptyList(),
    onBackPress: () -> Unit,
    customHeaders: Map<String, String> = mapOf(),
    onPositionChanged: (Long) -> Unit = {},
    initialPosition: Long = 0,
    onServerError: () -> Unit = {},
    onEpisodeNavigate: (isNext: Boolean) -> Unit = {},
    autoNextEpisode: Boolean = false,
    isLastEpisode: Boolean = false
) {
    val context = LocalContext.current
    rememberCoroutineScope()

    // Track device orientation
    val configuration = LocalConfiguration.current
    var isFullscreen by remember { mutableStateOf(configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) }

    // Settings
    var showSettings by remember { mutableStateOf(false) }
    var currentQuality by remember { mutableStateOf(qualityOptions.firstOrNull()?.first ?: "Auto") }
    var currentSubtitle by remember { mutableStateOf<String?>(null) }
    var currentPlaybackSpeed by remember { mutableStateOf(1.0f) }

    // Controls visibility
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hideControlsAfterMs = 3000L

    // Current position and duration display
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    // Create TrackSelector
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters())
        }
    }

    // Create the player
    val player = remember {
        createPlayer(
            context = context,
            url = streamUrl,
            trackSelector = trackSelector,
            startPosition = initialPosition,
            subtitleUrls = subtitleUrls,
            onPlayerError = { onServerError() },
            customHeaders = customHeaders
        )
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(hideControlsAfterMs)
            if (System.currentTimeMillis() - lastInteractionTime >= hideControlsAfterMs) {
                controlsVisible = false
            }
        }
    }

    // Update position and duration
    LaunchedEffect(player) {
        while (true) {
            if (!isSeeking) {
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0L)
                if (duration > 0) {
                    sliderPosition = (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
                }
            }
            delay(1000) // Update every second
        }
    }

    // Setup player for auto-next episode
    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && autoNextEpisode && !isLastEpisode) {
                    onEpisodeNavigate(true) // Navigate to next episode
                }
            }
        })
    }

    // Update player when streamUrl changes
    LaunchedEffect(streamUrl, currentQuality) {
        val selectedUrl = qualityOptions.find { it.first == currentQuality }?.second ?: streamUrl

        player.setMediaItem(MediaItem.fromUri(Uri.parse(selectedUrl)))
        player.prepare()
        player.play()
    }

    // Cleanup player when leaving the screen
    DisposableEffect(key1 = Unit) {
        onDispose {
            onPositionChanged(player.currentPosition)
            player.release()
        }
    }

    // Track player position periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Update position every 5 seconds
            onPositionChanged(player.currentPosition)
        }
    }

    // Set system UI flags to hide system buttons
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalSystemUiVisibility = activity?.window?.decorView?.systemUiVisibility

        activity?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                )

        onDispose {
            if (originalSystemUiVisibility != null) {
                activity?.window?.decorView?.systemUiVisibility = originalSystemUiVisibility
            }
        }
    }

    // Toggle fullscreen mode
    fun toggleFullscreen() {
        val activity = context.findActivity() ?: return
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        isFullscreen = !isFullscreen
    }

    // Handle back button
    BackHandler {
        onBackPress()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable {
                controlsVisible = !controlsVisible
                lastInteractionTime = System.currentTimeMillis()
            }
    ) {
        // Video view
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Controls overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top bar with title and back button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }

                // Center play/pause button
                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                ) {
                    // Duration text display
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTimeCorrectly(currentPosition),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = formatTimeCorrectly(duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Seeker bar
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            sliderPosition = it
                            isSeeking = true
                            currentPosition = (it * duration).toLong()
                        },
                        onValueChangeFinished = {
                            player.seekTo(currentPosition)
                            isSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )

                    // Control buttons row - grouped controls at left, fullscreen at right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left-aligned media controls
                        Row(
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            // Previous episode
                            IconButton(
                                onClick = { onEpisodeNavigate(false) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Episode",
                                    tint = Color.White
                                )
                            }

                            // Rewind 5 seconds
                            IconButton(
                                onClick = { player.seekBack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay5,
                                    contentDescription = "Rewind 5 seconds",
                                    tint = Color.White
                                )
                            }

                            // Play/Pause (small version)
                            IconButton(
                                onClick = {
                                    if (player.isPlaying) player.pause() else player.play()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }

                            // Forward 10 seconds
                            IconButton(
                                onClick = { player.seekForward() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White
                                )
                            }

                            // Next episode
                            IconButton(
                                onClick = { onEpisodeNavigate(true) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Episode",
                                    tint = Color.White
                                )
                            }
                        }

                        // Right-aligned fullscreen toggle
                        IconButton(
                            onClick = { toggleFullscreen() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Video settings dialog
    if (showSettings) {
        VideoSettingsDialog(
            onDismiss = { showSettings = false },
            currentQuality = currentQuality,
            currentSubtitle = currentSubtitle,
            currentPlaybackSpeed = currentPlaybackSpeed,
            qualityOptions = qualityOptions.map { it.first },
            subtitleOptions = subtitleUrls,
            onQualitySelected = { quality ->
                currentQuality = quality
            },
            onSubtitleSelected = { language ->
                currentSubtitle = language
                if (language != null) {
                    val parameters = trackSelector.buildUponParameters()
                    val subtitleTrackIndex = subtitleUrls.indexOfFirst { it.first == language }
                    if (subtitleTrackIndex != -1) {
                        parameters.setPreferredTextLanguage(language)
                        trackSelector.setParameters(parameters)
                    }
                } else {
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                            .setSelectUndeterminedTextLanguage(false)
                            .setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    )
                }
            },
            onPlaybackSpeedChanged = { speed ->
                currentPlaybackSpeed = speed
                player.setPlaybackSpeed(speed)
            },
            autoNextEpisode = autoNextEpisode,
            onAutoNextEpisodeChanged = { isEnabled ->
                // This will be handled by WatchAnimeViewModel
            }
        )
    }
}

// Fixed time formatting function
private fun formatTimeCorrectly(timeMs: Long): String {
    if (timeMs <= 0) return "00:00"

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

// Extension functions for ExoPlayer
private fun ExoPlayer.seekForward() {
    val newPosition = this.currentPosition + 10_000 // 10 seconds in milliseconds
    if (newPosition < this.duration) {
        this.seekTo(newPosition)
    } else {
        this.seekTo(this.duration)
    }
}

private fun ExoPlayer.seekBack() {
    val newPosition = (this.currentPosition - 5_000) // 5 seconds in milliseconds
        .coerceAtLeast(0)
    this.seekTo(newPosition)
}

@Composable
fun VideoSettingsDialog(
    onDismiss: () -> Unit,
    currentQuality: String,
    currentSubtitle: String?,
    currentPlaybackSpeed: Float,
    qualityOptions: List<String>,
    subtitleOptions: List<Pair<String, String>>,
    onQualitySelected: (String) -> Unit,
    onSubtitleSelected: (String?) -> Unit,
    onPlaybackSpeedChanged: (Float) -> Unit,
    autoNextEpisode: Boolean,
    onAutoNextEpisodeChanged: (Boolean) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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
                        items(qualityOptions) { quality ->
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

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // Auto next episode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Auto Play Next Episode",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Switch(
                        checked = autoNextEpisode,
                        onCheckedChange = onAutoNextEpisodeChanged
                    )
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
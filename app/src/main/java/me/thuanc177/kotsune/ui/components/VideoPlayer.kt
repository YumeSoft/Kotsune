package me.thuanc177.kotsune.ui.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@UnstableApi
@Composable
fun VideoPlayer(
    streamUrl: String?,
    subtitleUrls: List<Pair<String, String>> = emptyList(),
    qualityOptions: List<Pair<String, String>> = emptyList(),
    onBackPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableStateOf(0L) }
    var isCasting by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
    }

    // Hide controls after a delay when playing
    LaunchedEffect(isPlaying, showControls) {
        if (isPlaying && showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    player?.pause()
                    isPlaying = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Don't auto-play on resume
                }
                Lifecycle.Event.ON_DESTROY -> {
                    mediaSession?.release()
                    player?.release()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaSession?.release()
            player?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        if (streamUrl != null) {
            // ExoPlayer View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)

                        player = createPlayer(
                            context = ctx,
                            url = streamUrl,
                            trackSelector = trackSelector,
                            subtitleUrls = subtitleUrls,
                            onPlayerError = { error ->
                                playerError = "Playback error: ${error.message}"
                            }
                        ).also { exoPlayer ->
                            mediaSession = MediaSession.Builder(ctx, exoPlayer).build()

                            exoPlayer.addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    when (state) {
                                        Player.STATE_READY -> {
                                            isPlaying = exoPlayer.isPlaying
                                        }
                                    }
                                }

                                override fun onIsPlayingChanged(playing: Boolean) {
                                    isPlaying = playing
                                }
                            })

                            exoPlayer.prepare()
                            exoPlayer.play()
                        }

                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Player Controls Overlay with improved UI
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                ) {
                    // Title and top controls with improved spacing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
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

                        Row {
                            // Fullscreen toggle
                            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                                Icon(
                                    imageVector = if (isFullscreen)
                                        Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Toggle Fullscreen",
                                    tint = Color.White
                                )
                            }

                            // Cast button
                            IconButton(onClick = { isCasting = !isCasting }) {
                                Icon(
                                    imageVector = Icons.Default.Cast,
                                    contentDescription = "Cast",
                                    tint = if (isCasting) Color.Cyan else Color.White
                                )
                            }
                        }
                    }

                    // Playback control buttons in center
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind button
                        IconButton(
                            onClick = { player?.seekBack() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play/Pause button
                        IconButton(
                            onClick = { player?.let { it.playWhenReady = !it.playWhenReady } },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Forward button
                        IconButton(
                            onClick = { player?.seekForward() },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Bottom controls with improved layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        // Progress indicators and time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Slider(
                                value = currentPosition.toFloat(),
                                onValueChange = { position ->
                                    player?.seekTo(position.toLong())
                                    currentPosition = position.toLong()
                                },
                                valueRange = 0f..(player?.duration?.toFloat() ?: 1f),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Text(
                                text = formatTime(player?.duration ?: 0),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bottom row with controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Mute toggle
                            IconButton(onClick = {
                                isMuted = !isMuted
                                player?.volume = if (isMuted) 0f else 1f
                            }) {
                                Icon(
                                    imageVector = if (isMuted)
                                        Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Toggle Mute",
                                    tint = Color.White
                                )
                            }

                            // Speed selector with better UI
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .clickable {
                                        playbackSpeed = when (playbackSpeed) {
                                            0.5f -> 0.75f
                                            0.75f -> 1.0f
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.5f
                                            else -> 1.0f
                                        }
                                        player?.setPlaybackSpeed(playbackSpeed)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            // Quality selector with improved appearance
                            TextButton(
                                onClick = { showQualitySelector = true },
                                modifier = Modifier.background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = MaterialTheme.shapes.small
                                )
                            ) {
                                Text("Quality", color = Color.White)
                            }

                            // Subtitle selector with improved appearance
                            TextButton(
                                onClick = { showSubtitleSelector = true },
                                modifier = Modifier.background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = MaterialTheme.shapes.small
                                )
                            ) {
                                Text("Subtitles", color = Color.White)
                            }
                        }
                    }
                }
            }

            // Quality Selector Dialog with improved UI
            if (showQualitySelector && qualityOptions.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showQualitySelector = false },
                    title = {
                        Text(
                            "Select Quality",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column {
                            qualityOptions.forEach { (label, url) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Save current position
                                            val position = player?.currentPosition ?: 0

                                            // Create new player with selected quality
                                            coroutineScope.launch {
                                                player?.release()
                                                player = createPlayer(
                                                    context = context,
                                                    url = url,
                                                    trackSelector = trackSelector,
                                                    startPosition = position,
                                                    subtitleUrls = subtitleUrls
                                                )
                                                showQualitySelector = false
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)

                                    // Show checkmark for current quality
                                    if (streamUrl == url) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (label != qualityOptions.last().first) {
                                    Divider()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showQualitySelector = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Subtitle Selector Dialog with improved UI
            // Replace the subtitle selector section with this updated code
            if (showSubtitleSelector) {
                AlertDialog(
                    onDismissRequest = { showSubtitleSelector = false },
                    title = {
                        Text(
                            "Select Subtitles",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    text = {
                        Column {
                            // No subtitles option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        player?.trackSelectionParameters = player?.trackSelectionParameters
                                            ?.buildUpon()
                                            ?.setPreferredTextLanguage(null)
                                            ?.build()!!
                                        showSubtitleSelector = false
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("None", style = MaterialTheme.typography.bodyMedium)

                                // Show checkmark if no subtitles selected - using more modern API
                                val tracks = player?.currentTracks
                                val noSubtitlesSelected = tracks?.groups?.none {
                                    it.type == C.TRACK_TYPE_TEXT && it.isSelected
                                } != false

                                if (noSubtitlesSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Divider()

                            // Subtitle options
                            subtitleUrls.forEachIndexed { index, (label, _) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Enable this subtitle track
                                            player?.trackSelectionParameters = player?.trackSelectionParameters
                                                ?.buildUpon()
                                                ?.setPreferredTextLanguage(index.toString())
                                                ?.build()!!
                                            showSubtitleSelector = false
                                        }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)

                                    // Check if this subtitle is selected - using modern API
                                    val isSelected = player?.currentTracks?.groups?.any { group ->
                                        group.type == C.TRACK_TYPE_TEXT &&
                                                group.isSelected &&
                                                group.mediaTrackGroup.length > 0 &&
                                                group.getTrackFormat(0).language == index.toString()
                                    } == true

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                if (index < subtitleUrls.size - 1) {
                                    Divider()
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSubtitleSelector = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Improved error display
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
                                        subtitleUrls = subtitleUrls
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
        } else {
            // Loading indicator with improved appearance
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
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
    onPlayerError: (PlaybackException) -> Unit = {}
): ExoPlayer {
    return ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()
        .apply {
            val dataSourceFactory = DefaultDataSource.Factory(context)

            // Create media source based on URL type
            val mediaSource = when {
                url.endsWith(".m3u8") -> {
                    // HLS stream
                    HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                }
                else -> {
                    // Progressive (MP4) or other format
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(url)))
                }
            }

            // Add subtitle tracks if available
            if (subtitleUrls.isNotEmpty()) {
                val mediaSources = mutableListOf<MediaSource>(mediaSource)

                subtitleUrls.forEachIndexed { index, (language, subtitleUrl) ->
                    val subtitleMediaItem = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP) // or APPLICATION_TTML if using TTML
                        .setLanguage(index.toString()) // Using index as language code
                        .setLabel(language)
                        .build()

                    val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(subtitleMediaItem, C.TIME_UNSET)

                    mediaSources.add(subtitleSource)
                }

                setMediaSource(MergingMediaSource(*mediaSources.toTypedArray()))
            } else {
                setMediaSource(mediaSource)
            }

            // Set initial position if needed for seeking during quality change
            if (startPosition > 0) {
                seekTo(startPosition)
            }

            // Error listener
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    onPlayerError(error)
                }
            })
        }
}
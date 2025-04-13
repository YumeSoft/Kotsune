package me.thuanc177.kotsune.ui.components

import android.content.Context
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.time.Duration.Companion.seconds

@UnstableApi
@Composable
fun VideoPlayer(
    streamUrl: String?,
    savedPosition: Long = 0,
    onPositionChanged: (Long) -> Unit,
    onComplete: () -> Unit,
    subtitleUrls: List<Pair<String, String>> = emptyList(),
    qualityOptions: List<Pair<String, String>> = emptyList()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(savedPosition) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var playerError by remember { mutableStateOf<String?>(null) }

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

    // Update current position periodically
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(1000)
            player?.let {
                currentPosition = it.currentPosition
                onPositionChanged(currentPosition)
            }
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
                            startPosition = savedPosition,
                            subtitleUrls = subtitleUrls,
                            onPlayerError = { error ->
                                playerError = "Playback error: ${error.message}"
                            }
                        ).also { exoPlayer ->
                            mediaSession = MediaSession.Builder(ctx, exoPlayer).build()

                            exoPlayer.addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(state: Int) {
                                    when (state) {
                                        Player.STATE_ENDED -> {
                                            onComplete()
                                        }
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

            // Player Controls Overlay
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
                    // Title and top controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { /* Back button action */ }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            // Toggle cast mode
                        }) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = Color.White
                            )
                        }
                    }

                    // Center play/pause button
                    IconButton(
                        onClick = { player?.let { it.playWhenReady = !it.playWhenReady } },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.circular)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Bottom controls
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

                        // Playback controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind button
                            IconButton(onClick = {
                                player?.let {
                                    val newPosition = (it.currentPosition - 10.seconds.inWholeMilliseconds)
                                        .coerceAtLeast(0)
                                    it.seekTo(newPosition)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White
                                )
                            }

                            // Speed selector
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
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
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            // Quality selector
                            TextButton(onClick = { showQualitySelector = true }) {
                                Text("Quality", color = Color.White)
                            }

                            // Subtitle selector
                            TextButton(onClick = { showSubtitleSelector = true }) {
                                Text("Subtitles", color = Color.White)
                            }

                            // Forward button
                            IconButton(onClick = {
                                player?.let {
                                    val newPosition = it.currentPosition + 10.seconds.inWholeMilliseconds
                                    if (newPosition < (it.duration ?: 0)) {
                                        it.seekTo(newPosition)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Quality Selector Dialog
            if (showQualitySelector && qualityOptions.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showQualitySelector = false },
                    title = { Text("Select Quality") },
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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (label != qualityOptions.last().first) {
                                    Divider()
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }

            // Subtitle Selector Dialog
            if (showSubtitleSelector && subtitleUrls.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { showSubtitleSelector = false },
                    title = { Text("Select Subtitles") },
                    text = {
                        Column {
                            // No subtitles option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        player?.trackSelectionParameters = player?.trackSelectionParameters
                                            ?.buildUpon()
                                            ?.setDisabledTextTrackSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                            ?.build()
                                        showSubtitleSelector = false
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                Text("None", style = MaterialTheme.typography.bodyMedium)
                            }
                            Divider()

                            // Subtitle options
                            subtitleUrls.forEach { (label, _) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Enable this subtitle track
                                            val subtitleIndex = subtitleUrls.indexOf(subtitleUrls.find { it.first == label })
                                            if (subtitleIndex >= 0) {
                                                player?.trackSelectionParameters = player?.trackSelectionParameters
                                                    ?.buildUpon()
                                                    ?.setPreferredTextLanguage(subtitleIndex.toString())
                                                    ?.build()
                                            }
                                            showSubtitleSelector = false
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (label != subtitleUrls.last().first) {
                                    Divider()
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }

            // Error display
            playerError?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            // Retry playback
                            playerError = null
                            coroutineScope.launch {
                                player?.release()
                                player = createPlayer(
                                    context = context,
                                    url = streamUrl,
                                    trackSelector = trackSelector,
                                    startPosition = currentPosition,
                                    subtitleUrls = subtitleUrls
                                )
                                player?.prepare()
                                player?.play()
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
            }
        } else {
            // Loading indicator when no stream URL is available
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
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

            // Set initial position
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
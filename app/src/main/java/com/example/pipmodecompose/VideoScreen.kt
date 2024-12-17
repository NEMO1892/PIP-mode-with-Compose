package com.example.pipmodecompose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@Composable
fun VideoScreen(
    videoUrl: String,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val exoPlayer by viewModel.playerState.collectAsState()

    LaunchedEffect(videoUrl) {
        viewModel.initializePlayer(context, videoUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.savePlayerState()
            viewModel.releasePlayer()
        }
    }

    LocalLifecycleOwner.current.lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (exoPlayer?.isPlaying?.not() == true) {
                        exoPlayer?.play()
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    exoPlayer?.pause()
                }

                else -> Unit
            }
        }
    })

    Media3AndroidView(exoPlayer)
}

@Composable
fun Media3AndroidView(player: ExoPlayer?) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
        }
    )
}

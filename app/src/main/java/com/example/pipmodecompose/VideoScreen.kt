package com.example.pipmodecompose

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRect
import androidx.core.util.Consumer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private const val ACTION_STOPWATCH_CONTROL = "ActionStopwatchControl"
private const val EXTRA_CONTROL_TYPE = "control_type"
private const val CONTROL_TYPE_PLAY = 1
private const val CONTROL_TYPE_PAUSE = 2
private const val CONTROL_SEEK_FORWARD = 4
private const val CONTROL_SEEK_BACK = 5
private const val REQUEST_PLAY = 6
private const val REQUEST_PAUSE = 7
private const val REQUEST_SEEK_FORWARD = 9
private const val REQUEST_SEEK_BACK = 10

var shouldEnterPipMode by mutableStateOf(false)

@Composable
fun VideoScreen(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentPosition by remember { mutableLongStateOf(0L) }
    val sourceRect = remember { mutableStateOf(Rect()) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
            seekTo(currentPosition)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            currentPosition = exoPlayer.currentPosition
            exoPlayer.release()
            shouldEnterPipMode = false
        }
    }
    exoPlayer.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            shouldEnterPipMode = isPlaying
        }
    })
    modifier.onGloballyPositioned { layoutCoordinates ->
        sourceRect.value = layoutCoordinates.boundsInWindow()
            .toAndroidRectF()
            .toRect()
    }
    PipListenerPreAPI12(
        player = exoPlayer,
        shouldEnterPipMode = shouldEnterPipMode,
        sourceRect = sourceRect.value
    )
    PiPBuilderAfterApi12(
        exoPlayer,
        shouldEnterPipMode,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PlayerBroadcastReceiver(exoPlayer)
    }
}

@Composable
fun PipListenerPreAPI12(
    player: ExoPlayer,
    shouldEnterPipMode: Boolean,
    sourceRect: Rect,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
        val context = LocalContext.current
        DisposableEffect(context) {
            val activity = context.findActivity()
            val onUserLeaveBehavior: () -> Unit = {
                if (shouldEnterPipMode && player.videoSize != VideoSize.UNKNOWN) {
                    val pipParams = PictureInPictureParams.Builder()
                        .setSourceRectHint(sourceRect)
                        .setAspectRatio(
                            Rational(player.videoSize.width, player.videoSize.height)
                        )
                        .setActions(listOfRemoteActions(player.isPlaying, context))
                        .build()
                    activity.enterPictureInPictureMode(pipParams)
                }
            }
            activity.addOnUserLeaveHintListener(onUserLeaveBehavior)
            onDispose {
                activity.removeOnUserLeaveHintListener(onUserLeaveBehavior)
            }
        }
        Media3AndroidView(player = player, isInPipMode = rememberIsInPipMode(), modifier = modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun createRemoteAction(
    @DrawableRes iconResId: Int,
    @StringRes titleResId: Int,
    requestCode: Int,
    controlType: Int,
    context: Context
): RemoteAction = RemoteAction(
    Icon.createWithResource(context, iconResId),
    context.getString(titleResId),
    context.getString(titleResId),
    PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(ACTION_STOPWATCH_CONTROL)
            .putExtra(EXTRA_CONTROL_TYPE, controlType),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    ),
)

@Composable
fun PiPBuilderAfterApi12(
    player: ExoPlayer,
    shouldEnterPipMode: Boolean,
    modifier: Modifier = Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val context = LocalContext.current

        val pipModifier = modifier.onGloballyPositioned { layoutCoordinates ->
            val activity = context.findActivity()
            val builder = PictureInPictureParams.Builder().apply {
                setActions(listOfRemoteActions(player.isPlaying, context))

                if (shouldEnterPipMode && player.videoSize != VideoSize.UNKNOWN) {
                    val sourceRect = layoutCoordinates.boundsInWindow()
                        .toAndroidRectF()
                        .toRect()
                    setSourceRectHint(sourceRect)
                    setAspectRatio(
                        Rational(player.videoSize.width, player.videoSize.height)
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(shouldEnterPipMode)
                }
            }

            activity.setPictureInPictureParams(builder.build())
        }

        Media3AndroidView(
            player = player,
            isInPipMode = rememberIsInPipMode(),
            modifier = pipModifier
        )
    }
}

@Composable
fun rememberIsInPipMode(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val activity = LocalContext.current.findActivity()
    var isInPipMode by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val pipModeObserver = Consumer<PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        activity.addOnPictureInPictureModeChangedListener(pipModeObserver)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(pipModeObserver)
        }
    }
    return isInPipMode
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PlayerBroadcastReceiver(player: ExoPlayer) {
    val isInPipMode = rememberIsInPipMode()
    if (!isInPipMode) return
    val context = LocalContext.current
    DisposableEffect(player) {
        val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null || intent.action != ACTION_STOPWATCH_CONTROL) {
                    return
                }
                when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                    CONTROL_TYPE_PLAY -> player.playWhenReady = true
                    CONTROL_TYPE_PAUSE -> player.playWhenReady = false
                    CONTROL_SEEK_BACK -> player.seekTo(player.currentPosition - 10000)
                    CONTROL_SEEK_FORWARD -> player.seekTo(player.currentPosition + 10000)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            broadcastReceiver,
            IntentFilter(ACTION_STOPWATCH_CONTROL),
            ContextCompat.RECEIVER_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(broadcastReceiver)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
fun listOfRemoteActions(isPlaying: Boolean, context: Context): List<RemoteAction> = listOf(
    createRemoteAction(
        iconResId = R.drawable.ic_seek_back,
        titleResId = R.string.seek_back,
        requestCode = REQUEST_SEEK_BACK,
        controlType = CONTROL_SEEK_BACK,
        context = context
    ),
    createRemoteAction(
        iconResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
        titleResId = if (isPlaying) R.string.pause else R.string.play,
        requestCode = if (isPlaying) REQUEST_PAUSE else REQUEST_PLAY,
        controlType = if (isPlaying) CONTROL_TYPE_PAUSE else CONTROL_TYPE_PLAY,
        context = context
    ),
    createRemoteAction(
        iconResId = R.drawable.ic_seek_forward,
        titleResId = R.string.seek_forward,
        requestCode = REQUEST_SEEK_FORWARD,
        controlType = CONTROL_SEEK_FORWARD,
        context = context
    )
)

@Composable
fun Media3AndroidView(
    player: ExoPlayer,
    isInPipMode: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
            }
        },
        update = { playerView ->
            playerView.player = player
            playerView.useController = isInPipMode.not()
        }
    )
}

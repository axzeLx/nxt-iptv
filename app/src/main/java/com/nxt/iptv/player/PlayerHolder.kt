package com.nxt.iptv.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.nxt.iptv.data.Channel
import com.nxt.iptv.data.Http

/** Owns the ExoPlayer instance and keeps live streams alive (reconnects, HLS fallback). */
@OptIn(UnstableApi::class)
class PlayerHolder(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onBuffering(buffering: Boolean)
        fun onPlaying()
        fun onRetrying()
        fun onFailed(message: String)
        fun onVideoSize(width: Int, height: Int)
        /** Actually playing (not buffering/stopped); drives keep-screen-on. */
        fun onIsPlaying(playing: Boolean)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null
    private var channel: Channel? = null
    private var forceHls = false
    private var retries = 0

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> listener.onBuffering(true)
                Player.STATE_READY -> {
                    retries = 0
                    listener.onBuffering(false)
                    listener.onPlaying()
                }
                // A live stream should never end; treat it as a dropped connection.
                Player.STATE_ENDED -> retry()
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlaying(isPlaying)

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0) listener.onVideoSize(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            when {
                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    p.seekToDefaultPosition()
                    p.prepare()
                }
                !forceHls && isFormatError(error) -> {
                    // Many panels serve HLS from URLs without a .m3u8 extension.
                    forceHls = true
                    start()
                }
                else -> retry(error.errorCodeName)
            }
        }
    }

    fun attach(playerView: PlayerView) {
        view = playerView
        if (player == null) {
            val loadControl = DefaultLoadControl.Builder()
                // Start fast, keep a modest buffer so weak TV boxes don't run out of memory.
                .setBufferDurationsMs(15_000, 40_000, 1_000, 2_500)
                .build()
            val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
            player = ExoPlayer.Builder(context, renderers)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                            .build(),
                        true,
                    )
                    addListener(playerListener)
                }
        }
        playerView.player = player
    }

    fun play(ch: Channel) {
        channel = ch
        forceHls = false
        retries = 0
        start()
    }

    /** Re-tunes the current channel, e.g. after the user retries a failed stream. */
    fun replay() {
        channel?.let { play(it) }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.stop()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        view?.player = null
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    private fun start() {
        val p = player ?: return
        val ch = channel ?: return
        handler.removeCallbacksAndMessages(null)
        val headers = HashMap<String, String>()
        ch.referrer?.let { headers["Referer"] = it }
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(ch.userAgent ?: Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
            .setDefaultRequestProperties(headers)
        val item = MediaItem.Builder().setUri(ch.url).apply {
            if (forceHls) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        p.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(item))
        p.prepare()
        p.playWhenReady = true
    }

    private fun retry(reason: String? = null) {
        if (retries >= MAX_RETRIES) {
            listener.onFailed(reason ?: "")
            return
        }
        retries++
        listener.onRetrying()
        handler.postDelayed({ start() }, 1_500L * retries)
    }

    private fun isFormatError(e: PlaybackException) =
        e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            e.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    companion object {
        private const val MAX_RETRIES = 3
    }
}

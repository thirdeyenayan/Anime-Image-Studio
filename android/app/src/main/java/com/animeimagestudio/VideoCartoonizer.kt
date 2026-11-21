package com.animeimagestudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Offline video-to-cartoon transcoder.
 * Uses Android's built-in H.264 encoder; frames are stylized on-device.
 * This is a cartoon/anime-inspired filter, not a generative AI model.
 */
object VideoCartoonizer {
    private const val MIME = "video/avc"
    private const val FPS = 15
    private const val MAX_SIDE = 720
    private const val MAX_DURATION_MS = 30_000L

    fun convert(
        context: Context,
        uri: Uri,
        onProgress: (Int) -> Unit
    ): File {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: error("Cannot read video duration")
        require(durationMs > 0) { "Video has no duration" }
        require(durationMs <= MAX_DURATION_MS) { "For reliable phone processing, choose a video up to 30 seconds" }

        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            ?.toIntOrNull() ?: 0
        val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        require(rawW > 0 && rawH > 0) { "Unsupported video dimensions" }

        val displayW = if (rotation == 90 || rotation == 270) rawH else rawW
        val displayH = if (rotation == 90 || rotation == 270) rawW else rawH
        val scale = min(1f, MAX_SIDE.toFloat() / max(displayW, displayH))
        val width = ((displayW * scale).toInt().coerceAtLeast(2) / 2) * 2
        val height = ((displayH * scale).toInt().coerceAtLeast(2) / 2) * 2
        val frameCount = ((durationMs * FPS) / 1000L).toInt().coerceAtLeast(1)
        val output = File(context.cacheDir, "anime_video_${System.currentTimeMillis()}.mp4")

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        val info = MediaCodec.BufferInfo()

        try {
            val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
                setInteger(MediaFormat.KEY_BIT_RATE, (width * height * 5).coerceIn(1_000_000, 8_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            encoder = MediaCodec.createEncoderByType(MIME)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            val codec = encoder ?: error("Could not start video encoder")
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            fun drain(endOfStream: Boolean) {
                var idle = 0
                while (idle < 8) {
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return
                            idle++
                        }
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Encoder format changed twice" }
                            trackIndex = muxer!!.addTrack(codec.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        index >= 0 -> {
                            val buffer = codec.getOutputBuffer(index)
                            if (info.size > 0 && buffer != null && muxerStarted &&
                                (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                muxer!!.writeSampleData(trackIndex, buffer, info)
                            }
                            val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            codec.releaseOutputBuffer(index, false)
                            if (eos) return
                        }
                    }
                }
            }

            for (frame in 0 until frameCount) {
                val timeUs = frame * 1_000_000L / FPS
                val original = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: continue
                val scaled = Bitmap.createScaledBitmap(original, width, height, true)
                if (scaled !== original) original.recycle()
                val styled = cartoonFrame(scaled)
                if (styled !== scaled) scaled.recycle()
                val nv12 = bitmapToNv12(styled)
                styled.recycle()

                var inputIndex = codec.dequeueInputBuffer(100_000)
                var attempts = 0
                while (inputIndex < 0 && attempts++ < 20) {
                    drain(false)
                    inputIndex = codec.dequeueInputBuffer(100_000)
                }
                check(inputIndex >= 0) { "Video encoder is not responding" }
                val input = codec.getInputBuffer(inputIndex) ?: error("Encoder input buffer unavailable")
                input.clear()
                input.put(nv12)
                val presentationTimeUs = frame * 1_000_000L / FPS
                codec.queueInputBuffer(inputIndex, 0, nv12.size, presentationTimeUs, 0)
                drain(false)
                onProgress(((frame + 1) * 95 / frameCount).coerceIn(1, 95))
            }

            var eosIndex = codec.dequeueInputBuffer(100_000)
            var eosWaits = 0
            while (eosIndex < 0 && eosWaits++ < 20) {
                drain(false)
                eosIndex = codec.dequeueInputBuffer(100_000)
            }
            check(eosIndex >= 0) { "Could not finish video encoding" }
            codec.queueInputBuffer(
                eosIndex, 0, 0, frameCount * 1_000_000L / FPS,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            drain(true)
            check(muxerStarted && output.length() > 0) { "Could not encode this video" }
            onProgress(100)
            return output
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun cartoonFrame(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(pixels.size)
        fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val c = pixels[i]
            val r = (Color.red(c) / 24) * 24 + 12
            val g = (Color.green(c) / 24) * 24 + 12
            val b = (Color.blue(c) / 24) * 24 + 12
            var edge = 0
            if (x in 1 until w - 1 && y in 1 until h - 1) {
                edge = kotlin.math.abs(lum(pixels[i + 1]) - lum(pixels[i - 1])) +
                    kotlin.math.abs(lum(pixels[i + w]) - lum(pixels[i - w]))
            }
            val ink = if (edge > 82) 0.38f else 1f
            out[i] = Color.rgb((r * ink).toInt(), (g * ink).toInt(), (b * ink).toInt())
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun bitmapToNv12(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val output = ByteArray(w * h * 3 / 2)
        var yIndex = 0
        var uvIndex = w * h
        for (row in 0 until h) {
            for (col in 0 until w) {
                val c = pixels[row * w + col]
                val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                output[yIndex++] = y.coerceIn(0, 255).toByte()
                if (row % 2 == 0 && col % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    output[uvIndex++] = u.coerceIn(0, 255).toByte()
                    output[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return output
    }
}

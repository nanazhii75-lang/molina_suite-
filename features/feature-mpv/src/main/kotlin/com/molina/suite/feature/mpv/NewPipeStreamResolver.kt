package com.molina.suite.feature.mpv

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException

/**
 * Menyelesaikan satu video YouTube lewat NewPipeExtractor menjadi JSON berbentuk keluaran
 * yt-dlp (lihat [YtdlJson]). Memilih stream lewat [StreamPicker]; hanya stream HTTP langsung
 * yang dipakai (tanpa manifes DASH), kecuali siaran langsung yang memakai URL HLS.
 */
internal class NewPipeStreamResolver {

    /** Penyelesaian gagal; pesannya aman ditulis ke log (tanpa URL). */
    class ResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun resolve(videoId: String, maxHeight: Int): String {
        NewPipeRuntime.ensureReady()
        val pageUrl = WATCH_URL + videoId
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, pageUrl)
        } catch (e: ExtractionException) {
            throw ResolveException("Extractor: " + (e.message ?: e.javaClass.simpleName), e)
        } catch (e: IOException) {
            throw ResolveException("Jaringan: " + (e.message ?: e.javaClass.simpleName), e)
        } catch (e: RuntimeException) {
            throw ResolveException("Extractor galat: " + (e.message ?: e.javaClass.simpleName), e)
        }
        val meta = VideoMeta(
            id = videoId,
            title = info.name.orEmpty().ifBlank { videoId },
            webpageUrl = pageUrl,
            durationSec = info.duration,
            uploader = info.uploaderName,
            thumbnail = info.thumbnails.lastOrNull()?.url
        )
        if (info.streamType == StreamType.LIVE_STREAM || info.streamType == StreamType.AUDIO_LIVE_STREAM) {
            val hls = info.hlsUrl.orEmpty()
            if (hls.isEmpty()) throw ResolveException("Siaran langsung tanpa URL HLS")
            return YtdlJson.forLive(meta, hls, userAgent(clientOf(hls)))
        }
        val choice = try {
            StreamPicker.pick(
                videoOnly = info.videoOnlyStreams.mapNotNull(::videoCandidate),
                audio = audioCandidates(info.audioStreams),
                progressive = info.videoStreams.mapNotNull(::videoCandidate),
                maxHeight = maxHeight
            )
        } catch (e: RuntimeException) {
            throw ResolveException("Gagal memilih stream: " + (e.message ?: e.javaClass.simpleName), e)
        } ?: throw ResolveException("Tidak ada stream yang bisa diputar")
        return YtdlJson.forChoice(meta, choice, ::userAgent)
    }

    private fun isDirect(stream: Stream): Boolean =
        stream.isUrl &&
            stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
            !stream.content.isNullOrEmpty()

    private fun videoCandidate(stream: VideoStream): MediaCandidate? {
        if (!isDirect(stream)) return null
        val url = stream.content
        return MediaCandidate(
            formatId = stream.itag.toString(),
            url = url,
            ext = stream.format?.suffix ?: "mp4",
            codec = stream.codec,
            height = stream.height,
            width = stream.width,
            fps = stream.fps,
            bitrateKbps = (stream.bitrate / KILO).coerceAtLeast(0),
            client = clientOf(url)
        )
    }

    /** Hanya trek audio asli (bukan sulih suara atau deskripsi); semua trek bila tidak ada yang asli. */
    private fun audioCandidates(streams: List<AudioStream>): List<MediaCandidate> {
        val direct = streams.filter(::isDirect)
        val originals = direct.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }
        return originals.ifEmpty { direct }.map { stream ->
            val url = stream.content
            MediaCandidate(
                formatId = stream.itag.toString(),
                url = url,
                ext = stream.format?.suffix ?: "m4a",
                codec = stream.codec,
                height = 0,
                width = 0,
                fps = 0,
                bitrateKbps = stream.averageBitrate.coerceAtLeast(0),
                client = clientOf(url)
            )
        }
    }

    private fun clientOf(url: String): StreamClient = when {
        YoutubeParsingHelper.isIosStreamingUrl(url) -> StreamClient.IOS
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> StreamClient.ANDROID
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> StreamClient.VISIONOS
        else -> StreamClient.WEB
    }

    private fun userAgent(client: StreamClient): String = when (client) {
        StreamClient.IOS -> YoutubeParsingHelper.getIosUserAgent(null)
        StreamClient.ANDROID -> YoutubeParsingHelper.getAndroidUserAgent(null)
        StreamClient.VISIONOS -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        StreamClient.WEB -> NewPipeHttpDownloader.USER_AGENT
    }

    private companion object {
        const val WATCH_URL = "https://www.youtube.com/watch?v="
        const val KILO = 1000
    }
}

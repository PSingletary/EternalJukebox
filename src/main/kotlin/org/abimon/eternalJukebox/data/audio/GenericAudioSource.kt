package org.abimon.eternalJukebox.data.audio

import kotlinx.coroutines.*
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.MediaWrapper
import org.abimon.eternalJukebox.guaranteeDelete
import org.abimon.eternalJukebox.objects.*
import org.abimon.eternalJukebox.useThenDelete
import org.abimon.visi.io.DataSource
import org.abimon.visi.io.FileDataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Generic audio source that supports any yt-dlp compatible URL
 * Supports YouTube, SoundCloud, Bandcamp, Vimeo, and 1800+ other platforms
 */
object GenericAudioSource : IAudioSource {
    private val logger: Logger = LoggerFactory.getLogger("GenericAudioSource")
    
    private val uuid: String
        get() = UUID.randomUUID().toString()
    
    val format: String
    val command: List<String>
    
    val mimes = mapOf(
        "m4a" to "audio/m4a", "aac" to "audio/aac", "mp3" to "audio/mpeg", 
        "ogg" to "audio/ogg", "wav" to "audio/wav", "flac" to "audio/flac"
    )
    
    // URL patterns for different platforms
    private val platformPatterns = mapOf(
        "youtube" to listOf(
            Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([\\w-]{11})"),
            Regex("youtube\\.com/v/([\\w-]{11})"),
            Regex("youtube\\.com/.*[?&]v=([\\w-]{11})")
        ),
        "soundcloud" to listOf(
            Regex("soundcloud\\.com/([^/]+)/([^/?]+)"),
            Regex("soundcloud\\.com/[^/]+/[^/]+/([^/?]+)")
        ),
        "bandcamp" to listOf(
            Regex("([^.]+)\\.bandcamp\\.com/track/([^/?]+)"),
            Regex("bandcamp\\.com/track/([^/?]+)")
        ),
        "vimeo" to listOf(
            Regex("vimeo\\.com/(\\d+)"),
            Regex("player\\.vimeo\\.com/video/(\\d+)")
        ),
        "twitch" to listOf(
            Regex("twitch\\.tv/videos/(\\d+)"),
            Regex("clips\\.twitch\\.tv/([^/?]+)")
        ),
        "tiktok" to listOf(
            Regex("tiktok\\.com/@([^/]+)/video/(\\d+)"),
            Regex("vm\\.tiktok\\.com/([^/?]+)")
        ),
        "twitter" to listOf(
            Regex("twitter\\.com/[^/]+/status/(\\d+)"),
            Regex("x\\.com/[^/]+/status/(\\d+)")
        )
    )
    
    override suspend fun provide(info: JukeboxInfo, clientInfo: ClientInfo?): DataSource? {
        logger.trace("[{}] Attempting to provide audio for {}", clientInfo?.userUID, info.id)
        
        // Check if we have a cached location first
        val cachedLocation = provideLocation(info, clientInfo)
        if (cachedLocation != null) {
            logger.trace("[{}] Using cached location for {}", clientInfo?.userUID, info.id)
            return downloadFromUrl(cachedLocation.toString(), info, clientInfo)
        }
        
        // Try to extract URL from info
        val audioUrl = extractAudioUrl(info, clientInfo)
        if (audioUrl != null) {
            return downloadFromUrl(audioUrl, info, clientInfo)
        }
        
        logger.warn("[{}] No valid audio URL found for {}", clientInfo?.userUID, info.id)
        return null
    }
    
    override suspend fun provideLocation(info: JukeboxInfo, clientInfo: ClientInfo?): URL? {
        val dbLocation = withContext(Dispatchers.IO) { 
            EternalJukebox.database.provideAudioLocation(info.id, clientInfo) 
        }
        
        return if (dbLocation != null) {
            withContext(Dispatchers.IO) { URL(dbLocation) }
        } else null
    }
    
    /**
     * Extract audio URL from JukeboxInfo
     */
    private suspend fun extractAudioUrl(info: JukeboxInfo, clientInfo: ClientInfo?): String? {
        // If info.url is already a direct audio URL, use it
        if (isDirectAudioUrl(info.url)) {
            return info.url
        }
        
        // Try to find the URL from the info
        return when {
            info.url.isNotEmpty() -> info.url
            info.id.contains("://") -> info.id // ID might be a URL
            else -> {
                // Try to construct URL from service and ID
                constructUrlFromServiceAndId(info.service, info.id)
            }
        }
    }
    
    /**
     * Download audio from a URL using yt-dlp
     */
    private suspend fun downloadFromUrl(url: String, info: JukeboxInfo, clientInfo: ClientInfo?): DataSource? {
        logger.debug("[{}] Downloading audio from URL: {}", clientInfo?.userUID, url)
        
        val tmpFile = File("$uuid.tmp")
        val tmpLog = File("${info.id}-$uuid.log")
        val ffmpegLog = File("${info.id}-$uuid-ffmpeg.log")
        val endGoalTmp = File(tmpFile.absolutePath.replace(".tmp", ".tmp.$format"))
        
        try {
            withContext(Dispatchers.IO) {
                val cmd = ArrayList(command).apply {
                    add(url)
                    add(tmpFile.absolutePath)
                    add(format)
                }
                
                logger.debug("[{}] Executing command: {}", clientInfo?.userUID, cmd.joinToString(" "))
                
                val downloadProcess = ProcessBuilder()
                    .command(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(tmpLog)
                    .start()
                
                if (!downloadProcess.waitFor(90, TimeUnit.SECONDS)) {
                    downloadProcess.destroyForcibly().waitFor()
                    logger.error("[{}] Forcibly destroyed download process for {}", clientInfo?.userUID, url)
                }
            }
            
            // Handle conversion if needed
            if (!endGoalTmp.exists()) {
                logger.warn("[{}] {} does not exist, attempting conversion with ffmpeg", clientInfo?.userUID, endGoalTmp)
                
                if (!tmpFile.exists()) {
                    logger.error("[{}] {} does not exist, download failed", clientInfo?.userUID, tmpFile)
                    return null
                }
                
                if (MediaWrapper.ffmpeg.installed) {
                    if (!MediaWrapper.ffmpeg.convert(tmpFile, endGoalTmp, ffmpegLog)) {
                        logger.error("[{}] Failed to convert {} to {}", clientInfo?.userUID, tmpFile, endGoalTmp)
                        return null
                    }
                    
                    if (!endGoalTmp.exists()) {
                        logger.error("[{}] {} does not exist after conversion", clientInfo?.userUID, endGoalTmp)
                        return null
                    }
                } else {
                    logger.debug("[{}] ffmpeg not installed, cannot convert", clientInfo?.userUID)
                }
            }
            
            // Store the audio file and extract metadata
            withContext(Dispatchers.IO) {
                // Try to extract platform-specific ID from logs
                val extractedId = extractIdFromLogs(tmpLog, url)
                if (extractedId != null) {
                    logger.debug("[{}] Storing location: {}", clientInfo?.userUID, extractedId)
                    EternalJukebox.database.storeAudioLocation(info.id, extractedId, clientInfo)
                }
                
                // Store the audio file
                endGoalTmp.useThenDelete {
                    EternalJukebox.storage.store(
                        "${info.id}.$format",
                        EnumStorageType.AUDIO,
                        FileDataSource(it),
                        mimes[format] ?: "audio/mpeg",
                        clientInfo
                    )
                }
            }
            
            return EternalJukebox.storage.provide("${info.id}.$format", EnumStorageType.AUDIO, clientInfo)
            
        } catch (e: Exception) {
            logger.error("[{}] Error downloading from URL {}: {}", clientInfo?.userUID, url, e.message)
            return null
        } finally {
            // Cleanup temporary files
            tmpFile.guaranteeDelete()
            File(tmpFile.absolutePath + ".part").guaranteeDelete()
            
            withContext(Dispatchers.IO) {
                tmpLog.useThenDelete {
                    EternalJukebox.storage.store(
                        it.name, EnumStorageType.LOG, FileDataSource(it), "text/plain", clientInfo
                    )
                }
                ffmpegLog.useThenDelete {
                    EternalJukebox.storage.store(
                        it.name, EnumStorageType.LOG, FileDataSource(it), "text/plain", clientInfo
                    )
                }
                endGoalTmp.useThenDelete {
                    EternalJukebox.storage.store(
                        "${info.id}.$format",
                        EnumStorageType.AUDIO,
                        FileDataSource(it),
                        mimes[format] ?: "audio/mpeg",
                        clientInfo
                    )
                }
            }
        }
    }
    
    /**
     * Check if URL is a direct audio URL
     */
    private fun isDirectAudioUrl(url: String): Boolean {
        val audioExtensions = listOf(".mp3", ".wav", ".m4a", ".aac", ".ogg", ".flac")
        return audioExtensions.any { url.lowercase().contains(it) }
    }
    
    /**
     * Construct URL from service and ID
     */
    private fun constructUrlFromServiceAndId(service: String, id: String): String? {
        return when (service.uppercase()) {
            "YOUTUBE" -> {
                if (id.matches(Regex("[\\w-]{11}"))) {
                    "https://youtu.be/$id"
                } else null
            }
            "SOUNDCLOUD" -> {
                if (id.contains("/")) {
                    "https://soundcloud.com/$id"
                } else null
            }
            "BANDCAMP" -> {
                if (id.contains("/")) {
                    "https://$id"
                } else null
            }
            "VIMEO" -> {
                if (id.matches(Regex("\\d+"))) {
                    "https://vimeo.com/$id"
                } else null
            }
            else -> {
                // For unknown services, assume ID might be a URL
                if (id.startsWith("http")) id else null
            }
        }
    }
    
    /**
     * Extract platform-specific ID from yt-dlp logs
     */
    private fun extractIdFromLogs(logFile: File, originalUrl: String): String? {
        if (!logFile.exists()) return null
        
        try {
            logFile.forEachLine { line ->
                // Look for various platform-specific patterns in logs
                when {
                    line.contains("Video ID:") -> {
                        val match = Regex("Video ID: ([\\w-]+)").find(line)
                        if (match != null) {
                            return when {
                                originalUrl.contains("youtube.com") || originalUrl.contains("youtu.be") -> 
                                    "https://youtu.be/${match.groupValues[1]}"
                                originalUrl.contains("soundcloud.com") -> originalUrl
                                originalUrl.contains("bandcamp.com") -> originalUrl
                                originalUrl.contains("vimeo.com") -> originalUrl
                                else -> originalUrl
                            }
                        }
                    }
                    line.contains("Track ID:") -> {
                        val match = Regex("Track ID: ([\\w-]+)").find(line)
                        if (match != null) {
                            return originalUrl
                        }
                    }
                    line.contains("Downloading") && line.contains("from") -> {
                        // Extract the final URL that yt-dlp is downloading from
                        val match = Regex("Downloading.*from ([^\\s]+)").find(line)
                        if (match != null) {
                            return match.groupValues[1]
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Error reading log file: {}", e.message)
        }
        
        return originalUrl // Fallback to original URL
    }
    
    /**
     * Extract platform and ID from URL
     */
    fun extractPlatformAndId(url: String): Pair<String, String>? {
        for ((platform, patterns) in platformPatterns) {
            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    val id = match.groupValues.drop(1).joinToString("/")
                    return Pair(platform, id)
                }
            }
        }
        return null
    }
    
    /**
     * Check if URL is supported by yt-dlp
     */
    fun isSupportedUrl(url: String): Boolean {
        return platformPatterns.values.any { patterns ->
            patterns.any { pattern -> pattern.containsMatchIn(url) }
        } || isDirectAudioUrl(url)
    }
    
    init {
        format = (EternalJukebox.config.audioSourceOptions["AUDIO_FORMAT"]
            ?: EternalJukebox.config.audioSourceOptions["audioFormat"]) as? String ?: "m4a"
        
        command = ((EternalJukebox.config.audioSourceOptions["AUDIO_COMMAND"]
            ?: EternalJukebox.config.audioSourceOptions["audioCommand"]) as? List<*>)?.map { "$it" }
            ?: ((EternalJukebox.config.audioSourceOptions["AUDIO_COMMAND"]
                ?: EternalJukebox.config.audioSourceOptions["audioCommand"]) as? String)?.split("\\s+".toRegex())
                ?: if (System.getProperty("os.name").lowercase().contains("windows")) {
                    listOf("yt.bat")
                } else {
                    listOf("sh", "yt.sh")
                }
        
        logger.info("GenericAudioSource initialized with format: $format, command: ${command.joinToString(" ")}")
    }
}

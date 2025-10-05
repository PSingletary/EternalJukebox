package org.abimon.eternalJukebox.data.analysis

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.*
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.exponentiallyBackoff
import org.abimon.eternalJukebox.objects.ClientInfo
import org.abimon.eternalJukebox.objects.JukeboxInfo
import org.abimon.eternalJukebox.objects.SpotifyAudioBar
import org.abimon.eternalJukebox.objects.SpotifyAudioBeat
import org.abimon.eternalJukebox.objects.SpotifyAudioSection
import org.abimon.eternalJukebox.objects.SpotifyAudioSegment
import org.abimon.eternalJukebox.objects.SpotifyAudioTatum
import org.abimon.eternalJukebox.objects.JukeboxAnalysis
import org.abimon.eternalJukebox.objects.JukeboxSummary
import org.abimon.eternalJukebox.objects.JukeboxTrack
import org.schabi.newpipe.extractor.*
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Generic analyser that works with any yt-dlp compatible audio source
 * Replaces the SpotifyAnalyser with support for YouTube, SoundCloud, Bandcamp, etc.
 */
object GenericAnalyser : IAnalyser {
    private val logger: Logger = LoggerFactory.getLogger("GenericAnalyser")
    
    // Configuration for Python analysis service
    private val analysisServiceUrl: String = EternalJukebox.config.audioSourceOptions["ANALYSIS_SERVICE_URL"] as? String 
        ?: "http://localhost:5000"
    
    private val newPipeService = ServiceList.YouTube
    private const val MAX_SEARCH_RESULTS = 10
    private const val VIDEO_LINK_PREFIX = "https://youtu.be/"
    
    override suspend fun search(query: String, clientInfo: ClientInfo?): Array<JukeboxInfo> {
        logger.trace("[{}] Attempting to search for \"{}\"", clientInfo?.userUID, query)
        
        return try {
            val searchQuery = newPipeService
                .searchQHFactory
                .fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
            
            val searchInfo: SearchInfo = SearchInfo.getInfo(newPipeService, searchQuery)
            val infoItems = searchInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { it.streamType == StreamType.VIDEO_STREAM }
                .take(MAX_SEARCH_RESULTS)
                .map { item ->
                    JukeboxInfo(
                        service = "YOUTUBE",
                        id = extractVideoId(item.url) ?: generateUrlHash(item.url),
                        name = item.name ?: "Unknown Title",
                        title = item.name ?: "Unknown Title", 
                        artist = item.uploaderName ?: "Unknown Artist",
                        url = item.url,
                        duration = (item.duration ?: 0) * 1000 // Convert seconds to milliseconds
                    )
                }
                .toTypedArray()
            
            logger.trace("[{}] Successfully found {} results for \"{}\"", clientInfo?.userUID, infoItems.size, query)
            infoItems
            
        } catch (e: Exception) {
            logger.error("[{}] Failed to search for \"{}\": {}", clientInfo?.userUID, query, e.message)
            emptyArray()
        }
    }
    
    override suspend fun getInfo(id: String, clientInfo: ClientInfo?): JukeboxInfo? {
        logger.trace("[{}] Attempting to get info for ID: {}", clientInfo?.userUID, id)
        
        return try {
            // Check if this is a YouTube video ID
            if (isValidYouTubeId(id)) {
                val url = "$VIDEO_LINK_PREFIX$id"
                getInfoFromUrl(url, clientInfo)
            } else {
                // Try to extract info from URL hash or direct URL
                logger.warn("[{}] ID {} is not a valid YouTube ID, returning null", clientInfo?.userUID, id)
                null
            }
        } catch (e: Exception) {
            logger.error("[{}] Failed to get info for ID {}: {}", clientInfo?.userUID, id, e.message)
            null
        }
    }
    
    /**
     * Get track information from a URL
     */
    suspend fun getInfoFromUrl(url: String, clientInfo: ClientInfo?): JukeboxInfo? {
        logger.trace("[{}] Attempting to get info from URL: {}", clientInfo?.userUID, url)
        
        return try {
            val videoId = extractVideoId(url) ?: return null
            val streamInfo = StreamInfo.getInfo(newPipeService, url)
            
            JukeboxInfo(
                service = "YOUTUBE",
                id = videoId,
                name = streamInfo.name ?: "Unknown Title",
                title = streamInfo.name ?: "Unknown Title",
                artist = streamInfo.uploaderName ?: "Unknown Artist", 
                url = url,
                duration = (streamInfo.duration ?: 0) * 1000 // Convert seconds to milliseconds
            )
        } catch (e: Exception) {
            logger.error("[{}] Failed to get info from URL {}: {}", clientInfo?.userUID, url, e.message)
            null
        }
    }
    
    /**
     * Generate audio analysis using the Python analysis service
     */
    suspend fun generateAnalysis(audioUrl: String, clientInfo: ClientInfo?): JukeboxAnalysis? {
        logger.trace("[{}] Generating analysis for URL: {}", clientInfo?.userUID, audioUrl)
        
        return try {
            val success = exponentiallyBackoff(30000, 5) { attempt ->
                logger.debug("[{}] Analysis attempt {} for URL: {}", clientInfo?.userUID, attempt, audioUrl)
                
                val (_, response, _) = Fuel.post("$analysisServiceUrl/analyze/url")
                    .header("Content-Type", "application/json")
                    .body("""{"url": "$audioUrl"}""")
                    .awaitStringResponseResult()
                
                when (response.statusCode) {
                    200 -> {
                        val analysisData = withContext(Dispatchers.IO) { 
                            EternalJukebox.jsonMapper.readValue(response.data, Map::class.java) 
                        }
                        
                        val analysis = JukeboxAnalysis(
                            sections = parseSections(analysisData["sections"] as? List<*>),
                            bars = parseBars(analysisData["bars"] as? List<*>),
                            beats = parseBeats(analysisData["beats"] as? List<*>),
                            tatums = parseTatums(analysisData["tatums"] as? List<*>),
                            segments = parseSegments(analysisData["segments"] as? List<*>)
                        )
                        
                        logger.trace("[{}] Successfully generated analysis for URL: {}", clientInfo?.userUID, audioUrl)
                        return@exponentiallyBackoff false // Success, don't retry
                    }
                    429 -> {
                        logger.warn("[{}] Analysis service rate limited, backing off", clientInfo?.userUID)
                        delay(5000) // Wait 5 seconds before retry
                        return@exponentiallyBackoff true
                    }
                    else -> {
                        logger.error("[{}] Analysis service returned status {}: {}", 
                            clientInfo?.userUID, response.statusCode, response.body().asString())
                        return@exponentiallyBackoff true
                    }
                }
            }
            
            if (success) null else {
                logger.error("[{}] Failed to generate analysis after all retries", clientInfo?.userUID)
                null
            }
            
        } catch (e: Exception) {
            logger.error("[{}] Exception while generating analysis: {}", clientInfo?.userUID, e.message)
            null
        }
    }
    
    /**
     * Extract YouTube video ID from URL
     */
    private fun extractVideoId(url: String): String? {
        val patterns = listOf(
            Regex("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([\\w-]{11})"),
            Regex("youtube\\.com/v/([\\w-]{11})"),
            Regex("youtube\\.com/.*[?&]v=([\\w-]{11})")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    /**
     * Check if string is a valid YouTube video ID
     */
    private fun isValidYouTubeId(id: String): Boolean {
        return id.matches(Regex("[\\w-]{11}"))
    }
    
    /**
     * Generate a hash from URL as fallback ID
     */
    private fun generateUrlHash(url: String): String {
        return url.hashCode().toString()
    }
    
    // Parsing methods for analysis data from Python service
    private fun parseSections(data: List<*>?): Array<SpotifyAudioSection> {
        return data?.map { item ->
            val section = item as Map<*, *>
            SpotifyAudioSection(
                start = (section["start"] as? Number)?.toDouble() ?: 0.0,
                duration = (section["duration"] as? Number)?.toDouble() ?: 0.0,
                confidence = (section["confidence"] as? Number)?.toDouble() ?: 0.0,
                loudness = (section["loudness"] as? Number)?.toDouble() ?: 0.0,
                tempo = (section["tempo"] as? Number)?.toDouble() ?: 0.0,
                tempo_confidence = (section["tempo_confidence"] as? Number)?.toDouble() ?: 0.0,
                key = (section["key"] as? Number)?.toInt() ?: 0,
                key_confidence = (section["key_confidence"] as? Number)?.toDouble() ?: 0.0,
                mode = (section["mode"] as? Number)?.toInt() ?: 0,
                mode_confidence = (section["mode_confidence"] as? Number)?.toDouble() ?: 0.0,
                time_signature = (section["time_signature"] as? Number)?.toInt() ?: 4,
                time_signature_confidence = (section["time_signature_confidence"] as? Number)?.toDouble() ?: 0.0
            )
        }?.toTypedArray() ?: emptyArray()
    }
    
    private fun parseBars(data: List<*>?): Array<SpotifyAudioBar> {
        return data?.map { item ->
            val bar = item as Map<*, *>
            SpotifyAudioBar(
                start = (bar["start"] as? Number)?.toDouble() ?: 0.0,
                duration = (bar["duration"] as? Number)?.toDouble() ?: 0.0,
                confidence = (bar["confidence"] as? Number)?.toDouble() ?: 0.0
            )
        }?.toTypedArray() ?: emptyArray()
    }
    
    private fun parseBeats(data: List<*>?): Array<SpotifyAudioBeat> {
        return data?.map { item ->
            val beat = item as Map<*, *>
            SpotifyAudioBeat(
                start = (beat["start"] as? Number)?.toDouble() ?: 0.0,
                duration = (beat["duration"] as? Number)?.toDouble() ?: 0.0,
                confidence = (beat["confidence"] as? Number)?.toDouble() ?: 0.0
            )
        }?.toTypedArray() ?: emptyArray()
    }
    
    private fun parseTatums(data: List<*>?): Array<SpotifyAudioTatum> {
        return data?.map { item ->
            val tatum = item as Map<*, *>
            SpotifyAudioTatum(
                start = (tatum["start"] as? Number)?.toDouble() ?: 0.0,
                duration = (tatum["duration"] as? Number)?.toDouble() ?: 0.0,
                confidence = (tatum["confidence"] as? Number)?.toDouble() ?: 0.0
            )
        }?.toTypedArray() ?: emptyArray()
    }
    
    private fun parseSegments(data: List<*>?): Array<SpotifyAudioSegment> {
        return data?.map { item ->
            val segment = item as Map<*, *>
            SpotifyAudioSegment(
                start = (segment["start"] as? Number)?.toDouble() ?: 0.0,
                duration = (segment["duration"] as? Number)?.toDouble() ?: 0.0,
                confidence = (segment["confidence"] as? Number)?.toDouble() ?: 0.0,
                loudness_start = (segment["loudness_start"] as? Number)?.toInt() ?: 0,
                loudness_max_time = (segment["loudness_max_time"] as? Number)?.toInt() ?: 0,
                loudness_max = (segment["loudness_max"] as? Number)?.toInt() ?: 0,
                pitches = (segment["pitches"] as? List<*>)?.map { (it as? Number)?.toDouble() ?: 0.0 }?.toDoubleArray() ?: DoubleArray(12),
                timbre = (segment["timbre"] as? List<*>)?.map { (it as? Number)?.toDouble() ?: 0.0 }?.toDoubleArray() ?: DoubleArray(12)
            )
        }?.toTypedArray() ?: emptyArray()
    }
    
    init {
        logger.info("GenericAnalyser initialized with analysis service URL: $analysisServiceUrl")
    }
}

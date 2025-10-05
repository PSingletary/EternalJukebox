package org.abimon.eternalJukebox.handlers.api

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abimon.eternalJukebox.*
import org.abimon.eternalJukebox.objects.*
import org.abimon.visi.io.ByteArrayDataSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

object AnalysisAPI : IAPI {
    override val mountPath: String = "/analysis"
    private val logger: Logger = LoggerFactory.getLogger("AnalysisApi")

    override fun setup(router: Router) {
        router.get("/analyse/:id").suspendingHandler(this::analyseTrack)
        router.post("/analyse/url").suspendingHandler(this::analyseUrl)
        router.get("/search").suspendingHandler(AnalysisAPI::searchTracks)
        router.post("/upload/:id")
            .handler(BodyHandler.create().setDeleteUploadedFilesOnEnd(true).setBodyLimit(10 * 1000 * 1000))
        router.post("/upload/:id").suspendingHandler(this::upload)
    }

    private suspend fun analyseTrack(context: RoutingContext) {
        val id = context.pathParam("id")
        logger.trace("[{}] Analyzing track with ID: {}", context.clientInfo.userUID, id)
        
        if (!EternalJukebox.storage.shouldStore(EnumStorageType.ANALYSIS)) {
            return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(501).end(
                jsonObjectOf(
                    "error" to "Configured storage method does not support storing ANALYSIS",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        }
        
        // Check for existing analysis in storage
        if (EternalJukebox.storage.isStored("$id.json", EnumStorageType.ANALYSIS)) {
            if (EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS, context, context.clientInfo))
                return

            val data = EternalJukebox.storage.provide("$id.json", EnumStorageType.ANALYSIS, context.clientInfo)
            if (data != null)
                return context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                    .end(data, "application/json")
        }

        // Check for uploaded analysis
        if (EternalJukebox.storage.shouldStore(EnumStorageType.UPLOADED_ANALYSIS)) {
            if (EternalJukebox.storage.isStored("$id.json", EnumStorageType.UPLOADED_ANALYSIS)) {
                if (EternalJukebox.storage.provide(
                        "$id.json",
                        EnumStorageType.UPLOADED_ANALYSIS,
                        context,
                        context.clientInfo
                    )
                ) return

                val data = EternalJukebox.storage.provide(
                    "$id.json",
                    EnumStorageType.UPLOADED_ANALYSIS,
                    context.clientInfo
                )
                if (data != null)
                    return context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                        .end(data, "application/json")
            }
        }
        
        // Try to generate new analysis using Python service
        return generateAndStoreAnalysis(id, context)
    }

    private suspend fun searchTracks(context: RoutingContext) {
        val query = context.request().getParam("query") ?: "Never Gonna Give You Up"
        logger.trace("[{}] Searching for tracks with query: {}", context.clientInfo.userUID, query)
        
        val results = EternalJukebox.analyser.search(query, context.clientInfo)
        context.response().end(JsonArray(results.map(JukeboxInfo::toJsonObject)))
    }
    
    /**
     * New endpoint for analyzing audio from URL
     */
    private suspend fun analyseUrl(context: RoutingContext) {
        val body = context.bodyAsJson
        val url = body.getString("url")
        
        if (url.isNullOrBlank()) {
            return context.response().setStatusCode(400).end(
                jsonObjectOf(
                    "error" to "URL is required",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        }
        
        logger.trace("[{}] Analyzing URL: {}", context.clientInfo.userUID, url)
        
        // Get track info from URL
        val trackInfo = EternalJukebox.analyser.getInfoFromUrl(url, context.clientInfo)
            ?: return context.response().setStatusCode(400).end(
                jsonObjectOf(
                    "error" to "Could not extract track information from URL",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        
        // Generate analysis using Python service
        return generateAnalysisForTrack(trackInfo, context)
    }

    private suspend fun upload(context: RoutingContext) {
        val id = context.pathParam("id")

        if (!EternalJukebox.storage.shouldStore(EnumStorageType.UPLOADED_ANALYSIS)) {
            return context.endWithStatusCode(502) {
                this["error"] = "This server does not support uploaded analysis"
            }
        } else if (context.fileUploads().isEmpty()) {
            return context.endWithStatusCode(400) {
                this["error"] = "No file uploads"
            }
        }

        val uploadedFile = File(context.fileUploads().first().uploadedFileName())
        var track: JukeboxTrack? = null

        val info = EternalJukebox.analyser.getInfo(id, context.clientInfo) ?: run {
            uploadedFile.guaranteeDelete()
            logger.warn("[{}] Failed to get track info for {}", context.clientInfo.userUID, id)
            return context.endWithStatusCode(400) {
                this["error"] = "Failed to get track info"
            }
        }

        try {
            val mapResponse = withContext(Dispatchers.IO) {
                EternalJukebox.jsonMapper.tryReadValue(uploadedFile.readBytes(), Map::class)
            } ?: return context.endWithStatusCode(400) { this["error"] = "Analysis file could not be parsed" }

            val obj = JsonObject(mapResponse.mapKeys { (key) -> "$key" })
            track = JukeboxTrack(
                info,
                withContext(Dispatchers.IO) {
                    JukeboxAnalysis(
                        EternalJukebox.jsonMapper.readValue(
                            obj.getJsonArray("sections").toString(),
                            Array<SpotifyAudioSection>::class.java
                        ),
                        EternalJukebox.jsonMapper.readValue(
                            obj.getJsonArray("bars").toString(),
                            Array<SpotifyAudioBar>::class.java
                        ),
                        EternalJukebox.jsonMapper.readValue(
                            obj.getJsonArray("beats").toString(),
                            Array<SpotifyAudioBeat>::class.java
                        ),
                        EternalJukebox.jsonMapper.readValue(
                            obj.getJsonArray("tatums").toString(),
                            Array<SpotifyAudioTatum>::class.java
                        ),
                        EternalJukebox.jsonMapper.readValue(
                            obj.getJsonArray("segments").toString(),
                            Array<SpotifyAudioSegment>::class.java
                        )
                    )
                },
                JukeboxSummary((mapResponse["track"] as Map<*, *>)["duration"].toString().toDouble())
            )

            if (track.info.duration != (track.audio_summary.duration * 1000).toInt()) {
                return context.endWithStatusCode(400) {
                    this["error"] = "Track duration does not match analysis duration. This is likely due to an incorrect analysis file. Make sure it is for the song ${info.name} by ${info.artist}"
                }
            }

            context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                .end(track.toJsonObject())

            withContext(Dispatchers.IO) {
                EternalJukebox.storage.store(
                    "$id.json",
                    EnumStorageType.UPLOADED_ANALYSIS,
                    ByteArrayDataSource(track.toJsonObject().toString().toByteArray(Charsets.UTF_8)),
                    "application/json",
                    context.clientInfo
                )
            }
        } finally {
            uploadedFile.guaranteeDelete()

            if (track == null) {
                context.endWithStatusCode(400) { this["error"] = "Analysis file could not be parsed" }
            } else {
                logger.info("[{}] Uploaded analysis for {}", context.clientInfo.userUID, id)
            }
        }
    }


    /**
     * Generate analysis for a track ID and store it
     */
    private suspend fun generateAndStoreAnalysis(id: String, context: RoutingContext) {
        // Get track info
        val trackInfo = EternalJukebox.analyser.getInfo(id, context.clientInfo)
            ?: return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(400).end(
                jsonObjectOf(
                    "error" to "Could not find track information for ID: $id",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        
        return generateAnalysisForTrack(trackInfo, context)
    }
    
    /**
     * Generate analysis for a track and store it
     */
    private suspend fun generateAnalysisForTrack(trackInfo: JukeboxInfo, context: RoutingContext) {
        logger.info("[{}] Generating analysis for track: {} by {}", 
            context.clientInfo.userUID, trackInfo.title, trackInfo.artist)
        
        try {
            // Get audio source URL
            val audioUrl = getAudioUrlForTrack(trackInfo, context)
                ?: return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Could not get audio URL for track",
                        "client_uid" to context.clientInfo.userUID
                    )
                )
            
            // Generate analysis using Python service
            val analysis = EternalJukebox.analyser.generateAnalysis(audioUrl, context.clientInfo)
                ?: return context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(500).end(
                    jsonObjectOf(
                        "error" to "Failed to generate audio analysis. Analysis service may be unavailable.",
                        "client_uid" to context.clientInfo.userUID
                    )
                )
            
            // Create JukeboxTrack
            val track = JukeboxTrack(
                trackInfo,
                analysis,
                JukeboxSummary(trackInfo.duration / 1000.0)
            )
            
            // Store analysis
            withContext(Dispatchers.IO) {
                EternalJukebox.storage.store(
                    "${trackInfo.id}.json",
                    EnumStorageType.ANALYSIS,
                    ByteArrayDataSource(track.toJsonObject().toString().toByteArray(Charsets.UTF_8)),
                    "application/json",
                    context.clientInfo
                )
            }
            
            // Return analysis
            context.response().putHeader("X-Client-UID", context.clientInfo.userUID)
                .end(track.toJsonObject())
                
            logger.info("[{}] Successfully generated and stored analysis for {}", 
                context.clientInfo.userUID, trackInfo.id)
                
        } catch (e: Exception) {
            logger.error("[{}] Error generating analysis for {}: {}", 
                context.clientInfo.userUID, trackInfo.id, e.message)
            
            context.response().putHeader("X-Client-UID", context.clientInfo.userUID).setStatusCode(500).end(
                jsonObjectOf(
                    "error" to "Internal error while generating analysis: ${e.message}",
                    "client_uid" to context.clientInfo.userUID
                )
            )
        }
    }
    
    /**
     * Get audio URL for a track using the audio source
     */
    private suspend fun getAudioUrlForTrack(trackInfo: JukeboxInfo, context: RoutingContext): String? {
        return try {
            // Try to get cached location first
            val cachedLocation = EternalJukebox.audio?.provideLocation(trackInfo, context.clientInfo)
            if (cachedLocation != null) {
                logger.trace("[{}] Using cached location for {}", context.clientInfo.userUID, trackInfo.id)
                return cachedLocation.toString()
            }
            
            // If no cached location, use the track's URL directly
            if (trackInfo.url.isNotEmpty()) {
                logger.trace("[{}] Using track URL for {}", context.clientInfo.userUID, trackInfo.id)
                return trackInfo.url
            }
            
            logger.warn("[{}] No audio URL available for track {}", context.clientInfo.userUID, trackInfo.id)
            null
            
        } catch (e: Exception) {
            logger.error("[{}] Error getting audio URL for {}: {}", 
                context.clientInfo.userUID, trackInfo.id, e.message)
            null
        }
    }

    init {
        logger.info("Initialised Analysis Api")
    }
}

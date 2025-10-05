package org.abimon.eternalJukebox.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.ext.web.handler.RateLimitingHandler
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.LocalSessionStore
import org.abimon.eternalJukebox.EternalJukebox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.security.Key
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/**
 * Security configuration and middleware for EternalJukebox
 * Implements JWT authentication, rate limiting, CORS, and security headers
 */
object SecurityConfig {
    private val logger: Logger = LoggerFactory.getLogger("SecurityConfig")
    
    // JWT configuration
    private val jwtSecret: String = System.getenv("JWT_SECRET") ?: "eternaljukebox-default-secret-key-change-in-production"
    private val jwtExpirationMs: Long = TimeUnit.HOURS.toMillis(24) // 24 hours
    private val jwtRefreshExpirationMs: Long = TimeUnit.DAYS.toMillis(7) // 7 days
    
    // Rate limiting configuration
    private val rateLimitRequests: Int = 100 // requests per window
    private val rateLimitWindowMs: Long = TimeUnit.MINUTES.toMillis(15) // 15 minutes
    
    // Security headers
    private val securityHeaders = mapOf(
        "X-Content-Type-Options" to "nosniff",
        "X-Frame-Options" to "DENY",
        "X-XSS-Protection" to "1; mode=block",
        "Strict-Transport-Security" to "max-age=31536000; includeSubDomains",
        "Content-Security-Policy" to "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self'",
        "Referrer-Policy" to "strict-origin-when-cross-origin"
    )
    
    /**
     * Initialize JWT authentication
     */
    fun createJWTAuth(): JWTAuth {
        val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
        
        val config = JsonObject()
            .put("key", key)
            .put("algorithm", "HS256")
        
        return JWTAuth.create(EternalJukebox.vertx, config)
    }
    
    /**
     * Generate JWT token for user
     */
    fun generateToken(userId: String, isRefreshToken: Boolean = false): String {
        val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
        val expiration = if (isRefreshToken) jwtRefreshExpirationMs else jwtExpirationMs
        
        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expiration))
            .claim("type", if (isRefreshToken) "refresh" else "access")
            .claim("userId", userId)
            .signWith(key)
            .compact()
    }
    
    /**
     * Validate JWT token
     */
    fun validateToken(token: String): Boolean {
        return try {
            val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
            Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
            true
        } catch (e: Exception) {
            logger.warn("Invalid JWT token: ${e.message}")
            false
        }
    }
    
    /**
     * Extract user ID from JWT token
     */
    fun getUserIdFromToken(token: String): String? {
        return try {
            val key: SecretKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body
            claims.subject
        } catch (e: Exception) {
            logger.warn("Failed to extract user ID from token: ${e.message}")
            null
        }
    }
    
    /**
     * Setup security middleware for router
     */
    fun setupSecurityMiddleware(router: Router) {
        logger.info("Setting up security middleware")
        
        // CORS configuration
        router.route().handler(CorsHandler.create()
            .addOrigin("http://localhost:8080")
            .addOrigin("https://localhost:8080")
            .addOrigin("http://127.0.0.1:8080")
            .addOrigin("https://127.0.0.1:8080")
            .allowedMethods(setOf("GET", "POST", "PUT", "DELETE", "OPTIONS"))
            .allowedHeaders(setOf("Content-Type", "Authorization", "X-Requested-With"))
            .allowCredentials(true)
        )
        
        // Body handler with size limits
        router.route().handler(BodyHandler.create()
            .setBodyLimit(50 * 1024 * 1024) // 50MB limit
            .setDeleteUploadedFilesOnEnd(true)
        )
        
        // Session handler
        router.route().handler(SessionHandler.create(LocalSessionStore.create(EternalJukebox.vertx)))
        
        // Rate limiting
        router.route().handler(RateLimitingHandler.create(rateLimitRequests, rateLimitWindowMs))
        
        // Security headers
        router.route().handler { ctx ->
            securityHeaders.forEach { (header, value) ->
                ctx.response().putHeader(header, value)
            }
            ctx.next()
        }
        
        // Request logging
        router.route().handler { ctx ->
            val startTime = System.currentTimeMillis()
            ctx.addHeadersEndHandler {
                val duration = System.currentTimeMillis() - startTime
                logger.info("${ctx.request().method()} ${ctx.request().path()} - ${ctx.response().statusCode()} (${duration}ms)")
            }
            ctx.next()
        }
        
        logger.info("Security middleware setup complete")
    }
    
    /**
     * Setup authentication middleware for protected routes
     */
    fun setupAuthMiddleware(router: Router, jwtAuth: JWTAuth) {
        logger.info("Setting up authentication middleware")
        
        // Protected API routes
        router.route("/api/analysis/*").handler(JWTAuthHandler.create(jwtAuth))
        router.route("/api/audio/*").handler(JWTAuthHandler.create(jwtAuth))
        router.route("/api/site/*").handler(JWTAuthHandler.create(jwtAuth))
        
        // Admin routes (if any)
        router.route("/api/admin/*").handler(JWTAuthHandler.create(jwtAuth))
        
        logger.info("Authentication middleware setup complete")
    }
    
    /**
     * Validate URL to prevent SSRF attacks
     */
    fun isValidUrl(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val allowedSchemes = setOf("https", "http")
            val allowedHosts = setOf(
                "youtube.com", "www.youtube.com", "youtu.be",
                "soundcloud.com", "www.soundcloud.com",
                "bandcamp.com", "www.bandcamp.com",
                "vimeo.com", "www.vimeo.com",
                "localhost", "127.0.0.1"
            )
            
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            
            scheme in allowedSchemes && host in allowedHosts
        } catch (e: Exception) {
            logger.warn("Invalid URL format: $url - ${e.message}")
            false
        }
    }
    
    /**
     * Sanitize input to prevent injection attacks
     */
    fun sanitizeInput(input: String): String {
        return input
            .replace(Regex("[<>\"'&]"), "") // Remove HTML/XML characters
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "") // Remove control characters
            .trim()
    }
    
    /**
     * Generate secure random token
     */
    fun generateSecureToken(): String {
        val bytes = ByteArray(32)
        Random().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    /**
     * Hash password securely
     */
    fun hashPassword(password: String): String {
        return org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
    }
    
    /**
     * Verify password against hash
     */
    fun verifyPassword(password: String, hash: String): Boolean {
        return org.mindrot.jbcrypt.BCrypt.checkpw(password, hash)
    }
}

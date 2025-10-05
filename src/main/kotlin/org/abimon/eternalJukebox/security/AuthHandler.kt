package org.abimon.eternalJukebox.security

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.abimon.eternalJukebox.EternalJukebox
import org.abimon.eternalJukebox.jsonObjectOf
import org.abimon.eternalJukebox.suspendingHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Authentication handler for user management and JWT token operations
 */
object AuthHandler {
    private val logger: Logger = LoggerFactory.getLogger("AuthHandler")
    
    // In-memory user store (replace with database in production)
    private val users = ConcurrentHashMap<String, User>()
    
    data class User(
        val id: String,
        val username: String,
        val passwordHash: String,
        val email: String? = null,
        val createdAt: Long = System.currentTimeMillis(),
        val lastLogin: Long? = null,
        val isActive: Boolean = true
    )
    
    /**
     * Setup authentication routes
     */
    fun setup(router: Router) {
        logger.info("Setting up authentication routes")
        
        val authRouter = Router.router(EternalJukebox.vertx)
        
        // Authentication routes
        authRouter.post("/register").handler(BodyHandler.create()).suspendingHandler(::register)
        authRouter.post("/login").handler(BodyHandler.create()).suspendingHandler(::login)
        authRouter.post("/refresh").handler(BodyHandler.create()).suspendingHandler(::refreshToken)
        authRouter.post("/logout").suspendingHandler(::logout)
        authRouter.get("/me").suspendingHandler(::getCurrentUser)
        
        // Mount auth routes
        router.mountSubRouter("/auth", authRouter)
        
        logger.info("Authentication routes setup complete")
    }
    
    /**
     * Register a new user
     */
    private suspend fun register(context: RoutingContext) {
        try {
            val body = context.bodyAsJson
            val username = body.getString("username")?.let { SecurityConfig.sanitizeInput(it) }
            val password = body.getString("password")
            val email = body.getString("email")?.let { SecurityConfig.sanitizeInput(it) }
            
            // Validate input
            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                return context.response().setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Username and password are required",
                        "code" to "MISSING_CREDENTIALS"
                    )
                )
            }
            
            if (username.length < 3 || username.length > 50) {
                return context.response().setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Username must be between 3 and 50 characters",
                        "code" to "INVALID_USERNAME"
                    )
                )
            }
            
            if (password.length < 8) {
                return context.response().setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Password must be at least 8 characters",
                        "code" to "WEAK_PASSWORD"
                    )
                )
            }
            
            // Check if user already exists
            if (users.values.any { it.username == username }) {
                return context.response().setStatusCode(409).end(
                    jsonObjectOf(
                        "error" to "Username already exists",
                        "code" to "USER_EXISTS"
                    )
                )
            }
            
            // Create new user
            val userId = SecurityConfig.generateSecureToken()
            val passwordHash = SecurityConfig.hashPassword(password)
            val user = User(
                id = userId,
                username = username,
                passwordHash = passwordHash,
                email = email
            )
            
            users[userId] = user
            
            // Generate tokens
            val accessToken = SecurityConfig.generateToken(userId)
            val refreshToken = SecurityConfig.generateToken(userId, isRefreshToken = true)
            
            logger.info("User registered: $username")
            
            context.response().end(
                jsonObjectOf(
                    "message" to "User registered successfully",
                    "user" to jsonObjectOf(
                        "id" to userId,
                        "username" to username,
                        "email" to email
                    ),
                    "accessToken" to accessToken,
                    "refreshToken" to refreshToken,
                    "expiresIn" to SecurityConfig.jwtExpirationMs
                )
            )
            
        } catch (e: Exception) {
            logger.error("Registration error: ${e.message}", e)
            context.response().setStatusCode(500).end(
                jsonObjectOf(
                    "error" to "Internal server error",
                    "code" to "REGISTRATION_ERROR"
                )
            )
        }
    }
    
    /**
     * Login user
     */
    private suspend fun login(context: RoutingContext) {
        try {
            val body = context.bodyAsJson
            val username = body.getString("username")?.let { SecurityConfig.sanitizeInput(it) }
            val password = body.getString("password")
            
            // Validate input
            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                return context.response().setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Username and password are required",
                        "code" to "MISSING_CREDENTIALS"
                    )
                )
            }
            
            // Find user
            val user = users.values.find { it.username == username && it.isActive }
            if (user == null || !SecurityConfig.verifyPassword(password, user.passwordHash)) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "Invalid credentials",
                        "code" to "INVALID_CREDENTIALS"
                    )
                )
            }
            
            // Update last login
            users[user.id] = user.copy(lastLogin = System.currentTimeMillis())
            
            // Generate tokens
            val accessToken = SecurityConfig.generateToken(user.id)
            val refreshToken = SecurityConfig.generateToken(user.id, isRefreshToken = true)
            
            logger.info("User logged in: $username")
            
            context.response().end(
                jsonObjectOf(
                    "message" to "Login successful",
                    "user" to jsonObjectOf(
                        "id" to user.id,
                        "username" to user.username,
                        "email" to user.email,
                        "lastLogin" to user.lastLogin
                    ),
                    "accessToken" to accessToken,
                    "refreshToken" to refreshToken,
                    "expiresIn" to SecurityConfig.jwtExpirationMs
                )
            )
            
        } catch (e: Exception) {
            logger.error("Login error: ${e.message}", e)
            context.response().setStatusCode(500).end(
                jsonObjectOf(
                    "error" to "Internal server error",
                    "code" to "LOGIN_ERROR"
                )
            )
        }
    }
    
    /**
     * Refresh access token
     */
    private suspend fun refreshToken(context: RoutingContext) {
        try {
            val body = context.bodyAsJson
            val refreshToken = body.getString("refreshToken")
            
            if (refreshToken.isNullOrBlank()) {
                return context.response().setStatusCode(400).end(
                    jsonObjectOf(
                        "error" to "Refresh token is required",
                        "code" to "MISSING_REFRESH_TOKEN"
                    )
                )
            }
            
            // Validate refresh token
            if (!SecurityConfig.validateToken(refreshToken)) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "Invalid refresh token",
                        "code" to "INVALID_REFRESH_TOKEN"
                    )
                )
            }
            
            val userId = SecurityConfig.getUserIdFromToken(refreshToken)
            if (userId == null) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "Invalid refresh token",
                        "code" to "INVALID_REFRESH_TOKEN"
                    )
                )
            }
            
            val user = users[userId]
            if (user == null || !user.isActive) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "User not found or inactive",
                        "code" to "USER_NOT_FOUND"
                    )
                )
            }
            
            // Generate new access token
            val newAccessToken = SecurityConfig.generateToken(userId)
            
            context.response().end(
                jsonObjectOf(
                    "message" to "Token refreshed successfully",
                    "accessToken" to newAccessToken,
                    "expiresIn" to SecurityConfig.jwtExpirationMs
                )
            )
            
        } catch (e: Exception) {
            logger.error("Token refresh error: ${e.message}", e)
            context.response().setStatusCode(500).end(
                jsonObjectOf(
                    "error" to "Internal server error",
                    "code" to "REFRESH_ERROR"
                )
            )
        }
    }
    
    /**
     * Logout user
     */
    private suspend fun logout(context: RoutingContext) {
        // In a production system, you would invalidate the token in a blacklist
        // For now, we'll just return success
        context.response().end(
            jsonObjectOf(
                "message" to "Logout successful"
            )
        )
    }
    
    /**
     * Get current user information
     */
    private suspend fun getCurrentUser(context: RoutingContext) {
        try {
            val authHeader = context.request().getHeader("Authorization")
            val token = authHeader?.removePrefix("Bearer ") ?: ""
            
            if (token.isBlank()) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "Authorization header required",
                        "code" to "MISSING_AUTH_HEADER"
                    )
                )
            }
            
            val userId = SecurityConfig.getUserIdFromToken(token)
            if (userId == null) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "Invalid token",
                        "code" to "INVALID_TOKEN"
                    )
                )
            }
            
            val user = users[userId]
            if (user == null || !user.isActive) {
                return context.response().setStatusCode(401).end(
                    jsonObjectOf(
                        "error" to "User not found or inactive",
                        "code" to "USER_NOT_FOUND"
                    )
                )
            }
            
            context.response().end(
                jsonObjectOf(
                    "user" to jsonObjectOf(
                        "id" to user.id,
                        "username" to user.username,
                        "email" to user.email,
                        "createdAt" to user.createdAt,
                        "lastLogin" to user.lastLogin
                    )
                )
            )
            
        } catch (e: Exception) {
            logger.error("Get current user error: ${e.message}", e)
            context.response().setStatusCode(500).end(
                jsonObjectOf(
                    "error" to "Internal server error",
                    "code" to "USER_INFO_ERROR"
                )
            )
        }
    }
}

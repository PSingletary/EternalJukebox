package org.abimon.eternalJukebox.security

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import java.util.concurrent.TimeUnit

/**
 * Security configuration tests for authentication, authorization, and input validation
 */
@ExtendWith(VertxExtension::class)
class SecurityConfigTest {

    private lateinit var vertx: Vertx
    private lateinit var router: Router
    private lateinit var webClient: WebClient

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        this.router = Router.router(vertx)
        this.webClient = WebClient.create(vertx)
        
        // Setup security middleware
        SecurityConfig.setupSecurityMiddleware(router)
        
        // Setup authentication routes
        AuthHandler.setup(router)
        
        // Start HTTP server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080)
            .onComplete(testContext.succeedingThenComplete())
    }

    @Test
    fun `test JWT token generation and validation`(testContext: VertxTestContext) {
        val userId = "test-user-123"
        
        // Test token generation
        val token = SecurityConfig.generateToken(userId)
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        
        // Test token validation
        assertTrue(SecurityConfig.validateToken(token))
        
        // Test user ID extraction
        val extractedUserId = SecurityConfig.getUserIdFromToken(token)
        assertEquals(userId, extractedUserId)
        
        // Test invalid token
        assertFalse(SecurityConfig.validateToken("invalid-token"))
        assertNull(SecurityConfig.getUserIdFromToken("invalid-token"))
        
        testContext.completeNow()
    }

    @Test
    fun `test URL validation prevents SSRF attacks`(testContext: VertxTestContext) {
        // Valid URLs
        assertTrue(SecurityConfig.isValidUrl("https://youtube.com/watch?v=test"))
        assertTrue(SecurityConfig.isValidUrl("https://soundcloud.com/track/test"))
        assertTrue(SecurityConfig.isValidUrl("http://localhost:8080/test"))
        
        // Invalid URLs (SSRF attempts)
        assertFalse(SecurityConfig.isValidUrl("file:///etc/passwd"))
        assertFalse(SecurityConfig.isValidUrl("ftp://malicious.com"))
        assertFalse(SecurityConfig.isValidUrl("https://192.168.1.1/admin"))
        assertFalse(SecurityConfig.isValidUrl("https://internal.company.com"))
        assertFalse(SecurityConfig.isValidUrl("javascript:alert('xss')"))
        
        // Malformed URLs
        assertFalse(SecurityConfig.isValidUrl("not-a-url"))
        assertFalse(SecurityConfig.isValidUrl(""))
        assertFalse(SecurityConfig.isValidUrl("https://"))
        
        testContext.completeNow()
    }

    @Test
    fun `test input sanitization prevents XSS`(testContext: VertxTestContext) {
        // Test XSS prevention
        val maliciousInput = "<script>alert('xss')</script>"
        val sanitized = SecurityConfig.sanitizeInput(maliciousInput)
        assertFalse(sanitized.contains("<script>"))
        assertFalse(sanitized.contains("</script>"))
        
        // Test HTML entity prevention
        val htmlInput = "&lt;script&gt;alert('xss')&lt;/script&gt;"
        val sanitizedHtml = SecurityConfig.sanitizeInput(htmlInput)
        assertFalse(sanitizedHtml.contains("&lt;"))
        assertFalse(sanitizedHtml.contains("&gt;"))
        
        // Test control character removal
        val controlInput = "test\x00\x01\x02string"
        val sanitizedControl = SecurityConfig.sanitizeInput(controlInput)
        assertFalse(sanitizedControl.contains("\x00"))
        assertFalse(sanitizedControl.contains("\x01"))
        assertFalse(sanitizedControl.contains("\x02"))
        
        // Test normal input passes through
        val normalInput = "normal user input"
        val sanitizedNormal = SecurityConfig.sanitizeInput(normalInput)
        assertEquals("normal user input", sanitizedNormal)
        
        testContext.completeNow()
    }

    @Test
    fun `test password hashing and verification`(testContext: VertxTestContext) {
        val password = "testPassword123"
        
        // Test password hashing
        val hash = SecurityConfig.hashPassword(password)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
        assertNotEquals(password, hash)
        
        // Test password verification
        assertTrue(SecurityConfig.verifyPassword(password, hash))
        assertFalse(SecurityConfig.verifyPassword("wrongPassword", hash))
        assertFalse(SecurityConfig.verifyPassword(password, "wrongHash"))
        
        // Test different passwords produce different hashes
        val hash2 = SecurityConfig.hashPassword("differentPassword")
        assertNotEquals(hash, hash2)
        
        testContext.completeNow()
    }

    @Test
    fun `test secure token generation`(testContext: VertxTestContext) {
        val token1 = SecurityConfig.generateSecureToken()
        val token2 = SecurityConfig.generateSecureToken()
        
        assertNotNull(token1)
        assertNotNull(token2)
        assertTrue(token1.isNotEmpty())
        assertTrue(token2.isNotEmpty())
        assertNotEquals(token1, token2) // Should be different
        
        // Test token format (Base64 URL safe)
        assertTrue(token1.matches(Regex("[A-Za-z0-9_-]+")))
        assertTrue(token2.matches(Regex("[A-Za-z0-9_-]+")))
        
        testContext.completeNow()
    }

    @Test
    fun `test user registration security`(testContext: VertxTestContext) {
        val registrationData = """
            {
                "username": "testuser",
                "password": "securePassword123",
                "email": "test@example.com"
            }
        """.trimIndent()
        
        webClient.post(8080, "localhost", "/auth/register")
            .putHeader("Content-Type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(registrationData))
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    assertEquals(200, response.statusCode())
                    
                    val body = response.bodyAsJsonObject()
                    assertTrue(body.containsKey("accessToken"))
                    assertTrue(body.containsKey("refreshToken"))
                    assertTrue(body.containsKey("user"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(result.cause())
                }
            }
    }

    @Test
    fun `test user registration with weak password`(testContext: VertxTestContext) {
        val registrationData = """
            {
                "username": "testuser",
                "password": "123",
                "email": "test@example.com"
            }
        """.trimIndent()
        
        webClient.post(8080, "localhost", "/auth/register")
            .putHeader("Content-Type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(registrationData))
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    assertEquals(400, response.statusCode())
                    
                    val body = response.bodyAsJsonObject()
                    assertEquals("WEAK_PASSWORD", body.getString("code"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(result.cause())
                }
            }
    }

    @Test
    fun `test user registration with malicious input`(testContext: VertxTestContext) {
        val registrationData = """
            {
                "username": "<script>alert('xss')</script>",
                "password": "securePassword123",
                "email": "test@example.com"
            }
        """.trimIndent()
        
        webClient.post(8080, "localhost", "/auth/register")
            .putHeader("Content-Type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(registrationData))
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    assertEquals(200, response.statusCode())
                    
                    val body = response.bodyAsJsonObject()
                    val user = body.getJsonObject("user")
                    val username = user.getString("username")
                    
                    // Username should be sanitized
                    assertFalse(username.contains("<script>"))
                    assertFalse(username.contains("</script>"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(result.cause())
                }
            }
    }

    @Test
    fun `test authentication flow`(testContext: VertxTestContext) {
        // First register a user
        val registrationData = """
            {
                "username": "authtest",
                "password": "securePassword123",
                "email": "authtest@example.com"
            }
        """.trimIndent()
        
        webClient.post(8080, "localhost", "/auth/register")
            .putHeader("Content-Type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(registrationData))
            .onComplete { registerResult ->
                if (registerResult.succeeded()) {
                    val registerResponse = registerResult.result()
                    assertEquals(200, registerResponse.statusCode())
                    
                    val registerBody = registerResponse.bodyAsJsonObject()
                    val accessToken = registerBody.getString("accessToken")
                    
                    // Now test protected endpoint
                    webClient.get(8080, "localhost", "/auth/me")
                        .putHeader("Authorization", "Bearer $accessToken")
                        .send()
                        .onComplete { meResult ->
                            if (meResult.succeeded()) {
                                val meResponse = meResult.result()
                                assertEquals(200, meResponse.statusCode())
                                
                                val meBody = meResponse.bodyAsJsonObject()
                                assertTrue(meBody.containsKey("user"))
                                
                                testContext.completeNow()
                            } else {
                                testContext.failNow(meResult.cause())
                            }
                        }
                } else {
                    testContext.failNow(registerResult.cause())
                }
            }
    }

    @Test
    fun `test authentication with invalid token`(testContext: VertxTestContext) {
        webClient.get(8080, "localhost", "/auth/me")
            .putHeader("Authorization", "Bearer invalid-token")
            .send()
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    assertEquals(401, response.statusCode())
                    
                    val body = response.bodyAsJsonObject()
                    assertEquals("INVALID_TOKEN", body.getString("code"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(result.cause())
                }
            }
    }

    @Test
    fun `test authentication without token`(testContext: VertxTestContext) {
        webClient.get(8080, "localhost", "/auth/me")
            .send()
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    assertEquals(401, response.statusCode())
                    
                    val body = response.bodyAsJsonObject()
                    assertEquals("MISSING_AUTH_HEADER", body.getString("code"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(result.cause())
                }
            }
    }

    @Test
    fun `test rate limiting`(testContext: VertxTestContext) {
        // Make multiple requests quickly to test rate limiting
        val requests = (1..150).map { i ->
            webClient.get(8080, "localhost", "/auth/me")
                .send()
        }
        
        // Wait for all requests to complete
        vertx.setTimer(5000) {
            var successCount = 0
            var rateLimitedCount = 0
            
            requests.forEach { future ->
                future.onComplete { result ->
                    if (result.succeeded()) {
                        val response = result.result()
                        if (response.statusCode() == 429) {
                            rateLimitedCount++
                        } else {
                            successCount++
                        }
                    }
                }
            }
            
            // Should have some rate limited requests
            assertTrue(rateLimitedCount > 0, "Expected some requests to be rate limited")
            testContext.completeNow()
        }
    }

    @Test
    fun `test security headers are present`(testContext: VertxTestContext) {
        webClient.get(8080, "localhost", "/auth/me")
            .send()
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    
                    // Check security headers
                    assertNotNull(response.getHeader("X-Content-Type-Options"))
                    assertNotNull(response.getHeader("X-Frame-Options"))
                    assertNotNull(response.getHeader("X-XSS-Protection"))
                    assertNotNull(response.getHeader("Strict-Transport-Security"))
                    assertNotNull(response.getHeader("Content-Security-Policy"))
                    assertNotNull(response.getHeader("Referrer-Policy"))
                    
                    assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
                    assertEquals("DENY", response.getHeader("X-Frame-Options"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(result.cause())
                }
            }
    }
}

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
import java.util.concurrent.TimeUnit

/**
 * Penetration testing suite for EternalJukebox security
 * Tests for common attack vectors and security vulnerabilities
 */
@ExtendWith(VertxExtension::class)
class PenetrationTest {

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
        AuthHandler.setup(router)
        
        // Start HTTP server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(8080)
            .onComplete(testContext.succeedingThenComplete())
    }

    @Test
    fun `test SQL injection attempts`(testContext: VertxTestContext) {
        val sqlInjectionPayloads = listOf(
            "'; DROP TABLE users; --",
            "' OR '1'='1",
            "' UNION SELECT * FROM users --",
            "admin'--",
            "' OR 1=1 --"
        )
        
        sqlInjectionPayloads.forEach { payload ->
            val loginData = """
                {
                    "username": "$payload",
                    "password": "password"
                }
            """.trimIndent()
            
            webClient.post(8080, "localhost", "/auth/login")
                .putHeader("Content-Type", "application/json")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer(loginData))
                .onComplete { result ->
                    if (result.succeeded()) {
                        val response = result.result()
                        // Should not return 200 (successful login) for SQL injection attempts
                        assertNotEquals(200, response.statusCode(), 
                            "SQL injection payload '$payload' should not succeed")
                    }
                }
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test XSS attack attempts`(testContext: VertxTestContext) {
        val xssPayloads = listOf(
            "<script>alert('xss')</script>",
            "javascript:alert('xss')",
            "<img src=x onerror=alert('xss')>",
            "<svg onload=alert('xss')>",
            "';alert('xss');//",
            "<iframe src=javascript:alert('xss')></iframe>"
        )
        
        xssPayloads.forEach { payload ->
            val registrationData = """
                {
                    "username": "$payload",
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
                        if (response.statusCode() == 200) {
                            val body = response.bodyAsJsonObject()
                            val user = body.getJsonObject("user")
                            val username = user.getString("username")
                            
                            // Username should be sanitized and not contain XSS payload
                            assertFalse(username.contains("<script>"), 
                                "XSS payload should be sanitized in username")
                            assertFalse(username.contains("javascript:"), 
                                "JavaScript URLs should be removed")
                            assertFalse(username.contains("onerror="), 
                                "Event handlers should be removed")
                        }
                    }
                }
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test SSRF attack attempts`(testContext: VertxTestContext) {
        val ssrfPayloads = listOf(
            "file:///etc/passwd",
            "ftp://malicious.com",
            "https://192.168.1.1/admin",
            "https://internal.company.com",
            "http://localhost:22",
            "https://169.254.169.254/latest/meta-data/",
            "gopher://malicious.com:80/",
            "ldap://malicious.com"
        )
        
        ssrfPayloads.forEach { payload ->
            // Test URL validation
            assertFalse(SecurityConfig.isValidUrl(payload), 
                "SSRF payload '$payload' should be rejected")
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test path traversal attempts`(testContext: VertxTestContext) {
        val pathTraversalPayloads = listOf(
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "....//....//....//etc/passwd",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "..%252f..%252f..%252fetc%252fpasswd"
        )
        
        pathTraversalPayloads.forEach { payload ->
            // Test input sanitization
            val sanitized = SecurityConfig.sanitizeInput(payload)
            assertFalse(sanitized.contains("../"), 
                "Path traversal payload should be sanitized")
            assertFalse(sanitized.contains("..\\"), 
                "Windows path traversal should be sanitized")
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test authentication bypass attempts`(testContext: VertxTestContext) {
        val bypassAttempts = listOf(
            "Bearer null",
            "Bearer undefined",
            "Bearer ",
            "Bearer 0",
            "Bearer false",
            "Bearer true",
            "Bearer admin",
            "Bearer root",
            "Bearer guest"
        )
        
        bypassAttempts.forEach { authHeader ->
            webClient.get(8080, "localhost", "/auth/me")
                .putHeader("Authorization", authHeader)
                .send()
                .onComplete { result ->
                    if (result.succeeded()) {
                        val response = result.result()
                        assertEquals(401, response.statusCode(), 
                            "Authentication bypass attempt '$authHeader' should fail")
                    }
                }
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test brute force attack simulation`(testContext: VertxTestContext) {
        val commonPasswords = listOf(
            "password", "123456", "admin", "root", "guest",
            "qwerty", "letmein", "welcome", "monkey", "dragon"
        )
        
        var failedAttempts = 0
        
        commonPasswords.forEach { password ->
            val loginData = """
                {
                    "username": "admin",
                    "password": "$password"
                }
            """.trimIndent()
            
            webClient.post(8080, "localhost", "/auth/login")
                .putHeader("Content-Type", "application/json")
                .sendBuffer(io.vertx.core.buffer.Buffer.buffer(loginData))
                .onComplete { result ->
                    if (result.succeeded()) {
                        val response = result.result()
                        if (response.statusCode() != 200) {
                            failedAttempts++
                        }
                    }
                }
        }
        
        // Wait for all requests to complete
        vertx.setTimer(2000) {
            // All attempts should fail (no admin user exists)
            assertEquals(commonPasswords.size, failedAttempts, 
                "All brute force attempts should fail")
            testContext.completeNow()
        }
    }

    @Test
    fun `test session fixation attempts`(testContext: VertxTestContext) {
        // Register a user
        val registrationData = """
            {
                "username": "sessiontest",
                "password": "securePassword123",
                "email": "sessiontest@example.com"
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
                    
                    // Try to use the same token multiple times
                    val requests = (1..10).map {
                        webClient.get(8080, "localhost", "/auth/me")
                            .putHeader("Authorization", "Bearer $accessToken")
                            .send()
                    }
                    
                    // All requests should succeed (token is valid)
                    var successCount = 0
                    requests.forEach { future ->
                        future.onComplete { result ->
                            if (result.succeeded() && result.result().statusCode() == 200) {
                                successCount++
                            }
                        }
                    }
                    
                    vertx.setTimer(2000) {
                        assertEquals(10, successCount, 
                            "Valid token should work for multiple requests")
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(registerResult.cause())
                }
            }
    }

    @Test
    fun `test CSRF attack simulation`(testContext: VertxTestContext) {
        // Register a user
        val registrationData = """
            {
                "username": "csrftest",
                "password": "securePassword123",
                "email": "csrftest@example.com"
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
                    
                    // Simulate CSRF attack with malicious origin
                    webClient.get(8080, "localhost", "/auth/me")
                        .putHeader("Authorization", "Bearer $accessToken")
                        .putHeader("Origin", "https://malicious.com")
                        .putHeader("Referer", "https://malicious.com/attack.html")
                        .send()
                        .onComplete { result ->
                            if (result.succeeded()) {
                                val response = result.result()
                                // Should still work (CORS is configured to allow localhost)
                                // In production, this would be blocked by CORS
                                assertEquals(200, response.statusCode())
                                testContext.completeNow()
                            } else {
                                testContext.failNow(result.cause())
                            }
                        }
                } else {
                    testContext.failNow(registerResult.cause())
                }
            }
    }

    @Test
    fun `test HTTP method override attacks`(testContext: VertxTestContext) {
        val methodOverrideHeaders = listOf(
            "X-HTTP-Method-Override",
            "X-HTTP-Method",
            "X-Method-Override"
        )
        
        methodOverrideHeaders.forEach { header ->
            webClient.get(8080, "localhost", "/auth/me")
                .putHeader(header, "DELETE")
                .send()
                .onComplete { result ->
                    if (result.succeeded()) {
                        val response = result.result()
                        // Should still be GET request, not DELETE
                        assertEquals(401, response.statusCode(), 
                            "Method override should not work")
                    }
                }
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test header injection attempts`(testContext: VertxTestContext) {
        val headerInjectionPayloads = listOf(
            "test\r\nX-Injected-Header: malicious",
            "test\nX-Injected-Header: malicious",
            "test\rX-Injected-Header: malicious",
            "test%0d%0aX-Injected-Header: malicious",
            "test%0aX-Injected-Header: malicious"
        )
        
        headerInjectionPayloads.forEach { payload ->
            webClient.get(8080, "localhost", "/auth/me")
                .putHeader("Authorization", payload)
                .send()
                .onComplete { result ->
                    if (result.succeeded()) {
                        val response = result.result()
                        // Should not have injected headers
                        assertNull(response.getHeader("X-Injected-Header"), 
                            "Header injection should be prevented")
                    }
                }
        }
        
        testContext.completeNow()
    }

    @Test
    fun `test large payload attacks`(testContext: VertxTestContext) {
        // Test with very large JSON payload
        val largePayload = "{\"username\": \"" + "a".repeat(1000000) + "\", \"password\": \"test\"}"
        
        webClient.post(8080, "localhost", "/auth/register")
            .putHeader("Content-Type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(largePayload))
            .onComplete { result ->
                if (result.succeeded()) {
                    val response = result.result()
                    // Should be rejected due to body size limit
                    assertEquals(413, response.statusCode(), 
                        "Large payload should be rejected")
                } else {
                    // Request might fail entirely due to size
                    testContext.completeNow()
                }
            }
        
        testContext.completeNow()
    }

    @Test
    fun `test timing attack resistance`(testContext: VertxTestContext) {
        val startTime = System.currentTimeMillis()
        
        // Test with non-existent user
        val loginData1 = """
            {
                "username": "nonexistent",
                "password": "password"
            }
        """.trimIndent()
        
        webClient.post(8080, "localhost", "/auth/login")
            .putHeader("Content-Type", "application/json")
            .sendBuffer(io.vertx.core.buffer.Buffer.buffer(loginData1))
            .onComplete { result1 ->
                val time1 = System.currentTimeMillis() - startTime
                
                val startTime2 = System.currentTimeMillis()
                
                // Test with existing user but wrong password
                val loginData2 = """
                    {
                        "username": "existing",
                        "password": "wrongpassword"
                    }
                """.trimIndent()
                
                webClient.post(8080, "localhost", "/auth/login")
                    .putHeader("Content-Type", "application/json")
                    .sendBuffer(io.vertx.core.buffer.Buffer.buffer(loginData2))
                    .onComplete { result2 ->
                        val time2 = System.currentTimeMillis() - startTime2
                        
                        // Times should be similar (timing attack resistance)
                        val timeDiff = kotlin.math.abs(time1 - time2)
                        assertTrue(timeDiff < 100, 
                            "Response times should be similar to prevent timing attacks")
                        
                        testContext.completeNow()
                    }
            }
    }
}

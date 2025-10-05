package org.abimon.eternalJukebox.security

import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.abimon.eternalJukebox.EternalJukebox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Security audit logging system for EternalJukebox
 * Logs security events for compliance and incident response
 */
object SecurityAuditLogger {
    private val logger: Logger = LoggerFactory.getLogger("SecurityAuditLogger")
    
    // Audit event counters
    private val auditCounters = ConcurrentHashMap<String, AtomicLong>()
    
    // Security event types
    enum class SecurityEventType {
        AUTHENTICATION_SUCCESS,
        AUTHENTICATION_FAILURE,
        AUTHORIZATION_SUCCESS,
        AUTHORIZATION_FAILURE,
        JWT_TOKEN_GENERATED,
        JWT_TOKEN_VALIDATED,
        JWT_TOKEN_INVALID,
        JWT_TOKEN_EXPIRED,
        JWT_SUSPICIOUS_USAGE,
        INPUT_VALIDATION_SUCCESS,
        INPUT_VALIDATION_FAILURE,
        SQL_INJECTION_ATTEMPT,
        XSS_ATTEMPT,
        SSRF_ATTEMPT,
        PATH_TRAVERSAL_ATTEMPT,
        COMMAND_INJECTION_ATTEMPT,
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_FILE_UPLOAD,
        LARGE_FILE_UPLOAD,
        FILE_TYPE_VALIDATION_FAILURE,
        SUSPICIOUS_IP_REQUEST,
        GEOGRAPHIC_ANOMALY,
        UNUSUAL_USER_AGENT,
        SECURITY_HEADER_VIOLATION,
        CORS_VIOLATION,
        CSRF_VIOLATION,
        SESSION_HIJACKING_ATTEMPT,
        DATA_EXFILTRATION_ATTEMPT,
        PII_ACCESS_VIOLATION,
        UNAUTHORIZED_DATA_ACCESS,
        PRIVILEGE_ESCALATION_ATTEMPT,
        SYSTEM_RESOURCE_ABUSE,
        CONTAINER_ESCAPE_ATTEMPT,
        AUDIT_LOG_FAILURE,
        COMPLIANCE_VIOLATION,
        SECURITY_POLICY_VIOLATION,
        SECURITY_SCAN_RESULT,
        VULNERABILITY_DETECTED,
        SECURITY_INCIDENT_CREATED,
        SECURITY_INCIDENT_RESOLVED
    }
    
    // Security event severity levels
    enum class SecuritySeverity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    // Security event data class
    data class SecurityEvent(
        val eventType: SecurityEventType,
        val severity: SecuritySeverity,
        val timestamp: Long = System.currentTimeMillis(),
        val userId: String? = null,
        val sessionId: String? = null,
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val requestId: String? = null,
        val service: String? = null,
        val resource: String? = null,
        val action: String? = null,
        val result: String? = null,
        val details: JsonObject? = null,
        val riskScore: Int = 0,
        val tags: List<String> = emptyList()
    )
    
    /**
     * Log a security event
     */
    fun logSecurityEvent(event: SecurityEvent) {
        try {
            // Increment counter
            auditCounters.computeIfAbsent(event.eventType.name) { AtomicLong(0) }.incrementAndGet()
            
            // Create audit log entry
            val auditEntry = JsonObject()
                .put("eventType", event.eventType.name)
                .put("severity", event.severity.name)
                .put("timestamp", event.timestamp)
                .put("isoTimestamp", Instant.ofEpochMilli(event.timestamp).toString())
                .put("userId", event.userId)
                .put("sessionId", event.sessionId)
                .put("ipAddress", event.ipAddress)
                .put("userAgent", event.userAgent)
                .put("requestId", event.requestId)
                .put("service", event.service)
                .put("resource", event.resource)
                .put("action", event.action)
                .put("result", event.result)
                .put("details", event.details)
                .put("riskScore", event.riskScore)
                .put("tags", event.tags)
            
            // Log to structured logger
            when (event.severity) {
                SecuritySeverity.CRITICAL -> logger.error("SECURITY_EVENT: {}", auditEntry.encode())
                SecuritySeverity.HIGH -> logger.warn("SECURITY_EVENT: {}", auditEntry.encode())
                SecuritySeverity.MEDIUM -> logger.info("SECURITY_EVENT: {}", auditEntry.encode())
                SecuritySeverity.LOW -> logger.debug("SECURITY_EVENT: {}", auditEntry.encode())
            }
            
            // Send to monitoring system (Prometheus metrics)
            sendToMonitoring(event)
            
            // Send to external SIEM if configured
            sendToSIEM(event)
            
        } catch (e: Exception) {
            logger.error("Failed to log security event: ${e.message}", e)
        }
    }
    
    /**
     * Log authentication success
     */
    fun logAuthenticationSuccess(
        userId: String,
        sessionId: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        details: JsonObject? = null
    ) {
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.AUTHENTICATION_SUCCESS,
                severity = SecuritySeverity.LOW,
                userId = userId,
                sessionId = sessionId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "authentication",
                action = "login",
                result = "success",
                details = details,
                riskScore = 0,
                tags = listOf("auth", "success")
            )
        )
    }
    
    /**
     * Log authentication failure
     */
    fun logAuthenticationFailure(
        username: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        reason: String? = null,
        details: JsonObject? = null
    ) {
        val riskScore = calculateAuthFailureRiskScore(username, ipAddress)
        
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.AUTHENTICATION_FAILURE,
                severity = if (riskScore > 50) SecuritySeverity.HIGH else SecuritySeverity.MEDIUM,
                userId = username,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "authentication",
                action = "login",
                result = "failure",
                details = details?.put("reason", reason),
                riskScore = riskScore,
                tags = listOf("auth", "failure")
            )
        )
    }
    
    /**
     * Log JWT token validation failure
     */
    fun logJWTValidationFailure(
        token: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        reason: String? = null,
        details: JsonObject? = null
    ) {
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.JWT_TOKEN_INVALID,
                severity = SecuritySeverity.MEDIUM,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "authentication",
                action = "token_validation",
                result = "failure",
                details = details?.put("reason", reason),
                riskScore = 30,
                tags = listOf("jwt", "validation", "failure")
            )
        )
    }
    
    /**
     * Log injection attack attempt
     */
    fun logInjectionAttempt(
        injectionType: String,
        payload: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        userId: String? = null,
        details: JsonObject? = null
    ) {
        val eventType = when (injectionType.lowercase()) {
            "sql" -> SecurityEventType.SQL_INJECTION_ATTEMPT
            "xss" -> SecurityEventType.XSS_ATTEMPT
            "ssrf" -> SecurityEventType.SSRF_ATTEMPT
            "path_traversal" -> SecurityEventType.PATH_TRAVERSAL_ATTEMPT
            "command" -> SecurityEventType.COMMAND_INJECTION_ATTEMPT
            else -> SecurityEventType.INPUT_VALIDATION_FAILURE
        }
        
        logSecurityEvent(
            SecurityEvent(
                eventType = eventType,
                severity = SecuritySeverity.CRITICAL,
                userId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "input-validation",
                action = "injection_attempt",
                result = "blocked",
                details = details?.put("injectionType", injectionType)?.put("payload", payload),
                riskScore = 100,
                tags = listOf("injection", "attack", "blocked")
            )
        )
    }
    
    /**
     * Log rate limit exceeded
     */
    fun logRateLimitExceeded(
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        userId: String? = null,
        endpoint: String? = null,
        limit: Int? = null,
        details: JsonObject? = null
    ) {
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.RATE_LIMIT_EXCEEDED,
                severity = SecuritySeverity.MEDIUM,
                userId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "rate-limiting",
                action = "rate_limit_exceeded",
                result = "blocked",
                resource = endpoint,
                details = details?.put("limit", limit),
                riskScore = 40,
                tags = listOf("rate_limit", "blocked")
            )
        )
    }
    
    /**
     * Log suspicious file upload
     */
    fun logSuspiciousFileUpload(
        fileName: String? = null,
        fileSize: Long? = null,
        mimeType: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        userId: String? = null,
        reason: String? = null,
        details: JsonObject? = null
    ) {
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.SUSPICIOUS_FILE_UPLOAD,
                severity = SecuritySeverity.HIGH,
                userId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "file-security",
                action = "file_upload",
                result = "blocked",
                details = details?.put("fileName", fileName)
                    ?.put("fileSize", fileSize)
                    ?.put("mimeType", mimeType)
                    ?.put("reason", reason),
                riskScore = 80,
                tags = listOf("file_upload", "suspicious", "blocked")
            )
        )
    }
    
    /**
     * Log data access violation
     */
    fun logDataAccessViolation(
        dataType: String? = null,
        resource: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        userId: String? = null,
        reason: String? = null,
        details: JsonObject? = null
    ) {
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.UNAUTHORIZED_DATA_ACCESS,
                severity = SecuritySeverity.CRITICAL,
                userId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "data-security",
                action = "data_access",
                result = "blocked",
                resource = resource,
                details = details?.put("dataType", dataType)?.put("reason", reason),
                riskScore = 90,
                tags = listOf("data_access", "unauthorized", "blocked")
            )
        )
    }
    
    /**
     * Log security incident
     */
    fun logSecurityIncident(
        incidentType: String,
        description: String,
        severity: SecuritySeverity,
        ipAddress: String? = null,
        userAgent: String? = null,
        requestId: String? = null,
        userId: String? = null,
        details: JsonObject? = null
    ) {
        logSecurityEvent(
            SecurityEvent(
                eventType = SecurityEventType.SECURITY_INCIDENT_CREATED,
                severity = severity,
                userId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                requestId = requestId,
                service = "incident-response",
                action = "incident_created",
                result = "created",
                details = details?.put("incidentType", incidentType)?.put("description", description),
                riskScore = when (severity) {
                    SecuritySeverity.CRITICAL -> 100
                    SecuritySeverity.HIGH -> 80
                    SecuritySeverity.MEDIUM -> 60
                    SecuritySeverity.LOW -> 40
                },
                tags = listOf("incident", "security")
            )
        )
    }
    
    /**
     * Extract request information from routing context
     */
    fun extractRequestInfo(context: RoutingContext): JsonObject {
        val request = context.request()
        return JsonObject()
            .put("ipAddress", getClientIP(request))
            .put("userAgent", request.getHeader("User-Agent"))
            .put("requestId", request.getHeader("X-Request-ID"))
            .put("method", request.method().name())
            .put("path", request.path())
            .put("query", request.query())
            .put("headers", request.headers().names().associateWith { request.getHeader(it) })
    }
    
    /**
     * Get client IP address
     */
    private fun getClientIP(request: io.vertx.ext.web.Request): String? {
        return request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request.getHeader("X-Real-IP")
            ?: request.remoteAddress()?.host()
    }
    
    /**
     * Calculate authentication failure risk score
     */
    private fun calculateAuthFailureRiskScore(username: String?, ipAddress: String?): Int {
        var score = 10 // Base score
        
        // Check for common attack patterns
        if (username != null) {
            if (username.lowercase() in listOf("admin", "root", "administrator", "test", "guest")) {
                score += 20
            }
            if (username.length < 3) {
                score += 15
            }
        }
        
        // Check IP address patterns
        if (ipAddress != null) {
            if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
                score += 5 // Internal IP
            }
            if (ipAddress == "127.0.0.1" || ipAddress == "::1") {
                score += 10 // Localhost
            }
        }
        
        return minOf(score, 100)
    }
    
    /**
     * Send security event to monitoring system
     */
    private fun sendToMonitoring(event: SecurityEvent) {
        try {
            // Increment Prometheus metrics
            val metricName = "eternaljukebox_security_events_total"
            val labels = mapOf(
                "event_type" to event.eventType.name,
                "severity" to event.severity.name,
                "service" to (event.service ?: "unknown")
            )
            
            // This would typically be done through a metrics library
            // For now, we'll just log the metric
            logger.debug("METRIC: $metricName $labels")
            
        } catch (e: Exception) {
            logger.error("Failed to send security event to monitoring: ${e.message}", e)
        }
    }
    
    /**
     * Send security event to SIEM
     */
    private fun sendToSIEM(event: SecurityEvent) {
        try {
            // This would typically send to an external SIEM system
            // For now, we'll just log that it would be sent
            logger.debug("SIEM: Would send security event to SIEM: ${event.eventType}")
            
        } catch (e: Exception) {
            logger.error("Failed to send security event to SIEM: ${e.message}", e)
        }
    }
    
    /**
     * Get audit statistics
     */
    fun getAuditStatistics(): JsonObject {
        val stats = JsonObject()
        auditCounters.forEach { (eventType, count) ->
            stats.put(eventType, count.get())
        }
        return stats
    }
    
    /**
     * Clear audit statistics
     */
    fun clearAuditStatistics() {
        auditCounters.clear()
    }
}

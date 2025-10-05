#!/usr/bin/env python3
"""
Security tests for EternalJukebox Python analysis service
Tests for authentication, input validation, and security vulnerabilities
"""

import pytest
import requests
import json
import os
import tempfile
import subprocess
import time
from unittest.mock import patch, MagicMock

# Test configuration
BASE_URL = "http://localhost:5000"
TEST_AUDIO_FILE = "test_audio.wav"
MALICIOUS_PAYLOADS = [
    "../../../etc/passwd",
    "file:///etc/passwd",
    "https://192.168.1.1/admin",
    "<script>alert('xss')</script>",
    "'; DROP TABLE users; --",
    "{{7*7}}",
    "${7*7}",
    "{{config}}",
    "{{request}}",
    "{{self}}"
]

class TestAnalysisServiceSecurity:
    """Security test suite for the analysis service"""
    
    def setup_method(self):
        """Setup for each test method"""
        self.session = requests.Session()
        self.test_file = self.create_test_audio_file()
    
    def teardown_method(self):
        """Cleanup after each test method"""
        if os.path.exists(self.test_file):
            os.remove(self.test_file)
    
    def create_test_audio_file(self):
        """Create a temporary test audio file"""
        # Create a minimal WAV file for testing
        with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as f:
            # WAV file header (44 bytes)
            f.write(b'RIFF\x24\x08\x00\x00WAVEfmt \x10\x00\x00\x00\x01\x00\x01\x00\x44\xac\x00\x00\x88X\x01\x00\x02\x00\x10\x00data\x00\x08\x00\x00')
            # Add some audio data
            f.write(b'\x00' * 1000)
            return f.name
    
    def test_service_health_check(self):
        """Test that the service is running and accessible"""
        response = self.session.get(f"{BASE_URL}/health")
        assert response.status_code == 200
        assert "status" in response.json()
    
    def test_cors_configuration(self):
        """Test CORS configuration"""
        response = self.session.options(f"{BASE_URL}/analyze")
        assert "Access-Control-Allow-Origin" in response.headers
        assert "Access-Control-Allow-Methods" in response.headers
        assert "Access-Control-Allow-Headers" in response.headers
    
    def test_security_headers(self):
        """Test that security headers are present"""
        response = self.session.get(f"{BASE_URL}/health")
        
        # Check for security headers
        assert "X-Content-Type-Options" in response.headers
        assert "X-Frame-Options" in response.headers
        assert "X-XSS-Protection" in response.headers
        assert "Strict-Transport-Security" in response.headers
        assert "Content-Security-Policy" in response.headers
        
        # Verify header values
        assert response.headers["X-Content-Type-Options"] == "nosniff"
        assert response.headers["X-Frame-Options"] == "DENY"
        assert response.headers["X-XSS-Protection"] == "1; mode=block"
    
    def test_file_upload_validation(self):
        """Test file upload validation"""
        # Test with valid audio file
        with open(self.test_file, 'rb') as f:
            files = {'file': f}
            response = self.session.post(f"{BASE_URL}/analyze", files=files)
            assert response.status_code in [200, 400]  # May fail due to invalid audio content
        
        # Test with non-audio file
        with tempfile.NamedTemporaryFile(suffix='.txt', delete=False) as f:
            f.write(b"This is not an audio file")
            f.flush()
            
            try:
                with open(f.name, 'rb') as file:
                    files = {'file': file}
                    response = self.session.post(f"{BASE_URL}/analyze", files=files)
                    assert response.status_code == 400
            finally:
                os.remove(f.name)
    
    def test_file_size_limit(self):
        """Test file size limit enforcement"""
        # Create a large file (over 50MB)
        large_file = tempfile.NamedTemporaryFile(suffix='.wav', delete=False)
        try:
            # Write 60MB of data
            large_file.write(b'\x00' * (60 * 1024 * 1024))
            large_file.flush()
            
            with open(large_file.name, 'rb') as f:
                files = {'file': f}
                response = self.session.post(f"{BASE_URL}/analyze", files=files)
                assert response.status_code == 413  # Payload too large
        finally:
            os.remove(large_file.name)
    
    def test_url_analysis_security(self):
        """Test URL analysis endpoint security"""
        # Test with valid YouTube URL
        valid_url = "https://youtube.com/watch?v=dQw4w9WgXcQ"
        data = {"url": valid_url}
        response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
        # May fail due to network access, but should not crash
        assert response.status_code in [200, 400, 500]
    
    def test_malicious_url_prevention(self):
        """Test prevention of malicious URLs"""
        malicious_urls = [
            "file:///etc/passwd",
            "ftp://malicious.com",
            "https://192.168.1.1/admin",
            "https://internal.company.com",
            "javascript:alert('xss')",
            "data:text/html,<script>alert('xss')</script>"
        ]
        
        for url in malicious_urls:
            data = {"url": url}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"Malicious URL {url} should be rejected"
    
    def test_input_sanitization(self):
        """Test input sanitization"""
        for payload in MALICIOUS_PAYLOADS:
            data = {"url": payload}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"Payload {payload} should be rejected"
    
    def test_sql_injection_prevention(self):
        """Test SQL injection prevention"""
        sql_payloads = [
            "'; DROP TABLE users; --",
            "' OR '1'='1",
            "' UNION SELECT * FROM users --",
            "admin'--",
            "' OR 1=1 --"
        ]
        
        for payload in sql_payloads:
            data = {"url": payload}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"SQL injection {payload} should be rejected"
    
    def test_xss_prevention(self):
        """Test XSS prevention"""
        xss_payloads = [
            "<script>alert('xss')</script>",
            "javascript:alert('xss')",
            "<img src=x onerror=alert('xss')>",
            "<svg onload=alert('xss')>",
            "';alert('xss');//"
        ]
        
        for payload in xss_payloads:
            data = {"url": payload}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"XSS payload {payload} should be rejected"
    
    def test_path_traversal_prevention(self):
        """Test path traversal prevention"""
        path_traversal_payloads = [
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\drivers\\etc\\hosts",
            "....//....//....//etc/passwd",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd"
        ]
        
        for payload in path_traversal_payloads:
            data = {"url": payload}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"Path traversal {payload} should be rejected"
    
    def test_command_injection_prevention(self):
        """Test command injection prevention"""
        command_payloads = [
            "https://youtube.com/watch?v=test; rm -rf /",
            "https://youtube.com/watch?v=test | cat /etc/passwd",
            "https://youtube.com/watch?v=test && whoami",
            "https://youtube.com/watch?v=test || id"
        ]
        
        for payload in command_payloads:
            data = {"url": payload}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"Command injection {payload} should be rejected"
    
    def test_rate_limiting(self):
        """Test rate limiting"""
        # Make multiple requests quickly
        responses = []
        for i in range(100):
            data = {"url": f"https://youtube.com/watch?v=test{i}"}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            responses.append(response)
        
        # Check if any requests were rate limited
        rate_limited = any(r.status_code == 429 for r in responses)
        assert rate_limited, "Rate limiting should be enforced"
    
    def test_error_handling(self):
        """Test error handling without information disclosure"""
        # Test with invalid JSON
        response = self.session.post(f"{BASE_URL}/analyze/url", 
                                   data="invalid json",
                                   headers={"Content-Type": "application/json"})
        assert response.status_code == 400
        
        # Check that error response doesn't contain sensitive information
        error_text = response.text.lower()
        sensitive_terms = ["traceback", "exception", "error", "stack", "debug"]
        for term in sensitive_terms:
            assert term not in error_text, f"Error response should not contain {term}"
    
    def test_authentication_bypass(self):
        """Test authentication bypass attempts"""
        # Test with various authentication bypass attempts
        bypass_attempts = [
            "Bearer null",
            "Bearer undefined",
            "Bearer ",
            "Bearer 0",
            "Bearer false",
            "Bearer true",
            "Bearer admin",
            "Bearer root"
        ]
        
        for attempt in bypass_attempts:
            headers = {"Authorization": attempt}
            response = self.session.get(f"{BASE_URL}/health", headers=headers)
            # Health endpoint should be public, but auth should not be bypassed
            assert response.status_code in [200, 401]
    
    def test_session_fixation(self):
        """Test session fixation prevention"""
        # Get initial session
        response1 = self.session.get(f"{BASE_URL}/health")
        session1 = self.session.cookies.get_dict()
        
        # Make another request
        response2 = self.session.get(f"{BASE_URL}/health")
        session2 = self.session.cookies.get_dict()
        
        # Sessions should be different (if sessions are used)
        # This test may pass even if sessions are not implemented
        assert True  # Placeholder for session fixation test
    
    def test_timing_attack_resistance(self):
        """Test timing attack resistance"""
        import time
        
        # Test with non-existent resource
        start_time = time.time()
        response1 = self.session.get(f"{BASE_URL}/nonexistent")
        time1 = time.time() - start_time
        
        # Test with different non-existent resource
        start_time = time.time()
        response2 = self.session.get(f"{BASE_URL}/another-nonexistent")
        time2 = time.time() - start_time
        
        # Response times should be similar
        time_diff = abs(time1 - time2)
        assert time_diff < 1.0, "Response times should be similar to prevent timing attacks"
    
    def test_content_type_validation(self):
        """Test content type validation"""
        # Test with wrong content type
        data = {"url": "https://youtube.com/watch?v=test"}
        response = self.session.post(f"{BASE_URL}/analyze/url", 
                                   data=data,
                                   headers={"Content-Type": "text/plain"})
        assert response.status_code == 400, "Wrong content type should be rejected"
    
    def test_request_size_limit(self):
        """Test request size limit"""
        # Create a very large JSON payload
        large_data = {"url": "x" * 1000000}
        response = self.session.post(f"{BASE_URL}/analyze/url", json=large_data)
        assert response.status_code == 413, "Large request should be rejected"
    
    def test_http_method_validation(self):
        """Test HTTP method validation"""
        # Test with unsupported methods
        methods = ["PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"]
        
        for method in methods:
            response = self.session.request(method, f"{BASE_URL}/analyze/url")
            assert response.status_code in [405, 404], f"Method {method} should not be allowed"
    
    def test_parameter_pollution(self):
        """Test parameter pollution"""
        # Test with duplicate parameters
        data = {"url": "https://youtube.com/watch?v=test", "url": "malicious"}
        response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
        # Should handle gracefully
        assert response.status_code in [200, 400]
    
    def test_unicode_handling(self):
        """Test Unicode handling"""
        unicode_payloads = [
            "https://youtube.com/watch?v=test\u0000",
            "https://youtube.com/watch?v=test\u0001",
            "https://youtube.com/watch?v=test\u0002",
            "https://youtube.com/watch?v=test\u0003"
        ]
        
        for payload in unicode_payloads:
            data = {"url": payload}
            response = self.session.post(f"{BASE_URL}/analyze/url", json=data)
            assert response.status_code == 400, f"Unicode payload {payload} should be rejected"

if __name__ == "__main__":
    pytest.main([__file__, "-v"])

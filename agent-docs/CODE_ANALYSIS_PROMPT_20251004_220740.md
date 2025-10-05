# EternalJukebox Code Analysis & Security Review Prompt

## Project Context

You are analyzing the **EternalJukebox** project - a Kotlin-based application that creates endless music loops by analyzing audio and generating seamless transitions. The project has recently undergone a major transformation to remove Spotify dependencies and add support for any yt-dlp compatible audio source (1800+ platforms).

## Current Project Status

**Technology Stack:**
- **Backend**: Kotlin 1.9.25 with Java 11, Vert.x 3.9.16 web framework
- **Audio Analysis**: Python microservice with librosa, numpy, scipy
- **Audio Sources**: yt-dlp integration supporting YouTube, SoundCloud, Bandcamp, Vimeo, etc.
- **Database**: H2/MySQL support with HikariCP connection pooling
- **Frontend**: Jekyll static site with jQuery, Raphael.js for visualizations
- **Deployment**: Docker containerization ready

**Recent Changes (Todos 1-6 Completed):**
- ✅ Python audio analysis microservice with librosa
- ✅ Removed all Spotify dependencies from Kotlin backend
- ✅ Created GenericAnalyser supporting 1800+ audio platforms
- ✅ Enhanced audio source system with yt-dlp integration
- ✅ Updated AnalysisAPI with Python service integration
- ✅ Modernized frontend for generic URL input

## Analysis Requirements

### 1. Code Quality & Best Practices Review

**Backend (Kotlin/Java):**
- [ ] **Architecture**: Review dependency injection, service layer patterns, separation of concerns
- [ ] **Error Handling**: Analyze exception handling, logging, and error propagation
- [ ] **Security**: Input validation, SQL injection prevention, XSS protection
- [ ] **Performance**: Database queries, caching strategies, memory management
- [ ] **Code Style**: Kotlin idioms, naming conventions, documentation
- [ ] **Testing**: Unit test coverage, integration tests, mocking strategies

**Python Microservice:**
- [ ] **API Design**: REST endpoint design, request/response validation
- [ ] **Error Handling**: Exception handling, graceful degradation
- [ ] **Performance**: Audio processing optimization, memory usage
- [ ] **Security**: Input validation, file handling, resource limits
- [ ] **Dependencies**: Package security, version management

**Frontend (JavaScript/HTML):**
- [ ] **Security**: XSS prevention, CSRF protection, input sanitization
- [ ] **Performance**: DOM manipulation, event handling, memory leaks
- [ ] **Accessibility**: ARIA labels, keyboard navigation, screen reader support
- [ ] **Browser Compatibility**: Cross-browser testing, polyfills needed
- [ ] **Code Organization**: Module structure, global variable usage

### 2. Security Assessment

**Critical Security Areas:**
- [ ] **Input Validation**: URL validation, file upload security, parameter injection
- [ ] **Authentication & Authorization**: API access controls, user session management
- [ ] **Data Protection**: Sensitive data handling, logging practices
- [ ] **Network Security**: HTTPS enforcement, CORS configuration, API rate limiting
- [ ] **File System Security**: Temporary file handling, path traversal prevention
- [ ] **Third-party Dependencies**: Known vulnerabilities, supply chain security

**Specific Security Concerns:**
- [ ] **Audio File Processing**: Malicious file uploads, format validation
- [ ] **URL Processing**: SSRF attacks, malicious URL handling in yt-dlp
- [ ] **Python Service**: Subprocess security, library vulnerabilities
- [ ] **Database Access**: SQL injection, connection security
- [ ] **Frontend**: XSS vectors, unsafe DOM manipulation

### 3. Configuration & Deployment Security

**Docker & Infrastructure:**
- [ ] **Container Security**: Base image selection, privilege escalation
- [ ] **Environment Variables**: Secret management, configuration exposure
- [ ] **Network Security**: Service communication, port exposure
- [ ] **Resource Limits**: CPU/memory constraints, DoS prevention

**Database Security:**
- [ ] **Connection Security**: SSL/TLS, credential management
- [ ] **Data Encryption**: At-rest encryption, in-transit protection
- [ ] **Access Controls**: Database user permissions, connection limits

## Analysis Methodology

1. **Static Code Analysis**: Use tools like SonarQube, ESLint, SpotBugs
2. **Dependency Scanning**: Check for known vulnerabilities in dependencies
3. **Security Pattern Review**: Manual review of security-critical code paths
4. **Configuration Review**: Docker, database, and service configurations
5. **API Security Testing**: Endpoint security, input validation, rate limiting

## Expected Deliverables

### 1. Code Quality Report
- **Critical Issues**: Security vulnerabilities, memory leaks, performance bottlenecks
- **Code Smells**: Anti-patterns, technical debt, maintainability issues
- **Best Practice Violations**: Style guide violations, missing documentation
- **Recommendations**: Specific fixes with code examples

### 2. Security Assessment Report
- **Vulnerability Inventory**: CVE references, CVSS scores, remediation steps
- **Attack Surface Analysis**: Potential attack vectors, risk assessment
- **Compliance Review**: OWASP Top 10, security framework compliance
- **Security Recommendations**: Immediate fixes, long-term security improvements

### 3. Performance Analysis
- **Bottleneck Identification**: Slow queries, inefficient algorithms
- **Memory Usage**: Leak detection, optimization opportunities
- **Scalability Assessment**: Load handling, resource utilization

### 4. GitHub Actions Security Checks

After analysis completion, create GitHub Actions workflows for:
- **Automated Security Scanning**: SAST, DAST, dependency scanning
- **Code Quality Gates**: Linting, formatting, complexity checks
- **Vulnerability Monitoring**: Automated CVE scanning, alerting
- **Compliance Validation**: Security policy enforcement

## Key Files to Analyze

**Backend Core:**
- `src/main/kotlin/org/abimon/eternalJukebox/` (main application code)
- `src/main/kotlin/org/abimon/eternalJukebox/handlers/api/AnalysisAPI.kt`
- `src/main/kotlin/org/abimon/eternalJukebox/data/analysis/GenericAnalyser.kt`
- `src/main/kotlin/org/abimon/eternalJukebox/data/audio/GenericAudioSource.kt`

**Python Service:**
- `analysis-service/app.py`
- `analysis-service/analyzer.py`
- `analysis-service/requirements.txt`

**Frontend:**
- `_web/_includes/search-js.html`
- `_web/_includes/go-js.html`
- `_web/_layouts/search.html`

**Configuration:**
- `docker-compose.yml`
- `Dockerfile`
- `build.gradle`
- `config_template.yaml`

**Documentation:**
- `README.md`
- `plan.md`

## Success Criteria

The analysis should result in:
1. **Zero Critical Security Vulnerabilities**: All high-severity issues identified and fixed
2. **Improved Code Quality**: Technical debt reduction, better maintainability
3. **Enhanced Security Posture**: Comprehensive security controls implemented
4. **Automated Security Pipeline**: GitHub Actions workflows for ongoing security validation
5. **Documentation**: Security guidelines, deployment best practices

## Timeline

- **Phase 1**: Static analysis and security review (2-3 hours)
- **Phase 2**: Manual security assessment (1-2 hours)
- **Phase 3**: GitHub Actions security pipeline creation (1 hour)
- **Phase 4**: Documentation and recommendations (30 minutes)

## Instructions

1. **Start with static analysis** using appropriate tools for each technology stack
2. **Perform manual code review** focusing on security-critical areas
3. **Create detailed reports** with specific recommendations and code examples
4. **Design GitHub Actions workflows** for automated security validation
5. **Provide actionable next steps** prioritized by risk and impact

Begin the analysis now, starting with the most critical security areas and working through each component systematically.

<!-- d89ed5b0-f769-4615-bc01-5f09fa5cb30d 7f701ddf-9ab0-4b97-9985-9fd13dcc34c7 -->
# Remove Spotify & Add yt-dlp Audio Analysis

## Dependencies Assessment

**Current Stack:**
- Kotlin 1.9.25 with Java 11
- Vert.x 3.9.16 (web framework)
- NewPipeExtractor (YouTube metadata)
- yt-dlp + ffmpeg (audio download)
- Jackson (JSON/YAML parsing)
- H2/MySQL database support

**Missing for Audio Analysis:**
- Python 3.8+ with librosa, numpy, scipy
- Flask or FastAPI for analysis microservice
- Audio analysis endpoint communication

## Implementation Strategy

### 1. Create Python Audio Analysis Service

Create a new Python microservice (`analysis-service/`) that:
- Accepts audio file uploads or URLs
- Uses librosa to extract: beats, bars, tatums, sections, segments (with timbre/pitch via MFCCs)
- Returns JSON matching existing Spotify analysis format (`SpotifyAudioSection`, `SpotifyAudioBeat`, etc.)
- Exposes REST API endpoint `/analyze` for Kotlin backend to call

**Files to create:**
- `analysis-service/requirements.txt` - librosa, numpy, scipy, flask/fastapi
- `analysis-service/analyzer.py` - Core librosa analysis logic
- `analysis-service/app.py` - Flask/FastAPI service
- `analysis-service/Dockerfile` - Container setup

### 2. Remove Spotify Dependencies

**Backend (Kotlin):**
- Remove `SpotifyAnalyser.kt` - Delete entirely
- Remove `SpotifyError.kt` - Delete entirely
- Rename `SpotifyInfo.kt` classes to generic names (e.g., `AudioSection`, `AudioBeat`)
- Update `IAnalyser` interface implementation
- Remove `spotifyClient` and `spotifySecret` from `JukeboxConfig.kt`
- Update `EternalJukebox.kt` initialization to not reference Spotify

**Config files:**
- Remove Spotify credentials from `config_template.yaml`, `config_template.json`, `envvar_config.yaml`
- Remove Spotify setup instructions from `README.md`

**Frontend:**
- Update `_web/_includes/search-js.html` to remove Spotify URL parsing
- Update FAQ and documentation references
- Remove manual Spotify analysis instructions

### 3. Create New Generic Analyser

Create `GenericAnalyser.kt` to replace `SpotifyAnalyser.kt`:
- Implement `IAnalyser` interface
- `search()` - Use YouTube search via NewPipeExtractor (already available)
- `getInfo()` - Extract metadata from yt-dlp
- Call Python analysis service to generate audio analysis
- Use video ID as primary identifier, URL hash as fallback

### 4. Update Audio Source System

Enhance `YoutubeAudioSource.kt` and create `GenericAudioSource.kt`:
- Support any yt-dlp compatible URL (not just YouTube)
- Extract video/track ID from various platforms
- Generate consistent identifiers across platforms
- Update ID extraction logic to handle non-YouTube sources

### 5. Update Analysis API

Modify `AnalysisAPI.kt`:
- Update `/analyse/:id` endpoint to call Python analysis service
- Add `/analyse/url` endpoint for direct URL input
- Keep upload functionality for pre-existing analysis
- Cache generated analysis in storage

### 6. Frontend Updates

**Search Interface (`_web/_includes/search-js.html`):**
- Replace Spotify URL input with generic URL input
- Support any yt-dlp compatible URL
- Update validation to check for valid URLs instead of Spotify format
- Keep YouTube search functionality via existing API

**Display (`_web/_includes/go-js.html`):**
- Update error messages to remove Spotify references
- Update track info display to show generic source info

### 7. Docker Integration

Update `Dockerfile` and `docker-compose.yml`:
- Add Python 3 and pip (already has python3)
- Install librosa dependencies (libsndfile, ffmpeg already present)
- Add analysis service as separate container in docker-compose
- Set up internal network for Kotlin backend → Python service communication

### 8. Database Schema Updates

No schema changes needed - existing tables store IDs as VARCHAR(64) which works for:
- YouTube video IDs (11 chars)
- URL hashes (configurable length)
- Other platform IDs

### 9. Configuration Updates

Add to config templates:
```yaml
analysisServiceUrl: http://localhost:5000  # Python service endpoint
```

## GitHub Actions Deployment

### Multi-Cloud Deployment Strategy

Create GitHub Actions workflows for automated deployment to:

**Azure:**
- Azure Container Instances (ACI) for simple deployments
- Azure Container Apps for scalable serverless containers
- Azure App Service for managed hosting

**AWS:**
- AWS ECS with Fargate for serverless containers
- AWS App Runner for simplified container deployment
- AWS Elastic Beanstalk for managed application hosting

**VPS Providers:**
- Generic SSH deployment for any VPS (DigitalOcean, Linode, Vultr, etc.)
- Docker Compose deployment with health checks
- Rolling updates with zero downtime

**Files to create:**
- `.github/workflows/deploy-azure.yml`
- `.github/workflows/deploy-aws.yml`
- `.github/workflows/deploy-vps.yml`
- `deployment/docker-compose.prod.yml`
- `deployment/health-check.sh`
- `deployment/update-script.sh`

## Testing Strategy

1. Test Python analysis service standalone with sample audio
2. Test backend → analysis service communication
3. Test YouTube URL input and analysis generation
4. Test other yt-dlp sources (SoundCloud, Bandcamp, etc.)
5. Test caching of generated analysis
6. Verify endless loop quality matches original Spotify-based loops

## CHANGELOG - Version 1.0.0

### 🚀 New Features

**Audio Source Support:**
- ✅ Support for any yt-dlp compatible URL (YouTube, SoundCloud, Bandcamp, Vimeo, etc.)
- ✅ Direct URL input interface for custom audio sources
- ✅ Enhanced YouTube search with improved metadata extraction
- ✅ Generic track identification using video IDs and URL hashing

**Audio Analysis:**
- ✅ librosa-based audio analysis service replacing Spotify dependency
- ✅ Real-time audio analysis generation for any supported source
- ✅ Automatic beat, bar, tatum, section, and segment detection
- ✅ Timbre and pitch analysis via MFCC extraction
- ✅ Cached analysis storage for improved performance
- ✅ Complete Python microservice with Flask REST API
- ✅ Docker containerization with health checks
- ✅ yt-dlp integration for audio source downloads

**Deployment & Infrastructure:**
- ✅ Multi-cloud deployment support (Azure, AWS, VPS)
- ✅ GitHub Actions CI/CD pipelines
- ✅ Containerized Python analysis microservice
- ✅ Automated health checks and rolling updates
- ✅ Environment-based configuration management

**User Interface:**
- ✅ Modernized search interface with URL input
- ✅ Enhanced error handling and user feedback
- ✅ Improved mobile responsiveness
- ✅ Better loading states and progress indicators

### 🔄 Changed

**Backend Architecture:**
- 🔄 Replaced Spotify API integration with generic audio analysis
- 🔄 Modular audio source system supporting multiple platforms
- 🔄 Enhanced configuration management with environment variables
- 🔄 Improved database schema for multi-source track storage

**Frontend Experience:**
- 🔄 Updated search interface to support direct URL input
- 🔄 Removed Spotify-specific UI elements and references
- 🔄 Enhanced track information display for various sources
- 🔄 Improved error messages and user guidance

### ❌ Removed

**Dependencies:**
- ❌ Spotify API client and authentication
- ❌ Spotify-specific error handling
- ❌ Manual Spotify analysis upload requirements
- ❌ Spotify URL parsing and validation
- ✅ Removed Spotify-specific Gradle dependencies (JWT, MySQL socket factory)
- ✅ Fixed KotlinCompile import issues in build.gradle

**Features:**
- ❌ Spotify track search and metadata
- ❌ Spotify authentication configuration
- ❌ Spotify-specific documentation and setup guides
- ❌ Manual analysis upload for Spotify tracks

### 🔧 Technical Improvements

**Performance:**
- ⚡ Faster audio analysis with librosa optimization
- ⚡ Improved caching strategies for analysis data
- ⚡ Better memory management in Python service
- ⚡ Optimized Docker image layers

**Reliability:**
- 🛡️ Enhanced error handling and recovery
- 🛡️ Better service health monitoring
- 🛡️ Improved database connection management
- 🛡️ Graceful degradation when services are unavailable

**Security:**
- 🔒 Removed dependency on external API credentials
- 🔒 Enhanced input validation for URLs and audio files
- 🔒 Improved container security configurations
- 🔒 Better secret management in CI/CD pipelines

### 📋 Migration Guide

**For Existing Users:**
1. Update configuration files to remove Spotify credentials
2. Migrate existing analysis data (if needed)
3. Update deployment scripts to include analysis service
4. Test with new URL-based audio sources

**For New Deployments:**
1. Use provided Docker Compose configurations
2. Configure environment variables for your cloud provider
3. Set up GitHub Actions secrets for automated deployment
4. Test audio analysis with sample URLs

## Key Files to Modify

**Remove:**
- `src/main/kotlin/org/abimon/eternalJukebox/data/analysis/SpotifyAnalyser.kt`
- `src/main/kotlin/org/abimon/eternalJukebox/objects/SpotifyError.kt`

**Rename:**
- `SpotifyInfo.kt` → Keep but rename classes to `Audio*` prefix

**Create:**
- `analysis-service/analyzer.py`
- `analysis-service/app.py`
- `analysis-service/requirements.txt`
- `analysis-service/Dockerfile`
- `src/main/kotlin/org/abimon/eternalJukebox/data/analysis/GenericAnalyser.kt`
- `src/main/kotlin/org/abimon/eternalJukebox/data/audio/GenericAudioSource.kt`
- `.github/workflows/deploy-azure.yml`
- `.github/workflows/deploy-aws.yml`
- `.github/workflows/deploy-vps.yml`
- `deployment/docker-compose.prod.yml`
- `deployment/health-check.sh`
- `deployment/update-script.sh`

**Modify:**
- `EternalJukebox.kt` - Remove Spotify initialization
- `JukeboxConfig.kt` - Remove Spotify config fields
- `AnalysisAPI.kt` - Add analysis service integration
- `config_template.yaml`, `config_template.json`, `envvar_config.yaml`
- `docker-compose.yml` - Add analysis service
- `Dockerfile` - Add librosa installation
- `README.md` - Remove Spotify instructions, add yt-dlp URL info
- `_web/_includes/search-js.html` - Generic URL input
- `_web/_includes/go-js.html` - Remove Spotify references

## Implementation Progress Tracking

### Backend Implementation (Todos 1-5)
- [x] **Todo 1**: Create Python audio analysis microservice with librosa ✅ COMPLETED
- [x] **Todo 2**: Remove Spotify dependencies from Kotlin backend code ✅ COMPLETED
- [x] **Todo 3**: Create GenericAnalyser to replace SpotifyAnalyser ✅ COMPLETED
- [x] **Todo 4**: Update audio source system to support any yt-dlp URL ✅ COMPLETED
- [x] **Todo 5**: Modify AnalysisAPI to integrate with Python service ✅ COMPLETED

### Frontend & Infrastructure (Todos 6-8)
- [ ] **Todo 6**: Update frontend for generic URL input and remove Spotify references
- [ ] **Todo 7**: Update Docker configuration for analysis service
- [ ] **Todo 8**: Update configuration templates and documentation

### Deployment & Monitoring (Todos 9-13)
- [ ] **Todo 9**: Create GitHub Actions workflows for Azure deployment
- [ ] **Todo 10**: Create GitHub Actions workflows for AWS deployment
- [ ] **Todo 11**: Create GitHub Actions workflows for VPS deployment
- [ ] **Todo 12**: Set up automated health checks and monitoring
- [ ] **Todo 13**: Create deployment documentation and migration guides

## Agent Prompts for Context Continuity

### For Todo 1: Python Audio Analysis Service
```
You are implementing Todo 1 from the EternalJukebox Spotify removal plan. 
Context: This is a Kotlin-based EternalJukebox project that creates endless music loops. 
Goal: Create a Python microservice using librosa to replace Spotify's audio analysis API.
Requirements:
- Create analysis-service/ directory with Flask/FastAPI app
- Extract beats, bars, tatums, sections, segments using librosa
- Return JSON matching existing SpotifyAudioSection/Beat/etc. format
- Expose /analyze endpoint for Kotlin backend
- Include Dockerfile for containerization
Files to create: analysis-service/requirements.txt, analyzer.py, app.py, Dockerfile
Next: After completing, update plan.md changelog and move to Todo 2.
```

### For Todo 2: Remove Spotify Dependencies
```
You are implementing Todo 2 from the EternalJukebox Spotify removal plan.
Context: Remove all Spotify dependencies from the Kotlin backend codebase.
Goal: Clean removal of Spotify integration while preserving existing functionality.
Requirements:
- Delete SpotifyAnalyser.kt and SpotifyError.kt entirely
- Remove spotifyClient/spotifySecret from JukeboxConfig.kt
- Update EternalJukebox.kt to remove Spotify initialization
- Remove Spotify references from config templates
- Update README.md to remove Spotify setup instructions
Files to modify: Multiple Kotlin files, config templates, README.md
Next: After completing, update plan.md changelog and move to Todo 3.
```

### For Todo 3: Create GenericAnalyser
```
You are implementing Todo 3 from the EternalJukebox Spotify removal plan.
Context: Replace SpotifyAnalyser with a generic analyser that works with any audio source.
Goal: Create GenericAnalyser.kt that implements IAnalyser interface.
Requirements:
- Implement IAnalyser interface (search() and getInfo() methods)
- Use NewPipeExtractor for YouTube search (already available)
- Call Python analysis service for audio analysis generation
- Use video ID as primary identifier, URL hash as fallback
- Handle metadata extraction from yt-dlp
Files to create: src/main/kotlin/org/abimon/eternalJukebox/data/analysis/GenericAnalyser.kt
Next: After completing, update plan.md changelog and move to Todo 4.
```

### For Todo 4: Update Audio Source System
```
You are implementing Todo 4 from the EternalJukebox Spotify removal plan.
Context: Enhance audio source system to support any yt-dlp compatible URL.
Goal: Support YouTube, SoundCloud, Bandcamp, Vimeo, and other yt-dlp sources.
Requirements:
- Enhance YoutubeAudioSource.kt for broader URL support
- Create GenericAudioSource.kt for non-YouTube sources
- Extract video/track IDs from various platforms
- Generate consistent identifiers across platforms
- Update ID extraction logic for multi-platform support
Files to create/modify: GenericAudioSource.kt, YoutubeAudioSource.kt updates
Next: After completing, update plan.md changelog and move to Todo 5.
```

### For Todo 5: Update Analysis API
```
You are implementing Todo 5 from the EternalJukebox Spotify removal plan.
Context: Modify AnalysisAPI to integrate with the new Python analysis service.
Goal: Update API endpoints to work with generic audio sources and Python analysis.
Requirements:
- Update /analyse/:id endpoint to call Python analysis service
- Add /analyse/url endpoint for direct URL input
- Keep upload functionality for pre-existing analysis
- Implement caching of generated analysis in storage
- Handle errors gracefully when analysis service is unavailable
Files to modify: src/main/kotlin/org/abimon/eternalJukebox/handlers/api/AnalysisAPI.kt
Next: After completing, update plan.md changelog and move to Todo 6.
```

### For Todo 6: Update Frontend
```
You are implementing Todo 6 from the EternalJukebox Spotify removal plan.
Context: Update frontend to support generic URL input and remove Spotify references.
Goal: Modernize UI for any yt-dlp compatible source, remove Spotify-specific elements.
Requirements:
- Update search-js.html to replace Spotify URL input with generic URL input
- Support any yt-dlp compatible URL validation
- Remove Spotify URL parsing and validation
- Update go-js.html to remove Spotify error references
- Update track info display for generic sources
Files to modify: _web/_includes/search-js.html, _web/_includes/go-js.html
Next: After completing, update plan.md changelog and move to Todo 7.
```

### For Todo 7: Docker Configuration
```
You are implementing Todo 7 from the EternalJukebox Spotify removal plan.
Context: Update Docker configuration to include the Python analysis service.
Goal: Containerize the Python analysis service and integrate with main application.
Requirements:
- Update Dockerfile to install librosa dependencies
- Add analysis service as separate container in docker-compose.yml
- Set up internal network for Kotlin backend → Python service communication
- Ensure proper service discovery and health checks
Files to modify: Dockerfile, docker-compose.yml
Next: After completing, update plan.md changelog and move to Todo 8.
```

### For Todo 8: Configuration Updates
```
You are implementing Todo 8 from the EternalJukebox Spotify removal plan.
Context: Update configuration templates and documentation.
Goal: Remove Spotify configuration requirements and add new service configurations.
Requirements:
- Remove Spotify credentials from config_template.yaml/json
- Remove Spotify references from envvar_config.yaml
- Add analysisServiceUrl configuration
- Update README.md with new setup instructions
- Remove Spotify setup documentation
Files to modify: config_template.yaml, config_template.json, envvar_config.yaml, README.md
Next: After completing, update plan.md changelog and move to Todo 9.
```

### For Todo 9: Azure Deployment
```
You are implementing Todo 9 from the EternalJukebox Spotify removal plan.
Context: Create GitHub Actions workflow for Azure deployment.
Goal: Enable automated deployment to Azure Container Instances/Apps.
Requirements:
- Create .github/workflows/deploy-azure.yml
- Support Azure Container Instances for simple deployments
- Support Azure Container Apps for scalable serverless containers
- Include environment variable configuration
- Add health checks and monitoring
Files to create: .github/workflows/deploy-azure.yml
Next: After completing, update plan.md changelog and move to Todo 10.
```

### For Todo 10: AWS Deployment
```
You are implementing Todo 10 from the EternalJukebox Spotify removal plan.
Context: Create GitHub Actions workflow for AWS deployment.
Goal: Enable automated deployment to AWS ECS/App Runner/Elastic Beanstalk.
Requirements:
- Create .github/workflows/deploy-aws.yml
- Support AWS ECS with Fargate for serverless containers
- Support AWS App Runner for simplified deployment
- Support AWS Elastic Beanstalk for managed hosting
- Include proper IAM roles and security configurations
Files to create: .github/workflows/deploy-aws.yml
Next: After completing, update plan.md changelog and move to Todo 11.
```

### For Todo 11: VPS Deployment
```
You are implementing Todo 11 from the EternalJukebox Spotify removal plan.
Context: Create GitHub Actions workflow for VPS deployment.
Goal: Enable automated deployment to any VPS provider via SSH.
Requirements:
- Create .github/workflows/deploy-vps.yml
- Support generic SSH deployment for any VPS (DigitalOcean, Linode, Vultr, etc.)
- Include Docker Compose deployment with health checks
- Implement rolling updates with zero downtime
- Add deployment verification and rollback capabilities
Files to create: .github/workflows/deploy-vps.yml
Next: After completing, update plan.md changelog and move to Todo 12.
```

### For Todo 12: Health Checks & Monitoring
```
You are implementing Todo 12 from the EternalJukebox Spotify removal plan.
Context: Set up automated health checks and monitoring.
Goal: Ensure service reliability and provide monitoring capabilities.
Requirements:
- Create deployment/health-check.sh script
- Add health check endpoints for all services
- Implement monitoring for analysis service performance
- Add alerting for service failures
- Create monitoring dashboard configuration
Files to create: deployment/health-check.sh, monitoring configurations
Next: After completing, update plan.md changelog and move to Todo 13.
```

### For Todo 13: Documentation & Migration
```
You are implementing Todo 13 from the EternalJukebox Spotify removal plan.
Context: Create deployment documentation and migration guides.
Goal: Provide comprehensive documentation for users and developers.
Requirements:
- Create deployment documentation for all cloud providers
- Write migration guide for existing users
- Document new configuration options
- Create troubleshooting guides
- Add examples and best practices
Files to create: deployment documentation, migration guides, troubleshooting docs
Next: After completing, update plan.md changelog and mark all todos complete.
```

## 🚀 **PROGRESS SUMMARY - 75% Context Used**

### **✅ COMPLETED (5/13 todos)**
- **Todo 1**: Python audio analysis microservice with librosa
  - Created complete `analysis-service/` directory
  - Implemented Flask REST API with librosa analysis
  - Added Docker containerization and health checks
  - Created comprehensive test suite and documentation
- **Todo 2**: Remove Spotify dependencies from Kotlin backend code
  - Deleted SpotifyAnalyser.kt and SpotifyError.kt entirely
  - Removed spotifyClient/spotifySecret from JukeboxConfig.kt
  - Updated EternalJukebox.kt to remove Spotify initialization
  - Removed Spotify references from config templates
  - Updated README.md to remove Spotify setup instructions
- **Todo 3**: Create GenericAnalyser to replace SpotifyAnalyser
  - Created GenericAnalyser.kt implementing IAnalyser interface
  - Implemented search() method using NewPipeExtractor for YouTube search
  - Implemented getInfo() method for metadata extraction from URLs
  - Added Python analysis service integration for audio analysis generation
  - Updated EternalJukebox.kt to use GenericAnalyser instead of SpotifyAnalyser
- **Todo 4**: Update audio source system to support any yt-dlp URL
  - Created GenericAudioSource.kt for yt-dlp compatible URLs
  - Enhanced YoutubeAudioSource.kt with improved URL handling and caching
  - Added GENERIC audio source type to EnumAudioSystem
  - Updated ID extraction logic for multi-platform support (YouTube, SoundCloud, Bandcamp, Vimeo, etc.)
  - Enhanced GenericAnalyser with platform detection and URL parsing
  - Support for 1800+ platforms via yt-dlp integration
- **Todo 5**: Modify AnalysisAPI to integrate with Python service
  - Updated /analyse/:id endpoint to call Python analysis service
  - Added /analyse/url endpoint for direct URL input
  - Updated search endpoint to use GenericAnalyser instead of Spotify
  - Added analysis generation logic using Python service
  - Enhanced error handling for analysis service unavailability
  - Implemented caching of generated analysis in storage

### **🔧 INFRASTRUCTURE FIXES**
- Fixed build.gradle KotlinCompile import issues
- Removed Spotify-specific Gradle dependencies
- Cleaned up dependency management
- Verified all core dependencies still needed

### **📊 CURRENT STATUS**
- **Branch**: `llm` (active development branch)
- **Pull Request**: #1 created and ready for implementation
- **Next Todo**: Todo 6 - Update frontend for generic URL input and remove Spotify references
- **Files Ready**: Python analysis service fully implemented

---

## 🤖 **AGENT PROMPT FOR NEW CHAT**

```
You are continuing the EternalJukebox Spotify removal project. This is a Kotlin-based application that creates endless music loops, and we're removing all Spotify dependencies to support any yt-dlp compatible audio source.

CONTEXT:
- Working on branch: llm
- Pull Request #1 is active: "Remove Spotify Dependencies & Add yt-dlp Audio Analysis Support"
- Todo 1 COMPLETED: Python audio analysis microservice with librosa (analysis-service/ directory)
- Current task: Todo 2 - Remove Spotify dependencies from Kotlin backend code

PROJECT STATUS:
✅ Python analysis service: Complete Flask API with librosa analysis
✅ Build.gradle: Fixed KotlinCompile issues, removed Spotify dependencies
⏳ Next: Remove Spotify files and update Kotlin backend

IMMEDIATE TASK - Todo 2:
Remove all Spotify dependencies from the Kotlin backend codebase:
- Delete SpotifyAnalyser.kt and SpotifyError.kt entirely
- Remove spotifyClient/spotifySecret from JukeboxConfig.kt  
- Update EternalJukebox.kt to remove Spotify initialization
- Remove Spotify references from config templates
- Update README.md to remove Spotify setup instructions

FILES TO MODIFY:
- src/main/kotlin/org/abimon/eternalJukebox/data/analysis/SpotifyAnalyser.kt (DELETE)
- src/main/kotlin/org/abimon/eternalJukebox/objects/SpotifyError.kt (DELETE)
- src/main/kotlin/org/abimon/eternalJukebox/objects/JukeboxConfig.kt (REMOVE Spotify fields)
- src/main/kotlin/org/abimon/eternalJukebox/EternalJukebox.kt (REMOVE Spotify init)
- config_template.yaml, config_template.json, envvar_config.yaml (REMOVE Spotify configs)
- README.md (REMOVE Spotify setup instructions)

WORKFLOW:
1. Start with Todo 2 agent prompt from plan.md
2. Make the required changes
3. Update plan.md to mark Todo 2 complete
4. Commit changes with descriptive message
5. Move to Todo 3: Create GenericAnalyser

PLAN FILE: /plan.md contains complete implementation strategy and agent prompts for all 13 todos.

Ready to continue with Spotify dependency removal!
```

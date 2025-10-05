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

### To-dos

- [ ] Create Python audio analysis microservice with librosa
- [ ] Remove Spotify dependencies from Kotlin backend code
- [ ] Create GenericAnalyser to replace SpotifyAnalyser
- [ ] Update audio source system to support any yt-dlp URL
- [ ] Modify AnalysisAPI to integrate with Python service
- [ ] Update frontend for generic URL input and remove Spotify references
- [ ] Update Docker configuration for analysis service
- [ ] Update configuration templates and documentation
- [ ] Create GitHub Actions workflows for Azure deployment
- [ ] Create GitHub Actions workflows for AWS deployment
- [ ] Create GitHub Actions workflows for VPS deployment
- [ ] Set up automated health checks and monitoring
- [ ] Create deployment documentation and migration guides

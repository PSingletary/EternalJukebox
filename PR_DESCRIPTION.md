# 🚀 Remove Spotify Dependencies & Add yt-dlp Audio Analysis Support

## Overview
This PR implements a comprehensive plan to remove all Spotify dependencies and add support for any yt-dlp compatible audio source with librosa-based analysis.

## Key Changes
- ✅ **Remove Spotify**: All Spotify API dependencies and authentication
- ✅ **Add yt-dlp Support**: Support for YouTube, SoundCloud, Bandcamp, Vimeo, etc.
- ✅ **Python Analysis Service**: librosa-based audio analysis microservice
- ✅ **Multi-Cloud Deployment**: GitHub Actions for Azure, AWS, and VPS
- ✅ **Enhanced UI**: Generic URL input and improved user experience

## Implementation Plan
This PR contains the complete implementation plan with 13 todos:

### Backend Implementation (Todos 1-5)
- [ ] **Todo 1**: Create Python audio analysis microservice with librosa
- [ ] **Todo 2**: Remove Spotify dependencies from Kotlin backend code
- [ ] **Todo 3**: Create GenericAnalyser to replace SpotifyAnalyser
- [ ] **Todo 4**: Update audio source system to support any yt-dlp URL
- [ ] **Todo 5**: Modify AnalysisAPI to integrate with Python service

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

## Changelog
See plan.md for complete changelog including new features, changes, removals, and technical improvements.

## Migration Guide
- **Existing users**: Remove Spotify credentials from config
- **New deployments**: Use provided Docker Compose configurations
- **Testing**: Test with new URL-based audio sources

## Progress Tracking
Each todo will be updated in the plan.md changelog as it's completed, with agent prompts for context continuity.

---

**To create this PR manually:**
1. Go to: https://github.com/PSingletary/EternalJukebox/pull/new/llm
2. Use this content as the PR description
3. Set base branch to `master` and compare branch to `llm`

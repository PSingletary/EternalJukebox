# Audio Analysis Service

A Python microservice that provides audio analysis capabilities using librosa, designed to replace Spotify's audio analysis API for the EternalJukebox project.

## Features

- **Audio Analysis**: Extract beats, bars, tatums, sections, and segments from audio files
- **Multiple Input Methods**: Support for file uploads and URL-based audio sources
- **yt-dlp Integration**: Download audio from YouTube, SoundCloud, Bandcamp, and other platforms
- **Spotify Format Compatibility**: Output format matches Spotify's audio analysis API
- **REST API**: Clean HTTP endpoints for easy integration
- **Docker Support**: Containerized deployment with health checks

## API Endpoints

### Health Check
```
GET /health
```
Returns service health status.

### Analyze Audio (Flexible)
```
POST /analyze
```
Accepts either:
- JSON with `url` field
- Multipart form data with `file` field

### Analyze by URL
```
POST /analyze/url
Content-Type: application/json

{
  "url": "https://example.com/audio.mp3"
}
```

### Analyze File Upload
```
POST /analyze/file
Content-Type: multipart/form-data

file: [audio file]
```

### Supported Formats
```
GET /supported-formats
```
Returns list of supported audio formats and file size limits.

## Response Format

The service returns analysis data in the following format (matching Spotify's API):

```json
{
  "track": {
    "duration": 180.5
  },
  "beats": [
    {
      "start": 0.0,
      "duration": 0.5,
      "confidence": 0.85
    }
  ],
  "bars": [
    {
      "start": 0.0,
      "duration": 2.0,
      "confidence": 0.78
    }
  ],
  "tatums": [
    {
      "start": 0.0,
      "duration": 0.25,
      "confidence": 0.72
    }
  ],
  "sections": [
    {
      "start": 0.0,
      "duration": 30.0,
      "confidence": 0.8,
      "loudness": -12.5,
      "tempo": 120.0,
      "tempo_confidence": 0.85,
      "key": 0,
      "key_confidence": 0.7,
      "mode": 1,
      "mode_confidence": 0.8,
      "time_signature": 4,
      "time_signature_confidence": 0.9
    }
  ],
  "segments": [
    {
      "start": 0.0,
      "duration": 2.0,
      "confidence": 0.75,
      "loudness_start": -20,
      "loudness_max_time": 1000,
      "loudness_max": -15,
      "pitches": [0.1, 0.2, 0.15, ...],
      "timbre": [0.05, -0.1, 0.08, ...]
    }
  ]
}
```

## Installation

### Local Development

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Run the service:
```bash
python app.py
```

The service will start on `http://localhost:5000`.

### Docker

1. Build the image:
```bash
docker build -t audio-analysis-service .
```

2. Run the container:
```bash
docker run -p 5000:5000 audio-analysis-service
```

## Configuration

Environment variables:

- `HOST`: Service host (default: 0.0.0.0)
- `PORT`: Service port (default: 5000)
- `DEBUG`: Enable debug mode (default: False)

## Testing

Run the test suite:

```bash
python test_service.py
```

This will test:
- Health check endpoint
- File upload analysis
- URL-based analysis
- Supported formats endpoint

## Supported Audio Formats

- MP3
- WAV
- M4A
- AAC
- OGG
- FLAC

Maximum file size: 100MB

## Dependencies

- **librosa**: Audio analysis and feature extraction
- **Flask**: Web framework
- **yt-dlp**: Audio/video downloading
- **soundfile**: Audio file I/O
- **numpy**: Numerical computing
- **scipy**: Scientific computing

## Integration with EternalJukebox

This service is designed to replace Spotify's audio analysis API. The Kotlin backend will call this service via HTTP to get audio analysis data for any supported audio source.

The service maintains compatibility with the existing EternalJukebox data structures, so no changes are needed to the frontend audio visualization code.

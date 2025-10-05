"""
Audio Analysis Service - Flask web service
Provides REST API endpoints for audio analysis using librosa
"""

import os
import logging
import tempfile
import requests
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from werkzeug.utils import secure_filename
from analyzer import AudioAnalyzer
import yt_dlp
import json

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # Enable CORS for cross-origin requests

# Configuration
UPLOAD_FOLDER = '/tmp/audio_uploads'
MAX_FILE_SIZE = 100 * 1024 * 1024  # 100MB
ALLOWED_EXTENSIONS = {'mp3', 'wav', 'm4a', 'aac', 'ogg', 'flac'}

# Create upload directory
os.makedirs(UPLOAD_FOLDER, exist_ok=True)

# Initialize analyzer
analyzer = AudioAnalyzer()

def allowed_file(filename):
    """Check if file extension is allowed"""
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

def download_audio_from_url(url: str) -> str:
    """
    Download audio from URL using yt-dlp
    
    Args:
        url: Audio/video URL
        
    Returns:
        Path to downloaded audio file
    """
    temp_dir = tempfile.mkdtemp()
    
    # Configure yt-dlp options
    ydl_opts = {
        'format': 'bestaudio/best',
        'outtmpl': os.path.join(temp_dir, '%(title)s.%(ext)s'),
        'extractaudio': True,
        'audioformat': 'wav',
        'noplaylist': True,
        'max_filesize': MAX_FILE_SIZE,
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            # Extract info first
            info = ydl.extract_info(url, download=False)
            title = info.get('title', 'audio')
            
            # Download the audio
            ydl.download([url])
            
            # Find the downloaded file
            for file in os.listdir(temp_dir):
                if file.endswith(('.wav', '.mp3', '.m4a', '.aac', '.ogg', '.flac')):
                    return os.path.join(temp_dir, file)
            
            raise Exception("No audio file found after download")
            
    except Exception as e:
        logger.error(f"Error downloading audio from {url}: {str(e)}")
        raise

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'service': 'audio-analysis-service',
        'version': '1.0.0'
    })

@app.route('/analyze', methods=['POST'])
def analyze_audio():
    """
    Main analysis endpoint
    Accepts either file upload or URL
    
    Expected JSON payload:
    {
        "url": "https://example.com/audio.mp3"  # Optional
    }
    
    Or multipart/form-data with 'file' field
    """
    try:
        audio_file_path = None
        
        # Check if URL is provided
        if request.is_json:
            data = request.get_json()
            url = data.get('url')
            if url:
                logger.info(f"Analyzing audio from URL: {url}")
                audio_file_path = download_audio_from_url(url)
            else:
                return jsonify({'error': 'No URL provided'}), 400
        
        # Check if file is uploaded
        elif 'file' in request.files:
            file = request.files['file']
            if file.filename == '':
                return jsonify({'error': 'No file selected'}), 400
            
            if file and allowed_file(file.filename):
                filename = secure_filename(file.filename)
                audio_file_path = os.path.join(UPLOAD_FOLDER, filename)
                file.save(audio_file_path)
                logger.info(f"Analyzing uploaded file: {filename}")
            else:
                return jsonify({'error': 'Invalid file type'}), 400
        
        else:
            return jsonify({'error': 'No file or URL provided'}), 400
        
        if not audio_file_path or not os.path.exists(audio_file_path):
            return jsonify({'error': 'Audio file not found'}), 400
        
        # Perform analysis
        analysis_result = analyzer.analyze_audio(audio_file_path)
        
        # Clean up temporary file
        try:
            if audio_file_path.startswith('/tmp/'):
                os.remove(audio_file_path)
                # Also remove parent temp directory if it's empty
                temp_dir = os.path.dirname(audio_file_path)
                if temp_dir != UPLOAD_FOLDER and not os.listdir(temp_dir):
                    os.rmdir(temp_dir)
        except Exception as e:
            logger.warning(f"Could not clean up temporary file: {str(e)}")
        
        return jsonify(analysis_result)
        
    except Exception as e:
        logger.error(f"Error in analyze endpoint: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/analyze/url', methods=['POST'])
def analyze_url():
    """
    Dedicated endpoint for URL-based analysis
    
    Expected JSON payload:
    {
        "url": "https://example.com/audio.mp3"
    }
    """
    try:
        data = request.get_json()
        if not data or 'url' not in data:
            return jsonify({'error': 'URL is required'}), 400
        
        url = data['url']
        logger.info(f"Analyzing audio from URL: {url}")
        
        # Download and analyze
        audio_file_path = download_audio_from_url(url)
        analysis_result = analyzer.analyze_audio(audio_file_path)
        
        # Clean up
        try:
            os.remove(audio_file_path)
            temp_dir = os.path.dirname(audio_file_path)
            if not os.listdir(temp_dir):
                os.rmdir(temp_dir)
        except Exception as e:
            logger.warning(f"Could not clean up temporary file: {str(e)}")
        
        return jsonify(analysis_result)
        
    except Exception as e:
        logger.error(f"Error in analyze_url endpoint: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/analyze/file', methods=['POST'])
def analyze_file():
    """
    Dedicated endpoint for file upload analysis
    """
    try:
        if 'file' not in request.files:
            return jsonify({'error': 'No file provided'}), 400
        
        file = request.files['file']
        if file.filename == '':
            return jsonify({'error': 'No file selected'}), 400
        
        if not allowed_file(file.filename):
            return jsonify({'error': 'Invalid file type'}), 400
        
        filename = secure_filename(file.filename)
        audio_file_path = os.path.join(UPLOAD_FOLDER, filename)
        file.save(audio_file_path)
        
        logger.info(f"Analyzing uploaded file: {filename}")
        
        # Analyze
        analysis_result = analyzer.analyze_audio(audio_file_path)
        
        # Clean up
        try:
            os.remove(audio_file_path)
        except Exception as e:
            logger.warning(f"Could not clean up uploaded file: {str(e)}")
        
        return jsonify(analysis_result)
        
    except Exception as e:
        logger.error(f"Error in analyze_file endpoint: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/supported-formats', methods=['GET'])
def supported_formats():
    """Get list of supported audio formats"""
    return jsonify({
        'formats': list(ALLOWED_EXTENSIONS),
        'max_file_size': MAX_FILE_SIZE,
        'max_file_size_mb': MAX_FILE_SIZE // (1024 * 1024)
    })

@app.errorhandler(413)
def too_large(e):
    """Handle file too large error"""
    return jsonify({'error': 'File too large'}), 413

@app.errorhandler(400)
def bad_request(e):
    """Handle bad request error"""
    return jsonify({'error': 'Bad request'}), 400

@app.errorhandler(500)
def internal_error(e):
    """Handle internal server error"""
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    # Get configuration from environment
    host = os.getenv('HOST', '0.0.0.0')
    port = int(os.getenv('PORT', 5000))
    debug = os.getenv('DEBUG', 'False').lower() == 'true'
    
    logger.info(f"Starting Audio Analysis Service on {host}:{port}")
    app.run(host=host, port=port, debug=debug)

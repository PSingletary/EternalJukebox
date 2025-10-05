"""
Test script for Audio Analysis Service
"""

import requests
import json
import os
import tempfile
import numpy as np
import soundfile as sf

def create_test_audio(duration=10, sample_rate=22050):
    """Create a simple test audio file"""
    # Generate a simple sine wave with some variation
    t = np.linspace(0, duration, int(sample_rate * duration))
    
    # Create a simple melody
    frequencies = [440, 523, 659, 784]  # A, C, E, G
    audio = np.zeros_like(t)
    
    for i, freq in enumerate(frequencies):
        start_time = i * duration / len(frequencies)
        end_time = (i + 1) * duration / len(frequencies)
        mask = (t >= start_time) & (t < end_time)
        audio[mask] = 0.3 * np.sin(2 * np.pi * freq * t[mask])
    
    # Add some noise for realism
    audio += 0.01 * np.random.randn(len(audio))
    
    return audio, sample_rate

def test_health_endpoint():
    """Test health check endpoint"""
    print("Testing health endpoint...")
    try:
        response = requests.get("http://localhost:5000/health")
        print(f"Health check status: {response.status_code}")
        print(f"Health check response: {response.json()}")
        return response.status_code == 200
    except Exception as e:
        print(f"Health check failed: {e}")
        return False

def test_file_upload():
    """Test file upload analysis"""
    print("\nTesting file upload analysis...")
    
    # Create test audio file
    audio, sr = create_test_audio(duration=5)
    
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp_file:
        sf.write(tmp_file.name, audio, sr)
        
        try:
            with open(tmp_file.name, 'rb') as f:
                files = {'file': f}
                response = requests.post("http://localhost:5000/analyze/file", files=files)
            
            print(f"File upload status: {response.status_code}")
            
            if response.status_code == 200:
                result = response.json()
                print(f"Analysis result keys: {list(result.keys())}")
                print(f"Track duration: {result.get('track', {}).get('duration', 'N/A')}s")
                print(f"Number of beats: {len(result.get('beats', []))}")
                print(f"Number of bars: {len(result.get('bars', []))}")
                print(f"Number of sections: {len(result.get('sections', []))}")
                return True
            else:
                print(f"File upload failed: {response.text}")
                return False
                
        except Exception as e:
            print(f"File upload test failed: {e}")
            return False
        finally:
            os.unlink(tmp_file.name)

def test_url_analysis():
    """Test URL-based analysis (mock test)"""
    print("\nTesting URL analysis...")
    
    # This would normally test with a real URL, but for now just test the endpoint structure
    try:
        test_data = {
            "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"  # Rick Roll for testing
        }
        
        response = requests.post(
            "http://localhost:5000/analyze/url",
            json=test_data,
            timeout=60  # Longer timeout for download
        )
        
        print(f"URL analysis status: {response.status_code}")
        
        if response.status_code == 200:
            result = response.json()
            print(f"URL analysis successful - duration: {result.get('track', {}).get('duration', 'N/A')}s")
            return True
        else:
            print(f"URL analysis failed: {response.text}")
            return False
            
    except Exception as e:
        print(f"URL analysis test failed: {e}")
        return False

def test_supported_formats():
    """Test supported formats endpoint"""
    print("\nTesting supported formats...")
    try:
        response = requests.get("http://localhost:5000/supported-formats")
        print(f"Supported formats status: {response.status_code}")
        if response.status_code == 200:
            formats = response.json()
            print(f"Supported formats: {formats}")
            return True
        return False
    except Exception as e:
        print(f"Supported formats test failed: {e}")
        return False

def main():
    """Run all tests"""
    print("Audio Analysis Service Test Suite")
    print("=" * 40)
    
    tests = [
        ("Health Check", test_health_endpoint),
        ("Supported Formats", test_supported_formats),
        ("File Upload", test_file_upload),
        ("URL Analysis", test_url_analysis),
    ]
    
    results = []
    for test_name, test_func in tests:
        print(f"\n{test_name}:")
        print("-" * len(test_name))
        success = test_func()
        results.append((test_name, success))
    
    print("\n" + "=" * 40)
    print("Test Results:")
    print("=" * 40)
    
    for test_name, success in results:
        status = "✅ PASS" if success else "❌ FAIL"
        print(f"{test_name}: {status}")
    
    passed = sum(1 for _, success in results if success)
    total = len(results)
    print(f"\nOverall: {passed}/{total} tests passed")
    
    if passed == total:
        print("🎉 All tests passed!")
    else:
        print("⚠️  Some tests failed. Check the logs above.")

if __name__ == "__main__":
    main()

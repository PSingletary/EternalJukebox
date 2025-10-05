# EternalJukebox

This repository is a fork which fixes bugs and adds modern audio analysis capabilities. The upstream repo is unmaintained.

You can visit the hosted instance of this repository [here](https://eternalbox.floriegl.tech), in case you want to mess around with it without doing all the hard stuff.

The source files for the EternalJukebox, a rehosting of the Infinite Jukebox with enhanced audio source support.
This repo contains everything you need to host the EternalJukebox on your own server with support for 1800+ audio sources via yt-dlp and librosa-based audio analysis!

# Docker Install

## Prerequisites

You need to install [docker](https://docs.docker.com/engine/install/) and [docker-compose](https://docs.docker.com/compose/install/)

## Configuration

To configure, grab `.env.example` from this repository, rename it to `.env` and change the appropriate values.

You'll also need `envvar_config.yaml`, but only edit this if you want some advanced configuration.

## Running

You can use the following `docker-compose.yaml` as a starting point to run the application without needing a database running, if you want to use a db refer to the main `docker-compose.yaml`.

```yaml
version: "3"

services:
  main:
    image: daviirodrig/eternaljukebox
    ports:
      - 8080:8080
    env_file:
      - .env
    volumes:
      - "./envvar_config.yaml:/EternalJukebox/envvar_config.yaml"
```

To start, run `docker compose up -d` in the folder containing `envvar_config.yaml`, `.env` and `docker-compose.yaml`. To stop, run `docker compose down`.

If you want to you can upgrade the image by pulling the newest with `docker pull daviirodrig/eternaljukebox` and then restart with `docker compose down` and `docker compose up -d`

If you want to change the port from 8080, edit `docker-compose.yml` port, to be `- <your port>:8080`

## New Features

### 🎵 **Enhanced Audio Source Support**
- **1800+ Platforms**: Support for YouTube, SoundCloud, Bandcamp, Vimeo, and many more via yt-dlp
- **Direct URL Input**: Simply paste any supported audio URL to create endless loops
- **No API Keys Required**: Most audio sources work without authentication

### 🧠 **Advanced Audio Analysis**
- **librosa-based Analysis**: Modern Python microservice for accurate beat, bar, and segment detection
- **Real-time Generation**: Automatic audio analysis for any supported source
- **Cached Results**: Fast performance with intelligent caching

### 🐳 **Docker Integration**
- **Multi-service Architecture**: Separate containers for main app and analysis service
- **Health Monitoring**: Automatic health checks and service discovery
- **Development Support**: Hot-reload development environment

### 📋 **Configuration**
The application now uses an `analysisServiceUrl` configuration option:
```yaml
analysisServiceUrl: http://localhost:5000
```

# Manual Install

## Prerequisites

### Java:

##### Windows

Download and install Java from https://www.java.com/en/download/

##### Debian-based Linux distributions

For Ubuntu or Debian-based distributions execute `sudo apt-get install default-jre` in the terminal

##### Fedora and CentOS

There is a tutorial for installing java on Fedora and CentOS at https://www.digitalocean.com/community/tutorials/how-to-install-java-on-centos-and-fedora

### Yt-dlp (a more up-to-date fork of Youtube-dl):

##### Windows

Download the .exe at https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe and place it in `C:\Windows\`, or in another folder on the PATH.

##### Linux

Use these commands in the terminal to install youtube-dl on Linux:
`sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp`
`sudo chmod a+rx /usr/local/bin/yt-dlp`

### Python 3.8+ (for Audio Analysis Service):

##### Windows

Download and install Python from https://www.python.org/downloads/

##### Linux

Most distributions include Python 3. Install additional dependencies:
```bash
sudo apt-get install python3-pip python3-dev libsndfile1-dev
pip3 install -r analysis-service/requirements.txt
```

### ffmpeg:

##### Windows

Download the exe from https://ffmpeg.zeranoe.com/builds/ and place it in `C:\Windows\`, or in another folder on teh PATH.

##### Linux

ffmpeg is available to download in most distributions using `sudo apt-get install ffmpeg` or equivalent

## Getting the project files:

The whole process of obtaining project files is much easier now, as the build process is streamlined through Jenkins.

The project site is over [here](https://jenkins.abimon.org/job/EternalJukebox/), and contains the individual files to download, or an all-in-one zip for all the files. Alternatively, the files can be found over at a permanent server [here](https://abimon.org/eternal_jukebox)

## Configuring

First thing to do is create a new file called either `config.yaml` or `config.json` (YAML tends to be easier to write, but takes up slightly more space), then open it with notepad/notepad++ on Windows and whatever text editor you like on Linux (for example nano: `nano config.json`)

You will need a Youtube Data API key, which you can find about how to obtain [here](https://developers.google.com/youtube/v3/getting-started).

## Supported Audio Sources

The EternalJukebox now supports **1800+ audio sources** via yt-dlp integration, including:

- **YouTube** - Videos and playlists
- **SoundCloud** - Tracks and albums  
- **Bandcamp** - Albums and tracks
- **Vimeo** - Videos with audio
- **Mixcloud** - DJ mixes and radio shows
- **And many more!** - See [yt-dlp supported sites](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)

Simply paste any supported URL into the search interface to create endless loops!

There are a variety of config options (documentation coming soon) that allow most portions of the EternalJukebox to be configured, and these can be entered here.

## Starting the services:

### 1. Start the Audio Analysis Service

First, start the Python analysis service:
```bash
cd analysis-service
python app.py
```

The analysis service should start on `http://localhost:5000`

### 2. Start the Main Application

Open the Terminal or Command Prompt and navigate to the folder containing your EternalJukebox.jar:
```bash
cd /path/to/EternalJukebox
java -jar EternalJukebox.jar
```

If everything went right it should say `Listening at http://0.0.0.0:11037`

You should now be able to connect to it with a browser through http://localhost:11037

**Note**: Make sure to configure the `analysisServiceUrl` in your config file to point to `http://localhost:5000`

Congrats you did it!

## Manually Building

This is not recommended unless you're making some modifications, and as such should only be performed by more advanced users

You'll need to obtain a copy of [Gradle](https://gradle.org/install/), likely a [JDK](https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html), and [Jekyll](https://jekyllrb.com/). You'll also need the project files in some capacity, be it `git clone` or downloading the archive from GitHub.

From there, building in Gradle is simple; just run `gradle clean shadowJar` from the project file directory. That should produce a jar file in `build/libs` that will work for you. In addition, you'll need to build the Jekyll webpages, which can be done by running `jekyll build --source _web --destination web`

# turbo-audio-dubu

A React Native Turbo Module for advanced audio playback and control.

[![npm version](https://img.shields.io/npm/v/turbo-audio-dubu.svg)](https://www.npmjs.com/package/turbo-audio-dubu)
[![npm downloads](https://img.shields.io/npm/dm/turbo-audio-dubu.svg)](https://www.npmjs.com/package/turbo-audio-dubu)

## Installation

```sh
npm install turbo-audio-dubu
# or
yarn add turbo-audio-dubu
```

### iOS Setup

Add the following permissions to your `Info.plist`:

```xml
<!-- Background audio playback -->
<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>

<!-- Microphone permission (if needed) -->
<key>NSMicrophoneUsageDescription</key>
<string>We need access to your microphone for audio recording.</string>
```

## Links

- NPM Package: https://www.npmjs.com/package/turbo-audio-dubu
- GitHub Repository: https://github.com/kim-jiha95/turbo-audio

## Features

- Streamlined audio playback with rich metadata support
- Media controls integration (lock screen, notification)
- Precise audio seeking and position tracking
- Background playback capabilities
- Promise-based API for easy integration

## Usage

```javascript
import { TurboAudio } from "turbo-audio-dubu";

// Load and play audio with metadata
const playAudio = async () => {
  try {
    const result = await TurboAudio.loadAndPlayAudio(
      "https://example.com/audio.mp3",
      {
        title: "Amazing Song",
        artist: "Famous Artist",
        artwork: "https://example.com/artwork.jpg", // Optional album art
      }
    );

    console.log(`Audio loaded successfully: ${result.success}`);
    console.log(`Total duration: ${result.duration} seconds`);
  } catch (error) {
    console.error("Failed to load audio:", error);
  }
};

// Playback control
const controlPlayback = async () => {
  // Pause current audio
  const paused = await TurboAudio.pauseAudio();
  console.log(`Audio paused: ${paused}`);

  // Resume playback
  const resumed = await TurboAudio.resumeAudio();
  console.log(`Audio resumed: ${resumed}`);

  // Stop and unload audio
  const stopped = await TurboAudio.stopAudio();
  console.log(`Audio stopped: ${stopped}`);

  // Seek to specific position (in seconds)
  const seeked = await TurboAudio.seekAudio(45.5);
  console.log(`Seeked to position: ${seeked}`);
};

// Get current playback status
const checkStatus = async () => {
  const status = await TurboAudio.getStatus();
  console.log("Playback status:");
  console.log(`- Duration: ${status.duration} seconds`);
  console.log(`- Current position: ${status.position} seconds`);
  console.log(`- Is playing: ${status.isPlaying}`);
};
```

## API Reference

### `loadAndPlayAudio(uri: string, trackInfo: TrackInfo): Promise<{duration: number, success: boolean}>`

Loads and starts playing an audio file.

Parameters:

- `uri`: URL or file path of the audio to play
- `trackInfo`: Object containing track metadata
  - `title`: Song title (required)
  - `artist`: Artist name (required)
  - `artwork`: URL to album artwork (optional)

Returns: Promise resolving to object with:

- `duration`: Total duration in seconds
- `success`: Boolean indicating if loading was successful

### `pauseAudio(): Promise<boolean>`

Pauses the currently playing audio.

Returns: Promise resolving to success status

### `resumeAudio(): Promise<boolean>`

Resumes the previously paused audio.

Returns: Promise resolving to success status

### `stopAudio(): Promise<boolean>`

Stops and unloads the current audio.

Returns: Promise resolving to success status

### `seekAudio(position: number): Promise<boolean>`

Seeks to a specific position in the audio.

Parameters:

- `position`: Target position in seconds

Returns: Promise resolving to success status

### `getStatus(): Promise<{duration: number, position: number, isPlaying: boolean}>`

Gets the current playback status.

Returns: Promise resolving to object with:

- `duration`: Total duration in seconds
- `position`: Current position in seconds
- `isPlaying`: Boolean indicating if audio is currently playing

## License

MIT

// AudioPlayerModule.ts
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface TrackInfo {
  title: string;
  artist: string;
  artwork?: string;
}

export interface Spec extends TurboModule {
  loadAndPlayAudio(
    uri: string,
    trackInfo: TrackInfo,
  ): Promise<{
    duration: number;
    success: boolean;
  }>;
  pauseAudio(): Promise<boolean>;
  resumeAudio(): Promise<boolean>;
  stopAudio(): Promise<boolean>;
  seekAudio(position: number): Promise<boolean>;
  getStatus(): Promise<{
    duration: number;
    position: number;
    isPlaying: boolean;
  }>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('TurboAudio');

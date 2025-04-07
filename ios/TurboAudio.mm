#import "TurboAudio.h"
#import <MediaPlayer/MediaPlayer.h>

@interface TurboAudio () <AVAudioPlayerDelegate>
@property (nonatomic, strong, readwrite) AVAudioPlayer *audioPlayer;
@end

@implementation TurboAudio

RCT_EXPORT_MODULE();

- (dispatch_queue_t)methodQueue {
    return dispatch_get_main_queue();
}

- (void)setupAudioSession:(RCTPromiseRejectBlock)reject {
    NSError *error = nil;
    AVAudioSession *session = [AVAudioSession sharedInstance];

    if (![session setCategory:AVAudioSessionCategoryPlayback
                      error:&error]) {
        reject(@"session_error", @"Failed to set audio session category", error);
        return;
    }

    if (![session setActive:YES error:&error]) {
        reject(@"session_error", @"Failed to activate audio session", error);
        return;
    }
}

- (void)setupRemoteCommandCenter {
    MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];

    [commandCenter.playCommand removeTarget:nil];
    [commandCenter.pauseCommand removeTarget:nil];
    [commandCenter.changePlaybackPositionCommand removeTarget:nil];
    [commandCenter.nextTrackCommand removeTarget:nil];
    [commandCenter.previousTrackCommand removeTarget:nil];

    [commandCenter.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        if (self.audioPlayer && !self.audioPlayer.isPlaying) {
            [self.audioPlayer play];
            return MPRemoteCommandHandlerStatusSuccess;
        }
        return MPRemoteCommandHandlerStatusCommandFailed;
    }];

    [commandCenter.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        if (self.audioPlayer && self.audioPlayer.isPlaying) {
            [self.audioPlayer pause];
            return MPRemoteCommandHandlerStatusSuccess;
        }
        return MPRemoteCommandHandlerStatusCommandFailed;
    }];

    [commandCenter.changePlaybackPositionCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        MPChangePlaybackPositionCommandEvent *positionEvent = (MPChangePlaybackPositionCommandEvent *)event;
        if (self.audioPlayer) {
            self.audioPlayer.currentTime = positionEvent.positionTime;
            return MPRemoteCommandHandlerStatusSuccess;
        }
        return MPRemoteCommandHandlerStatusCommandFailed;
    }];
}

RCT_EXPORT_METHOD(loadAndPlayAudio:(NSString *)uri
                  withTrackInfo:(NSDictionary *)trackInfo
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    [self setupAudioSession:reject];

    // 기존 플레이어 정리
    if (self.audioPlayer) {
        [self.audioPlayer stop];
        self.audioPlayer = nil;
    }

    NSError *error = nil;
    NSURL *url;

    if ([uri hasPrefix:@"http://"] || [uri hasPrefix:@"https://"]) {
        url = [NSURL URLWithString:uri];
    } else {
        url = [NSURL fileURLWithPath:uri];
    }

    if (!url) {
        reject(@"invalid_url", @"Invalid audio URL", nil);
        return;
    }

    if ([url.scheme hasPrefix:@"http"]) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
            NSError *downloadError = nil;
            NSData *audioData = [NSData dataWithContentsOfURL:url options:NSDataReadingMappedIfSafe error:&downloadError];

            dispatch_async(dispatch_get_main_queue(), ^{
                if (downloadError || !audioData) {
                    reject(@"download_error", @"Failed to download audio file", downloadError);
                    return;
                }

                NSError *playerError = nil;
                self.audioPlayer = [[AVAudioPlayer alloc] initWithData:audioData error:&playerError];
                [self finishAudioSetup:playerError trackInfo:trackInfo resolve:resolve reject:reject];
            });
        });
    } else {
        self.audioPlayer = [[AVAudioPlayer alloc] initWithContentsOfURL:url error:&error];
        [self finishAudioSetup:error trackInfo:trackInfo resolve:resolve reject:reject];
    }
}

- (void)finishAudioSetup:(NSError *)error
              trackInfo:(NSDictionary *)trackInfo
               resolve:(RCTPromiseResolveBlock)resolve
                reject:(RCTPromiseRejectBlock)reject {
    if (error || !self.audioPlayer) {
        reject(@"load_error", @"Failed to initialize audio player", error);
        return;
    }

    self.audioPlayer.delegate = self;

    [self updateNowPlayingInfo:trackInfo];

    [self setupRemoteCommandCenter];

    if ([self.audioPlayer prepareToPlay]) {
        [self.audioPlayer play];
        resolve(@{
            @"duration": @(self.audioPlayer.duration),
            @"success": @YES
        });
    } else {
        reject(@"playback_error", @"Failed to prepare audio for playback", nil);
    }
}

- (void)updateNowPlayingInfo:(NSDictionary *)trackInfo {
    if (!trackInfo) return;

    NSMutableDictionary *nowPlayingInfo = [NSMutableDictionary dictionary];

    [nowPlayingInfo setObject:trackInfo[@"title"] ?: @"" forKey:MPMediaItemPropertyTitle];
    [nowPlayingInfo setObject:trackInfo[@"artist"] ?: @"" forKey:MPMediaItemPropertyArtist];
    [nowPlayingInfo setObject:@(self.audioPlayer.duration) forKey:MPMediaItemPropertyPlaybackDuration];
    [nowPlayingInfo setObject:@(self.audioPlayer.currentTime) forKey:MPNowPlayingInfoPropertyElapsedPlaybackTime];
    [nowPlayingInfo setObject:@(1.0) forKey:MPNowPlayingInfoPropertyPlaybackRate];

    if (trackInfo[@"artwork"]) {
        NSURL *artworkUrl = [NSURL URLWithString:trackInfo[@"artwork"]];
        if (artworkUrl) {
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
                NSData *imageData = [NSData dataWithContentsOfURL:artworkUrl];
                if (imageData) {
                    UIImage *artwork = [UIImage imageWithData:imageData];
                    if (artwork) {
                        dispatch_async(dispatch_get_main_queue(), ^{
                            MPMediaItemArtwork *mediaArtwork = [[MPMediaItemArtwork alloc] initWithBoundsSize:artwork.size
                                                                                              requestHandler:^UIImage * _Nonnull(CGSize size) {
                                return artwork;
                            }];
                            [nowPlayingInfo setObject:mediaArtwork forKey:MPMediaItemPropertyArtwork];
                            [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nowPlayingInfo;
                        });
                    }
                }
            });
        }
    }

    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nowPlayingInfo;
}

RCT_EXPORT_METHOD(pauseAudio:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    if (self.audioPlayer) {
        [self.audioPlayer pause];

        NSMutableDictionary *nowPlayingInfo = [[MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo mutableCopy];
        [nowPlayingInfo setObject:@(0.0) forKey:MPNowPlayingInfoPropertyPlaybackRate];
        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nowPlayingInfo;

        resolve(@YES);
    } else {
        reject(@"no_player", @"Audio player not initialized", nil);
    }
}

RCT_EXPORT_METHOD(resumeAudio:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    if (self.audioPlayer) {
        [self.audioPlayer play];

        NSMutableDictionary *nowPlayingInfo = [[MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo mutableCopy];
        [nowPlayingInfo setObject:@(1.0) forKey:MPNowPlayingInfoPropertyPlaybackRate];
        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nowPlayingInfo;

        resolve(@YES);
    } else {
        reject(@"no_player", @"Audio player not initialized", nil);
    }
}

RCT_EXPORT_METHOD(stopAudio:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    if (self.audioPlayer) {
        [self.audioPlayer stop];
        self.audioPlayer.currentTime = 0;

        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;

        resolve(@YES);
    } else {
        reject(@"no_player", @"Audio player not initialized", nil);
    }
}

RCT_EXPORT_METHOD(seekAudio:(double)position
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    if (!self.audioPlayer) {
        reject(@"no_player", @"Audio player not initialized", nil);
        return;
    }

    if (position < 0 || position > self.audioPlayer.duration) {
        reject(@"invalid_position", @"Invalid seek position", nil);
        return;
    }

    self.audioPlayer.currentTime = position;

    NSMutableDictionary *nowPlayingInfo = [[MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo mutableCopy];
    [nowPlayingInfo setObject:@(position) forKey:MPNowPlayingInfoPropertyElapsedPlaybackTime];
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nowPlayingInfo;

    resolve(@YES);
}

RCT_EXPORT_METHOD(getStatus:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    if (self.audioPlayer) {
        resolve(@{
            @"duration": @(self.audioPlayer.duration),
            @"position": @(self.audioPlayer.currentTime),
            @"isPlaying": @(self.audioPlayer.isPlaying)
        });
    } else {
        reject(@"no_player", @"Audio player not initialized", nil);
    }
}

#pragma mark - AVAudioPlayerDelegate

- (void)audioPlayerDidFinishPlaying:(AVAudioPlayer *)player successfully:(BOOL)flag {
    if (flag) {
        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
    }
}

- (void)audioPlayerDecodeErrorDidOccur:(AVAudioPlayer *)player error:(NSError *)error {
    NSLog(@"Audio player decode error: %@", error);
}

@end
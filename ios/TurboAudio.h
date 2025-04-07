// // TurboAudio.h
#import <React/RCTBridgeModule.h>
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>

@interface TurboAudio : NSObject <RCTBridgeModule>
@property (nonatomic, strong, readonly) AVAudioPlayer *audioPlayer;
@end
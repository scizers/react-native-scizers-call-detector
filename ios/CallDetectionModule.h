#import <React/RCTEventEmitter.h>
#import <CallKit/CallKit.h>

@interface CallDetectionModule : RCTEventEmitter <CXCallObserverDelegate>
@end

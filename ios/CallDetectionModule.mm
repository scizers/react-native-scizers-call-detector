#import "CallDetectionModule.h"

@implementation CallDetectionModule {
    CXCallObserver* _callObserver;
    BOOL _hasListeners;
    UIBackgroundTaskIdentifier bgTask;
}

RCT_EXPORT_MODULE(CallDetectionModule)

- (NSDictionary *)constantsToExport {
    return @{ @"Incoming": @"Incoming", @"Offhook": @"Offhook", @"Disconnected": @"Disconnected", @"Missed": @"Missed" };
}

+ (BOOL)requiresMainQueueSetup { return YES; }

- (NSArray<NSString *> *)supportedEvents { return @[ @"PhoneCallStateUpdate" ]; }

- (void)startObserving { _hasListeners = YES; }
- (void)stopObserving { _hasListeners = NO; }

RCT_EXPORT_METHOD(startListener) {
    _callObserver = [[CXCallObserver alloc] init];
    __weak CallDetectionModule *weakSelf = self;
    [_callObserver setDelegate:weakSelf queue:nil];
    bgTask = UIBackgroundTaskInvalid;
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(applicationDidEnterBackground:) name:UIApplicationDidEnterBackgroundNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(applicationWillEnterForeground:) name:UIApplicationWillEnterForegroundNotification object:nil];
}

RCT_EXPORT_METHOD(stopListener) {
    _callObserver = nil;
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    if (bgTask != UIBackgroundTaskInvalid) {
        [[UIApplication sharedApplication] endBackgroundTask:bgTask];
        bgTask = UIBackgroundTaskInvalid;
    }
}

- (void)applicationDidEnterBackground:(NSNotification *)notification {
    UIApplication *app = [UIApplication sharedApplication];
    bgTask = [app beginBackgroundTaskWithName:@"CallDetection" expirationHandler:^{ [app endBackgroundTask:bgTask]; bgTask = UIBackgroundTaskInvalid; }];
}

- (void)applicationWillEnterForeground:(NSNotification *)notification {
    if (bgTask != UIBackgroundTaskInvalid) { [[UIApplication sharedApplication] endBackgroundTask:bgTask]; bgTask = UIBackgroundTaskInvalid; }
}

RCT_EXPORT_METHOD(makePhoneCall:(NSString *)phoneNumber resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject) {
  dispatch_async(dispatch_get_main_queue(), ^{
    NSString *phoneURLString = [NSString stringWithFormat:@"tel://%@", phoneNumber];
    NSURL *phoneURL = [NSURL URLWithString:phoneURLString];
    if ([[UIApplication sharedApplication] canOpenURL:phoneURL]) {
      [[UIApplication sharedApplication] openURL:phoneURL options:@{} completionHandler:^(BOOL success) { success ? resolve(@(YES)) : reject(@"call_failed", @"Failed to make the phone call", nil); }];
    } else {
      reject(@"cannot_open_url", @"Cannot open phone URL", nil);
    }
  });
}

RCT_EXPORT_METHOD(checkPhoneState:(RCTResponseSenderBlock)callback) {
    if (!_callObserver) { _callObserver = [[CXCallObserver alloc] init]; }
    BOOL isOnCall = NO;
    for (CXCall *call in _callObserver.calls) { if (!call.hasEnded) { isOnCall = YES; break; } }
    callback(@[@(isOnCall)]);
}

- (void)callObserver:(CXCallObserver *)callObserver callChanged:(CXCall *)call {
    if (!_hasListeners) return;
    NSString *state;
    if (call.hasEnded) state = @"Disconnected";
    else if (call.hasConnected) state = @"Connected";
    else if (call.isOutgoing) state = @"Dialing";
    else state = @"Incoming";
    [self sendEventWithName:@"PhoneCallStateUpdate" body:state];
}
@end

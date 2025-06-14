import {
  NativeEventEmitter,
  Platform,
  PermissionsAndroid,
} from 'react-native';

import NativeCallDetector from './NativeCallDetection';

export const permissionDenied = 'PERMISSION DENIED';

const NativeCallDetectorAndroid = NativeCallDetector;

const requestPermissionsAndroid = (permissionMessage: {
  title: string;
  message: string;
  buttonPositive: string;
}) => {
  const requiredPermission = parseInt(Platform.Version.toString(), 10) >= 9
    ? PermissionsAndroid.PERMISSIONS.READ_CALL_LOG
    : PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE;

  return PermissionsAndroid.check(requiredPermission)
    .then((gotPermission) => gotPermission
      ? true
      : PermissionsAndroid.request(requiredPermission, permissionMessage)
          .then((result) => result === PermissionsAndroid.RESULTS.GRANTED),
    );
};

const permissionMessage = {
  title: 'Phone State Permission',
  message: 'This app needs access to your phone state in order to react and/or to adapt to incoming calls.',
  buttonPositive: 'OK',
};

class CallDetectorManager {
  subscription: NativeEventEmitter | undefined;
  callback: (state: string, phoneNumber?: string) => void;

  constructor(
    callback: (state: string, phoneNumber?: string) => void,
    readPhoneNumberAndroid = false,
    permissionDeniedCallback: (error?: string | boolean) => void = () => {},
    initCallback: (platform?: string) => void = () => {},
  ) {
    this.callback = callback;

    if (Platform.OS === 'ios') {
      // should never happen here, android file
      return;
    } else if (NativeCallDetectorAndroid) {
      if (readPhoneNumberAndroid) {
        requestPermissionsAndroid(permissionMessage)
          .then((permissionGrantedReadState) => {
            if (!permissionGrantedReadState) {
              permissionDeniedCallback(permissionGrantedReadState);
            }
          })
          .catch(permissionDeniedCallback);
      }

      NativeCallDetectorAndroid.startListener().then((data) => {
        if (data === 'success') {
          this.subscription = new NativeEventEmitter();
          this.subscription.addListener('PhoneCallStateUpdateAndroid', callback);
          initCallback();
        }
      });
    }
  }

  static checkPhoneState(cb: any = () => {}) {
    if (NativeCallDetectorAndroid) {
      NativeCallDetectorAndroid.checkPhoneState(cb);
    }
  }

  static makePhoneCallAndroid(phoneNumber: string): void {
    if (!phoneNumber) return;
    if (NativeCallDetectorAndroid) {
      NativeCallDetectorAndroid
        .makePhoneCall(phoneNumber)
        .catch((error) => console.warn('makePhoneCall error', error));
    }
  }

  dispose() {
    NativeCallDetectorAndroid && NativeCallDetectorAndroid.stopListener();
    if (this.subscription) {
      this.subscription.removeAllListeners('PhoneCallStateUpdate');
      this.subscription.removeAllListeners('PhoneCallStateUpdateAndroid');
      this.subscription = undefined;
    }
  }
}

export default CallDetectorManager;

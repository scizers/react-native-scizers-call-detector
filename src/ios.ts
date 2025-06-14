import {
  NativeEventEmitter,
  Platform,
  PermissionsAndroid,
} from 'react-native';

import CallDetectionModule from './NativeCallDetection';

export type CallEvent = 'Disconnected' | 'Connected' | 'Incoming' | 'Dialing' | 'Offhook' | 'Missed';
export const permissionDenied = 'PERMISSION DENIED';

const requestPermissionsAndroid = async (permissionMessage: { title: string; message: string; buttonPositive: string }) => {
  const requiredPermission = parseInt(Platform.Version.toString(), 10) >= 9
    ? PermissionsAndroid.PERMISSIONS.READ_CALL_LOG
    : PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE;
  try {
    const gotPermission = await PermissionsAndroid.check(requiredPermission);
    if (gotPermission) return true;
    const result = await PermissionsAndroid.request(requiredPermission, permissionMessage);
    return result === PermissionsAndroid.RESULTS.GRANTED;
  } catch (error) {
    console.error('Error checking permission:', error);
    return false;
  }
};

const permissionMessage = {
  title: 'Phone State Permission',
  message: 'This app needs access to your phone state in order to react and/or to adapt to incoming calls.',
  buttonPositive: 'OK',
};

export default class CallDetector {
  private eventEmitter?: NativeEventEmitter;
  private callback: (event: CallEvent, phoneNumber: string) => void;

  constructor(
    callback: (event: CallEvent, phoneNumber: string) => void,
    readPhoneNumberAndroid = false,
    permissionDeniedCallback: (error?: string) => void = () => {},
    initCallback: (platform?: string) => void = () => {},
  ) {
    this.callback = callback;
    if (Platform.OS === 'ios') {
      console.log('CallDetectionModule ios before startliner  ');
      CallDetectionModule.startListener();
      console.log('CallDetectionModule ios before NativeEventEmitter  ');
      this.eventEmitter = new NativeEventEmitter(CallDetectionModule as any);
      this.eventEmitter.addListener('PhoneCallStateUpdate', (state: string) => {
        this.callback(state as CallEvent, '');
      });
      initCallback('ios');
    } else if (readPhoneNumberAndroid) {
      requestPermissionsAndroid(permissionMessage)
        .then((permissionGranted) => {
          if (!permissionGranted) permissionDeniedCallback(permissionDenied);
        })
        .catch((error) => {
          console.error('Error requesting permission:', error);
          permissionDeniedCallback(permissionDenied);
        });
      CallDetectionModule.startListener();
      this.eventEmitter = new NativeEventEmitter(CallDetectionModule as any);
      this.eventEmitter.addListener('CallStateUpdate', (event: { state: string; phoneNumber: string }) => {
        this.callback(event.state as CallEvent, event.phoneNumber || '');
      });
      initCallback();
    }
  }

  dispose() {
    CallDetectionModule.stopListener();
    this.eventEmitter?.removeAllListeners('PhoneCallStateUpdate');
    this.eventEmitter?.removeAllListeners('CallStateUpdate');
    this.eventEmitter = undefined;
  }
}

export const CheckPhoneState = (cb: (isInCall: boolean) => void = () => {}) => {
  CallDetectionModule.checkPhoneState(cb);
};

export const MakePhoneCall = (phoneNumber: string): void => {
  if (!phoneNumber) return;
  CallDetectionModule.makePhoneCall(phoneNumber);
};

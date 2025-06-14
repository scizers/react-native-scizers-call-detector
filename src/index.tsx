import { Platform } from 'react-native';
import CallDetectorAndroid from './android';
import CallDetectorIos, { CheckPhoneState, MakePhoneCall } from './ios';

const CallDetector = Platform.OS === 'ios' ? CallDetectorIos : CallDetectorAndroid;

if (Platform.OS === 'ios') {
  (CallDetector as any).checkPhoneState = CheckPhoneState;
  (CallDetector as any).makePhoneCall = MakePhoneCall;
}

export default CallDetector;

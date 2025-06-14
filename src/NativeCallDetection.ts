import { TurboModuleRegistry } from 'react-native';
import type { TurboModule } from 'react-native';

export interface CallLog {
  number: string;
  timestamp: number;
  duration: number;
  type: number;
  name: string;
}

export interface Spec extends TurboModule {
  // Common methods
  startListener(): Promise<string>;
  stopListener(): void;
  checkPhoneState(callback: (isInCall: boolean) => void): void;
  makePhoneCall(phoneNumber: string): Promise<boolean>;
  getCallLogs(minTimestamp: number, maxTimestamp: number): Promise<CallLog[]>;

  // Constants
  getConstants(): {
    Incoming: string;
    Offhook: string;
    Disconnected: string;
    Missed: string;
  };
}

export default TurboModuleRegistry.getEnforcing<Spec>('CallDetectionModule');

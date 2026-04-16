import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class FlashTogglePlugin {
  FlashTogglePlugin._();

  static const MethodChannel _channel = MethodChannel(
    'flash_toggle_plugin/methods',
  );

  static Future<bool> toggleLight() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      throw UnsupportedError(
        'Flashlight control is supported only on Android.',
      );
    }

    final isEnabled = await _channel.invokeMethod<bool>('toggleLight');
    return isEnabled ?? false;
  }

  static Future<int> get batteryLevel async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) {
      throw UnsupportedError(
        'Battery level is supported only on Android in this plugin.',
      );
    }

    final level = await _channel.invokeMethod<int>('getBatteryLevel');

    if (level == null || level < 0) {
      throw PlatformException(
        code: 'BATTERY_UNAVAILABLE',
        message: 'Battery level is unavailable.',
      );
    }

    return level;
  }
}

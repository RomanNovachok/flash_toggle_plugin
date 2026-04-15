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
}

// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_test/flutter_test.dart';
import 'package:v_video_compressor_example/main.dart';

void main() {
  testWidgets('Video Compressor App smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const MyApp());

    // Verify the title in both the app bar and the page header.
    expect(find.text('V Video Compressor Example'), findsNWidgets(2));

    // Verify the primary video-selection action is available.
    expect(find.text('Pick Video from Gallery'), findsOneWidget);

    // Verify the dedicated 4K test route is discoverable.
    expect(find.text('4K Test'), findsOneWidget);
  });
}

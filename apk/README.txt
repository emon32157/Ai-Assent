JARVIS for Android - APK Build Report
======================================
Generated: 2026-11-08

Package: app.shilex.android
Version Name: 0.1.0
Version Code: 1

Built APK:
----------
File: jarvis-debug-0.1.0.apk
File Size: 146,616,574 bytes (139.82 MB)
SHA-256: 46ceee3d4b6c69228f924fd2c2d9444690895aec30361cae53589c83c28c4105
minSdk: 29 (Android 10)
targetSdk: 35 (Android 15)
ABIs Included: arm64-v8a, armeabi-v7a, x86_64
Signed: Yes (Debug certificate: CN=Android Debug, O=Android, C=US)
Signer SHA-256 Digest: 81c6a40805d9d4604a9cc92b4facebb1e3898e4d1967bfca0234c3776bbfbe59

Release APK:
------------
Status: Skipped
Reason: Release signing environment variables (SHILEX_KEYSTORE and SHILEX_KEYSTORE_PASSWORD) are not set in the build environment. A release APK requires the release keystore.

Sanity Checks:
--------------
- Vosk Model Assets: Included under assets/vosk/ (e.g. assets/vosk/am/final.mdl, assets/vosk/conf/mfcc.conf)
- Native Libraries: Included for all declared ABIs (libshilex_aec.so, libvosk.so, libonnxruntime.so, libonnxruntime4j_jni.so, libjnidispatch.so, libandroidx.graphics.path.so)
- Permissions: Confirmed android.permission.CALL_PHONE is present in AndroidManifest along with required audio, foreground service, and calendar permissions.

Install Command:
----------------
adb install -r apk/jarvis-debug-0.1.0.apk

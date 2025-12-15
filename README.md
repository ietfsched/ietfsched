IETF Android App
======================

The Internet Engineering Task Force (IETF) is a large open international community of network designers, operators, vendors, and researchers concerned with the evolution of the Internet architecture and the smooth operation of the Internet. It is open to any interested individual.

The actual technical work of the IETF is done in its working groups, which are organized by topic into several areas (e.g., routing, transport, security, etc.). Much of the work is handled via mailing lists. The IETF holds meetings three times per year.

This project is the Android app for the IETF meeting. The app supports devices running Android 8.0+ (API level 26+), and is optimized for phones and tablets of all shapes and sizes.

<h2>Source</h2>

The source code in this repository reflects the app and Datatracker API as of IETF 124.

<h2>Features</h2>

With the app, you can:

- View the conference agenda and edit your personal schedule
- View detailed session agenda

<h2>How to build IETFSched</h2>

1. Install the following software:
   - Android Studio (latest version recommended):
         https://developer.android.com/studio
   - Android SDK with the following components:
     - Android SDK Platform-tools (latest)
     - Android SDK Build-tools (latest)
     - Android SDK Platform API 36 (Android 16)
     - Android SDK Platform API 26 (Android 8.0) - minimum SDK
     - AndroidX libraries (included with Android Studio)

2. Ensure you have Java 17 or later installed.

3. Open the project in Android Studio, or build from the command line:
   ```bash
   cd ietfsched
   ./gradlew assembleDebug
   ```

4. The app uses:
   - Gradle 8.13
   - Android Gradle Plugin 8.13.1
   - compileSdkVersion 36 (Android 16)
   - minSdkVersion 26 (Android 8.0)
   - targetSdkVersion 36
   - Java 17
   - AndroidX libraries (Activity 1.9.2, etc.)
   - GeckoView for WebView functionality

<h2>Copyright</h2>

    Copyright 2011 Google Inc. All rights reserved.
    Copyright 2015 Isabelle Dalmasso

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

<!-- inconsequential change -->

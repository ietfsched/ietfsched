IETF Android App
======================

The Internet Engineering Task Force (IETF) is a large open international community of network designers, operators, vendors, and researchers concerned with the evolution of the Internet architecture and the smooth operation of the Internet. It is open to any interested individual.

The actual technical work of the IETF is done in its working groups, which are organized by topic into several areas (e.g., routing, transport, security, etc.). Much of the work is handled via mailing lists. The IETF holds meetings three times per year.

This project is the Android app for the IETF meeting. The app supports devices running Android 2.3+, and is optimized for phones and tablets of all shapes and sizes.

<h2>Source</h2>

The source code in this repository reflects the app as of IETF 92.

<h2>Features</h2>

With the app, you can:

- View the conference agenda and edit your personal schedule
- View detailed session agenda

<h2>How to build IETFSched</h2>

1. Install the following software:
       - Android SDK:
         http://developer.android.com/sdk/index.html

2. Run the Android SDK Manager by running the `android` command in a terminal window.

3. In the Android SDK Manager, ensure that the following are installed, and are updated to the latest available version:
       - Tools > Android SDK Platform-tools (rev 21 or above)
       - Tools > Android SDK Tools (rev 23.0.5 or above)
       - Tools > Android SDK Build-tools version 20
       - Tools > Android SDK Build-tools version 21 (rev 21.1.2 or above)
       - Android 4.4.2 > SDK Platform (API 19)
       - Extras > Android Support Library

4. List the targets available by running the `android list target` command in a terminal window.  Remember the target id (i.e. android-xx) that have "Name: Android 4.4.2".

5. Update the project by going into the `android` directory and run `android update project --subprojects -p . -t android-xx`, replacing the `android-xx` by the target id from the previous step.

6. Copy the `android-support-v4.jar` file that you will find in the `extras/android/support/v4/` directory of your Android SDK into the `android/libs` directory of your project.

7. Run the `ant clean debug` command to build the app.

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

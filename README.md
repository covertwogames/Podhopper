# PodHopper

A privacy-first podcast player for Android.

PodHopper is an independent fork of [Pocket Casts](https://github.com/Automattic/pocket-casts-android), the podcast app open-sourced by Automattic. It keeps the parts that make a great listening app and strips out the parts that watch you. No ads, no analytics, no usage tracking. It is built and maintained by Cover Two Strategies LLC, doing business as Cover Two Games.

## What makes it different

- **No tracking.** The inherited third-party analytics trackers have been removed. Nothing about how you use the app is transmitted anywhere.
- **No ads.** There is no advertising and no ad tracking.
- **Local by default.** Your listening stats are calculated and kept on your device.
- **Sync that stays out of your way.** Sign in once and your subscriptions, your queue, and the exact spot you paused follow you from device to device. Start an episode on your phone, pick it up on a tablet, and it is right where you left it. That synced information is used for one thing only, keeping your devices in step.

See the in-app privacy policy or [podhopper.app](https://podhopper.app) for the full details.

## Building

PodHopper is a standard multi-module Gradle project written in Kotlin with Jetpack Compose.

Requirements:

- Android Studio (latest stable) with its bundled JDK
- Android SDK with compileSdk 36 installed
- minSdk 24

To build:

1. Clone the repository.
2. Open the project folder in Android Studio.
3. Let the initial Gradle sync finish.
4. Select the app run configuration and press Run, or build a debug APK from the command line with the Gradle wrapper:

   - Windows (PowerShell): `.\gradlew assembleDebug`
   - macOS or Linux: `./gradlew assembleDebug`

## Roadmap

- **Android Automotive (AAOS).** The goal PodHopper is built toward is carrying that same effortless handoff straight into your dashboard. On vehicles with Android Automotive built in, the episode you started in the kitchen should already be cued up the moment you start the engine, no phone in hand. It is a separate app target with its own sign-in flow, and it is the headline feature still in progress.

## Privacy

PodHopper is built to collect as little as possible. The short version: it does not track you, show ads, run analytics, or sell or share your information. The only information it handles is what is needed to sync your podcasts between your own devices. Full policy: [podhopper.app](https://podhopper.app).

## License

PodHopper is based on Pocket Casts by Automattic and is distributed under the Mozilla Public License 2.0 (MPL-2.0). The full license text is in [LICENSE.md](LICENSE.md).

Original Pocket Casts code is Copyright Automattic, Inc. New PodHopper code is Copyright Cover Two Strategies LLC.

Pocket Casts is a trademark of Automattic, Inc. PodHopper is an independent project and is not affiliated with, sponsored by, or endorsed by Automattic.

## Contact

Questions or feedback: info@covertwogames.com

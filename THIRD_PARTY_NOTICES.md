# Third-party notices

## LibreTube

Rush's YouTube playback integration adapts the extraction and stream-resolution approach used by **LibreTube**.

- Project: https://github.com/Libre-tube/LibreTube
- License: GNU General Public License v3 or later (GPL-3.0-or-later)
- Upstream files informing this adaptation:
  - `app/src/main/java/com/github/libretube/helpers/NewPipeExtractorInstance.kt`
  - `app/src/main/java/com/github/libretube/util/NewPipeDownloaderImpl.kt`
  - `app/src/main/java/com/github/libretube/api/NewPipeMediaServiceRepository.kt`
  - `app/src/main/java/com/github/libretube/api/obj/PipedStream.kt`

The playback adapter in `app/src/main/java/com/widgetly/app/LibreTubePlayback.kt` is an adaptation for Rush's Compose UI. It uses NewPipeExtractor to resolve YouTube audio and video streams, then Media3 to play the resolved URL.

The NewPipeExtractor dependency is provided by the LibreTube project's NewPipeExtractor fork:
https://github.com/libre-tube/NewPipeExtractor

LibreTube and NewPipeExtractor are not affiliated with or endorsed by YouTube or Google.

The complete GPL-3.0-or-later license text is included at `LICENSES/LibreTube-GPL-3.0-or-later.txt`.

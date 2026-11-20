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

The NewPipeExtractor dependency is provided by the LibreTube project's NewPipeExtractor fork.

- Maven coordinate: `com.github.libre-tube:NewPipeExtractor:894ae973d29b7c26502c72834bad636a72e345ca`
- Pinned upstream commit: `894ae973d29b7c26502c72834bad636a72e345ca` (2026-09-22)
- Source: https://github.com/libre-tube/NewPipeExtractor/tree/894ae973d29b7c26502c72834bad636a72e345ca
- License: GPL-3.0-or-later

This fork contains upstream NewPipeExtractor changes used by LibreTube. Keep the included GPL license and corresponding notices when redistributing.

LibreTube and NewPipeExtractor are not affiliated with or endorsed by YouTube or Google.

The complete GPL-3.0-or-later license text is included at `LICENSES/LibreTube-GPL-3.0-or-later.txt`.


## FFmpegKit maintained audio package

Rush uses the community-maintained Android FFmpegKit-compatible audio package to convert offline audio downloads to MP3 on-device.

- Maven artifact: `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`
- Project: https://github.com/ffmpegkit-maintained/ffmpeg
- License: GNU Lesser General Public License v3.0 (LGPL-3.0)
- The audio package includes the LAME MP3 encoder and other audio codecs.
- The complete LGPL-3.0 license text is included at `LICENSES/FFmpegKit-Maintained-LGPL-3.0.txt`.

The package is an independent community-maintained continuation, not affiliated with the original FFmpegKit author. See the upstream project for its build, component and licensing details.

# Lumina — Native Android (API 33+) Jetpack Compose Gallery Architecture

Lumina is a lightweight, fully native Jetpack Compose gallery application built for modern Android (API 33+). It completely eliminates legacy Android Views (no `PlayerView`, `SubsamplingScaleImageView`, `ImageView`, or `MapView`), third-party image loading libraries (Picasso, Glide), and monolithic XML bindings.

---

## Complete Project Directory Tree

```text
Lumina/
├── .fossify
│   └── release-marker.txt
├── .github
│   ├── ISSUE_TEMPLATE
│   │   ├── bug_report.yml
│   │   ├── config.yml
│   │   └── feature_request.yml
│   ├── workflows
│   │   └── folio-compose-ci.yml
│   └── dependabot.yml
├── app
│   ├── src
│   │   ├── debug
│   │   │   └── res
│   │   │       └── values
│   │   │           └── strings.xml
│   │   ├── main
│   │   │   ├── kotlin
│   │   │   │   └── org
│   │   │   │       └── fossify
│   │   │   │           └── gallery
│   │   │   │               ├── engine
│   │   │   │               │   ├── image
│   │   │   │               │   │   ├── NativeImageDecoder.kt
│   │   │   │               │   │   ├── NativeThumbnailLoader.kt
│   │   │   │               │   │   └── NativeTileRegionEngine.kt
│   │   │   │               │   ├── metadata
│   │   │   │               │   │   └── ExifUtils.kt
│   │   │   │               │   └── video
│   │   │   │               │       ├── PlayerPool.kt
│   │   │   │               │       └── VideoGestureState.kt
│   │   │   │               ├── models
│   │   │   │               │   ├── Directory.kt
│   │   │   │               │   └── Medium.kt
│   │   │   │               ├── ui
│   │   │   │               │   ├── data
│   │   │   │               │   │   └── LuminaMediaRepository.kt
│   │   │   │               │   ├── details
│   │   │   │               │   │   ├── ExifMetadataSection.kt
│   │   │   │               │   │   ├── LocationSection.kt
│   │   │   │               │   │   ├── LuminaDetailsBottomSheet.kt
│   │   │   │               │   │   └── StorageInfoSection.kt
│   │   │   │               │   ├── home
│   │   │   │               │   │   ├── AlbumsGrid.kt
│   │   │   │               │   │   ├── GalleryViewModel.kt
│   │   │   │               │   │   ├── LuminaHomeScreen.kt
│   │   │   │               │   │   ├── MediaTimelineGrid.kt
│   │   │   │               │   │   └── SelectionTopBar.kt
│   │   │   │               │   ├── navigation
│   │   │   │               │   │   ├── DockScrollConnection.kt
│   │   │   │               │   │   └── LuminaBottomDock.kt
│   │   │   │               │   ├── theme
│   │   │   │               │   │   ├── LuminaMotion.kt
│   │   │   │               │   │   ├── LuminaSurfaces.kt
│   │   │   │               │   │   └── LuminaTheme.kt
│   │   │   │               │   ├── viewer
│   │   │   │               │   │   ├── LuminaViewer.kt
│   │   │   │               │   │   ├── PhotoPage.kt
│   │   │   │               │   │   ├── VideoPage.kt
│   │   │   │               │   │   ├── ViewerTopBar.kt
│   │   │   │               │   │   └── ViewerViewModel.kt
│   │   │   │               │   ├── LuminaActivity.kt
│   │   │   │               │   └── LuminaApp.kt
│   │   │   │               └── App.kt
│   │   │   ├── res
│   │   │   │   ├── drawable
│   │   │   │   │   ├── ic_aspect_ratio_vector.xml
│   │   │   │   │   ├── ic_cardboard_vector.xml
│   │   │   │   │   ├── ic_crop_rotate_vector.xml
│   │   │   │   │   ├── ic_draw_vector.xml
│   │   │   │   │   ├── ic_explore_off_vector.xml
│   │   │   │   │   ├── ic_explore_vector.xml
│   │   │   │   │   ├── ic_files_vector.xml
│   │   │   │   │   ├── ic_flip_horizontally_vector.xml
│   │   │   │   │   ├── ic_flip_vertically_vector.xml
│   │   │   │   │   ├── ic_folders_vector.xml
│   │   │   │   │   ├── ic_launcher_background.xml
│   │   │   │   │   ├── ic_launcher_foreground.xml
│   │   │   │   │   ├── ic_launcher_monochrome.xml
│   │   │   │   │   ├── ic_maximize_vector.xml
│   │   │   │   │   ├── ic_minimize_vector.xml
│   │   │   │   │   ├── ic_photo_filter_vector.xml
│   │   │   │   │   ├── ic_play_vector.xml
│   │   │   │   │   ├── ic_playback_speed_slow_vector.xml
│   │   │   │   │   ├── ic_playback_speed_vector.xml
│   │   │   │   │   ├── ic_portrait_photo_vector.xml
│   │   │   │   │   ├── ic_rotate_right_vector.xml
│   │   │   │   │   ├── ic_slideshow_vector.xml
│   │   │   │   │   ├── ic_vector_speaker_off.xml
│   │   │   │   │   ├── ic_vector_speaker_on.xml
│   │   │   │   │   ├── ic_vector_warning_colored.xml
│   │   │   │   │   └── shortcut_image.xml
│   │   │   │   ├── drawable-hdpi
│   │   │   │   │   ├── ic_panorama_outline.webp
│   │   │   │   │   └── sample_logo.webp
│   │   │   │   ├── drawable-nodpi
│   │   │   │   │   └── img_widget_preview.webp
│   │   │   │   ├── drawable-xhdpi
│   │   │   │   │   ├── ic_panorama_outline.webp
│   │   │   │   │   └── sample_logo.webp
│   │   │   │   ├── drawable-xxhdpi
│   │   │   │   │   ├── ic_panorama_outline.webp
│   │   │   │   │   └── sample_logo.webp
│   │   │   │   ├── drawable-xxxhdpi
│   │   │   │   │   ├── ic_panorama_outline.webp
│   │   │   │   │   └── sample_logo.webp
│   │   │   │   ├── mipmap-anydpi-v26
│   │   │   │   │   └── ic_launcher.xml
│   │   │   │   ├── mipmap-hdpi
│   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   ├── ic_launcher_amber.webp
│   │   │   │   │   ├── ic_launcher_blue.webp
│   │   │   │   │   ├── ic_launcher_blue_grey.webp
│   │   │   │   │   ├── ic_launcher_brown.webp
│   │   │   │   │   ├── ic_launcher_cyan.webp
│   │   │   │   │   ├── ic_launcher_deep_orange.webp
│   │   │   │   │   ├── ic_launcher_deep_purple.webp
│   │   │   │   │   ├── ic_launcher_grey_black.webp
│   │   │   │   │   ├── ic_launcher_indigo.webp
│   │   │   │   │   ├── ic_launcher_light_blue.webp
│   │   │   │   │   ├── ic_launcher_light_green.webp
│   │   │   │   │   ├── ic_launcher_lime.webp
│   │   │   │   │   ├── ic_launcher_orange.webp
│   │   │   │   │   ├── ic_launcher_pink.webp
│   │   │   │   │   ├── ic_launcher_purple.webp
│   │   │   │   │   ├── ic_launcher_red.webp
│   │   │   │   │   ├── ic_launcher_teal.webp
│   │   │   │   │   └── ic_launcher_yellow.webp
│   │   │   │   ├── mipmap-mdpi
│   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   ├── ic_launcher_amber.webp
│   │   │   │   │   ├── ic_launcher_blue.webp
│   │   │   │   │   ├── ic_launcher_blue_grey.webp
│   │   │   │   │   ├── ic_launcher_brown.webp
│   │   │   │   │   ├── ic_launcher_cyan.webp
│   │   │   │   │   ├── ic_launcher_deep_orange.webp
│   │   │   │   │   ├── ic_launcher_deep_purple.webp
│   │   │   │   │   ├── ic_launcher_grey_black.webp
│   │   │   │   │   ├── ic_launcher_indigo.webp
│   │   │   │   │   ├── ic_launcher_light_blue.webp
│   │   │   │   │   ├── ic_launcher_light_green.webp
│   │   │   │   │   ├── ic_launcher_lime.webp
│   │   │   │   │   ├── ic_launcher_orange.webp
│   │   │   │   │   ├── ic_launcher_pink.webp
│   │   │   │   │   ├── ic_launcher_purple.webp
│   │   │   │   │   ├── ic_launcher_red.webp
│   │   │   │   │   ├── ic_launcher_teal.webp
│   │   │   │   │   └── ic_launcher_yellow.webp
│   │   │   │   ├── mipmap-xhdpi
│   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   ├── ic_launcher_amber.webp
│   │   │   │   │   ├── ic_launcher_blue.webp
│   │   │   │   │   ├── ic_launcher_blue_grey.webp
│   │   │   │   │   ├── ic_launcher_brown.webp
│   │   │   │   │   ├── ic_launcher_cyan.webp
│   │   │   │   │   ├── ic_launcher_deep_orange.webp
│   │   │   │   │   ├── ic_launcher_deep_purple.webp
│   │   │   │   │   ├── ic_launcher_grey_black.webp
│   │   │   │   │   ├── ic_launcher_indigo.webp
│   │   │   │   │   ├── ic_launcher_light_blue.webp
│   │   │   │   │   ├── ic_launcher_light_green.webp
│   │   │   │   │   ├── ic_launcher_lime.webp
│   │   │   │   │   ├── ic_launcher_orange.webp
│   │   │   │   │   ├── ic_launcher_pink.webp
│   │   │   │   │   ├── ic_launcher_purple.webp
│   │   │   │   │   ├── ic_launcher_red.webp
│   │   │   │   │   ├── ic_launcher_teal.webp
│   │   │   │   │   └── ic_launcher_yellow.webp
│   │   │   │   ├── mipmap-xxhdpi
│   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   ├── ic_launcher_amber.webp
│   │   │   │   │   ├── ic_launcher_blue.webp
│   │   │   │   │   ├── ic_launcher_blue_grey.webp
│   │   │   │   │   ├── ic_launcher_brown.webp
│   │   │   │   │   ├── ic_launcher_cyan.webp
│   │   │   │   │   ├── ic_launcher_deep_orange.webp
│   │   │   │   │   ├── ic_launcher_deep_purple.webp
│   │   │   │   │   ├── ic_launcher_grey_black.webp
│   │   │   │   │   ├── ic_launcher_indigo.webp
│   │   │   │   │   ├── ic_launcher_light_blue.webp
│   │   │   │   │   ├── ic_launcher_light_green.webp
│   │   │   │   │   ├── ic_launcher_lime.webp
│   │   │   │   │   ├── ic_launcher_orange.webp
│   │   │   │   │   ├── ic_launcher_pink.webp
│   │   │   │   │   ├── ic_launcher_purple.webp
│   │   │   │   │   ├── ic_launcher_red.webp
│   │   │   │   │   ├── ic_launcher_teal.webp
│   │   │   │   │   └── ic_launcher_yellow.webp
│   │   │   │   ├── mipmap-xxxhdpi
│   │   │   │   │   ├── ic_launcher.webp
│   │   │   │   │   ├── ic_launcher_amber.webp
│   │   │   │   │   ├── ic_launcher_blue.webp
│   │   │   │   │   ├── ic_launcher_blue_grey.webp
│   │   │   │   │   ├── ic_launcher_brown.webp
│   │   │   │   │   ├── ic_launcher_cyan.webp
│   │   │   │   │   ├── ic_launcher_deep_orange.webp
│   │   │   │   │   ├── ic_launcher_deep_purple.webp
│   │   │   │   │   ├── ic_launcher_grey_black.webp
│   │   │   │   │   ├── ic_launcher_indigo.webp
│   │   │   │   │   ├── ic_launcher_light_blue.webp
│   │   │   │   │   ├── ic_launcher_light_green.webp
│   │   │   │   │   ├── ic_launcher_lime.webp
│   │   │   │   │   ├── ic_launcher_orange.webp
│   │   │   │   │   ├── ic_launcher_pink.webp
│   │   │   │   │   ├── ic_launcher_purple.webp
│   │   │   │   │   ├── ic_launcher_red.webp
│   │   │   │   │   ├── ic_launcher_teal.webp
│   │   │   │   │   └── ic_launcher_yellow.webp
│   │   │   │   ├── values
│   │   │   │   │   ├── bools.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   ├── dimens.xml
│   │   │   │   │   ├── donottranslate.xml
│   │   │   │   │   ├── integers.xml
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   └── styles.xml
│   │   │   │   ├── values-ar
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-az
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-b+es+419
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-be
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-bg
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-bn
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-bn-rBD
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-bqi
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-br
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-bs
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ca
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ckb
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-cr
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-cs
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-cy
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-da
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-de
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-el
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-en-rGB
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-en-rIN
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-eo
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-es
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-es-rUS
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-et
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-eu
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-fa
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-fi
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-fil
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-fr
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ga
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-gl
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-gu
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-hi
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-hr
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-hu
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ia
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-in
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-is
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-it
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-iw
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ja
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-kab
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-kn
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ko-rKR
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-kr
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-lt
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ltg
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-lv
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-mk
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ml
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ms
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-my
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-nb-rNO
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ne
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-nl
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-nn
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-or
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-pa
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-pa-rPK
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-pl
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-pt
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-pt-rBR
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-pt-rPT
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ro
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-ru
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-sat
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-si
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-sk
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-sl
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-sr
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-sv
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-sw600dp
│   │   │   │   │   └── dimens.xml
│   │   │   │   ├── values-ta
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-te
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-th
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-tr
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-uk
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-vi
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-w480dp
│   │   │   │   │   └── integers.xml
│   │   │   │   ├── values-w600dp
│   │   │   │   │   └── integers.xml
│   │   │   │   ├── values-zgh
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-zh-rCN
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-zh-rHK
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── values-zh-rTW
│   │   │   │   │   └── strings.xml
│   │   │   │   ├── xml
│   │   │   │   │   └── provider_paths.xml
│   │   │   │   └── resources.properties
│   │   │   ├── AndroidManifest.xml
│   │   │   └── ic_launcher-playstore.png
│   │   └── test
│   │       └── kotlin
│   │           └── org
│   │               └── fossify
│   │                   └── gallery
│   │                       └── ui
│   │                           ├── home
│   │                           │   └── GalleryViewModelTest.kt
│   │                           └── LuminaViewModelTest.kt
│   ├── .gitignore
│   ├── build.gradle.kts
│   ├── detekt-baseline.xml
│   ├── lint-baseline.xml
│   └── proguard-rules.pro
├── gradle
│   ├── wrapper
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── graphics
│   ├── featureGraphic.png
│   ├── foreground.svg
│   ├── icon.svg
│   └── icon.webp
├── .editorconfig
├── .gitignore
├── CHANGELOG.md
├── CODEOWNERS
├── CONTRIBUTING.md
├── Gemfile
├── Gemfile.lock
├── LICENSE
├── LUMINA_REWRITE_PLAN.md
├── README.md
├── build.gradle.kts
├── detekt.yml
├── gradle.properties
├── gradlew
├── gradlew.bat
├── keystore.properties_sample
├── lint.xml
└── settings.gradle.kts
```

---

## Architectural Breakdown & File Responsibilities

### 1. Build & Version Catalog
- **`gradle/libs.versions.toml`**: Centralized Gradle version catalog configuring Kotlin 2.4.21, Compose BOM 2026.11.01, Material 3 1.5.0, Media3 1.11.1, ExifInterface 1.4.2, AGP 9.2.1, compileSdk 37, minSdk 33.
- **`build.gradle.kts`**: Root Gradle configuration with Android Application and Kotlin Compose plugins.
- **`settings.gradle.kts`**: Repository declarations (Google, Maven Central, JitPack) and plugin resolution management.
- **`app/build.gradle.kts`**: Application module build file enforcing minSdk 33, targetSdk 36, compileSdk 37, JVM target 17, and pure Compose dependencies.
- **`app/proguard-rules.pro`**: R8 optimization and shrinking rules for AndroidX and Media3.
- **`LUMINA_REWRITE_PLAN.md`**: Architecture specification document and design principles.

### 2. Manifest & Application Entry
- **`app/src/main/AndroidManifest.xml`**: Single-activity manifest enforcing API 33+ granular permissions (`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`, `ACCESS_MEDIA_LOCATION`) and handling `ACTION_MAIN`, `ACTION_VIEW`, `ACTION_PICK`, and `ACTION_GET_CONTENT`.
- **`app/src/main/kotlin/org/fossify/gallery/App.kt`**: Custom `Application` subclass.
- **`app/src/main/kotlin/org/fossify/gallery/ui/LuminaActivity.kt`**: Single `ComponentActivity` entry point. Calls `enableEdgeToEdge()`, processes launch and picker intents, and hosts the root `LuminaApp` composable.

### 3. Application Shell & Root Routing
- **`app/src/main/kotlin/org/fossify/gallery/ui/LuminaApp.kt`**: Root Compose shell.
  - Hosts single persistent morphing dock (`LuminaBottomDock`) across Home, Selection, and Viewer states.
  - Provides `SharedTransitionLayout` for grid-to-viewer morphing.
  - Controls `isViewerOpen` and passes the filtered timeline scope to the viewer.
  - Instantiates `GalleryViewModel` via `AndroidViewModelFactory`.

### 4. Design System, Theme & Motion
- **`app/src/main/kotlin/org/fossify/gallery/ui/theme/LuminaTheme.kt`**: Material 3 Expressive theme with dynamic color schemes (`dynamicDarkColorScheme`, `dynamicLightColorScheme`) and `MotionScheme.standard()`.
- **`app/src/main/kotlin/org/fossify/gallery/ui/theme/LuminaMotion.kt`**: Centralized spring and transition specifications (critically damped: dampingRatio 0.95f–1.0f, stiffness 550f–800f) ensuring non-bouncy 120Hz animation.
- **`app/src/main/kotlin/org/fossify/gallery/ui/theme/LuminaSurfaces.kt`**: Reusable draw-phase modifiers:
  - `TopEdgeFeatherFade`: Cached vertical gradient scrim behind full-bleed scrolling media.
  - `pressScale`: Immediate touch-down tactile compression (0.94f) executed strictly in `graphicsLayer`.

### 5. Navigation & Persistent Morphing Dock
- **`app/src/main/kotlin/org/fossify/gallery/ui/navigation/LuminaBottomDock.kt`**: Single mounted floating dock. Morphs smoothly between:
  - `DockMode.Home` (Photos & Albums pill tabs)
  - `DockMode.Selection` (Share, Favorite, Delete thumb actions)
  - `DockMode.Viewer` (Share, External Edit, Favorite, Details, Delete)
  - Uses `contentKey = { it::class }` to prevent flickering on media swipes or state toggles.
- **`app/src/main/kotlin/org/fossify/gallery/ui/navigation/DockScrollConnection.kt`**: Primitive accumulator with 28dp directional hysteresis for 120Hz scroll hiding/revealing without recomposition churn.

### 6. Home Screen & Timeline Feed
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/LuminaHomeScreen.kt`**: Main feed container. Manages full-bleed feed, fixed-height floating top chrome, runtime API 33+ permissions, back gestures, and scoped MediaStore intent launchers.
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/AlbumDetailOverlay.kt`**: Stacked Album overlay mounted above AlbumsGrid with quick scale+slide entry and native API 33+ PredictiveBackHandler revealing AlbumsGrid underneath.
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/FloatingTopChrome.kt`**: Centered floating dark date pill (e.g., Sun, 1 Nov) with zero-churn derivedStateOf lookup, right-aligned circular overflow pill, optional left album back-chip, and clean in-place swaps to SelectionTopBar.
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/MediaTimelineGrid.kt`**: Grouped date headers, multi-column `LazyVerticalGrid` (2–5 columns), 2dp tight spacing, draw-phase selection insets (0.86f scale + 14dp corner rounding), and `sharedBounds` transitions.
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/AlbumsGrid.kt`**: Two-column album folder grid with negative bucket ID proxying to avoid thumbnail cache collisions.
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/SelectionTopBar.kt`**: 52dp floating selection top bar showing count, Select All, and Close buttons.
- **`app/src/main/kotlin/org/fossify/gallery/ui/home/GalleryViewModel.kt`**: `AndroidViewModel` owning library UI state, album filtering, search, selection, and MediaStore mutations.

### 7. Fullscreen Viewer
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/LuminaViewer.kt`**: `HorizontalPager` container with `WindowInsetsControllerCompat` synchronization, dark system bar icon enforcement, single hoisted `PlayerPool`, draw-phase Crop ↔ Fit Canvas overlay morph, predictive-back tile snapping, and swipe-away video pausing.
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/ViewerTopBar.kt`**: Gradient-backed top action bar with back button, media title, and overflow menu (Favorite, Details, Set as wallpaper, Open with, Use as).
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/VideoTransportPill.kt`**: Slim 44dp floating transport pill sitting 8dp above the Viewer bottom dock for video playback (Play/Pause, custom Canvas scrubber, duration, Mute).
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/AddToAlbumSheet.kt`**: Modal bottom sheet for the + Add to dock button (pick existing album or create a new one).
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/PhotoPage.kt`**: Deep-zoom tiling, immediate thumbnail preview display during full bitmap decode, decoded full ImageBitmap reporting for crisp close morphs, and `AnimatedImageDrawable` gesture dispatch.
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/VideoPage.kt`**: `androidx.media3.ui.compose.PlayerSurface` (`SURFACE_TYPE_SURFACE_VIEW`) driven by hoisted `PlayerPool`.
- **`app/src/main/kotlin/org/fossify/gallery/ui/viewer/ViewerViewModel.kt`**: State holder for viewer index, chrome visibility, metadata sheet, and favorite/trash actions.

### 8. Technical Details Bottom Sheet
- **`app/src/main/kotlin/org/fossify/gallery/ui/details/LuminaDetailsBottomSheet.kt`**: Modal bottom sheet container for metadata.
- **`app/src/main/kotlin/org/fossify/gallery/ui/details/ExifMetadataSection.kt`**: Camera make/model, lens, exposure (aperture, shutter speed, ISO, focal length), flash, and video codecs.
- **`app/src/main/kotlin/org/fossify/gallery/ui/details/LocationSection.kt`**: Geocoded reverse-lookup address with tap-to-open map intent.
- **`app/src/main/kotlin/org/fossify/gallery/ui/details/StorageInfoSection.kt`**: File path (with tap-to-copy), dimensions, file size, and timestamp.

### 9. Data Layer & Domain Models
- **`app/src/main/kotlin/org/fossify/gallery/models/Medium.kt`**: Lean, `@Immutable` media domain model.
- **`app/src/main/kotlin/org/fossify/gallery/models/Directory.kt`**: Album directory model.
- **`app/src/main/kotlin/org/fossify/gallery/ui/data/LuminaMediaRepository.kt`**: Reactive MediaStore querying with debounced `ContentObserver` and native scoped `createTrashRequest`, `createDeleteRequest`, and `createFavoriteRequest`.

### 10. Native Graphics & Video Engines
- **`app/src/main/kotlin/org/fossify/gallery/engine/share/RecentShareStore.kt`**: Passive Sharesheet target recorder (`ShareTargetReceiver` via `EXTRA_CHOSEN_COMPONENT`), package manager query seeder, and cached `ImageBitmap` icon provider for the swipe-up Smart Share FAB.
- **`app/src/main/kotlin/org/fossify/gallery/engine/image/NativeThumbnailLoader.kt`**: Fast hardware thumbnail loading via `ContentResolver.loadThumbnail` with bounded LRU memory caching and cooperative coroutine cancellation.
- **`app/src/main/kotlin/org/fossify/gallery/engine/image/NativeImageDecoder.kt`**: Native `ImageDecoder` pipeline decoding hardware bitmaps and animated drawables (`AnimatedImageDrawable` for GIF/WebP/APNG).
- **`app/src/main/kotlin/org/fossify/gallery/engine/image/NativeTileRegionEngine.kt`**: Deep-zoom tile engine using `BitmapRegionDecoder` on Compose `Canvas` for pan/pinch zooming (1x to 8x) without memory spikes.
- **`app/src/main/kotlin/org/fossify/gallery/engine/video/PlayerPool.kt`**: Lifecycle-aware shared ExoPlayer pool auto-pausing on page swipe.
- **`app/src/main/kotlin/org/fossify/gallery/engine/video/VideoGestureState.kt`**: Compose gesture overlay handling volume, brightness, and horizontal scrub dragging.
- **`app/src/main/kotlin/org/fossify/gallery/engine/metadata/ExifUtils.kt`**: Off-main-thread EXIF extractor using `ExifInterface` and `MediaStore.setRequireOriginal`.
- **`app/src/main/kotlin/org/fossify/gallery/engine/metadata/NativeMetadataEngine.kt`**: Video and image metadata aggregator.

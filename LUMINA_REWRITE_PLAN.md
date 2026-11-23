# Lumina direct Compose rewrite

## Decisions

- Repository: `skray9403/Gallery`
- Rewrite branch: `rewrite/compose-expressive`
- Strategy: direct migration; `main` remains the untouched Fossify Gallery baseline.
- Android application ID: `com.skray.gallery` (debug suffix: `.debug`).
- Kotlin/resource namespace: `org.fossify.gallery` during the transition. This is intentionally decoupled from the install ID so the existing source tree and generated resource references do not break in the foundation commit.
- Product name: **Lumina**.
- Data policy: fresh app-private state. No legacy database, preferences or private files are imported. Media in shared storage is not changed; Lumina will request access and rebuild its index when the media layer is migrated.
- Build deliverables: FOSS and Google Play debug APKs from GitHub Actions. Release signing is a later stage and requires configured signing material.

## Design and motion reference

The design reference is the user's `skray9403/monomail` repository, specifically:

- `core/designsystem/.../theme/Theme.kt`: `MaterialExpressiveTheme`, wallpaper-derived dynamic color, and `MotionScheme.expressive()`.
- `core/designsystem/.../theme/Animations.kt`: centralized spring profiles and restrained content fades.
- `app/.../ui/navigation/ExpressivePersistentDock.kt` and `feature/inbox/.../BottomDockBar.kt`: one persistent floating navigation surface and animated selection.
- The 19 November 2026 Monomail performance commit: directional scroll thresholds, primitive accumulators, less per-frame state work, and removal of blanket `animateItem()` usage where it added scrolling overhead.

Lumina takes these as design and motion principles, not as a copied mail UI. Its first shell uses stock Material 3 navigation components, dynamic color where available, rounded expressive surfaces, and locally owned motion tokens.

## Migration stages

1. **Foundation:** new Lumina identity, Compose compiler/dependencies, expressive theme and motion tokens, Compose activity/launcher path, initial Photos/Albums shell, and GitHub Actions verification of both flavors.
2. **Media domain:** permission-aware MediaStore querying, mixed photo/video models, album grouping, and a Picasso thumbnail bridge are now implemented. SAF paging, background indexing, and format-specific thumbnail validation remain.
3. **Library UI:** photo grid, date grouping, albums, sorting, filtering, search, multi-select and favorites.
4. **Viewer:** full-screen image viewer, zoom/pan, GIF/APNG/WebP, HDR-aware rendering, video via Media3, and sharing.
5. **Editing and file operations:** crop, rotate, resize, filters, EXIF, delete/restore, wallpaper and file operations.
6. **Advanced library features:** hidden/excluded folders, recycle bin, OTG/SD-card handling, widgets, shortcuts, import/export and permission edge cases.
7. **Hardening and cleanup:** accessibility, adaptive layouts, performance/memory checks, tests, package migration, removal of legacy XML/ViewBinding screens and unused dependencies, signed release pipelines.

## Guardrails

- Preserve the current Fossify code as a behaviour and compatibility reference while each feature is ported. It is not intended to remain a parallel end-user UI.
- Keep format support intact: AVIF, JPEG XL, animated WebP, APNG, GIF, SVG and RAW handling must not be dropped as a side effect of moving the screens to Compose.
- Do not replace the existing decoder/viewer stack until each format has a verified Compose-compatible or Android View interop path.
- Do not run local compilation as the validation source. Pushes to this branch trigger the GitHub Actions build; both FOSS and Google Play debug APKs and reports are uploaded as workflow artifacts.

# Implementation Plan - Fix Unresolved reference 'kotlinOptions'

The project is reporting an `Unresolved reference 'kotlinOptions'` error in `app/build.gradle.kts`. This occurs because the Kotlin Gradle plugin is not applied, which is required to use the `kotlinOptions` configuration block within the `android` extension.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/USER/AndroidStudioProjects/MovilDilo/gradle/libs.versions.toml)
- Add `kotlin` version (using `2.0.0` as a stable modern version).
- Add `kotlin-android` plugin to the `[plugins]` section.

#### [MODIFY] [build.gradle.kts](file:///C:/Users/USER/AndroidStudioProjects/MovilDilo/build.gradle.kts) (root)
- Add the Kotlin Android plugin to the `plugins` block.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/USER/AndroidStudioProjects/MovilDilo/app/build.gradle.kts)
- Apply the `kotlin-android` plugin.
- Add the `kotlinOptions` block inside `android` to configure `jvmTarget = "11"`, ensuring it matches the `compileOptions` Java version.

## Verification Plan

### Automated Tests
- Run `gradle_sync` to verify that the "Unresolved reference" error is resolved and the project structure is correctly recognized.

### Manual Verification
- Verify that line 28 (or the new location of `kotlinOptions`) no longer shows an error in the IDE.

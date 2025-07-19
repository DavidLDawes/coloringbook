# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android coloring book application that allows users to color SVG vector images. The app demonstrates SVG usage in Android and includes custom touch-based color selection and painting functionality.

## Key Dependencies and Libraries

- **PhotoView**: Custom fork from `https://github.com/chrisbanes/PhotoView` (in `photoview/` module)
- **Sharp library**: For SVG rendering from `https://github.com/Pixplicity/sharp` (in `vectimage/` module)
- **Android SDK**: Target/Compile SDK 36, Min SDK 26
- **Java**: Requires Java 17 (project uses language version 17)

## Build Commands

**Important**: This project requires Java 17 to build. The current environment may have Java 11, which will cause build failures.

- **Build**: `./gradlew build`
- **Assemble**: `./gradlew assemble`
- **Tests**: `./gradlew test` and `./gradlew connectedAndroidTest`
- **Lint**: `./gradlew lint` (uses lint-baseline.xml)
- **Clean**: `./gradlew clean`

## Architecture

### Module Structure
- **app/**: Main Android application module
  - Core activity: `MainActivity.java` - handles color picker UI and main app logic
  - Custom views: `PhilImageView.java` (main coloring canvas), `BrushImageView.java`, `VectorImageView.java`
  - Database: SQLite with `DataBaseHelper.java`, `SectorsDAO.java`, `dbsDAO.java`
- **photoview/**: Custom photo viewing library (fork)
- **vectimage/**: SVG vector image handling library

### Key Components
- **MainActivity**: Main activity with color picker gradients, touch handling, and menu actions (share, undo, clear)
- **PhilImageView**: Central coloring canvas that extends VectorImageView with PhotoView integration
- **BrushImageView**: Displays current brush/color selection
- **VectorImageView**: Base class for SVG rendering and touch interaction
- **Database Layer**: SQLite storage for sector coloring data

### Assets
- SVG files in `app/src/main/assets/`: `brush7.svg`, `ul.svg`
- Standard Android resources in `res/` directories

## Development Notes

- **Namespace**: `ml.formi.apps` (note: there's a typo in namespace vs package name `ml.fomi.apps`)
- **Version**: Currently 2.3.0 (versionCode 5)
- **ProGuard**: Enabled for release builds with custom rules in `proguard-rules.pro`
- **Lint**: Uses baseline file at `app/lint-baseline.xml`

## Special Configurations

- **Gradle Properties**: Custom JVM args for memory management, parallel builds enabled
- **JitPack**: Used for dependency resolution with auth token
- **GitHub Actions**: Has CI workflows for Android builds (`android.yml`, `gradle.yml`)
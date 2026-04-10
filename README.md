# ScreenShot Captor

A tiny Java Swing desktop utility for taking screenshots. Click a button, get a timestamped image. Supports full-screen and drag-to-select region capture, multi-monitor setups, and optionally appends each capture into an RTF document so you can build help docs incrementally. Zero runtime dependencies — just the JDK.

## Requirements

- Java 8 or newer
- Maven 3.x

## Build

```sh
mvn package
```

## Run

From sources:

```sh
mvn compile exec:java -Dexec.mainClass=in.adarshr.screenshotcaptor.ScreenShotCaptor
```

Or after compiling:

```sh
java -cp target/classes in.adarshr.screenshotcaptor.ScreenShotCaptor
```

The configuration file is loaded from the classpath, so the app can be launched from any working directory.

## Usage

The app launches ready to go: **Full** mode and **Image to disk** are on by default, so you can press the capture button immediately without configuring anything.

The window has, top to bottom:

1. **Take ScreenShot** button — fires the capture.
2. **Filename text field** — optional custom name for this one capture. Leave it as the placeholder to use an auto-generated `IMG_yyyyMMdd_HHmmssSSS` name.
3. **Full / Region** radio toggles:
   - **Full** captures every connected display (the union of all monitors).
   - **Region** opens a translucent overlay across all monitors. Click and drag to select a rectangle, release to capture, **Esc** to cancel.
4. **Image to disk** checkbox — when on, writes the screenshot to the configured `Location` in the configured `Format` (PNG, JPG, etc.). On by default.
5. **RTF document** checkbox + **Browse...** button — when on, also appends the screenshot into an RTF document of your choice. Ticking the checkbox the first time prompts a file picker; the picked path is shown below. Use Browse to change it. Toggle the checkbox off to pause RTF logging without forgetting the path.

Either or both output checkboxes can be on. If both are off, the capture button is disabled.

### RTF help-doc workflow

Each captured image is appended to the chosen RTF file with a two-line caption above it:

```
IMG_20260410_103015123      <- the filename used for this capture
2026-04-10 10:30:15         <- human-readable 24-hour timestamp

[image, ~6 inches wide]
```

The file is created on first append if it doesn't exist. Word, LibreOffice, and Pages all open RTF as a normal editable document — open it later, rewrite the captions into help-doc prose, save as `.docx` if you want.

If the RTF file is open in Word when a capture fires (Windows holds an exclusive lock), the append fails and the app shows a warning dialog. The disk image is still saved if that option is on, so nothing is lost — close Word and the next capture will succeed.

### Importing existing screenshots

The **Add image to RTF...** button (inside the *Save to* group, enabled once you've picked an RTF target via Browse) opens a multi-select file picker filtered to image files. Each chosen image is appended to the RTF document with the same caption format as live captures: the filename (without extension) on one line, and the file's last-modified time formatted as a 24-hour timestamp on the next.

This is useful when you can't use live screen capture — for example, on a macOS machine where you don't have admin rights to grant Screen Recording permission. You can still take screenshots with the built-in OS shortcut (Cmd+Shift+4 on macOS, PrtSc on Linux/Windows), then bring them into the help doc through this button. The picker remembers the last directory you imported from, so after navigating to your custom screenshot folder once, subsequent imports start there.

A summary dialog appears at the end showing how many images were appended and which ones (if any) failed.

## Configuration

All tunable behavior lives in `src/in/adarshr/screenshotcaptor/ScreenShotCaptor.properties`. The file is bundled onto the classpath at build time.

| Key | Purpose | Default |
| --- | --- | --- |
| `Title` | Window title | `ScreenShot Captor` |
| `ButtonName` | Capture button label | `Take ScreenShot` |
| `TextFieldDefaultString` | Placeholder shown in the filename field | `File Name` |
| `LookAndFeel` | Fully-qualified Swing L&F class name (optional) | system default |
| `Location` | Output directory (trailing separator optional) | current working directory |
| `Format` | Image format passed to `ImageIO.write` (`png`, `jpg`, ...) | `png` |
| `Prefix` | Filename prefix used when no custom name is given | `IMG_` |
| `TimeFormat` | `SimpleDateFormat` pattern appended after the prefix (24-hour) | `yyyyMMdd_HHmmssSSS` |

## Project layout

Non-standard Maven layout: sources live directly under `src/in/adarshr/screenshotcaptor/` (configured via `<sourceDirectory>src</sourceDirectory>` in `pom.xml`), and the same directory is registered as a `<resource>` so the `.properties` file ships on the classpath.

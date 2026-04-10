# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Maven project targeting Java 1.8. Note the non-standard source layout: `<sourceDirectory>src</sourceDirectory>` in `pom.xml` (sources live under `src/in/adarshr/screenshotcaptor/`, not `src/main/java`). `pom.xml` also declares `src` as a `<resource>` directory filtered to `**/*.properties`, so `ScreenShotCaptor.properties` (which sits next to the `.java` file in the package) is copied onto the classpath at build time.

- Build: `mvn package`
- Run from sources: `mvn compile exec:java -Dexec.mainClass=in.adarshr.screenshotcaptor.ScreenShotCaptor`
- Or after compile: `java -cp target/classes in.adarshr.screenshotcaptor.ScreenShotCaptor`

Configuration is loaded via `getClass().getResourceAsStream("ScreenShotCaptor.properties")`, so the app no longer cares about the working directory. If the resource is missing, `defaultFileNameText` falls back to the literal `"File Name"` rather than NPEing.

There are no tests and no lint configuration in this repo.

## Architecture

Single-file Swing application (`ScreenShotCaptor.java`). The `JFrame` subclass owns the UI, configuration loading, and capture orchestration. Two private static nested classes handle isolated concerns: `RegionSelector` owns the drag-to-select overlay, and `RtfAppender` owns the dependency-free RTF append writer.

- Constructor loads `ScreenShotCaptor.properties`, applies an optional `LookAndFeel`, and builds a fixed-size frame containing: capture button, filename `JTextField`, **Full / Region** `JRadioButton` group, two output `JCheckBox`es (**Image to disk** + **RTF document**), a Browse button, and a small path label. Defaults at launch: Full mode + Image-to-disk on, RTF off — so the app is one click away from a screenshot without configuration.
- `actionPerformed` dispatches to `doFullScreenCapture()` or `doRegionCapture()` based on the radio. If both output checkboxes are off, the capture button is disabled via `updateCaptureEnabled()` (called from item listeners on both checkboxes); the early-return in `actionPerformed` is a belt-and-braces guard.
- The RTF checkbox's item listener opens a `JFileChooser` (filtered to `*.rtf`, auto-appends extension if missing) the first time it's selected and `rtfTarget == null`. If the user cancels the picker, the checkbox un-checks itself. The chosen file is held only in memory — there is no cross-launch persistence by design.
- `virtualScreenBounds()` returns the union `Rectangle` over every `GraphicsDevice` from `GraphicsEnvironment.getLocalGraphicsEnvironment()`. Both capture paths use this so multi-monitor setups are handled uniformly.
- Both capture paths funnel into `captureRect(Rectangle)`, which:
  1. Computes the base filename (custom text if typed, else `Prefix` + `TimeFormat` timestamp).
  2. Calls `Robot.createScreenCapture(rect)` once, then routes the resulting `BufferedImage` to whichever sinks are enabled.
  3. If **Image to disk** is on, writes the image to `Location` in the configured `Format` via `ImageIO.write`.
  4. If **RTF document** is on and `rtfTarget` is set, calls `RtfAppender.append(...)`. Any `IOException` becomes a `JOptionPane` warning rather than killing the capture — the disk image (if enabled) is written first, so a locked RTF never costs you the screenshot.
- Both paths use a `javax.swing.Timer` (~150 ms) before firing the actual `Robot` capture so the hidden main frame / disposed overlay is fully off-screen first — without the delay the captured image can include the disappearing window.

`RtfAppender` is dependency-free RTF generation:

- If the target file is missing or empty, writes a minimal `{\rtf1\ansi\deff0\n}` skeleton first.
- Always encodes the embed as PNG via `ImageIO.write(image, "png", baos)` regardless of the user's `Format` choice for disk output. PNG is the only RTF blip type with reliable cross-version Word rendering, and re-encoding from the in-memory `BufferedImage` is essentially free.
- Uses `RandomAccessFile` to scan backward from EOF for the last `}` (skipping trailing whitespace), seeks there, writes the new fragment plus a fresh `}`, and truncates with `setLength`. This avoids loading the whole file into memory but does require the file to be a single valid RTF document with one top-level brace pair.
- Image dimensions: `\picw`/`\pich` carry the source pixel dimensions; `\picwgoal`/`\pichgoal` are computed in twips (1 inch = 1440) from a fixed 6.0-inch target width, with height scaled proportionally.
- Caption format: `\pard\sa120 <filename>\line <readable timestamp>\par` immediately above the `\pict` group. `rtfEscape` handles `\`, `{`, `}`, and non-ASCII via `\uNNNN?`.

`RegionSelector` is a per-call helper:

- Builds an undecorated `JWindow` sized to the virtual screen bounds with a per-pixel translucent background (`Color(0,0,0,0)`), falling back to `setOpacity(0.35f)` if the platform rejects per-pixel translucency.
- The content `JPanel` paints a dim fill, then uses `AlphaComposite.Clear` to "punch out" the user's selection rectangle and draws a red border around it.
- Mouse listeners track press/drag/release in panel-local coordinates. The release callback receives a Rectangle in panel coordinates; `doRegionCapture()` translates it back to screen coordinates by adding the virtual-bounds origin (which can be negative on multi-monitor setups where the primary isn't the leftmost display).
- Esc cancels via `InputMap`/`ActionMap` on `WHEN_IN_FOCUSED_WINDOW`. Cancellation passes a null Rectangle to the callback.
- `getLocation()` falls back to the current working directory using `File.separator`, so it works on both Windows and Unix.

Static behavior flows through `ScreenShotCaptor.properties` keys: `Title`, `ButtonName`, `TextFieldDefaultString`, `LookAndFeel`, `Location`, `Format`, `Prefix`, `TimeFormat`. **Dynamic per-session state** (output toggles, RTF target file) lives in the UI rather than the properties file because users change it between sessions; mixing the two on purpose. When adding a new "set once and forget" knob, prefer a property; when adding something users will toggle per capture, prefer a UI control.

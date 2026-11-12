# YTX Feature Research & Roadmap

Maintainer: [kairo-xy](https://github.com/Kairo-xy)

This document compares feature ideas from established open-source video clients and maps them to YTXTube's current codebase. It is a product/engineering research note, not a claim that every idea is implemented.

## Repository baseline

YTXTube is a native Android app using Kotlin, Jetpack Compose, Media3 ExoPlayer, and NewPipeExtractor. The repository already contains substantial playback, UI, discovery, downloads, and privacy-related functionality. Avoid replacing existing systems simply to imitate another app.

Current relevant modules include:

- Playback and stream recovery: `PlaybackService.kt`, `PlayerState.kt`, `PlayerFallback.kt`, `PlayerTracks.kt`, `VideoView.kt`, `VideoSurface.kt`
- Extraction and discovery: `YouTube.kt`, `Feeds.kt`, `Screens.kt`, `Channel.kt`
- Playback extras: `SponsorBlock.kt`, `Hdr.kt`, `HdrFormats.kt`, `CaptionTracks.kt`, `ScrubPreview.kt`
- Downloads and offline state: `Downloads.kt`, `DownloadsUi.kt`
- UI and personalization: `Glass.kt`, `GlassMotion.kt`, `GlassNavBar.kt`, `Theme.kt`, `SettingsScreen.kt`

## Open-source feature references

### NewPipe

Reference: https://github.com/TeamNewPipe/NewPipe

Ideas worth evaluating: local channel subscriptions and groups, local playlists, queueing, audio-only background playback, popup playback, subtitle downloads, and useful history controls. YTX already has background playback, PiP, downloads, and broad YouTube browsing; prioritize gaps rather than duplicate implementations.

### PipePipe

Reference: https://github.com/InfinityLoop1308/PipePipe

Ideas worth evaluating: richer format selection, advanced search filters, per-content playback options, and carefully scoped additional extraction sources. Keep YTX's current YouTube-first architecture unless multi-service support is an explicit product goal.

### LibreTube

Reference: https://github.com/libre-tube/LibreTube

Ideas worth evaluating: subscription groups, playlist bookmarks, optional dislike data, DeArrow, and a refined Material experience. YTX already includes DeArrow and SponsorBlock-related code, so focus on integration quality, settings clarity, and resilience.

## Prioritized implementation roadmap

### P0 — Playback correctness and recovery

1. Audit stream refresh and retry behavior for expired/forbidden URLs, transient network failures, and extractor errors.
2. Ensure retry logic is bounded, preserves playback position and queue state, and does not loop forever.
3. Make error messages actionable: distinguish offline/network issues, unavailable videos, and extraction failures when the underlying error supports it.
4. Add tests around stream selection, retry eligibility, and position preservation before changing core playback behavior.
5. Verify audio/video track pairing, captions, live streams, HDR, and fixed quality preferences across a representative test set.

### P1 — Player experience

1. Make the quality picker explain Auto vs fixed quality and show the active stream/track when possible.
2. Improve queue controls and make next/previous behavior consistent across video and music modes.
3. Keep gestures discoverable and configurable; avoid conflicting swipe zones with system navigation.
4. Review player layouts at narrow widths, landscape, tablet, and Android TV D-pad focus.
5. Validate PiP entry/exit and background session behavior through app lifecycle transitions.

### P2 — Discovery and personal library

1. Search filters and clear filter chips.
2. Subscription groups and focused feeds.
3. Local playlist/bookmark workflows and robust import/export.
4. Searchable watch history and a clear history/privacy control.
5. Better channel tabs and playlist continuation where extractor data is available.

### P3 — Interface and polish

1. Keep the existing Liquid Glass design language; refine spacing, contrast, and motion consistency.
2. Offer compact and comfortable feed density options.
3. Improve empty, loading, offline, and error states.
4. Keep accessibility labels, scalable text, reduced-motion considerations, and TV focus states.
5. Profile startup and scrolling before adding heavier blur or animation effects.

### P4 — Engineering quality

1. Add regression tests for critical player state transitions and parsing.
2. Keep CI build/test checks visible and actionable.
3. Add a reproducible issue template for playback failures (app version, Android version, media type, selected quality, and sanitized logs—never request account tokens or private cookies).
4. Document contribution workflow and third-party licensing checks.
5. Track feature work in small, reviewable commits and test on lower-memory Android devices as well as modern devices.

## Research links

- NewPipe README/features: https://github.com/TeamNewPipe/NewPipe
- PipePipe repository: https://github.com/InfinityLoop1308/PipePipe
- LibreTube README/features: https://github.com/libre-tube/LibreTube

## Implementation guardrails

- Treat this as feature inspiration, not permission to copy source code.
- Review the upstream license and preserve notices before any code reuse.
- Prefer compatible improvements that strengthen the existing extractor/player.
- YouTube extraction and stream availability can change outside YTX's control; do not promise uninterrupted playback.
- Do not add tracking or collect account secrets as part of diagnostics.

# Feature roadmap

Status key: **Implemented** = in a release and built by CI; **Partial** = exists but incomplete; **Planned** = not started. "Device-tested" is `no` for everything added by the lab work so far: CI compiles the app and runs unit tests, but the UI has not been exercised on a phone.

| Area (brief) | Item | Status | Notes |
|---|---|---|---|
| A. Player | Double-tap seek step, hold-to-speed rate, controls timeout, gesture sensitivity | Implemented (YTX Tweaks) | Device-tested: no |
| A. Player | Chapter ticks, thumbnails and haptics while scrubbing, stats HUD, equalizer, loop, skip silence | Implemented | Device-tested: no |
| A. Player | Frame-by-frame stepping while paused | Implemented | Exact seeks; accuracy depends on the stream. Device-tested: no |
| A. Player | Alternative player layouts, customisable control layout | Planned | |
| A. Player | Playback recovery | Implemented (earlier work) | Unit tests for the policy |
| B. Theme | Glass settings and theme presets already in the app; live ambient light with its own settings | Implemented / Partial | Separate player/home themes, theme import/export planned |
| C. Offline | Downloads, storage usage and cache clearing | Existing | Queue reordering, profiles, collections planned |
| D. Queue | Add to queue, remove, clear | Existing | |
| D. Queue | Restore last queue after the app is closed | Implemented | Drag reorder and save as playlist still planned |
| E. Music | Synced lyrics, recently played | Existing | |
| E. Music | Lyrics timing offset; ListenBrainz scrobbling with offline queue | Implemented | Crossfade, folders planned. Device/network-tested: no |
| F. Subscriptions | Subscribe/unsubscribe | Existing | Groups, folders, import/export planned |
| G. Discovery | Personalised "More videos" ranking from title words and channels you watch, with a reason shown, resettable | Implemented | Off by default; only reorders YouTube's related list, so it is not a full recommender. New-channel discovery and a Home feed remain planned |
| H. Home dashboard | Reorderable sections | Planned | |
| I. Privacy | Privacy sheet: clear search history, watched list, resume positions, cache; plain-language list of what leaves the device | Implemented | Device-tested: no |
| J. Settings search | Search box that jumps to the matching setting; player-only options show where to find them | Implemented | Device-tested: no |
| Feeds | Hide Shorts/live/watched/short/long/low-view videos; search filters | Implemented | |
| Watch page | Share at time, copy link, open in browser, mark watched, like/dislike bar | Implemented | |

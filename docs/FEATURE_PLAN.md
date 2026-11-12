# YTX Feature Plan

Research notes by kairo-xy. Branch: `kairo-xy/ytx-feature-research`.

## How this was researched

- Feature descriptions of NewPipe, LibreTube, SmartTube, Flow, Tubular, SkyTube, VueTube,
  Simple Tube, ReVanced Extended and FreeTube, taken from public listings (AlternativeTo and
  similar pages) and from Metrolist's README.
- A file-name scan of `app/src/main/java/com/tensei/ytxtube`.
- **Not done:** reading each competitor's source, or checking whether a feature below really is
  missing here. Items marked "verify" must be checked in the code before building.

## Already in YTX (from the README and file names)

SponsorBlock, DeArrow, live chat, comments, synced lyrics, ambient light, HDR formats,
speed panel, skip silence, sleep timer, scrub preview, captions, audio tracks, downloads,
Shorts, PiP, Android TV mode, resume position, network speed, offline media cache.

## Candidate features, best first

| # | Feature | Seen in | Why it matters | Status |
|---|---------|---------|----------------|--------|
| 1 | Playback reliability (retry, resume, clear errors) | all | A player that drops on bad networks feels broken | **Phase 1 started, see PR** |
| 2 | Return YouTube Dislike | NewPipe forks, Flow, Tubular, ReVanced | Restores dislike counts, one small API | verify |
| 3 | Subscription and data import/export (NewPipe, LibreTube, Google Takeout) | NewPipe, LibreTube, Flow | Lets people switch to YTX without starting over | verify |
| 4 | Channel groups and feed filters | LibreTube, NewPipe | Big subscription lists become usable | verify |
| 5 | Local recommendations (no account, all on device) | Flow | Strong privacy selling point | verify, large |
| 6 | Equalizer and loudness boost | Metrolist, Simple Tube | Works for video and music | prototype exists on branch `kairo/video-player-upgrades` |
| 7 | Android Auto and media-session polish | Simple Tube, Metrolist | Music mode in the car | verify |
| 8 | Scrobbling (Last.fm) and Discord presence | Metrolist, Tubular | Popular with music users | verify |
| 9 | Search filters and playlist import | LibreTube, Metrolist | Everyday usability | verify |
| 10 | Casting | Grayjay | TV viewing from the phone | verify, large |

## Phases

1. **Reliability** (in progress): network retry, position and track preservation, tests.
   Next: quality switching (`PlayerState.kt` / `VideoView.kt`) and a device check of PiP,
   background playback, notification controls, audio focus and rotation.
2. **Quick wins:** Return YouTube Dislike, subscription import/export, equalizer.
3. **Library:** channel groups, feed filters, search filters, playlist import.
4. **Music extras:** Android Auto, scrobbling, Discord presence.
5. **Big bets:** local recommendations, casting.

## Rules for every phase

- Verify in code before building.
- Keep `main` untouched. Work on feature branches and open PRs.
- Keep GPLv3 notices and upstream credits (Koustubh12345, FlashTube, NewPipeExtractor).
- Build and test in CI or Android Studio before claiming anything works.

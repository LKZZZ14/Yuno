# Yuno — keep on Skipii upstream merges

Edit when you want something new protected. Cursor rule: `.cursor/rules/yuno-upstream-preserve.mdc`.

## Product & branding

- App name **Yuno**; package **`com.yuno`**.
- Self-update: users always get APKs from **`danielkanan/Yuno`** — merge upstream `Updater.kt` fixes freely, never point releases at Skipii.
- No upload/Create; tabs Home · Subscriptions · Shorts · Search · Library (Shorts optional).

## Look & feel (keep on merge)

- Liquid glass floating tab bar + **liquid glass setting** on Settings page.
- System / Light / Dark only — **no** colour palette chips or wallpaper themes.
- **Settings page** (`Settings.kt`) — not a legacy account settings sheet; don’t re-add appearance chip rows.
- Icons, font weights, `FadingLine` truncation, sharp thumbnails.
- Player: **on-video** controls + **edge scrub**; **no** player bar on tabs; **no** “controls in the bar” setting.
- **Mini-player:** glass accessory, fading titles, morph — keep Yuno.
- **Shorts:** keep Yuno’s glass rail. Drag-to-seek, the double-tap thumb burst, and the progress line are copied from upstream `Shorts.kt`, not rewritten.
- **Player:** on-video controls are upstream `VideoView` (tiles, seek step, fullscreen cards, tap waits so a double tap only seeks). Seek icons are upstream `seekIcon`, including 15/20/25, and they follow the setting. Zoom scales the picture only; captions stay on the unzoomed box. Portrait edge scrub stays. No PlayerBar and no “controls in the bar” setting.
- **Pages:** Settings uses the app background (`c.background`), the same as the rest of the shell.

## Shell & motion (keep Yuno)

- Mini-player ↔ player morph, headers, vignettes, no ripples, `pressable`.

## Your changes — ask only if an update touches them

When you change or add something in Yuno (a layout, a screen, a setting, a behavior), write it here **and** in `.cursor/rules/yuno-upstream-preserve.mdc` in the same turn, without being asked. One line: what changed, and where it lives. Leave it in this list until you decide.

On a later Skipii merge, ask only if that update **conflicts with or rewrites** the change. If Skipii does not touch it, keep it and do not ask. When it does collide, ask: **keep** it, or **let Skipii replace it**?

- **Keep** — move the line into **Look & feel** / **Always keep**.
- **Let Skipii replace it** — delete the line and take upstream there.

- Loading skeletons (`Skeleton.kt`) on feeds, the channel page, and comments. Channel placeholders match the tab they stand in for: the opening page is the header, the tab row, a `PageGutter`, then the 168dp video rows; Videos and Live stay those rows, or the home card when the grid is on; Shorts is the 160dp grid with the title under the poster and the 12dp gaps; Playlists, Shows and Podcasts stay the 142dp rows, or the inset card in the grid; Posts keep the 12dp gap above each one. Spinners stay for playback, sign-in, and posting.
- Settings has no developer card: no "Made by @…", "Come say hi", or social links (`Settings.kt`).
- Liquid glass stays on across launches (`LiquidGlassSetting.init` in `YunoApplication`).
- Page changes use BitChord's switcher (`AppNavigator`, wired in `MainActivity`): tabs cut with no fade, and search, settings, a settings section, a playlist, and a channel crossfade in 180ms. The top bar stays outside that switcher. Its actions pill is BitChord's `ArtworkPageActions` (`PageActions` in `Header.kt`): the glass sits outside their no-bounce spring, and `ActionsRow` opens each gap with the action after it, so the pill shrinks and grows instead of the buttons clipping. The tab bar stays up on those pages.
- The hold menu lifts BitChord's picture (`HoldCapture` in `HeldMenu.kt`, wired from `pressable`): the row is not press-scaled, and its horizontal gutter is trimmed off the picture (`liftPadding`, `PageGutter` on `VideoCard`).
- Yuno's own version, not Skipii's. First release is `versionName` 1.0.0 and `versionCode` 1 in `app/build.gradle.kts`. A Skipii merge must not put their version back.
- Settings uses BitChord's settings chrome throughout (`Settings.kt`, `SettingsChrome.kt`, `IosSwitch.kt`, `PillSegmentedControl.kt`): floating top bar, their padding and grouped cards, the press-stretch switch, and the sliding segment pill. Yuno's own settings stay (playback, captions, SponsorBlock, Shorts, appearance as System/Light/Dark plus liquid glass, language, notifications, about, account). The settings page does not draw `PageTopFade`.
- Signed-out Home still shows recommendations (`InnerTube.guestHome` in `Account.kt`, chosen in `LibraryData` in `Feeds.kt`). Anonymous home browse is a sign-in wall, so guests search for popular videos.
- Channel pages use BitChord's artist header (`Channel.kt`, `ArtworkPalette.kt`): the page is the colour taken from the profile picture the whole way down; the banner sits below the top bar, a bit taller than the 35% step, with a smaller corner radius and no dissolve; the profile picture sits on the left of the name, the @handle and the subscriber count, and those three share the picture's height, spaced evenly; Subscribe is a full-width glass pill that says Subscribe, and the bell is the artist page's glass circle at the end of that row once subscribed; the description lives on the About page; the tabs are Flow's scrollable tab row and the pages swipe (`ChannelFlow.kt`, `Channel.kt`); Live, Shows and Podcasts are included when the channel publishes them, read from the tab URL the way Flow does (`toChannelTab` in `Account.kt`) — Live is the videos list with streams kept, and Shows and Podcasts are the playlist rows; About (with the channel's links) and Posts are their own pages; Latest / Popular / Oldest is a menu and the other filters are chips; videos are 168dp rows, shorts are a 160dp grid with the title under the poster, and playlists are 142dp rows; the line under a video title, the tabs, See all, and those chips use BitChord's artist-line secondary (`ArtworkPalette.onBackgroundVariant`); the bar is the shared floating top bar with share. An empty tab, playlist, or search is one centred line. The header collapses with that page's own scroll (`channelScrollHead`), one motion back to the top. A swipe stays level with the list you left, then the tab you stop on glides back to the top. Another swipe cancels that glide, so a tab you pass through does not keep scrolling; a channel with no Home opens on Videos at the top. Every tab's content is inset by `PageGutter`, and the gap under the tab row — and under the filter chips — is `PageGutter` too, with or without chips. Home is Flow's shelf list (`homeTab`): the featured video is the home page's card (`ChannelFeedVideo`), vertical rows with four showing and an expander, and a horizontal Shorts strip. Videos and Live list view is the 168dp rows (`ChannelVideoRow`); their grid is the home page's card (`ChannelFeedVideo`). Playlists, Shows and Podcasts stay 142dp rows in list; their grid is that same inset card with the count badge (`ChannelPlaylistCard`). The header button (`LayoutAction`) switches list and grid. The filter chips, the sort menu and the search button scroll away with the videos (they are not sticky) and travel with the Videos page while you swipe. They are the subscribe pill, liquid glass while that is on. Latest / Popular / Oldest is Flow's sheet dropdown (`parseChannelFilters`), including channels that also have Members only and Public. The sort menu is the hold menu, blur included (`ChannelSortMenuOverlay`). Videos search is that circle at the end of the filter row. Tapping it clears the chips, puts the field to the left of the circle, and turns the icon into an X that closes it. Posts keep the gap between them.
- Home pull-to-refresh stretches the top bar (more top and bottom padding — about 6dp more at the reload point, and it still grows if you pull past while the wordmark size and fill stay put — wordmark grows to the right from the gutter, fades to 20% with a white left-to-right fill that finishes at the size limit, haptic when full, then the wordmark fills and empties starting fill-unfill-fill, then unfill-fill with a half-second hold between those, and after reload it finishes that pass through to a full fill before returning to normal with a click and a quick scale jump; a pull during that animation still grows the bar and clicks, and letting go of a pull that started during the reload does not start another one) via `BarPullRefresh.kt`, wired in `HomeScreen` (wordmark), `SubscriptionsScreen` and `LibraryScreen` (page titles), `PageTopBar`, `LargePageBrandMark`, and `LargePageTitle`. Playlist pages still use the circular indicator.
- A livestream plays from its DASH manifest URL, or HLS when that URL is missing, using MeeTube's live manifest parser (`LiveDashManifestParser`, wired in `YouTubeMediaSourceFactory`). The watch page shows the watching count and live chat (`Live.kt`). A finished livestream stays on the ordinary player. A live thumbnail shows LIVE in the duration's corner (`DurationPill`, `VideoItem.live`). A live bar with no rewind stays full and says LIVE; one you can rewind shows the time and MeeTube's red LIVE chip (`VideoView.kt`, `jumpToLiveEdge`). A refusal mid-watch reloads the manifest at the live edge (`StreamRecoverer`).
- Home recommendation tabs use the channel page's tab row (`ChannelTabRow`, from `HomeChips` in `Screens.kt`). They scroll away with the open list and clear the top of the screen, with the hairline and a `PageGutter` before the videos. The gap under the wordmark before the tabs is 8dp tighter than under a page title (`contentGap` 4.dp in `HomeChipPager`). A long name keeps 16dp on each side so it does not meet the next tab. Swiping the feed pages between them (`HomeChipPager`); each category keeps its own list (`LibraryData.category`). A swipe stays level with the list you left, then the tab you stop on glides back to the top (`animateScrollToItem`). Another swipe cancels that glide, so a page you pass through does not keep scrolling. A fresh open lays the list out with the header already measured, so the wordmark and tabs are on screen on the first frame (`SubcomposeLayout` in `HomeChipPager`).

## Ask on each merge

- Playback, extraction, bot-check, SponsorBlock **service**, Media3/NewPipe/Gradle deps.
- Large Shorts bank changes.
- A line under **Your changes** only when this update conflicts with it or rewrites that spot.

## OK to follow upstream

| Topic | Your choice |
|--------|----------------|
| Audio-only stage | Upstream OK |
| Queue clear animation | Upstream OK |
| Updater implementation | Upstream OK (releases stay **Yuno** repo) |
| SponsorBlock settings UI | Upstream OK |
| Account / settings structure | Keep Yuno page + liquid glass; no colour chips |
| Related list wording | Upstream OK |
| RTL / `supportsRtl` / bidi text | Upstream OK |
| Playful.kt / Comments panel | Upstream OK unless veto |
| Shorts disk bank | Upstream OK (ask if large diff) |
| Subscriptions channel tap | Opens the channel page |
| Hold menu | iOS-style hold menu only; new video actions go there |
| Playback settings rows | Skip step, chapters stay open, auto PiP, zoom to fill, music speed — must be visible in Settings › Playback |
| Fullscreen zoom | Pinch and the setting both fill the screen |

## Never remake a Skipii feature

Port the upstream code (`com.decio.skipii` → `com.yuno`, user-facing Skipii → Yuno). Do not write a new gesture, animation, layout, or screen that only “does the same thing.” Chrome this file says to keep stays; the upstream behavior is wired into it.

## End of every upstream update

Do this without being asked, after the merge builds. Two lists, one number sequence:

1. **Left out** — Skipii changes the user would notice that Yuno did not ship.
2. **Added** — Skipii changes the user would notice that did land. Continue the numbers (if left-out ends at 7, added starts at 8).

You answer `1. agree`, `2. disagree`, and so on. Then update this file and `.cursor/rules/yuno-upstream-preserve.mdc`:

| List | Agree | Disagree |
|------|--------|----------|
| Left out | Stay out. Add to **Always keep** / Look & feel if it is not already written there. | Port it in. Move it to **OK to follow upstream** and drop any keep-rule that blocks it. |
| Added | Stay in. Add to **OK to follow upstream** if it is not already written there. If it is only in code and not on screen, wire it before calling it added. | Remove it. Add the Yuno behavior to **Always keep**. |

After those votes are applied, post a **fresh** numbered left-out / added list. That list is the new state, not a repeat of the one just voted on. **Keep posting a new list after every fix pass until you say all is good.**

## Open questions

Add checkboxes when something new comes up:

- [ ] _

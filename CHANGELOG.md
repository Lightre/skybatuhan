# Changelog

## 1.8.1

### Fixes
- Exclude SkyBlock armor stand indicators from Auto Fish entity hook recovery to prevent premature reeling

## 1.8.0

### Features
- Add Auto Farm session history with the last 10 sessions, duration, earnings, item counts, coins/hour and stop reason
- Add `/sbh sessions` to view completed Auto Farm sessions
- Verify the Garden scoreboard after reconnect commands before resuming Auto Farm
- Count a failed Garden verification as a reconnect attempt, then disconnect and retry
- Add a configurable Garden verification timeout

## 1.7.1

### Fixes
- Fix: the Farming Profit numbers are no longer wiped when Auto Farm stops by itself (stuck, stop timer, low health...); the last sack batch is still counted
- Fix: the Farming Profit box no longer sits on top of the Auto Fish box by default
- Fix: a Bazaar price update that stalls can no longer stop the prices from refreshing
- Fix: a damaged config.json section could keep the rest of the settings from loading, which could let Auto Farm start by itself when the game launched

### Changes
- The Farming Profit switch moved into HUD > Auto Farm (your existing setting is moved over automatically)

## 1.7.0

### Features
- Add Farming Profit HUD box (coins/hr and total) for Auto Farm
- Count sack gains and inventory drops (Squash, Overclocker 3000, ...)
- Price items with Bazaar sell prices, refreshed every 2 minutes
- Keep coins/hr steady between sack messages and ease value changes
- Skip sack messages from selling or moving items
- Reopen the config menu on the last opened page

## 1.6.0

### Features
- New HUD tab with on-screen boxes: Session Time (time since the game was opened), Auto Farm and Auto Fish (status, run time, time left on the stop timer, and the reconnect progress for Auto Farm)
- Position editor: drag the boxes where you want them (they snap to the screen margin, the center and each other), nudge with the arrow keys, reset one box or all of them. Open it from the HUD tab or with `/sbh hud`
- Boxes follow their screen corner, so they stay in place when the window or GUI scale changes
- All HUD boxes are hidden with F1

### Changes
- HUD boxes are off by default; the Auto Farm and Auto Fish boxes only show while the feature runs (can be changed)
- `/sbh help` lists the new `/sbh hud` command

## 1.5.0

### Features
- New Safety tab: Auto Farm and Auto Fish now stop on low health, death or a sudden teleport (Auto Farm's own `/home` is ignored)
- "Leave Server On Stop" option: after any automatic stop the game waits a few seconds and leaves the server
- Auto Stop Timer for Auto Farm and Auto Fish (counts only the time the feature is on; Auto Farm keeps its time across reconnects)
- Discord heartbeat: a quiet "still running" message at an interval you choose
- Discord message 5 minutes and 1 minute before a Dark Auction
- The taskbar icon flashes on automatic stops, kicks, world changes and failed recoveries when the game is in the background
- New commands: `/sbh farm list`, `/sbh farm remove <id>`, `/sbh status`, `/sbh toggle <farm|fish>` and `/sbh help`

### Changes
- Reconnect for Farming has its own tab, separate from Discord (your existing settings are moved over automatically)
- Turning points are numbered by order; removing one moves the next ones up
- `/sbh farm` clear now asks for confirmation
- Points are saved safely, and an unreadable points.json is kept as points.json.broken instead of being overwritten
- Renamed the Auto Fish "Safety Settings" group to "Delays & Safety"

## 1.4.0

### Features
- Update checker: a clickable chat message appears when you join a world and a newer version is available
- Added "Check For Updates" toggle and "Check Now" button to the About tab

### Fixes
- Fix: session monitor missing the level unload, which could break disconnect handling
- Fix: config is now saved when the game closes (changes made from Mod Menu on the title screen were lost)
- Fix: Auto Fish fishing rod check now also applies to the menu toggle and reconnect
- Fix: overlapping update checks are now prevented
- Fix: Dark Auction notifier now only runs on Hypixel
- Fix: mouse lock mixin now reads the real feature state

### Changes
- Alarm sounds moved into a shared Alerts helper (removed three duplicate copies)
- Link handling moved into a Links utility, repository URLs kept in one place
- Update checker logs tidied
- Removed empty mixin list, a leftover commented import and extra blank lines

## 1.3.0

### Features
- Added "Misc" category to config
- Added "Dark Auction Notifier" which sends notifications 5 minutes and 1 minute before the Dark Auction.

## 1.2.0

### Features
- Added "Lock Mouse" option to Farming (locks camera/mouse movement while Auto Farm is active)
- Added "Lock Mouse" option to Fishing (locks camera/mouse movement while Auto Fish is active)

### Changes
- Reorganized Farming and Fishing categories in the config for a cleaner layout
- Standardized config descriptions, improved clarity, and set better default values

## 1.1.0

### Fixes
- Fix: type mismatch in FishMode comparison causing SkyBlock detection issues
- Fix: session recovery cancelling prematurely during server transfers
- Fix: entity hook recovery firing twice in Auto Fish
- Fix: Auto Fish being able to toggle on without holding a fishing rod
- Fix: action slot activating when it already matched the selected rod slot
- Fix: update checker falsely showing older releases as available updates
- Fix: Auto Farm holding stale level references after being toggled off
- Fix: SkyBlock armor stand distance detection around the bobber

### Changes
- Unified disconnect and reconnect handling into a single robust SessionMonitor
- Added automatic landing/sneaking before resuming Auto Farm after a recovery
- Added "Reset Attempts" button to reconnect configuration
- Added Discord Webhook integration for disconnect, world change, and stuck alerts
- Added ModMenu integration for accessing settings directly from the mod list
- External links now open safely through Minecraft's ConfirmLinkScreen
- Refactored Fishing Mode and Action Slot options to type-safe enums
- Added dynamic library version display in config via ModInfo and ConfigEditorInfoValue
- Enforced minimum validation limits for safety thresholds and waypoint ranges
- Updated Gradle wrapper to 9.8.0 and cleaned up build configurations

## 1.0.0

### Added

- Added `DisconnectNotifier` for Discord webhook notifications on disconnects and world switches
- Added `ReconnectManager` for automatic server reconnect and farming recovery
- Added reconnect rate limiting and cooldown handling

### Changes

- Switched configuration system to MoulConfig
- Added keybind to open the MoulConfig editor
- Added real-time synchronization between feature states and MoulConfig
- Initialized `DisconnectNotifier` and `ReconnectManager` during mod startup
- Moved `FishingHookMixin` to the client mixin list

### Removed

- Removed legacy YACL `ModConfigScreen`
- Removed old `SkyBatuhanModMenu` ModMenu integration

## 0.3.2

### Fixes
- Fix: reelJump now works (jump key held for 2 ticks, starting right before reel-in)
- Fix: added "Jump On Reel" option to the fishing config screen
- Fix: stuck movement keys when a movement set is empty
- Fix: stuck detection triggering when no movement is configured
- Fix: AutoFarm safety check logic and isReversed not reset on home point
- Fix: cast delay slider allowing values below the enforced minimum
- Fix: stale AutoFish scheduled callbacks after toggling (AtomicInteger generation counter)
- Fix: duplicate messages in farm clear and undo commands
- Fix: config screen Reset button now restores real defaults
- Fix: null waypoints when loading points.json
- Fix: mod contact links, YACL is now a required dependency

### Changes
- Lowered minimum cast delay to 200 ms
- Feature enabled state is now kept in memory (removed featureStates from ModConfig)
- Removed ConfigManager dependency from Feature
- Removed unnecessary save call from AutoFishFeature
- Switched license to GPL-3.0

## 0.3.1

### Fixes
- Fix: added missing null-checks in ConfigManager (fishing, farming.general)
- Fix: replaced hardcoded "Auto Farm" string with Feature.isEnabled()
- Fix: removed orphaned lang keys (clear/undo, now handled by commands)
- Fix: replaced no-op assertions with real null-checks in AutoFarm/AutoFish
- Fix: unified Auto Fish state to single source (removed duplicate autoFishEnabled field)

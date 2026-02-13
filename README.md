# FishDetector

FishDetector is a server plugin designed to detect and prevent automated or AFK fishing.

## How the Detection Logic Works

- Starts tracking players when they reel in or catch a fish.
- Resets the AFK timer if the player moves their camera beyond a configured threshold.
- Sends an on-screen warning and sound if the player actively fishes without moving their camera for a set time.
- Optionally cancels fishing and/or optionally kicks the player, and adds a punishment strike if they still do not move after the warning.
- Ignores players who cast a hook and go AFK without ever reeling it in.
- Evaluates these conditions in batches every 100 ticks by default to for performance.

## Commands

All commands are accessed via `/fishdetector` (or `/fd`) and require the `fishdetector.admin` permission.

- `/fd toggle [state]`: Toggles the plugin on or off.
- `/fd reload`: Reloads the configuration file and updates the checking interval.
- `/fd list <punished|fishing> [page]`: Lists all punished players or shows currently active fishers. [1]
- `/fd check <player>`: Displays how many times a specific player has been punished.
- `/fd reset <player>`: Resets a player's punishment count to zero.

[1] You can click on any player name in this list to teleport to them.

## Configuration Features

The `config.yml` file allows you to customize the following values:

- `enabled`: Toggles the plugin's detection logic.
- `afk-time-seconds`: Maximum time a hook can sit in water without a catch or reel event before the player is ignored (prevents punishing legitimate non-botting AFKers).
- `bot-afk-time-seconds`: Total time a player (bot) can fish without moving their camera before being punished.
- `warning-time-seconds`: The time threshold that triggers the on-screen warning.
- `cleanup-timeout-seconds`: How long the plugin waits after a hook is gone before removing the player's tracking session from memory.
- `rotation-threshold`: The combined change in yaw and pitch required to count as a "move" and reset the AFK timer.
- `check-interval-ticks`: The delay between detection cycles (20 ticks = 1 second).
- `page-size`: The number of entries shown per page in list commands.
- `actions.cancel-fishing`: Whether to remove the player's fishing hook upon bot detection.
- `actions.kick-player`: Whether to kick the player from the server upon detection.
- `actions.warning-message`: The main text of the warning title (supports MiniMessage formatting).
- `actions.warning-subtitle`: The subtitle text of the warning title.
- `actions.broadcast-alert`: The message broadcast to the server when a player is caught (supports the `<player>` placeholder).
- `actions.kick-message`: The reason displayed to the player if they are kicked.

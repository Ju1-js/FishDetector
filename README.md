# FishDetector (v1.1)

FishDetector is a lightweight, highly optimized Paper server plugin designed to detect and prevent automated or AFK fishing without punishing legitimate players.

## How the Detection Logic Works

- Starts tracking players only when they reel in or catch a fish.
- Resets the AFK timer if the player moves their camera beyond a configured threshold.
- Sends an on-screen warning and sound if the player actively fishes without moving their camera for a set time.
- Optionally cancels fishing, kicks the player, and/or executes custom console commands if they still do not move after the warning.
- Adds a punishment strike to the player's offline record upon detection.
- Ignores players who cast a hook and go AFK without ever reeling it in.
- Evaluates conditions in batches every 100 ticks (configurable) for maximum server performance.

## Commands

All commands are accessed via `/fishdetector` (or `/fd`) and require the `fishdetector.admin` permission.

- `/fd toggle [state]`: Toggles the plugin on or off natively (preserves your config formatting!).
- `/fd reload`: Reloads the configuration file and updates the checking interval.
- `/fd list <punished|fishing> [page]`: Lists all punished players or shows currently active fishers. You can safely click on any player name in this list to teleport to them.
- `/fd check <player>`: Displays how many times a specific player has been punished.
- `/fd reset <player>`: Resets a player's punishment count to zero.
- `/fd tp <player>`: Silently teleports to a player (used natively by the chat-click interface to bypass the vanilla UI warning).

## Permissions

- `fishdetector.admin`: Grants access to all `/fd` commands. (Default: OP)
- `fishdetector.bypass`: Players with this permission are completely ignored by the AFK checker. (Default: False)

## Configuration Features

The `config.yml` file allows you to customize the following values:

- `enabled`: Toggles the plugin's detection logic.
- `disabled-worlds`: A list of world names where the AFK check will be completely ignored (e.g., a dedicated AFK world or Creative mode).
- `afk-time-seconds`: Maximum time a hook can sit in water without a catch or reel event before the player is ignored (prevents punishing legitimate non-botting AFKers).
- `bot-afk-time-seconds`: Total time a player (bot) can fish without moving their camera before being punished.
- `warning-time-seconds`: The time threshold that triggers the on-screen warning.
- `cleanup-timeout-seconds`: How long the plugin waits after a hook is gone before removing the player's tracking session from memory.
- `rotation-threshold`: The combined change in yaw and pitch required to count as a "move" and reset the AFK timer.
- `check-interval-ticks`: The delay between detection cycles (20 ticks = 1 second).
- `page-size`: The number of entries shown per page in list commands.
- `actions.cancel-fishing`: Whether to remove the player's fishing hook upon bot detection.
- `actions.kick-player`: Whether to kick the player from the server upon detection.
- `actions.execute-commands`: A list of commands the console will run when a player is punished (Supports the `<player>` placeholder. e.g., `- "eco take <player> 1000"`).
- `actions.warning-message`: The main text of the warning title (supports MiniMessage formatting).
- `actions.warning-subtitle`: The subtitle text of the warning title.
- `actions.broadcast-alert`: The message broadcast to the server when a player is caught (supports the `<player>` placeholder).
- `actions.kick-message`: The reason displayed to the player if they are kicked.
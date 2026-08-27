# Lifeline

A Paper plugin designed for 2-player co-op servers, adding shared waypoints, a shared stash, a revive mechanic, and direct player teleportation.

Works on Paper 1.21+ (Java 21).
Yeah, also works 26.2+

---

## Features

### Shared Waypoints (`/node`, `/wp`)

- GUI-based waypoint system with multi-page support (up to 90 waypoints).
- Dimension-based icons:
  - Compass for the Overworld
  - Nether Star for the Nether
  - Eye of Ender for The End
- **Left-click** to start a 3-second teleport warm-up (cancels on movement or damage).
- **Shift + Right-click** to delete a waypoint.
- Click **`+ Add Waypoint`** to create a waypoint at your current location. Type the name in chat (or `cancel` to abort).

### Shared Stash (`/stash`, `/safe`)

- A 54-slot chest inventory shared between players.
- Synced live in memory so both players can interact at the same time without desyncs or dupe glitches.
- Preserves all item metadata (enchants, lore, trims, shulker contents, damaged items).
- Auto-saves to `vault.yml` whenever closed and during server shutdown.

### Downed & Revive System

- When a player takes fatal damage (except from the void or `/kill`), they enter a downed state at half a heart instead of dying immediately.
- Downed players get debuffs (blindness, darkness, slowness) and a 30-second bleedout timer in the action bar with heartbeat audio.
- To revive, teammate holds **Sneak (Shift)** and **Right-clicks** the downed player for 3 seconds.
- Reviving clears debuffs, restores health, and grants brief resistance/regeneration.
- If the bleedout timer expires or the player takes lethal damage while downed, normal death occurs (`PlayerDeathEvent`), keeping full compatibility with gravestone and death-chest plugins.

### Player Teleportation (`/tpq`)

- Open the teleport GUI with `/tpq` or request directly via `/tpq <player>`.
- GUI shows teammates with their current dimension, distance in blocks, and health.
- Interactive chat buttons for accepting or declining requests, with shorthand commands (`/tpq a`, `/tpq d`, `/tpq c`) for mobile / Bedrock players (Geyser / Pojav).
- Configurable warm-up delay that aborts if you move or take damage.

### Bedrock Native Forms (Geyser API Hook)

- When Bedrock / Pocket Edition players join via [Geyser](https://geysermc.org/), Lifeline automatically renders native Bedrock Form UI windows instead of virtual chest inventories.
- Native forms for browsing waypoints, modal forms for waypoint actions (teleport/delete), custom text-input forms for naming waypoints, and native button lists for `/tpq`.
- Java Edition players continue to receive native chest GUIs seamlessly.

### Teammate Actionbar Radar (`/coradar`)

- Live actionbar HUD showing your nearby partner's live status, health, and a dynamic 8-directional arrow (`Partner: 140m ↗ | ❤ 8.5/10`).
- Dynamically updates relative to your player's facing direction (`↑`, `↗` ...).
- Only appears when your teammate is within nearby range (configurable, default 100m in the same dimension).
- Toggleable per player via `/coradar` (aliases: `/teamradar`, `/lfradar`) or `/lifeline radar [on|off|toggle]`.
- Master toggle in `config.yml` under `radar.enabled`.

### Localization & Config

- All messages, GUI titles, and action bar text are in `messages.yml` using [MiniMessage](https://docs.advntr.dev/minimessage/format.html) (supports hex colors and gradients).
- Configuration updater automatically merges new keys on plugin updates without overwriting existing settings.
- Reload on the fly with `/lifeline reload`.

---

## Commands & Permissions

| Command                             | Aliases / Shortcuts      | Description                       | Permission        |
| :---------------------------------- | :----------------------- | :-------------------------------- | :---------------- |
| `/node`                             | `/nd`, `/wp`             | Open waypoints GUI / Bedrock form | `lifeline.node`   |
| `/stash`                            | `/st`, `/safe`           | Open shared stash                 | `lifeline.stash`  |
| `/tpq`                              | `/teleportgui`           | Open player teleport GUI / form   | `lifeline.tpq`    |
| `/tpq <player>`                     | —                        | Send a teleport request           | `lifeline.tpq`    |
| `/tpq accept [player]`              | `/tpq a`                 | Accept teleport request           | `lifeline.tpq`    |
| `/tpq deny [player]`                | `/tpq d`, `/tpq decline` | Deny teleport request             | `lifeline.tpq`    |
| `/tpq cancel`                       | `/tpq c`                 | Cancel outgoing teleport request  | `lifeline.tpq`    |
| `/coradar`                          | `/teamradar`, `/lfradar` | Toggle teammate actionbar radar   | `lifeline.radar`  |
| `/lifeline radar [on\|off\|toggle]` | `/ll radar`              | Toggle or set teammate radar      | `lifeline.radar`  |
| `/lifeline revives [player]`        | `/ll revives`            | Check remaining revives           | `lifeline.revive` |
| `/lifeline reload`                  | `/ll reload`             | Reload configuration & messages   | `lifeline.admin`  |
| `/lifeline resetrevives <player>`   | `/ll resetrevives`       | Reset player revive counters      | `lifeline.admin`  |

---

## Configuration

Files are located in `plugins/Lifeline/`:

- `config.yml` — Timers, warm-ups, debuffs, sounds, and max waypoint pages.
- `messages.yml` — All user-facing text with MiniMessage formatting.
- `waypoints.yml` — Saved waypoint data.
- `vault.yml` — Stored items for the shared stash.

### Example `config.yml`

```yaml
waypoints:
  max-pages: 2 # 1 or 2 pages (up to 45 or 90 waypoints)
  teleport-warmup-seconds: 3

tether:
  teleport-warmup-seconds: 3
  request-timeout-seconds: 60
  cooldown-seconds: 30

downed-timer-seconds: 30
```

### Customizing `messages.yml`

Messages support MiniMessage tags and hex colors:

```yaml
prefix: "<color:#00FFA3><bold>Lifeline</bold></color> <dark_gray>»</dark_gray> "
waypoints.gui-title: "<gradient:#00FFA3:#00B8D9><bold>Shared Waypoints</bold></gradient>"
teleport.request-received-buttons: "<green><click:run_command:'/tpq accept <player>'>[✔ ACCEPT]</click></green>"
```

---

## Building

Requires **Java 21+ JDK**.

### Maven

```bash
mvn clean package
```

Output jar: `target/Lifeline-<version>.jar`

### Gradle

```bash
./gradlew clean build
```

Output jar: `build/libs/Lifeline-<version>.jar`

---

## Installation

1. Drop the compiled `.jar` file into your server's `plugins/` directory.
2. Start or restart the server.

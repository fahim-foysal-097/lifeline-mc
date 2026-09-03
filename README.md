<div align="center">

# 💎 Lifeline

**Co-op mechanics for small Paper Minecraft servers.**

_Shared & Personal Waypoints • Shared & Personal Stash • DBNO Revives • Actionbar Radar • Native Bedrock Forms_

[![Lifeline](https://img.shields.io/hangar/dt/Lifeline?link=https%3A%2F%2Fhangar.papermc.io%2FZero097%2FLifeline&style=for-the-badge)](https://hangar.papermc.io/Zero097/Lifeline)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1673953?logo=curseforge&color=F16436&style=for-the-badge)](https://www.curseforge.com/minecraft/bukkit-plugins/life-line)

[![PaperMC](https://img.shields.io/badge/PaperMC-1.21%2B-1976D2?style=for-the-badge&logo=papermc&logoColor=white)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21%2B-007396?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Geyser](https://img.shields.io/badge/Geyser-Supported-38B5E6?style=for-the-badge&logo=geysermc&logoColor=white)](https://geysermc.org)
[![License](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](#license)

</div>

---

Lifeline adds essential team mechanics to small co-op servers: shared and personal waypoints, shared and personal storage, a downed/revive system, an actionbar radar, and native UI for Bedrock players joining through Geyser.

Requires **Paper 1.21+** running **Java 21+**.

Works on **26.2+** too.

---

## Features

### Shared Waypoints (`/node`, `/wp`, `/waypoints`)

- Multi-page chest GUI supporting up to 90 shared waypoints.
- Dimension-specific icons (Compass for Overworld, Nether Star for Nether, Eye of Ender for The End).
- **Left-click** to start a 3-second teleport warm-up (aborts on movement or damage).
- **Shift + Right-click** to delete a waypoint.
- Click **`+ Add Waypoint`**, then type the name in chat (or type `cancel` to abort).

### Personal Waypoints (`/mywp`, `/pwaypoints`, `/lfnode`)

- Private, per-player waypoints with an enforced maximum limit of 27 waypoints per player.
- Dedicated chest GUI and native Bedrock Form integration.
- Dimension-specific icons and configurable teleport warm-up.
- **Left-click** to teleport, **Shift + Right-click** to delete, and click **`+ Add Personal Waypoint`** to create.
- Saved per-player to `personal-waypoints.yml`.

### Shared Stash (`/stash`, `/safe`)

- A 54-slot chest inventory shared across players.
- Live in-memory sync allows simultaneous access without dupe glitches or desyncs.
- Retains all item metadata (enchants, lore, armor trims, container contents, damage values).
- Saves to `vault.yml` on window close and server shutdown.

### Personal Stash (`/pstash`, `/stashp`, `/pst`, `/mystash`)

- Private per-player storage inventory saved to `personal-stashes.yml`.
- Configurable capacity: **27** (single chest) or **54** (double chest) slots (default: `27`).
- Retains full item metadata and saves automatically upon closing the inventory or logging off.

### Downed & Revive System

- Taking fatal damage (excluding void or `/kill`) puts players into a Downed state at half a heart instead of instantly killing them.
- Downed players receive Blindness, Darkness, and Slowness, alongside a 30-second actionbar bleedout timer with heartbeat audio.
- Teammates hold **Sneak (Shift)** and **Right-click** the downed player for 3 seconds to revive.
- Reviving clears debuffs, restores health, and gives brief Resistance and Regeneration.
- If the timer runs out or the player takes lethal damage while downed, regular death occurs (`PlayerDeathEvent`). Compatible with grave and death-chest plugins.
- Configurable max revives (`max-revives: 0` for infinite) and master toggle (`revive-enabled`).

### Player Teleportation (`/tpq`)

- Open the teleport menu with `/tpq` or send a direct request via `/tpq <player>`.
- Displays teammate status, current dimension, distance in blocks, and remaining health.
- Interactive chat confirmation buttons, plus shorthand commands (`/tpq a`, `/tpq d`, `/tpq c`) for mobile and Bedrock players.
- Configurable warm-up delay that cancels on movement or damage.

### Native Bedrock Forms (Geyser API)

- When Bedrock players join through Geyser, Lifeline automatically serves native Bedrock Form UI windows instead of virtual chest inventories.
- Includes form-based waypoint browsing, action modals (teleport/delete), text inputs for naming waypoints, and clean button lists for `/tpq`.
- Java players continue to receive normal chest GUIs.

### Teammate Actionbar Radar (`/coradar`)

- Actionbar HUD displaying your partner's current status, health, and a dynamic 8-directional arrow (`Partner: 24m ↗ | ❤ 8.5/10`).
- Updates direction relative to where your character is facing (`↑`, `↗`, `➔`, etc.).
- Displays only when your teammate is within range (default: 40 blocks in the same dimension).
- Toggle per player using `/coradar` or `/lifeline radar [on|off|toggle]`.
- Toggle states persist across relogs and restarts in `radar-toggles.yml`.

### Config & Localization

- All user messages, GUI titles, and actionbar alerts are managed in `messages.yml` using [MiniMessage](https://docs.advntr.dev/minimessage/format.html) (supports hex colors and gradients).
- Automatic configuration updating merges missing keys on updates without resetting your changes.
- Hot-reload settings anytime with `/lifeline reload`.

---

## Commands & Permissions

| Command                             | Aliases                                           | Description                        | Permission        |
| :---------------------------------- | :------------------------------------------------ | :--------------------------------- | :---------------- |
| `/node`                             | `/nd`, `/wp`, `/nodes`, `/waypoint`, `/waypoints` | Open shared waypoints GUI / form   | `lifeline.node`   |
| `/mywp`                             | `/pwaypoints`, `/lfnode`                          | Open personal waypoints GUI / form | `lifeline.mywp`   |
| `/stash`                            | `/st`, `/safe`                                    | Open shared stash                  | `lifeline.stash`  |
| `/pstash`                           | `/stashp`, `/pst`, `/mystash`                     | Open personal stash                | `lifeline.pstash` |
| `/tpq`                              | `/teleportgui`                                    | Open player teleport GUI / form    | `lifeline.tpq`    |
| `/tpq <player>`                     | —                                                 | Send a teleport request            | `lifeline.tpq`    |
| `/tpq accept [player]`              | `/tpq a`                                          | Accept teleport request            | `lifeline.tpq`    |
| `/tpq deny [player]`                | `/tpq d`, `/tpq decline`                          | Deny teleport request              | `lifeline.tpq`    |
| `/tpq cancel`                       | `/tpq c`                                          | Cancel outgoing teleport request   | `lifeline.tpq`    |
| `/coradar`                          | `/teamradar`, `/lfradar`                          | Toggle actionbar radar             | `lifeline.radar`  |
| `/lifeline radar [on\|off\|toggle]` | `/ll radar`                                       | Change radar toggle state          | `lifeline.radar`  |
| `/lifeline revives [player]`        | `/ll revives`                                     | Check remaining revives            | `lifeline.revive` |
| `/lifeline backup [create\|list]`   | `/ll backup`                                      | Create or list data backups        | `lifeline.admin`  |
| `/lifeline reload`                  | `/ll reload`                                      | Reload configuration & messages    | `lifeline.admin`  |
| `/lifeline resetrevives <player>`   | `/ll resetrevives`                                | Reset player revive counters       | `lifeline.admin`  |

---

## Configuration

Files are stored in `plugins/Lifeline/`:

- `config.yml` — Timers, warm-ups, debuffs, sounds, and toggles.
- `messages.yml` — All user-facing text with MiniMessage formatting.
- `waypoints.yml` — Saved shared waypoint data.
- `personal-waypoints.yml` — Saved per-player personal waypoint data.
- `vault.yml` — Stored items for the shared stash.
- `personal-stashes.yml` — Stored items for personal stashes.
- `radar-toggles.yml` — Saved per-player radar toggle preferences.
- `backup/` — Automatic rolling backups (`.bak` files and timestamped zip snapshots in `snapshots/`).

### Example `config.yml`

```yaml
max-revives: 0
revive-enabled: true
downed-timer-seconds: 30
downed-invulnerable: true
revive-channel-seconds: 3
revive-max-distance: 4.0
revive-health-restored: 6.0

waypoints:
  max-pages: 2 # 1 or 2 pages (up to 45 or 90 waypoints)
  teleport-warmup-seconds: 3

personal-waypoints:
  enabled: true

personal-stash:
  enabled: true
  slots: 27 # 27 or 54 slots

tether:
  teleport-warmup-seconds: 3
  request-timeout-seconds: 60
  cooldown-seconds: 30

bedrock-forms:
  enabled: true

radar:
  enabled: true
  enabled-by-default: true
  max-distance: 40
  update-interval-ticks: 10
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

1. Download or compile `.jar` file from release or the website.
2. Drop the `.jar` file into your server's `plugins/` directory.
3. Start or restart the server.

## Extras

The `/docs` folder is the folder for gh-pages.

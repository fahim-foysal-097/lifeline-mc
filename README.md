# Lifeline

**Lifeline** is a modern, high-performance, production-ready 2-player co-op Paper plugin written in **Java 21** for **Paper 1.21+ / Paper 26.2+**.

It seamlessly integrates three core co-op features into one lightweight, zero-bloat plugin:

1. **Shared Waypoints** (with visual 54-slot GUI, dimension-aware icons, 3s warm-up, and chat creation prompts)
2. **Shared 54-Slot Co-op Vault** (with synchronized in-memory singleton preventing desync/dupes, and full metadata persistence)
3. **Downed & Revive Mechanics** (with 30s bleedout timer, heartbeat audio, sneak-revive progress bar, and 100% safe death integration with Gravestone/DeathChest plugins)

---

## ✨ Features

### 📍 1. Shared Waypoint / Node System

- Open with `/node` (aliases: `/nd`, `/wp`).
- Displays up to **90 shared waypoints across multiple pages** (configurable via `waypoints.max-pages`, hard max: 2 pages / 45 waypoints per page).
- **Interactive Multi-Page Navigation**:
  - Click **`« Previous Page`** or **`Next Page »`** to browse pages with smooth page-turn sound cues.
- **Dimension-Adaptive Icons**:
  - 🧭 **Compass**: Overworld
  - ⭐ **Nether Star**: The Nether
  - 👁️ **Eye of Ender**: The End
- **Safe Teleportation**:
  - **Left-Click** any waypoint to start a **3-second warm-up** with portal particle cues and audio ticks. Moving or taking damage aborts the teleport.
  - **Shift + Right-Click** any waypoint to instantly delete it with confirmation.
- **Add Waypoints In-Game**:
  - Click the **`+ Add Waypoint`** button at the bottom of the GUI.
  - The plugin gives you a 15-second chat prompt to type your custom name (type `cancel` to abort).
  - Automatically captures your exact coordinates, world, pitch, and yaw.

---

### 📦 2. Shared 54-Slot Stash / Safe

- Open with `/stash` (aliases: `/st`, `/safe`).
- Both players open the **exact same in-memory `Inventory` instance**, meaning items placed or moved by one player update in real-time for the other without desync or duplicate item glitches.
- Full support for all item metadata: enchantments, custom lore, armor trims, potion effects, damaged tools, and shulker box contents.
- **Auto-Saves** automatically to `vault.yml` whenever any player closes the chest and upon server shutdown (`onDisable`).

---

### 🩺 3. Downed & Revive Mechanics

- **Downed State**:
  - When taking lethal damage (except the Void or `/kill`), the player doesn't instantly die. Instead, they fall into a downed state at **0.5 heart (1.0 HP)**.
  - Receives `DARKNESS`, `BLINDNESS`, `SLOWNESS V`, and `GLOWING`.
  - A **30-second bleed-out timer** appears in their Action Bar accompanied by realistic rhythmic heartbeat sound effects.
- **Partner Revive**:
  - Partner player holds **Sneak (Shift)** and **Right-Clicks** the downed teammate.
  - A **3-second interactive progress bar** (`[██████████]`) appears on both players' Action Bars with healing heart particles.
  - Releasing sneak or walking too far interrupts the revive channel.
  - Completing the revive restores **3 full hearts (6.0 HP)**, clears all debuffs, grants brief Regeneration & Resistance buffs, and plays celebratory Totem sounds/particles.
- **🛡️ 100% Safe Death Integration**:
  - If the 30-second bleedout timer runs out, the player disconnects, or the downed player takes another lethal hit, the plugin unmarks their downed state and executes `player.setHealth(0)`.
  - This allows the natural **Vanilla `PlayerDeathEvent`** to fire cleanly, ensuring full compatibility with **Gravestone, DeathChest, and inventory restore plugins** with zero duplicate graves or lost inventories!

---

### 🚀 4. Player Teleportation System & GUI

- **Open GUI**: `/tpq` or `/teleportgui`
- **Direct Request**: `/tpq <player>`
- **Sleek 27-Slot Player Selection GUI**:
  - Shows custom player heads of all online teammates with real-time **Dimension**, **Distance (in blocks)**, and **Health** stats.
  - Downed players are shown with a red label and cannot be selected.
  - Spectator mode players are shown with a gray label and cannot be selected.
  - Click or tap any valid player head to instantly dispatch a teleport request.
- **Interactive Chat Buttons & Mobile Shortcuts**:
  - The recipient receives interactive MiniMessage chat buttons:
    - `[✔ ACCEPT]` - click to run `/tpq accept <player>`
    - `[✖ DECLINE]` - click to run `/tpq deny <player>`
  - **Mobile / Touchscreen Friendly (Bedrock / Geyser / PojavLauncher)**:
    - `/tpq a` - Quick-accept teleport request
    - `/tpq d` - Quick-deny teleport request
    - `/tpq c` - Quick-cancel outgoing request
  - Requests expire automatically after the configured timeout (default: 60s).
- **Safe Warm-Up**:
  - Upon acceptance, the requester undergoes a configurable **warm-up** (default: 3s) with portal particle cues, audio ticks, and Action Bar countdown.
  - **Cancels automatically** if the requester moves, takes damage, dies, gets downed, or if the target disconnects or dies during warm-up.
  - Starting a waypoint (`/node`) warm-up cancels any active teleport warm-up, and vice versa - they are mutually exclusive.
  - The requester is automatically ejected from any vehicle before teleportation.
- **Guard Rails & Compatibility**:
  - Cannot send requests to yourself, downed players, or players in Spectator mode.
  - Cooldown between requests is configurable (default: 30s).
  - 100% compatible with both **online-mode** and **offline-mode (LAN)** Paper servers.

---

### 🌐 5. 100% Translatable MiniMessage Localization

- All chat messages, action bars, GUI titles, items, and lore are fully translatable in **`messages.yml`**.
- Full support for **Adventure MiniMessage formatting**, hex color codes (`#00FFA3`), and color gradients (`<gradient:#00FFA3:#00B8D9>...</gradient>`).
- Hot-reloadable in real-time via `/lifeline reload`.

---

## 📜 Commands & Permissions

| Command                           | Aliases / Shortcuts      | Description                                 | Permission                               |
| :-------------------------------- | :----------------------- | :------------------------------------------ | :--------------------------------------- |
| `/node`                           | `/nd`, `/wp`             | Opens the shared waypoints (nodes) GUI      | `lifeline.node` (or `lifeline.waypoint`) |
| `/stash`                          | `/st`, `/safe`           | Opens the shared 54-slot co-op stash / safe | `lifeline.stash` (or `lifeline.safe`)    |
| `/tpq`                            | `/teleportgui`           | Opens player teleport GUI                   | `lifeline.tpq` (or `lifeline.tether`)    |
| `/tpq <player>`                   | -                        | Sends a teleport request to a player        | `lifeline.tpq`                           |
| `/tpq accept [player]`            | `/tpq a`                 | Accepts an incoming teleport request        | `lifeline.tpq`                           |
| `/tpq deny [player]`              | `/tpq d`, `/tpq decline` | Declines an incoming teleport request       | `lifeline.tpq`                           |
| `/tpq cancel`                     | `/tpq c`                 | Cancels your outgoing teleport request      | `lifeline.tpq`                           |
| `/lifeline revives [player]`      | `/ll revives`            | Checks remaining co-op revives              | `lifeline.use`                           |
| `/lifeline reload`                | `/ll reload`             | Reloads configuration and `messages.yml`    | `lifeline.admin`                         |
| `/lifeline resetrevives <player>` | `/ll resetrevives`       | Resets player revive counters               | `lifeline.admin`                         |

---

## ⚙️ Configuration & Data Storage

All data is stored in the plugin's data folder (`plugins/Lifeline/`):

| File            | Purpose                                                                                   |
| :-------------- | :---------------------------------------------------------------------------------------- |
| `config.yml`    | Core settings: revive timers, debuffs, warm-ups, sounds, particles, `waypoints.max-pages` |
| `messages.yml`  | All user-facing strings: MiniMessage format, hex colors, gradient support                 |
| `waypoints.yml` | Shared waypoint data (coordinates, dimensions, creator)                                   |
| `vault.yml`     | Serialized inventory contents of the shared stash                                         |

### Key `config.yml` Settings

```yaml
waypoints:
  max-pages: 2 # 1 or 2 pages (hard max). Sets max waypoints to 45 or 90.
  teleport-warmup-seconds: 3

tether:
  teleport-warmup-seconds: 3
  request-timeout-seconds: 60
  cooldown-seconds: 30

downed-timer-seconds: 30 # 0 disables the revive system entirely
```

### Customizing `messages.yml`

All messages support [Adventure MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags:

```yaml
# Hex colors
prefix: "<color:#00FFA3><bold>Lifeline</bold></color> <dark_gray>»</dark_gray> "

# Gradients
waypoints.gui-title: "<gradient:#00FFA3:#00B8D9><bold>Shared Waypoints</bold></gradient>"

# Click events (used for accept/deny buttons)
teleport.request-received-buttons: "<green><click:run_command:'/tpq accept <player>'>[✔ ACCEPT]</click></green>"
```

Run `/lifeline reload` to apply changes without restarting.

---

## 🔨 How to Build / Package the Plugin (.jar)

### Prerequisites:

- **Java 21 JDK** installed (`java -version` should show Java 21).
- **Maven** (Recommended) or **Gradle**.

---

### Method A: Build with Maven (Recommended)

1. **Clean & Build:**

   ```bash
   mvn clean package
   ```

2. **Locate your compiled `.jar`:**
   ```
   target/Lifeline-0.0.1.jar
   ```

---

### Method B: Build with Gradle

1. **Clean & Build:**

   ```bash
   ./gradlew clean build
   ```

   _(On Windows Command Prompt: `gradlew.bat clean build`)_

2. **Locate your compiled `.jar`:**
   ```
   build/libs/Lifeline-0.0.1.jar
   ```

---

## 🚀 Installation

1. Copy `Lifeline-0.0.1.jar` (from `target/` or `build/libs/`) into your Paper server's `plugins/` directory.
2. Start or reload your Paper 1.21+ / Paper 26.2+ server.

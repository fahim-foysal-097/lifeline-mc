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
- Displays up to 45 shared waypoints in a sleek 54-slot chest interface.
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

## 📜 Commands & Permissions

| Command                           | Aliases        | Description                                 | Permission                               |
| :-------------------------------- | :------------- | :------------------------------------------ | :--------------------------------------- |
| `/node`                           | `/nd`, `/wp`   | Opens the shared waypoints (nodes) GUI      | `lifeline.node` (or `lifeline.waypoint`) |
| `/stash`                          | `/st`, `/safe` | Opens the shared 54-slot co-op stash / safe | `lifeline.stash` (or `lifeline.safe`)    |
| `/lifeline revives [player]`      | `/ll`          | Checks remaining co-op revives              | `lifeline.revive`                        |
| `/lifeline reload`                | `/ll`          | Reloads plugin configuration                | `lifeline.admin`                         |
| `/lifeline resetrevives <player>` | `/ll`          | Resets player revive counters               | `lifeline.admin`                         |

---

## ⚙️ Data Storage

All data is stored cleanly inside the plugin's data folder (`plugins/Lifeline/`):

- `waypoints.yml`: Stores all shared waypoints with coordinates, dimensions, and creator details.
- `vault.yml`: Stores the full Bukkit serialized `ItemStack` array for the shared vault.

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

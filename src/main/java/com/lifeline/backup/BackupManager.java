package com.lifeline.backup;

import com.lifeline.Lifeline;
import com.lifeline.util.MessageUtil;
import com.lifeline.util.SafeFileUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages automatic and manual rolling backups for Lifeline storage files.
 * Backups are synchronized with Paper's server autosave (WorldSaveEvent)
 * to guarantee that player inventory state and stash contents remain strictly consistent,
 * completely preventing item duplication.
 */
public class BackupManager implements Listener {

    private final Lifeline plugin;
    private final File backupDir;
    private final File snapshotsDir;

    private final File vaultFile;
    private final File personalStashesFile;
    private final File personalWaypointsFile;
    private final File waypointsFile;

    private long lastAutosaveTick = -1;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    public BackupManager(Lifeline plugin) {
        this.plugin = plugin;
        this.backupDir = new File(plugin.getDataFolder(), "backup");
        this.snapshotsDir = new File(backupDir, "snapshots");

        this.vaultFile = new File(plugin.getDataFolder(), "vault.yml");
        this.personalStashesFile = new File(plugin.getDataFolder(), "personal-stashes.yml");
        this.personalWaypointsFile = new File(plugin.getDataFolder(), "personal-waypoints.yml");
        this.waypointsFile = new File(plugin.getDataFolder(), "waypoints.yml");

        ensureDirectories();
    }

    public File getBackupDir() {
        return backupDir;
    }

    public File getSnapshotsDir() {
        return snapshotsDir;
    }

    public File getVaultBakFile() {
        return new File(backupDir, "vault.yml.bak");
    }

    public File getPersonalStashesBakFile() {
        return new File(backupDir, "personal-stashes.yml.bak");
    }

    public File getPersonalWaypointsBakFile() {
        return new File(backupDir, "personal-waypoints.yml.bak");
    }

    public File getWaypointsBakFile() {
        return new File(backupDir, "waypoints.yml.bak");
    }

    private void ensureDirectories() {
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        if (!snapshotsDir.exists()) {
            snapshotsDir.mkdirs();
        }
    }

    /**
     * Synchronizes backups directly with Paper's world autosave cycle.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        if (!plugin.getPluginConfig().isBackupEnabled() || !plugin.getPluginConfig().isBackupSyncWithAutosave()) {
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        if (currentTick == lastAutosaveTick) {
            return;
        }
        lastAutosaveTick = currentTick;

        // Check if any in-memory data has uncommitted modifications
        boolean dirty = (plugin.getSharedVaultManager() != null && plugin.getSharedVaultManager().isDirty())
                || (plugin.getPersonalVaultManager() != null && plugin.getPersonalVaultManager().isDirty());

        if (dirty) {
            performBackup(false);
        }
    }

    /**
     * Updates the fixed-named .bak files directly inside the backup/ folder:
     * - vault.yml.bak
     * - personal-stashes.yml.bak
     * - personal-waypoints.yml.bak & persnal-waypoint.yml.bak
     * - waypoints.yml.bak
     */
    public synchronized void updateBakFiles() {
        ensureDirectories();

        SafeFileUtil.copyBackupAtomically(vaultFile, new File(backupDir, "vault.yml.bak"));
        SafeFileUtil.copyBackupAtomically(personalStashesFile, new File(backupDir, "personal-stashes.yml.bak"));

        File pwBak = new File(backupDir, "personal-waypoints.yml.bak");
        SafeFileUtil.copyBackupAtomically(personalWaypointsFile, pwBak);
        // Also maintain alias persnal-waypoint.yml.bak for backward/user-script compatibility
        SafeFileUtil.copyBackupAtomically(personalWaypointsFile, new File(backupDir, "persnal-waypoint.yml.bak"));

        SafeFileUtil.copyBackupAtomically(waypointsFile, new File(backupDir, "waypoints.yml.bak"));
    }

    /**
     * Creates a rolling zip snapshot of all current data files in backup/snapshots/
     * and prunes older snapshots beyond max-backups.
     *
     * @return the created snapshot File, or null if creation failed or no files exist
     */
    public synchronized File createSnapshotArchive() {
        ensureDirectories();
        updateBakFiles();

        List<File> filesToArchive = new ArrayList<>();
        if (vaultFile.exists() && vaultFile.length() > 0) filesToArchive.add(vaultFile);
        if (personalStashesFile.exists() && personalStashesFile.length() > 0) filesToArchive.add(personalStashesFile);
        if (personalWaypointsFile.exists() && personalWaypointsFile.length() > 0) filesToArchive.add(personalWaypointsFile);
        if (waypointsFile.exists() && waypointsFile.length() > 0) filesToArchive.add(waypointsFile);

        if (filesToArchive.isEmpty()) {
            return null;
        }

        String timestamp = DATE_FORMAT.format(new Date());
        File zipFile = new File(snapshotsDir, "backup-" + timestamp + ".zip");
        File tempZip = new File(snapshotsDir, zipFile.getName() + ".tmp." + System.nanoTime());

        try {
            try (FileOutputStream fos = new FileOutputStream(tempZip);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                byte[] buffer = new byte[4096];
                for (File file : filesToArchive) {
                    ZipEntry entry = new ZipEntry(file.getName());
                    zos.putNextEntry(entry);

                    try (FileInputStream fis = new FileInputStream(file)) {
                        int len;
                        while ((len = fis.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }
                    }
                    zos.closeEntry();
                }
                zos.finish();
                fos.getFD().sync();
            }

            try {
                Files.move(tempZip.toPath(), zipFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempZip.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            pruneSnapshots(plugin.getPluginConfig().getBackupMaxBackups());
            return zipFile;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create backup archive snapshot: " + zipFile.getName(), e);
            return null;
        } finally {
            if (tempZip.exists()) {
                try {
                    Files.deleteIfExists(tempZip.toPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Retains at most {@code maxBackups} snapshots, deleting the oldest ones first.
     */
    public synchronized void pruneSnapshots(int maxBackups) {
        if (!snapshotsDir.exists()) {
            return;
        }

        File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null || files.length <= maxBackups) {
            return;
        }

        // Sort ascending by last modified time (oldest first)
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int toDelete = files.length - maxBackups;
        for (int i = 0; i < toDelete; i++) {
            try {
                Files.deleteIfExists(files[i].toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to prune old backup snapshot: " + files[i].getName());
            }
        }
    }

    /**
     * Executes a complete save and backup cycle.
     *
     * @param force if true, forces stash disk writes even if not dirty
     * @return the created snapshot File, or null
     */
    public synchronized File performBackup(boolean force) {
        // Save stashes and sync players first
        plugin.saveAllStashesAndPlayers(force);

        // Update the primary .bak files in backup/
        updateBakFiles();

        // Create snapshot zip and prune
        File snapshot = createSnapshotArchive();
        if (snapshot != null) {
            plugin.getLogger().info("Automatic backup created: " + snapshot.getName());
        }
        return snapshot;
    }

    /**
     * Handles manual execution of the /lifeline backup create command.
     */
    public void handleManualBackup(CommandSender sender) {
        File snapshot = performBackup(true);
        if (snapshot != null) {
            MessageUtil.sendPrefixed(sender, "backup.created", MessageUtil.p("snapshot", snapshot.getName()));
        } else {
            MessageUtil.sendPrefixed(sender, "backup.failed");
        }
    }

    /**
     * Handles manual execution of the /lifeline backup list command.
     */
    public void handleListBackups(CommandSender sender) {
        File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null || files.length == 0) {
            MessageUtil.sendPrefixed(sender, "backup.no-backups");
            return;
        }

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified())); // newest first
        int max = plugin.getPluginConfig().getBackupMaxBackups();

        MessageUtil.sendPrefixed(sender, "backup.list-header",
                MessageUtil.p("count", String.valueOf(files.length)),
                MessageUtil.p("max", String.valueOf(max)));

        SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (File f : files) {
            String sizeStr = formatFileSize(f.length());
            String dateStr = displayFormat.format(new Date(f.lastModified()));
            MessageUtil.sendRaw(sender, "backup.list-item",
                    MessageUtil.p("name", f.getName()),
                    MessageUtil.p("size", sizeStr),
                    MessageUtil.p("date", dateStr));
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.ROOT, "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    public void cleanup() {
        // Ensure final backup on shutdown if dirty
        boolean dirty = (plugin.getSharedVaultManager() != null && plugin.getSharedVaultManager().isDirty())
                || (plugin.getPersonalVaultManager() != null && plugin.getPersonalVaultManager().isDirty());
        if (dirty && plugin.getPluginConfig().isBackupEnabled()) {
            performBackup(true);
        } else {
            updateBakFiles();
        }
    }
}

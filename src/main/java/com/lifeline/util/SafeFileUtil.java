package com.lifeline.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides robust atomic file persistence, physical media flushing (fsync),
 * durable backups, and corrupt file auto-recovery for all Lifeline YAML storage.
 */
public final class SafeFileUtil {

    private SafeFileUtil() {
    }

    /**
     * Atomically saves a {@link YamlConfiguration} to disk by writing to a temporary file,
     * syncing bytes to physical media, and atomically replacing the target file.
     *
     * @param config     the configuration to save
     * @param targetFile the target file to persist to
     * @throws IOException if writing or moving the file fails
     */
    public static void saveConfigurationAtomically(YamlConfiguration config, File targetFile) throws IOException {
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(parentDir, targetFile.getName() + ".tmp." + System.nanoTime());
        try {
            config.save(tempFile);

            // Guarantee that file content and metadata are flushed to physical media before rename
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(tempFile.toPath(), StandardOpenOption.WRITE)) {
                channel.force(true);
            } catch (Exception ignored) {
                // Some virtual or network filesystems may not support sync/force
            }

            try {
                Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            if (tempFile.exists()) {
                // Best effort delete if move did not consume the temp file
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Creates an atomic backup copy of the specified source file at the backup destination.
     *
     * @param sourceFile the file to backup
     * @param backupFile the destination backup file
     * @return true if backup was successfully created or updated, false if source file does not exist or is empty
     */
    public static boolean copyBackupAtomically(File sourceFile, File backupFile) {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            return false;
        }

        File parentDir = backupFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(parentDir, backupFile.getName() + ".tmp." + System.nanoTime());
        try {
            Files.copy(sourceFile.toPath(), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(tempFile.toPath(), StandardOpenOption.WRITE)) {
                channel.force(true);
            } catch (Exception ignored) {
            }

            try {
                Files.move(
                        tempFile.toPath(),
                        backupFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(
                        tempFile.toPath(),
                        backupFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (tempFile.exists()) {
                try {
                    Files.deleteIfExists(tempFile.toPath());
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Loads a YamlConfiguration from the target data file with automatic corruption detection
     * and fallback recovery from backup.
     *
     * @param dataFile   the primary data file to load
     * @param backupFile the backup file to fall back to if dataFile is missing, empty, or corrupted
     * @param logger     logger to output recovery alerts to (can be null)
     * @return the successfully loaded YamlConfiguration, or an empty one if neither exists
     */
    public static YamlConfiguration loadWithAutoRecovery(File dataFile, File backupFile, Logger logger) {
        if (dataFile == null) {
            return new YamlConfiguration();
        }

        // Case 1: Primary file exists and has content
        if (dataFile.exists() && dataFile.length() > 0) {
            try {
                YamlConfiguration config = new YamlConfiguration();
                config.load(dataFile);
                return config;
            } catch (IOException | InvalidConfigurationException ex) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "Data file '" + dataFile.getName() + "' is corrupted or unreadable! Attempting recovery from backup...", ex);
                }
            }
        } else if (dataFile.exists() && dataFile.length() == 0) {
            if (logger != null) {
                logger.severe("Data file '" + dataFile.getName() + "' is 0 bytes (truncated)! Attempting recovery from backup...");
            }
        }

        // Case 2: Attempt auto-recovery from backup
        if (backupFile != null && backupFile.exists() && backupFile.length() > 0) {
            try {
                YamlConfiguration backupConfig = new YamlConfiguration();
                backupConfig.load(backupFile);

                // Restore backup to data file
                copyBackupAtomically(backupFile, dataFile);

                if (logger != null) {
                    logger.warning("Auto-recovery successful! Restored '" + dataFile.getName() + "' from backup '" + backupFile.getName() + "'.");
                }
                return backupConfig;
            } catch (Exception backupEx) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "Failed to recover '" + dataFile.getName() + "' from backup file '" + backupFile.getName() + "'.", backupEx);
                }
            }
        }

        // Fallback: return fresh empty configuration
        return new YamlConfiguration();
    }
}

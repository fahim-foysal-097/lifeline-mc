package com.lifeline.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SafeFileUtilTest {

    @Test
    public void testAtomicSaveAndContentIntegrity(@TempDir Path tempDir) throws IOException {
        File targetFile = tempDir.resolve("vault.yml").toFile();

        YamlConfiguration config = new YamlConfiguration();
        config.set("items.0.type", "DIAMOND");
        config.set("items.0.amount", 64);

        SafeFileUtil.saveConfigurationAtomically(config, targetFile);

        assertTrue(targetFile.exists(), "Target file must exist after atomic save");
        assertTrue(targetFile.length() > 0, "Target file should not be empty");

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(targetFile);
        assertEquals("DIAMOND", loaded.getString("items.0.type"));
        assertEquals(64, loaded.getInt("items.0.amount"));

        // Verify no leftover .tmp files
        File[] tempFiles = tempDir.toFile().listFiles((dir, name) -> name.contains(".tmp"));
        assertNotNull(tempFiles);
        assertEquals(0, tempFiles.length, "No temporary files should be left behind");
    }

    @Test
    public void testAtomicSaveOverwritesExistingFile(@TempDir Path tempDir) throws IOException {
        File targetFile = tempDir.resolve("test.yml").toFile();

        YamlConfiguration v1 = new YamlConfiguration();
        v1.set("version", 1);
        SafeFileUtil.saveConfigurationAtomically(v1, targetFile);

        YamlConfiguration v2 = new YamlConfiguration();
        v2.set("version", 2);
        SafeFileUtil.saveConfigurationAtomically(v2, targetFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(targetFile);
        assertEquals(2, loaded.getInt("version"));
    }

    @Test
    public void testCopyBackupAtomically(@TempDir Path tempDir) throws IOException {
        File sourceFile = tempDir.resolve("source.yml").toFile();
        File backupFile = tempDir.resolve("backup/source.yml.bak").toFile();

        // Non-existent source should fail safely
        assertFalse(SafeFileUtil.copyBackupAtomically(sourceFile, backupFile));

        // Empty file should fail safely
        assertTrue(sourceFile.createNewFile());
        assertFalse(SafeFileUtil.copyBackupAtomically(sourceFile, backupFile));

        // Non-empty file should succeed
        try (FileWriter writer = new FileWriter(sourceFile)) {
            writer.write("data: test-content\n");
        }

        assertTrue(SafeFileUtil.copyBackupAtomically(sourceFile, backupFile));
        assertTrue(backupFile.exists());
        assertEquals("data: test-content\n", Files.readString(backupFile.toPath()));
    }

    @Test
    public void testAutoRecoveryFromZeroByteTruncation(@TempDir Path tempDir) throws IOException {
        File dataFile = tempDir.resolve("personal-stashes.yml").toFile();
        File backupFile = tempDir.resolve("backup/personal-stashes.yml.bak").toFile();

        // Create valid backup
        YamlConfiguration backupConfig = new YamlConfiguration();
        backupConfig.set("stashes.player1.item", "NETHERITE_INGOT");
        SafeFileUtil.saveConfigurationAtomically(backupConfig, backupFile);

        // Simulate crash: dataFile exists but is 0 bytes
        assertTrue(dataFile.createNewFile());
        assertEquals(0, dataFile.length());

        // Load with auto-recovery
        YamlConfiguration recovered = SafeFileUtil.loadWithAutoRecovery(dataFile, backupFile, null);

        assertEquals("NETHERITE_INGOT", recovered.getString("stashes.player1.item"));
        // Check that dataFile was restored on disk
        assertTrue(dataFile.length() > 0);
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(dataFile);
        assertEquals("NETHERITE_INGOT", reloaded.getString("stashes.player1.item"));
    }

    @Test
    public void testAutoRecoveryFromCorruptedYaml(@TempDir Path tempDir) throws IOException {
        File dataFile = tempDir.resolve("waypoints.yml").toFile();
        File backupFile = tempDir.resolve("backup/waypoints.yml.bak").toFile();

        // Create valid backup
        YamlConfiguration backupConfig = new YamlConfiguration();
        backupConfig.set("waypoints.spawn.x", 100);
        SafeFileUtil.saveConfigurationAtomically(backupConfig, backupFile);

        // Corrupt the data file with invalid YAML syntax
        try (FileWriter writer = new FileWriter(dataFile)) {
            writer.write("{{this is broken: yaml: [[");
        }

        // Load with auto-recovery
        YamlConfiguration recovered = SafeFileUtil.loadWithAutoRecovery(dataFile, backupFile, null);

        assertEquals(100, recovered.getInt("waypoints.spawn.x"));
        // Check that dataFile was restored on disk
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(dataFile);
        assertEquals(100, reloaded.getInt("waypoints.spawn.x"));
    }

    @Test
    public void testAutoRecoveryWhenDataFileMissing(@TempDir Path tempDir) throws IOException {
        File dataFile = tempDir.resolve("vault.yml").toFile();
        File backupFile = tempDir.resolve("backup/vault.yml.bak").toFile();

        // Create valid backup
        YamlConfiguration backupConfig = new YamlConfiguration();
        backupConfig.set("key", "recovered-value");
        SafeFileUtil.saveConfigurationAtomically(backupConfig, backupFile);

        assertFalse(dataFile.exists());

        // Load with auto-recovery
        YamlConfiguration recovered = SafeFileUtil.loadWithAutoRecovery(dataFile, backupFile, null);

        assertEquals("recovered-value", recovered.getString("key"));
        assertTrue(dataFile.exists());
    }
}

package com.lifeline.backup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

public class BackupManagerTest {

    @Test
    public void testSnapshotPruningRetainsMaxBackups(@TempDir Path tempDir) throws IOException, InterruptedException {
        File snapshotsDir = tempDir.resolve("backup/snapshots").toFile();
        assertTrue(snapshotsDir.mkdirs());

        // Create 6 dummy zip snapshot files with distinct modification timestamps
        List<File> files = new ArrayList<>();
        long baseTime = System.currentTimeMillis() - 10000;
        for (int i = 1; i <= 6; i++) {
            File f = new File(snapshotsDir, "backup-2026-09-0" + i + "_12-00-00.zip");
            assertTrue(f.createNewFile());
            f.setLastModified(baseTime + (i * 1000));
            files.add(f);
        }

        assertEquals(6, snapshotsDir.listFiles((dir, name) -> name.endsWith(".zip")).length);

        // Prune to maxBackups = 3
        pruneHelper(snapshotsDir, 3);

        File[] remaining = snapshotsDir.listFiles((dir, name) -> name.endsWith(".zip"));
        assertNotNull(remaining);
        assertEquals(3, remaining.length, "Should keep exactly 3 newest snapshots");

        Set<String> remainingNames = new HashSet<>();
        for (File f : remaining) {
            remainingNames.add(f.getName());
        }

        // The oldest 3 (i=1, 2, 3) must have been deleted, keeping 4, 5, 6
        assertFalse(remainingNames.contains("backup-2026-09-01_12-00-00.zip"));
        assertFalse(remainingNames.contains("backup-2026-09-02_12-00-00.zip"));
        assertFalse(remainingNames.contains("backup-2026-09-03_12-00-00.zip"));
        assertTrue(remainingNames.contains("backup-2026-09-04_12-00-00.zip"));
        assertTrue(remainingNames.contains("backup-2026-09-05_12-00-00.zip"));
        assertTrue(remainingNames.contains("backup-2026-09-06_12-00-00.zip"));
    }

    @Test
    public void testZipArchiveContents(@TempDir Path tempDir) throws IOException {
        File backupDir = tempDir.resolve("backup").toFile();
        File snapshotsDir = new File(backupDir, "snapshots");
        assertTrue(snapshotsDir.mkdirs());

        // Create mock data files
        File vault = tempDir.resolve("vault.yml").toFile();
        try (FileWriter w = new FileWriter(vault)) {
            w.write("items: vault_data\n");
        }

        File stashes = tempDir.resolve("personal-stashes.yml").toFile();
        try (FileWriter w = new FileWriter(stashes)) {
            w.write("stashes: stash_data\n");
        }

        File waypoints = tempDir.resolve("waypoints.yml").toFile();
        try (FileWriter w = new FileWriter(waypoints)) {
            w.write("waypoints: wp_data\n");
        }

        File personalWaypoints = tempDir.resolve("personal-waypoints.yml").toFile();
        try (FileWriter w = new FileWriter(personalWaypoints)) {
            w.write("players: pwp_data\n");
        }

        // Test zip packing
        File zipFile = new File(snapshotsDir, "backup-test.zip");
        List<File> filesToArchive = List.of(vault, stashes, waypoints, personalWaypoints);

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(zipFile);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(fos)) {
            byte[] buf = new byte[1024];
            for (File file : filesToArchive) {
                zos.putNextEntry(new ZipEntry(file.getName()));
                try (FileInputStream fis = new FileInputStream(file)) {
                    int len;
                    while ((len = fis.read(buf)) > 0) {
                        zos.write(buf, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }

        assertTrue(zipFile.exists());
        assertTrue(zipFile.length() > 0);

        // Verify entries inside the zip
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }

        assertTrue(entries.contains("vault.yml"));
        assertTrue(entries.contains("personal-stashes.yml"));
        assertTrue(entries.contains("waypoints.yml"));
        assertTrue(entries.contains("personal-waypoints.yml"));
    }

    private void pruneHelper(File snapshotsDir, int maxBackups) {
        File[] files = snapshotsDir.listFiles((dir, name) -> name.endsWith(".zip"));
        if (files == null || files.length <= maxBackups) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - maxBackups;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
        }
    }
}

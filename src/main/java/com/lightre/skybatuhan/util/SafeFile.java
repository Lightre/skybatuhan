package com.lightre.skybatuhan.util;

import com.lightre.skybatuhan.SkyBatuhan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SafeFile {
    private SafeFile() {
    }

    /**
     * Writes into a temp file first and then moves it over the target,
     * so the target is never left empty or half written if the game crashes mid-save.
     */
    public static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /**
     * Renames an unreadable file to "name.broken" so the next save cannot overwrite it.
     *
     * @return the backup path, or null if the rename failed
     */
    public static Path backupBroken(Path file) {
        Path backup = file.resolveSibling(file.getFileName() + ".broken");
        try {
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException e) {
            SkyBatuhan.LOGGER.warn("Could not back up {}: {}", file.getFileName(), e.toString());
            return null;
        }
    }
}
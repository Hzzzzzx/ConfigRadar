package io.github.hzzzzzx.configradar.core.scan;

import java.util.List;

/** Classified file list for detectors. */
public record FileIndex(
    List<IndexedFile> files
) {
    public FileIndex {
        files = List.copyOf(files == null ? List.of() : files);
    }

    public List<IndexedFile> ofType(FileType type) {
        return files.stream().filter(file -> file.type() == type).toList();
    }
}

package com.tradacoms.parser.batch;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Sealed interface representing input sources for batch processing.
 * Supports multiple input types: file paths, streams, and directory scans.
 */
public sealed interface InputSource permits InputSource.PathSource, InputSource.StreamSource, InputSource.DirectoryScan {

    /**
     * Returns a unique identifier for this input source.
     */
    String getId();

    /**
     * Input source from a file path.
     */
    record PathSource(Path path) implements InputSource {
        public PathSource {
            Objects.requireNonNull(path, "path must not be null");
        }

        @Override
        public String getId() {
            return path.toString();
        }
    }

    /**
     * Input source from a stream supplier.
     * The supplier is called each time the stream needs to be read,
     * allowing for retry scenarios.
     */
    record StreamSource(String id, Supplier<InputStream> supplier) implements InputSource {
        public StreamSource {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(supplier, "supplier must not be null");
        }

        @Override
        public String getId() {
            return id;
        }
    }

    /**
     * Input source from a directory scan with glob pattern.
     * Matches files in the directory using the specified glob pattern.
     */
    record DirectoryScan(Path directory, String glob) implements InputSource {
        public DirectoryScan {
            Objects.requireNonNull(directory, "directory must not be null");
            Objects.requireNonNull(glob, "glob must not be null");
        }

        @Override
        public String getId() {
            return directory.toString() + "/" + glob;
        }
    }
}

package com.tradacoms.parser;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Sealed interface defining output targets for split batch writing.
 * 
 * Requirements: 5.1, 5.7
 */
public sealed interface OutputTarget permits 
        OutputTarget.Directory,
        OutputTarget.Callback {

    /**
     * Output target that writes to files in a directory.
     * Uses a filename template to generate unique filenames for each output.
     *
     * @param dir the directory to write files to
     * @param template the filename template for generating filenames
     */
    record Directory(Path dir, FilenameTemplate template) implements OutputTarget {
        public Directory {
            Objects.requireNonNull(dir, "dir must not be null");
            Objects.requireNonNull(template, "template must not be null");
        }

        /**
         * Creates a Directory target with a default filename template.
         *
         * @param dir the directory to write files to
         * @return a new Directory target
         */
        public static Directory of(Path dir) {
            return new Directory(dir, FilenameTemplate.defaultTemplate());
        }
    }

    /**
     * Output target that invokes a callback for each written artifact.
     * Allows custom sinks for processing split output.
     *
     * @param sink consumer that receives each written artifact
     */
    record Callback(Consumer<WrittenArtifact> sink) implements OutputTarget {
        public Callback {
            Objects.requireNonNull(sink, "sink must not be null");
        }
    }
}

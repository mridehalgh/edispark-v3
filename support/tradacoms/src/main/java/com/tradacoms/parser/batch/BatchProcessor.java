package com.tradacoms.parser.batch;

import com.tradacoms.parser.EdiParser;
import com.tradacoms.parser.ParseException;
import com.tradacoms.parser.ParserConfig;
import com.tradacoms.parser.model.Batch;
import com.tradacoms.parser.model.Message;
import com.tradacoms.parser.validation.BatchValidationReport;
import com.tradacoms.parser.validation.EdiValidator;
import com.tradacoms.parser.validation.MessageValidationReport;
import com.tradacoms.parser.validation.ValidationConfig;
import com.tradacoms.parser.validation.ValidationIssue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Processor for batch EDI file operations.
 * Supports processing multiple files with configurable concurrency,
 * validation, and error handling.
 */
public final class BatchProcessor {

    private final EdiParser parser;
    private final EdiValidator validator;

    public BatchProcessor() {
        this.parser = new EdiParser();
        this.validator = new EdiValidator();
    }

    public BatchProcessor(EdiParser parser, EdiValidator validator) {
        this.parser = parser != null ? parser : new EdiParser();
        this.validator = validator != null ? validator : new EdiValidator();
    }

    /**
     * Process multiple input sources with the specified configuration.
     *
     * @param inputs list of input sources to process
     * @param config batch processing configuration
     * @return consolidated processing result
     */
    public BatchProcessingResult process(List<InputSource> inputs, BatchProcessConfig config) {
        if (inputs == null || inputs.isEmpty()) {
            return BatchProcessingResult.fromResults(List.of());
        }

        // Expand directory scans to individual file sources
        List<InputSourceWithIndex> expandedInputs = expandInputSources(inputs);

        List<FileResult> results;
        
        if (config.getThreadPoolSize() == 1) {
            // Single-threaded processing
            results = processSingleThreaded(expandedInputs, config);
        } else {
            // Multi-threaded processing
            results = processMultiThreaded(expandedInputs, config);
        }

        return BatchProcessingResult.fromResults(results);
    }

    /**
     * Expands input sources, converting DirectoryScan to individual PathSources.
     * Preserves original order with index for deterministic output ordering.
     */
    private List<InputSourceWithIndex> expandInputSources(List<InputSource> inputs) {
        List<InputSourceWithIndex> expanded = new ArrayList<>();
        int index = 0;

        for (InputSource input : inputs) {
            if (input instanceof InputSource.DirectoryScan scan) {
                try {
                    List<Path> matchedFiles = scanDirectory(scan.directory(), scan.glob());
                    for (Path file : matchedFiles) {
                        expanded.add(new InputSourceWithIndex(
                                new InputSource.PathSource(file), 
                                index++
                        ));
                    }
                } catch (IOException e) {
                    // Create a failed result for the directory scan
                    expanded.add(new InputSourceWithIndex(input, index++));
                }
            } else {
                expanded.add(new InputSourceWithIndex(input, index++));
            }
        }

        return expanded;
    }

    /**
     * Scans a directory for files matching the glob pattern.
     */
    private List<Path> scanDirectory(Path directory, String glob) throws IOException {
        List<Path> files = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        }
        
        // Sort for deterministic ordering
        files.sort(Path::compareTo);
        return files;
    }

    /**
     * Process inputs single-threaded.
     */
    private List<FileResult> processSingleThreaded(
            List<InputSourceWithIndex> inputs, 
            BatchProcessConfig config
    ) {
        List<FileResult> results = new ArrayList<>();

        for (InputSourceWithIndex input : inputs) {
            FileResult result = processInput(input.source(), config);
            results.add(result);

            // Check if we should stop on file error
            if (result.isFailed() && !config.isContinueOnFileError()) {
                break;
            }
        }

        return results;
    }

    /**
     * Process inputs multi-threaded with optional deterministic ordering.
     */
    private List<FileResult> processMultiThreaded(
            List<InputSourceWithIndex> inputs,
            BatchProcessConfig config
    ) {
        ExecutorService executor = Executors.newFixedThreadPool(config.getThreadPoolSize());
        
        try {
            List<Future<IndexedFileResult>> futures = new ArrayList<>();

            for (InputSourceWithIndex input : inputs) {
                Callable<IndexedFileResult> task = () -> {
                    FileResult result = processInput(input.source(), config);
                    return new IndexedFileResult(input.index(), result);
                };
                futures.add(executor.submit(task));
            }

            List<FileResult> results = new ArrayList<>();
            
            if (config.isDeterministicOutputOrder()) {
                // Collect all results and sort by original index
                List<IndexedFileResult> indexedResults = new ArrayList<>();
                
                for (Future<IndexedFileResult> future : futures) {
                    try {
                        indexedResults.add(future.get());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Processing interrupted", e);
                    } catch (ExecutionException e) {
                        // This shouldn't happen as we catch exceptions in processInput
                        throw new RuntimeException("Processing failed", e.getCause());
                    }
                }
                
                // Sort by original index to preserve input order
                indexedResults.sort((a, b) -> Integer.compare(a.index(), b.index()));
                
                for (IndexedFileResult ir : indexedResults) {
                    results.add(ir.result());
                }
            } else {
                // Return results in completion order
                for (Future<IndexedFileResult> future : futures) {
                    try {
                        results.add(future.get().result());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Processing interrupted", e);
                    } catch (ExecutionException e) {
                        throw new RuntimeException("Processing failed", e.getCause());
                    }
                }
            }

            return results;
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Process a single input source.
     */
    private FileResult processInput(InputSource input, BatchProcessConfig config) {
        String correlationId = generateCorrelationId();
        String filename = input.getId();

        try {
            InputStream stream = openInputStream(input);
            
            try (stream) {
                return processStream(stream, correlationId, filename, config);
            }
        } catch (IOException e) {
            return FileResult.failed(correlationId, filename, e);
        } catch (ParseException e) {
            return FileResult.failed(correlationId, filename, e);
        } catch (Exception e) {
            return FileResult.failed(correlationId, filename, e);
        }
    }

    /**
     * Opens an input stream for the given input source.
     */
    private InputStream openInputStream(InputSource input) throws IOException {
        if (input instanceof InputSource.PathSource ps) {
            return Files.newInputStream(ps.path());
        } else if (input instanceof InputSource.StreamSource ss) {
            return ss.supplier().get();
        } else if (input instanceof InputSource.DirectoryScan) {
            throw new IOException("DirectoryScan should be expanded before processing");
        } else {
            throw new IOException("Unknown input source type: " + input.getClass());
        }
    }

    /**
     * Process a stream and return the file result.
     */
    private FileResult processStream(
            InputStream stream,
            String correlationId,
            String filename,
            BatchProcessConfig config
    ) {
        ParserConfig parserConfig = config.getParserConfig();
        ValidationConfig validationConfig = ValidationConfig.builder()
                .failFast(config.getValidationConfig().isFailFast())
                .maxIssues(config.getValidationConfig().getMaxIssues())
                .validateEnvelope(config.getValidationConfig().isValidateEnvelope())
                .validateSchema(config.getValidationConfig().isValidateSchema())
                .schemas(config.getValidationConfig().getSchemas())
                .filename(filename)
                .build();

        // Parse the batch
        Batch batch = parser.parseBatch(stream, parserConfig);

        // Validate the batch
        BatchValidationReport validationReport = validator.validateBatch(batch, validationConfig);

        // Convert to message results
        List<MessageResult> messageResults = new ArrayList<>();
        
        for (Message message : batch.getMessages()) {
            int msgIndex = message.getMessageIndexInBatch();
            String messageId = MessageResult.deriveMessageId(
                    message.getMessageControlRef(), 
                    msgIndex
            );
            
            MessageValidationReport msgReport = validationReport.getMessageReport(msgIndex)
                    .orElse(MessageValidationReport.pass(msgIndex, message.getMessageControlRef()));
            
            MessageStatus status;
            if (msgReport.isFailed()) {
                status = MessageStatus.INVALID;
            } else {
                status = MessageStatus.VALID;
            }
            
            messageResults.add(new MessageResult(
                    messageId,
                    msgIndex,
                    message.getMessageType(),
                    status,
                    msgReport.getIssues()
            ));
        }

        // Determine file status
        FileStatus fileStatus;
        List<ValidationIssue> fileIssues = validationReport.getBatchLevelIssues();
        
        boolean hasInvalidMessages = messageResults.stream()
                .anyMatch(MessageResult::isInvalid);
        boolean hasFileErrors = !fileIssues.isEmpty() && 
                fileIssues.stream().anyMatch(ValidationIssue::isError);

        if (hasFileErrors) {
            fileStatus = FileStatus.FAILED;
        } else if (hasInvalidMessages) {
            fileStatus = FileStatus.PARTIAL;
        } else {
            fileStatus = FileStatus.SUCCESS;
        }

        return new FileResult(correlationId, filename, fileStatus, messageResults, fileIssues);
    }

    /**
     * Generates a unique correlation ID.
     */
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Internal record for tracking input source with original index.
     */
    private record InputSourceWithIndex(InputSource source, int index) {}

    /**
     * Internal record for tracking file result with original index.
     */
    private record IndexedFileResult(int index, FileResult result) {}
}

# TRADACOMS Parser Library

A Java library for parsing, validating, and writing TRADACOMS EDI files used in UK retail supply chain communications.

## Features

- DOM and streaming parsers for single messages and batches
- Schema-based and heuristic group/loop detection
- Comprehensive validation with detailed error reporting
- Round-trip serialization preserving original formatting
- Batch splitting by message, type, or routing key
- Multi-file batch processing with configurable concurrency
- JSON schema loading for message type definitions

## Requirements

- Java 17+
- Maven 3.6+

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.tradacoms</groupId>
    <artifactId>tradacoms-parser</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Parsing a Batch

```java
import com.tradacoms.parser.EdiParser;
import com.tradacoms.parser.model.Batch;
import com.tradacoms.parser.model.Message;

EdiParser parser = new EdiParser();

// Parse from string
Batch batch = parser.parseBatch("""
    STX=ANAA:1+SENDER:+RECEIVER:+260115:120000+1'
    MHD=1+CREDIT:9'
    CLO=5012345678900:BUYER+SUPPLIER REF'
    MTR=3'
    END=1'
    """);

// Access messages
for (Message message : batch.getMessages()) {
    System.out.println("Type: " + message.getMessageType());
    System.out.println("Segments: " + message.getAllSegments().size());
}
```

### Parsing from File

```java
try (InputStream input = Files.newInputStream(Path.of("orders.edi"))) {
    Batch batch = parser.parseBatch(input);
}
```

### Streaming Large Files

For memory-efficient processing of large batches:

```java
// Stream complete messages one at a time
Iterator<Message> messages = parser.streamMessages(input);
while (messages.hasNext()) {
    Message message = messages.next();
    processMessage(message);
}

// Or use event-based streaming for maximum control
try (BatchEventReader reader = parser.streamBatch(input)) {
    while (reader.hasNext()) {
        BatchEvent event = reader.next();
        // Handle START_MESSAGE, SEGMENT_READ, END_MESSAGE events
    }
}
```

## Configuration

### Parser Configuration

```java
ParserConfig config = ParserConfig.builder()
    .charset(StandardCharsets.ISO_8859_1)
    .segmentTerminator('\'')
    .lenientMode(true)                          // Tolerate minor syntax issues
    .messageTypeAllowlist(Set.of("ORDERS", "INVOIC"))  // Filter by type
    .maxMessages(100)                           // Limit messages parsed
    .stopOnError(false)                         // Continue on errors
    .continueOnError(true)                      // Collect all errors
    .build();

EdiParser parser = new EdiParser(config);
```

### Writer Configuration

```java
WriterConfig config = WriterConfig.builder()
    .charset(StandardCharsets.ISO_8859_1)
    .computeControlTotals(true)    // Auto-compute segment/message counts
    .roundTripMode(true)           // Preserve original envelope formatting
    .deterministicOrdering(true)   // Consistent output ordering
    .build();

EdiWriter writer = new EdiWriter(config);
```

## Writing TRADACOMS

### Serialize a Batch

```java
EdiWriter writer = new EdiWriter();

// To string
String ediContent = writer.serializeBatch(batch);

// To output stream
try (OutputStream output = Files.newOutputStream(Path.of("output.edi"))) {
    writer.writeBatch(batch, output);
}
```

### Building Messages Programmatically

```java
Message message = Message.of("CREDIT", List.of(
    Segment.of("CLO", List.of(
        Element.of("5012345678900"),
        Element.of("SUPPLIER REF")
    )),
    Segment.of("CDT", List.of(
        Element.of(List.of("260115", "CREDIT DATE"))
    ))
));

Batch batch = Batch.of("SENDER123", "RECEIVER456", List.of(message));
String output = writer.serializeBatch(batch);
```

## Validation

```java
EdiValidator validator = new EdiValidator();

ValidationConfig config = ValidationConfig.builder()
    .validateEnvelope(true)     // Check STX/END integrity
    .validateSchema(true)       // Validate against message schema
    .failFast(false)            // Collect all issues
    .maxIssues(100)             // Limit reported issues
    .build();

BatchValidationReport report = validator.validateBatch(batch, config);

if (report.getOverallStatus() == ValidationStatus.FAIL) {
    for (ValidationIssue issue : report.getAllIssues()) {
        System.err.println(issue.getCode() + ": " + issue.getMessage());
        System.err.println("  Location: " + issue.getLocation());
    }
}
```

## Batch Splitting

Split a batch into multiple output files:

```java
EdiWriter writer = new EdiWriter();

// Split by individual message
List<WrittenArtifact> artifacts = writer.writeSplit(
    batch,
    new SplitStrategy.ByMessage(),
    new OutputTarget.Directory(Path.of("output"), FilenameTemplate.defaultTemplate())
);

// Split by message type
writer.writeSplit(batch, new SplitStrategy.ByMessageType(), target);

// Split by routing key (e.g., receiver GLN)
writer.writeSplit(batch, new SplitStrategy.ByRoutingKey("receiverId"), target);

// Custom predicate-based splitting
writer.writeSplit(batch, new SplitStrategy.Custom(
    msg -> msg.getMessageType().startsWith("ORD") ? "orders" : "other"
), target);
```

## Multi-File Batch Processing

Process multiple EDI files with configurable concurrency:

```java
BatchProcessor processor = new BatchProcessor();

BatchProcessConfig config = BatchProcessConfig.builder()
    .parserConfig(ParserConfig.defaults())
    .validationConfig(ValidationConfig.defaults())
    .threadPoolSize(4)                    // Parallel processing
    .deterministicOutputOrder(true)       // Preserve input order in results
    .continueOnFileError(true)            // Don't stop on individual file errors
    .build();

List<InputSource> inputs = List.of(
    new InputSource.PathSource(Path.of("file1.edi")),
    new InputSource.PathSource(Path.of("file2.edi")),
    new InputSource.DirectoryScan(Path.of("inbox"), "*.edi")
);

BatchProcessingResult result = processor.process(inputs, config);

for (FileResult fileResult : result.getFileResults()) {
    System.out.println(fileResult.getFilename() + ": " + fileResult.getStatus());
    for (MessageResult msgResult : fileResult.getMessageResults()) {
        System.out.println("  " + msgResult.getMessageId() + ": " + msgResult.getStatus());
    }
}
```

## Schema Loading

Load message schemas from JSON files:

```java
SchemaLoader loader = new SchemaLoader();

// Load single schema
MessageSchema creditSchema = loader.loadFromJson(Path.of("CREDIT.json"));

// Load all schemas from directory
Map<String, MessageSchema> schemas = loader.loadAllFromDirectory(Path.of("schemas"));

// Use with parser for schema-based group detection
EdiParser parser = new EdiParser(ParserConfig.defaults(), schemas);
```

## Data Model

### Batch Structure

```
Batch
├── batchId, senderId, receiverId, creationTimestamp
├── rawHeader (STX segment)
├── rawTrailer (END segment)
└── messages[]
    └── Message
        ├── messageType, messageIndexInBatch, messageControlRef
        ├── header (MHD segment)
        ├── trailer (MTR segment)
        └── content[] (SegmentOrGroup)
            ├── Segment
            │   ├── tag, lineNumber, charPosition, rawContent
            │   └── elements[]
            │       └── Element (components[])
            └── Group
                ├── groupId, loopIndex, maxOccurs
                └── content[] (nested segments/groups)
```

### TRADACOMS Syntax

| Character | Purpose | Default |
|-----------|---------|---------|
| `'` | Segment terminator | `'` |
| `+` | Element separator | `+` |
| `:` | Component separator | `:` |
| `=` | Tag separator | `=` |
| `?` | Release (escape) character | `?` |

## Error Handling

The library provides structured exceptions with detailed context:

```java
try {
    Batch batch = parser.parseBatch(input);
} catch (ParseException e) {
    System.err.println("Error: " + e.getMessage());
    System.err.println("Code: " + e.getErrorCode());
    System.err.println("Line: " + e.getLineNumber());
    System.err.println("Position: " + e.getCharPosition());
    System.err.println("Snippet: " + e.getRawSnippet());
}
```

### Error Codes

| Code | Category | Description |
|------|----------|-------------|
| `PARSE_001` | Syntax | Invalid segment terminator |
| `PARSE_002` | Syntax | Malformed segment structure |
| `ENV_001` | Envelope | Missing STX segment |
| `ENV_002` | Envelope | Missing END segment |
| `ENV_003` | Envelope | Message count mismatch |
| `VALID_001` | Schema | Missing required segment |
| `VALID_004` | Schema | Element exceeds max length |

## Supported Message Types

The library supports all standard TRADACOMS message types:

- ORDERS, ORDHDR, ORDTLR - Purchase orders
- INVOIC - Invoices
- CREDIT - Credit notes
- DELIVR - Delivery notifications
- PRICAT - Price catalogs
- ACKHDR - Acknowledgments
- And more...

## Building

```bash
mvn clean install
```

## Running Tests

```bash
mvn test
```

## License

See LICENSE file for details.

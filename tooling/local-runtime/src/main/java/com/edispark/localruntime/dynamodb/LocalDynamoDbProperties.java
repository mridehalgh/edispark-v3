package com.edispark.localruntime.dynamodb;

import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("dynamodb.local")
public class LocalDynamoDbProperties {

    private String endpoint = "http://localhost:8000";
    private Path dataDirectory = Path.of(".local", "dynamodb");
    private Path nativeLibraryPath = Path.of("tooling", "local-runtime", "target", "dependencies");
}

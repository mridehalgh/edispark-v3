package com.edispark.documents.infrastructure.storage;

import com.edispark.crypto.LocalTenantCipher;
import com.edispark.documents.domain.model.*;
import com.edispark.identifiers.tenant.TenantId;
import io.cottn.core.tenancy.*;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import static org.assertj.core.api.Assertions.*;

class S3ContentIsolationTest {
    @TempDir Path root;

    @Test void contentIsTenantAndResourceBoundBeforeAndAfterS3() {
        Map<String, byte[]> blobs = new HashMap<>();
        S3Client client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[]{S3Client.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "putObject" -> {
                        var request = (PutObjectRequest) args[0];
                        blobs.put(request.key(), ((RequestBody) args[1]).contentStreamProvider().newStream().readAllBytes());
                        yield PutObjectResponse.builder().build();
                    }
                    case "getObjectAsBytes" -> {
                        byte[] bytes = blobs.get(((GetObjectRequest) args[0]).key());
                        if (bytes == null) throw NoSuchKeyException.builder().message("missing").build();
                        yield ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes);
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var store = new S3ContentStore(client, new LocalTenantCipher(root.resolve("key"), "edispark-documents"), "content-test");
        var content = Content.of("[content]".getBytes(), Format.JSON);
        assertThatThrownBy(() -> store.store(content)).isInstanceOf(NoTenantSetException.class);
        assertThat(blobs).isEmpty();
        String a = TenantId.newTenantId().asString();
        String b = TenantId.newTenantId().asString();
        TenantExecution.run(a, () -> store.store(content));
        byte[] ciphertext = blobs.values().iterator().next();
        TenantExecution.run(b, () -> {
            assertThat(store.retrieve(content.hash())).isEmpty();
            blobs.put(ContentAddress.currentKey(content.hash()), ciphertext);
            assertThatThrownBy(() -> store.retrieve(content.hash())).isInstanceOf(SecurityException.class);
        });
        TenantExecution.run(a, () -> assertThat(store.retrieve(content.hash())).hasValue(content.data()));
    }
}

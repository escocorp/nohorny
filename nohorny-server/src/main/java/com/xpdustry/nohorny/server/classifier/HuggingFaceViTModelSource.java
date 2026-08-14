// SPDX-License-Identifier: MIT
package com.xpdustry.nohorny.server.classifier;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HuggingFaceViTModelSource implements ViTModelSource {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceViTModelSource.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(10);

    private final HuggingFaceViTModelSourceProperties properties;

    public HuggingFaceViTModelSource(final HuggingFaceViTModelSourceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return this.properties.repository() + ":" + this.properties.revision();
    }

    @SuppressWarnings("EmptyCatch")
    @Override
    public Path retrieve() {
        final var name =
                (this.name() + "/" + this.properties.file()).replace('/', '-').replace(':', '-');
        final var target = this.properties.downloadDirectory().resolve(name);
        if (Files.notExists(target)) {
            try {
                Files.createDirectories(this.properties.downloadDirectory());
            } catch (final IOException e) {
                throw new RuntimeException(
                        "Failed to create the download directory for hugging face models: "
                                + this.properties.downloadDirectory(),
                        e);
            }
            final var url = "https://huggingface.co/" + this.properties.repository() + "/resolve/"
                    + this.properties.revision() + "/" + this.properties.file();
            log.info("Model {} does not exist locally, downloading from hugging face at {}", name, url);
            final var temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            try {
                final var connection =
                        (HttpURLConnection) URI.create(url).toURL().openConnection();
                try {
                    connection.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
                    connection.setReadTimeout((int) READ_TIMEOUT.toMillis());
                    connection.setInstanceFollowRedirects(true);
                    if (this.properties.token() != null) {
                        connection.setRequestProperty("Authorization", "Bearer " + this.properties.token());
                    }
                    try (final var in = connection.getInputStream()) {
                        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.move(temp, target);
                } finally {
                    connection.disconnect();
                }
            } catch (final IOException e) {
                try {
                    Files.deleteIfExists(temp);
                } catch (final IOException ignored) {
                }
                throw new RuntimeException("Failed to download hugging face model " + name, e);
            }
        }
        return target;
    }
}

package io.github.rush.replay;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class ReplayStorage {

    private static final int MAX_REPLAYS = 20;
    private static final Gson GSON = ReplayActionSerializer.createGson();

    private final Path replaysDir;

    public ReplayStorage(Path replaysDir) {
        this.replaysDir = replaysDir;
    }

    public void save(ReplayFile file) {
        try {
            Files.createDirectories(replaysDir);
            enforceCapBeforeSave();

            final Path target = replaysDir.resolve(file.header().sessionId() + ".json");

            try (Writer w = Files.newBufferedWriter(target)) {
                GSON.toJson(file, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save replay " + file.header().sessionId(), e);
        }
    }

    public List<ReplayHeader> listReplays() {
        try (Stream<Path> files = Files.list(replaysDir)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(this::readHeader)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public Optional<ReplayFile> load(String sessionId) {
        final Path target = replaysDir.resolve(sessionId + ".json");
        if (!Files.exists(target)) return Optional.empty();

        try (Reader r = Files.newBufferedReader(target)) {
            return Optional.of(GSON.fromJson(r, ReplayFile.class));
        } catch (IOException | JsonParseException e) {
            return Optional.empty();
        }
    }

    private void enforceCapBeforeSave() throws IOException {
        try (Stream<Path> files = Files.list(replaysDir)) {
            final List<Path> sorted = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::lastModified))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            int idx = 0;
            while (sorted.size() - idx >= MAX_REPLAYS) {
                Files.deleteIfExists(sorted.get(idx++));
            }
        }
    }

    private long lastModified(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Optional<ReplayHeader> readHeader(Path p) {
        try (Reader r = Files.newBufferedReader(p)) {
            final ReplayFile file = GSON.fromJson(r, ReplayFile.class);
            return Optional.of(file.header());
        } catch (IOException | JsonParseException e) {
            return Optional.empty();
        }
    }
}

package com.aerospike.demo.booking.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.demo.booking.model.Landmark;
import com.aerospike.demo.booking.util.Json;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Loads /data/landmarks.json once at startup and serves it from memory - landmarks are never
 * queried or indexed in Aerospike (docs/design.md).
 */
public final class LandmarkStore {
    private static final Logger log = LoggerFactory.getLogger(LandmarkStore.class);

    private volatile List<Landmark> landmarks = List.of();
    private final Map<String, Landmark> byId = new ConcurrentHashMap<>();

    public void load(Path dataDir) {
        Path file = dataDir.resolve("landmarks.json");
        try {
            byte[] bytes = Files.readAllBytes(file);
            List<Landmark> loaded = Json.MAPPER.readValue(bytes, new TypeReference<List<Landmark>>() {
            });
            byId.clear();
            for (Landmark l : loaded) {
                byId.put(l.landmarkId, l);
            }
            this.landmarks = List.copyOf(loaded);
            log.info("Loaded {} landmarks from {}", loaded.size(), file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read landmarks file at " + file, e);
        }
    }

    public List<Landmark> all() {
        return landmarks;
    }

    public Landmark byId(String landmarkId) {
        return byId.get(landmarkId);
    }
}

package com.aerospike.demo.booking.data;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.demo.booking.util.Json;

/**
 * Loads scenarios/*.json at startup (geo, locality, path-expressions, strings, booking) and
 * builds the "demo" block of API responses from them - see the API contract's POST /search,
 * GET /suggest, GET /hotels/{id}, POST /bookings: "substitute the request's actual values into
 * aelTemplate ... and include the scenario's static fields verbatim except for that substitution.
 * Don't hardcode this copy a second time in Java."
 */
public final class ScenarioRegistry {
    private static final Logger log = LoggerFactory.getLogger(ScenarioRegistry.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

    private final Map<String, Scenario> byId = new LinkedHashMap<>();
    private boolean loaded = false;

    public void load(Path scenariosDir) {
        if (!Files.isDirectory(scenariosDir)) {
            log.warn("Scenarios directory {} not found - demo=true will return demo:null on every "
                    + "endpoint. See backend/README.md \"Deviations\" (docker-compose.yml bind mount).",
                    scenariosDir);
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(scenariosDir, "*.json")) {
            for (Path file : files) {
                Scenario s = Json.MAPPER.readValue(Files.readAllBytes(file), Scenario.class);
                if (s.id == null) {
                    log.warn("Scenario file {} has no \"id\" field, skipping", file);
                    continue;
                }
                byId.put(s.id, s);
            }
            loaded = true;
            log.info("Loaded {} demo scenarios from {}: {}", byId.size(), scenariosDir, byId.keySet());
        } catch (IOException e) {
            log.warn("Could not read scenarios directory {} - demo=true will return demo:null.",
                    scenariosDir, e);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Builds the "demo" response block for {@code scenarioId}, substituting {@code {placeholder}}
     * tokens (present in {@code userAction} and {@code aelTemplate}) from {@code values}. Returns
     * null if the scenario wasn't found (missing scenarios dir, or an id that isn't one of the
     * five defined) - callers should surface that as {@code "demo": null} rather than fail the
     * request, since demo=true is a debugging/presentation aid, not core functionality.
     */
    public Map<String, Object> buildDemo(String scenarioId, Map<String, Object> values) {
        Scenario s = byId.get(scenarioId);
        if (s == null) {
            return null;
        }
        Map<String, Object> demo = new LinkedHashMap<>();
        demo.put("userAction", substitute(s.userAction, values));
        demo.put("aerospikeAction", s.aerospikeAction);
        demo.put("queryMechanism", s.queryMechanism);
        demo.put("index", s.index);
        demo.put("aelTemplate", substitute(s.aelTemplate, values));
        demo.put("relevantCode", s.relevantCode);
        demo.put("queryHint", s.queryHint);
        demo.put("notes", substitute(s.notes, values));
        return demo;
    }

    private static String substitute(String template, Map<String, Object> values) {
        if (template == null) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object v = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(v == null ? m.group(0) : String.valueOf(v)));
        }
        m.appendTail(out);
        return out.toString();
    }
}

package com.aerospike.demo.booking;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Environment-driven configuration. Defaults match docker-compose.yml's {@code backend} service
 * definition so the same jar runs unmodified in-container or standalone on a laptop.
 */
public final class AppConfig {
    public final String aerospikeHost;
    public final int aerospikePort;
    public final String namespace;
    public final Path dataDir;
    public final Path scenariosDir;
    public final int serverPort;

    private AppConfig(String aerospikeHost, int aerospikePort, String namespace,
                       Path dataDir, Path scenariosDir, int serverPort) {
        this.aerospikeHost = aerospikeHost;
        this.aerospikePort = aerospikePort;
        this.namespace = namespace;
        this.dataDir = dataDir;
        this.scenariosDir = scenariosDir;
        this.serverPort = serverPort;
    }

    public static AppConfig fromEnv() {
        String host = env("AEROSPIKE_HOST", "aerospike");
        int port = Integer.parseInt(env("AEROSPIKE_PORT", "3000"));
        String namespace = env("AEROSPIKE_NAMESPACE", "demo");
        Path dataDir = Path.of(env("DATA_DIR", "/data"));
        int serverPort = Integer.parseInt(env("SERVER_PORT", "8081"));
        Path scenariosDir = findScenariosDir();
        return new AppConfig(host, port, namespace, dataDir, scenariosDir, serverPort);
    }

    /**
     * scenarios/*.json live at the repo root, not under backend/ or the datagen-owned /data
     * volume. See backend/README.md "Deviations" - docker-compose.yml's backend service gained
     * one extra read-only bind mount (SCENARIOS_DIR / ../scenarios) so this directory is reachable
     * without duplicating the JSON files into the backend image. This helper also supports running
     * the jar straight from a repo checkout (mvn exec, IDE run) where "../scenarios" resolves
     * relative to backend/.
     */
    private static Path findScenariosDir() {
        String override = System.getenv("SCENARIOS_DIR");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        for (String candidate : new String[] {"/app/scenarios", "../scenarios", "scenarios"}) {
            Path p = Path.of(candidate);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        // Fall back to the container path even if it doesn't exist yet - ScenarioRegistry logs a
        // clear warning and demo blocks degrade to null rather than crashing the app.
        return Path.of("/app/scenarios");
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}

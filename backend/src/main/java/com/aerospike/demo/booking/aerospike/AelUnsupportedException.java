package com.aerospike.demo.booking.aerospike;

/**
 * Thrown when an endpoint needs a {@code .where(String ael, ...)} query and it can't run — for
 * two genuinely different reasons that used to be conflated into one message (see
 * backend/README.md's "Fixing a self-contradictory error message" for why that was a real problem,
 * not just a wording nitpick): either the connected cluster's minimum server version doesn't
 * support server-side AEL parsing at all ({@link #versionGateFailed} = true — the version bump
 * documented in "AEL and server version 8.1.2 vs 8.1.3" actually fixes this one), or the version
 * gate passed but the server rejected this *specific* query anyway ({@link #versionGateFailed} =
 * false — a not-yet-implemented function or grammar construct on an otherwise AEL-capable server,
 * like {@code lowercase()} on every build tested so far; upgrading the server version alone won't
 * fix this case). Mapped to HTTP 503 with an {@code AEL_UNSUPPORTED} body by the web layer.
 */
public final class AelUnsupportedException extends RuntimeException {
    public final String serverVersion;
    public final String requiredVersion;
    public final boolean versionGateFailed;

    public AelUnsupportedException(String serverVersion, String requiredVersion, boolean versionGateFailed) {
        super(versionGateFailed
                ? "AEL requires Aerospike server " + requiredVersion + "+; connected cluster is " + serverVersion
                : "Server " + serverVersion + " supports AEL, but rejected this specific query "
                        + "(a not-yet-implemented function or grammar construct, not a version problem)");
        this.serverVersion = serverVersion;
        this.requiredVersion = requiredVersion;
        this.versionGateFailed = versionGateFailed;
    }
}

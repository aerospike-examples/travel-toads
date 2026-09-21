import type { AelUnsupportedError } from "../api/types";
import "./AelUnsupportedBanner.css";

/**
 * Deliberate, on-brand UI state — NOT a generic error toast. Covers two different situations (see
 * AelUnsupportedError.versionGateFailed's own comment): the cluster's version is genuinely too
 * low, or the version is fine but this specific query hits a not-yet-implemented server feature.
 * Showing "Cluster version: 8.2.0.0 · Requires: 8.2.0+" for the second case reads as broken (the
 * requirement looks satisfied) and was reported as confusing in practice — so that line only
 * renders when it's actually the reason, not just whenever this banner shows at all.
 */
export function AelUnsupportedBanner({ info }: { info: AelUnsupportedError }) {
  const versionGateFailed = info.versionGateFailed ?? true;
  return (
    <div className="ael-banner" role="status">
      <div className="ael-banner-icon">⚡</div>
      <div>
        <div className="ael-banner-title">
          {versionGateFailed
            ? "Live results aren't available on this cluster yet"
            : "This query isn't supported on this server build yet"}
        </div>
        <p className="ael-banner-message">
          {info.message ||
            (versionGateFailed
              ? `This search runs on Aerospike's upcoming AEL query language (Aerospike ${info.requiredVersion}+) — this demo cluster is on ${info.serverVersion}, so live results aren't available yet.`
              : `This cluster (${info.serverVersion}) supports AEL, but this specific query isn't implemented on this server build yet — a feature gap, not a version problem.`)}
        </p>
        {versionGateFailed && (
          <p className="ael-banner-versions">
            Cluster version: <code>{info.serverVersion}</code> · Requires:{" "}
            <code>{info.requiredVersion}+</code>
          </p>
        )}
      </div>
    </div>
  );
}

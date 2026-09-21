import { useState } from "react";
import type { ReactNode } from "react";
import type { DemoBlock } from "../api/types";
import { useDemoMode } from "../context/DemoModeContext";
import "./DemoPanel.css";

interface DemoPanelProps {
  title: string;
  demo: DemoBlock | null | undefined;
}

/**
 * The collapsible "what just happened in Aerospike" panel. Renders nothing
 * when the mode is Off, or when there's no demo block yet (e.g. AEL
 * unsupported, or before the first request has resolved).
 *
 * Demo mode shows userAction / aerospikeAction / queryMechanism / index /
 * ael. Engineering mode expands the same panel to also show relevantCode,
 * queryHint, and notes — same component, more depth, never a different UI.
 */
export function DemoPanel({ title, demo }: DemoPanelProps) {
  const { showDemoPanel, showEngineering } = useDemoMode();
  const [open, setOpen] = useState(true);

  if (!showDemoPanel || !demo) return null;

  return (
    <section className="demo-panel">
      <button
        type="button"
        className="demo-panel-header"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        <span className="demo-panel-title">
          <span className="demo-panel-badge">⬡ Aerospike</span>
          {title}
        </span>
        <span className="demo-panel-toggle">{open ? "Hide ▲" : "Show ▼"}</span>
      </button>

      {open && (
        <div className="demo-panel-body">
          {demo.userAction && (
            <Row label="User action">
              <span className="demo-panel-action">{demo.userAction}</span>
            </Row>
          )}
          {demo.aerospikeAction && (
            <Row label="Aerospike action">{demo.aerospikeAction}</Row>
          )}
          <div className="demo-panel-meta">
            {demo.queryMechanism && <Pill label="Mechanism" value={demo.queryMechanism} />}
            <Pill label="Index" value={demo.index ?? "none (full scan)"} />
          </div>
          {demo.aelTemplate && (
            <Row label="AEL">
              <pre className="demo-panel-code">
                <code>{demo.aelTemplate}</code>
              </pre>
            </Row>
          )}

          {showEngineering && (
            <>
              {demo.relevantCode && (
                <Row label="Relevant code">
                  <pre className="demo-panel-code">
                    <code>{demo.relevantCode}</code>
                  </pre>
                </Row>
              )}
              {demo.queryHint && <Row label="Query hint">
                <code className="demo-panel-inline-code">{demo.queryHint}</code>
              </Row>}
              {demo.notes && (
                <Row label="Notes">
                  <p className="demo-panel-notes">{demo.notes}</p>
                </Row>
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="demo-panel-row">
      <div className="demo-panel-row-label">{label}</div>
      <div className="demo-panel-row-value">{children}</div>
    </div>
  );
}

function Pill({ label, value }: { label: string; value: string }) {
  return (
    <span className="demo-panel-pill">
      <span className="demo-panel-pill-label">{label}</span>
      {value}
    </span>
  );
}

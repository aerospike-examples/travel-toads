/** Formatting and small data-shape helpers shared across screens. */

export function slugify(s: string): string {
  return s
    .toLowerCase()
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

/**
 * Best-effort reconstruction of the backend's `locality` slug from
 * city + neighborhood. design.md documents the *shape* ("city" +
 * "neighborhood" normalized, e.g. "austin-downtown") but not the exact
 * normalization rules for multi-word neighborhoods (e.g. whether "The
 * Domain" drops "the"). This is a reasonable guess — see
 * frontend/README.md "Deviations" for how to adjust if the real backend
 * computes it differently.
 */
export function toLocalitySlug(city: string, neighborhood: string): string {
  return `${slugify(city)}-${slugify(neighborhood)}`;
}

export function formatIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function addDays(d: Date, days: number): Date {
  const copy = new Date(d);
  copy.setDate(copy.getDate() + days);
  return copy;
}

export function todayIso(): string {
  return formatIsoDate(new Date());
}

export function nightsBetween(checkInIso: string, checkOutIso: string): number {
  const a = new Date(checkInIso + "T00:00:00");
  const b = new Date(checkOutIso + "T00:00:00");
  const ms = b.getTime() - a.getTime();
  return Math.max(0, Math.round(ms / (1000 * 60 * 60 * 24)));
}

/** "2026-09-16" -> 20260916 (the backend's day-integer format). */
export function isoToYmdInt(iso: string): number {
  return Number(iso.replaceAll("-", ""));
}

/** 20260916 -> "Sep 16, 2026" */
export function formatYmdInt(n: number): string {
  const s = String(n).padStart(8, "0");
  const y = Number(s.slice(0, 4));
  const m = Number(s.slice(4, 6));
  const d = Number(s.slice(6, 8));
  const date = new Date(y, m - 1, d);
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function formatIsoDatePretty(iso: string): string {
  const date = new Date(iso + "T00:00:00");
  return date.toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function formatMoney(n: number): string {
  return `$${Math.round(n).toLocaleString()}`;
}

export function formatRating(n: number): string {
  return (n / 10).toFixed(1);
}

export function ratingLabel(n: number): string {
  if (n >= 90) return "Superb";
  if (n >= 80) return "Very good";
  if (n >= 70) return "Good";
  if (n >= 60) return "Pleasant";
  return "Fair";
}

export function stars(n: number): string {
  return "★".repeat(Math.max(0, Math.min(5, n))) + "☆".repeat(Math.max(0, 5 - n));
}


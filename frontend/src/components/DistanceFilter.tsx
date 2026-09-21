import { useState } from "react";
import type { Landmark } from "../api/types";
import "./DistanceFilter.css";

interface DistanceFilterProps {
	landmarks: Landmark[];
	landmarkId: string;
	radiusMiles: number;
	onLandmarkChange: (landmarkId: string) => void;
	onRadiusChange: (miles: number) => void;
}

type Kind = "airport" | "downtown";

const MIN_MILES = 1;
const MAX_MILES = 200;

/**
 * "Within N miles of ___" — the exact demo scenario design.md's screen-to-query mapping names
 * ("Within N miles of the airport / downtown"). One line: a free-form mile textbox, then a
 * two-option dropdown (airport / downtown). "Airport" resolves to the one airport landmark in the
 * dataset; "Downtown" resolves to Austin's city_center landmark (the primary/default city) — both
 * looked up by landmark `type` rather than hardcoding a landmark ID, so this still works if the
 * dataset's IDs ever change.
 *
 * Drives the exact same landmarkId/radiusMiles state the Destination box and its preset
 * 1/3/5/10/25/35mi chips already use — there's only one geo predicate per search (see backend
 * SearchService's "one index predicate per query") — so this is a second, more discoverable way
 * to set the same thing, not a second independent geo filter. The mile field starts empty and
 * doesn't touch the destination until a valid number is entered, so just having this filter
 * visible on the page doesn't force a destination on first landing.
 */
export function DistanceFilter({
	landmarks,
	landmarkId,
	radiusMiles,
	onLandmarkChange,
	onRadiusChange,
}: DistanceFilterProps) {
	const airport = landmarks.find((l) => l.type === "airport");
	const downtown = landmarks.find((l) => l.type === "city_center");

	const [kind, setKind] = useState<Kind>(
		landmarkId === downtown?.landmarkId ? "downtown" : "airport",
	);
	const [radiusText, setRadiusText] = useState(
		landmarkId &&
			(landmarkId === airport?.landmarkId ||
				landmarkId === downtown?.landmarkId)
			? String(radiusMiles)
			: "",
	);

	function idFor(k: Kind): string | undefined {
		return k === "airport" ? airport?.landmarkId : downtown?.landmarkId;
	}

	function apply(nextKind: Kind, text: string) {
		const parsed = Math.round(Number(text));
		if (text.trim() === "" || !Number.isFinite(parsed)) {
			// Nothing valid entered — if this control was the one driving the destination, clear it;
			// otherwise leave whatever else (e.g. the Destination box) has set alone.
			if (
				landmarkId === airport?.landmarkId ||
				landmarkId === downtown?.landmarkId
			) {
				onLandmarkChange("");
			}
			return;
		}
		const clamped = Math.min(MAX_MILES, Math.max(MIN_MILES, parsed));
		setRadiusText(String(clamped));
		const id = idFor(nextKind);
		if (id) onLandmarkChange(id);
		onRadiusChange(clamped);
	}

	return (
		<div className="distance-filter">
			<span className="distance-filter-label">Within</span>
			<input
				type="number"
				inputMode="numeric"
				className="text-input distance-radius-input"
				min={MIN_MILES}
				max={MAX_MILES}
				value={radiusText}
				onChange={(e) => setRadiusText(e.target.value)}
				onBlur={() => apply(kind, radiusText)}
				onKeyDown={(e) => {
					if (e.key === "Enter") {
						e.preventDefault();
						apply(kind, radiusText);
					}
				}}
			/>
			<span className="distance-filter-label">miles of</span>
			<select
				className="select-input"
				aria-label="Landmark"
				value={kind}
				onChange={(e) => {
					const nextKind = e.target.value as Kind;
					setKind(nextKind);
					apply(nextKind, radiusText);
				}}
			>
				<option value="airport">Airport</option>
				<option value="downtown">Downtown</option>
			</select>
		</div>
	);
}

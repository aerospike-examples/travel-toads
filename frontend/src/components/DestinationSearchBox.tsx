import {
	forwardRef,
	useEffect,
	useImperativeHandle,
	useMemo,
	useRef,
	useState,
} from "react";
import { useNavigate } from "react-router-dom";
import { suggest } from "../api/client";
import type {
	AelUnsupportedError,
	DemoBlock,
	Landmark,
	SuggestResultItem,
} from "../api/types";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { useDemoMode } from "../context/DemoModeContext";
import { LANDMARK_TYPE_ICON, LANDMARK_TYPE_LABEL } from "../lib/constants";
import { DemoPanel } from "./DemoPanel";
import { AelUnsupportedBanner } from "./AelUnsupportedBanner";
import "./DestinationSearchBox.css";

interface DestinationSearchBoxProps {
	landmarks: Landmark[];
	landmarkId: string;
	city: string;
	checkIn: string;
	checkOut: string;
	onLandmarkChange: (landmarkId: string) => void;
	onCityChange: (city: string) => void;
}

export type DestinationSubmitResult =
	| { status: "selected" } // exactly one landmark matched and was picked; a destination is now active
	| { status: "city"; city: string } // the query exactly named a whole city; that city is now the destination
	| { status: "navigated" } // a hotel was picked; navigated to its detail page instead
	| { status: "empty" } // nothing was typed at all
	| { status: "no-match"; query: string } // typed something, but it matched no landmark, city, or hotel
	| { status: "ambiguous"; query: string }; // matched more than one specific landmark, and isn't a whole-city name

export interface DestinationSearchBoxHandle {
	/**
	 * Resolves whatever's currently typed the same way pressing Enter (or clicking the search
	 * bar's Search button) does. Priority: an exact whole-city name (e.g. "Austin") wins outright —
	 * that's an unambiguous destination in its own right, even though it also happens to match
	 * every landmark in that city by substring. Otherwise, picks the matching landmark if there's
	 * exactly one, or jumps to the top matching hotel if there is one. Deliberately does NOT pick an
	 * arbitrary "first" landmark when several match and the query isn't a city name — silently
	 * defaulting to whichever one happens to be first would look like the system understood
	 * something more specific than it did. The result distinguishes every case that needs a
	 * different message.
	 */
	trySubmit: () => DestinationSubmitResult;
}

/**
 * Combined "Destination" box: replaces the old landmark <select> and the
 * separate hotel-name search box with one text field. Typing filters the
 * fixed landmark list client-side (no API — `landmarks` is already loaded)
 * and, for hotel names, debounces into GET /suggest exactly like the old
 * NameSearchBox did. Picking a landmark drives the geo search (same
 * onLandmarkChange this field always had); picking a hotel jumps straight to
 * its detail page; picking (or exactly typing) a whole city drives a
 * city-wide search via onCityChange.
 */
export const DestinationSearchBox = forwardRef<
	DestinationSearchBoxHandle,
	DestinationSearchBoxProps
>(function DestinationSearchBox(
	{
		landmarks,
		landmarkId,
		city,
		checkIn,
		checkOut,
		onLandmarkChange,
		onCityChange,
	},
	ref,
) {
	const navigate = useNavigate();
	const { demoFlag } = useDemoMode();
	const containerRef = useRef<HTMLDivElement>(null);

	// Free-typed text. Selecting a landmark or city sets this to its display
	// name so the field reflects the active destination; clearing the field
	// (or typing something else) drops back to "Anywhere" / a fresh
	// hotel-name search.
	const [query, setQuery] = useState("");
	const [open, setOpen] = useState(false);
	const [hotelResults, setHotelResults] = useState<SuggestResultItem[]>([]);
	const [demo, setDemo] = useState<DemoBlock | null>(null);
	const [aelBlocked, setAelBlocked] = useState<AelUnsupportedError | null>(
		null,
	);
	const debouncedQuery = useDebouncedValue(query, 300);

	// Keep the field's text in sync if the landmark/city is changed/cleared
	// from outside this component (there's no such caller today, but this
	// avoids a stale display if one is added later).
	useEffect(() => {
		if (landmarkId) {
			const lm = landmarks.find((l) => l.landmarkId === landmarkId);
			if (lm) setQuery(lm.name);
		} else if (city) {
			setQuery(city);
		}
	}, [landmarkId, city, landmarks]);

	const knownCities = useMemo(
		() => Array.from(new Set(landmarks.map((l) => l.city))),
		[landmarks],
	);

	const matchingLandmarks = useMemo(() => {
		const q = query.trim().toLowerCase();
		// Nothing until the user actually types — no pre-populated "browse all"
		// list on focus.
		if (q.length < 1) return [];
		return landmarks.filter(
			(lm) =>
				lm.name.toLowerCase().includes(q) || lm.city.toLowerCase().includes(q),
		);
	}, [landmarks, query]);

	// An exact (case-insensitive) match against a whole city name — "Austin", not "Austin Convention
	// Center". This is what makes typing a bare city name search that whole city instead of forcing
	// a pick among its landmarks (which also substring-match the same text).
	const matchingCity = useMemo(() => {
		const q = query.trim().toLowerCase();
		if (q.length < 1) return null;
		return knownCities.find((c) => c.toLowerCase() === q) ?? null;
	}, [knownCities, query]);

	useEffect(() => {
		if (debouncedQuery.trim().length < 2) {
			setHotelResults([]);
			setDemo(null);
			setAelBlocked(null);
			return;
		}
		const controller = new AbortController();
		suggest(debouncedQuery.trim(), demoFlag, controller.signal).then((res) => {
			if (controller.signal.aborted) return;
			if (res.ok) {
				setHotelResults(res.data.results);
				setDemo(res.data.demo);
				setAelBlocked(null);
			} else if (res.kind === "ael_unsupported") {
				setHotelResults([]);
				setDemo(null);
				setAelBlocked(res.info);
			} else {
				setHotelResults([]);
				setDemo(null);
			}
		});
		return () => controller.abort();
	}, [debouncedQuery, demoFlag]);

	useEffect(() => {
		function onClickOutside(e: MouseEvent) {
			if (
				containerRef.current &&
				!containerRef.current.contains(e.target as Node)
			) {
				setOpen(false);
			}
		}
		document.addEventListener("mousedown", onClickOutside);
		return () => document.removeEventListener("mousedown", onClickOutside);
	}, []);

	function pickLandmark(lm: Landmark) {
		onLandmarkChange(lm.landmarkId);
		setQuery(lm.name);
		setOpen(false);
	}

	function pickCity(cityName: string) {
		onCityChange(cityName);
		setQuery(cityName);
		setOpen(false);
	}

	function pickHotel(hotelId: string) {
		setOpen(false);
		setQuery("");
		navigate(
			`/hotels/${encodeURIComponent(hotelId)}?checkIn=${checkIn}&checkOut=${checkOut}`,
		);
	}

	function trySubmit(): DestinationSubmitResult {
		const trimmed = query.trim();
		if (matchingCity) {
			pickCity(matchingCity);
			return { status: "city", city: matchingCity };
		}
		if (matchingLandmarks.length === 1) {
			pickLandmark(matchingLandmarks[0]);
			return { status: "selected" };
		}
		if (matchingLandmarks.length > 1) {
			return { status: "ambiguous", query: trimmed };
		}
		if (hotelResults.length > 0) {
			pickHotel(hotelResults[0].hotelId);
			return { status: "navigated" };
		}
		return trimmed.length === 0
			? { status: "empty" }
			: { status: "no-match", query: trimmed };
	}

	useImperativeHandle(ref, () => ({ trySubmit }));

	function handleChange(value: string) {
		setQuery(value);
		setOpen(true);
		// Free typing means the user has moved on from whatever destination was
		// active — an exact re-match happens below if they retype it, otherwise
		// this clears back to an unfiltered ("Anywhere") search.
		if (landmarkId) onLandmarkChange("");
		if (city) onCityChange("");
	}

	function clear() {
		setQuery("");
		if (landmarkId) onLandmarkChange("");
		if (city) onCityChange("");
		setOpen(false);
	}

	const showHotelSection = debouncedQuery.trim().length >= 2;
	const showDropdown =
		open &&
		(matchingCity !== null || matchingLandmarks.length > 0 || showHotelSection);

	return (
		<div className="destination-search" ref={containerRef}>
			<label className="field-label" htmlFor="destination-input">
				Destination
			</label>
			<div className="destination-input-wrap">
				<input
					id="destination-input"
					className="text-input"
					type="text"
					placeholder="Enter destination or hotel name"
					value={query}
					onChange={(e) => handleChange(e.target.value)}
					onFocus={() => setOpen(true)}
					onKeyDown={(e) => {
						if (e.key === "Enter") {
							e.preventDefault();
							trySubmit();
						}
					}}
					autoComplete="off"
				/>
				{query && (
					<button
						type="button"
						className="destination-clear"
						onClick={clear}
						aria-label="Clear destination"
					>
						×
					</button>
				)}
			</div>

			{showDropdown && (
				<div className="destination-dropdown">
					{(matchingCity || matchingLandmarks.length > 0) && (
						<div className="destination-section">
							<div className="destination-section-title">Destinations</div>
							<ul className="destination-list">
								{matchingCity && (
									<li>
										<button
											type="button"
											className="destination-option destination-option-city"
											onClick={() => pickCity(matchingCity)}
										>
											<span className="destination-option-icon">📍</span>
											<span>
												{matchingCity}
												<span className="destination-option-hint">
													All neighborhoods
												</span>
											</span>
										</button>
									</li>
								)}
								{matchingLandmarks.map((lm) => (
									<li key={lm.landmarkId}>
										<button
											type="button"
											className="destination-option"
											onClick={() => pickLandmark(lm)}
										>
											<span className="destination-option-icon">
												{LANDMARK_TYPE_ICON[lm.type]}
											</span>
											<span>
												{lm.name}
												<span className="destination-option-hint">
													{lm.city} · {LANDMARK_TYPE_LABEL[lm.type]}
												</span>
											</span>
										</button>
									</li>
								))}
							</ul>
						</div>
					)}

					{showHotelSection && (
						<div className="destination-section">
							<div className="destination-section-title">Hotels</div>
							{aelBlocked ? (
								<div className="destination-section-inner">
									<AelUnsupportedBanner info={aelBlocked} />
								</div>
							) : hotelResults.length > 0 ? (
								<ul className="destination-list">
									{hotelResults.map((r) => (
										<li key={r.hotelId}>
											<button
												type="button"
												className="destination-option"
												onClick={() => pickHotel(r.hotelId)}
											>
												{r.name}
											</button>
										</li>
									))}
								</ul>
							) : (
								<div className="destination-empty">
									No hotels match "{debouncedQuery}"
								</div>
							)}
							{demo && (
								<div className="destination-section-inner">
									<DemoPanel title="Name search" demo={demo} />
								</div>
							)}
						</div>
					)}
				</div>
			)}
		</div>
	);
});

import { useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import { getLandmarks, search } from "../api/client";
import type {
	AelUnsupportedError,
	DemoBlock,
	Landmark,
	SearchResultItem,
} from "../api/types";
import { useDemoMode } from "../context/DemoModeContext";
import { SearchBar } from "../components/SearchBar";
import { LocalityChips } from "../components/LocalityChips";
import type { LocalityOption } from "../components/LocalityChips";
import { FilterSidebar, EMPTY_FILTERS } from "../components/FilterSidebar";
import type { Filters } from "../components/FilterSidebar";
import { DEFAULT_GUESTS_ROOMS } from "../components/GuestsRoomsPicker";
import type { GuestsRooms } from "../components/GuestsRoomsPicker";
import { HotelCard } from "../components/HotelCard";
import { Pagination } from "../components/Pagination";
import { DemoPanel } from "../components/DemoPanel";
import { AelUnsupportedBanner } from "../components/AelUnsupportedBanner";
import {
	DEFAULT_LOCALITY_CHIPS,
	PAGE_SIZE,
	PRICE_BRACKETS,
} from "../lib/constants";
import { addDays, formatIsoDate, toLocalitySlug } from "../lib/format";
import type { RestorableSearchState } from "../lib/searchState";
import heroImage from "../assets/hero-tt.jpg";
import "./SearchResultsPage.css";

function defaultCheckIn(): string {
	return formatIsoDate(addDays(new Date(), 1));
}
function defaultCheckOut(): string {
	return formatIsoDate(addDays(new Date(), 4));
}

export function SearchResultsPage() {
	const { setCurrentQuery } = useDemoMode();

	// A "back to results" control elsewhere (hotel detail page, booking confirmation) hands the
	// search back exactly as it was via location.state — see lib/searchState.ts's doc comment for
	// why this exists at all. Read once with lazy initializers below; this page's own state changes
	// (picking a new destination, adjusting a filter) are the sole source of truth after that.
	const location = useLocation();
	const restore = (
		location.state as { restoreSearch?: RestorableSearchState } | null
	)?.restoreSearch;

	const [landmarks, setLandmarks] = useState<Landmark[]>([]);
	const [landmarkId, setLandmarkId] = useState(() => restore?.landmarkId ?? "");
	const [radiusMiles, setRadiusMiles] = useState(
		() => restore?.radiusMiles ?? 10,
	);
	const [locality, setLocality] = useState(() => restore?.locality ?? "");
	const [city, setCity] = useState(() => restore?.city ?? "");
	const [checkIn, setCheckIn] = useState(
		() => restore?.checkIn ?? defaultCheckIn(),
	);
	const [checkOut, setCheckOut] = useState(
		() => restore?.checkOut ?? defaultCheckOut(),
	);
	const [guests, setGuests] = useState<GuestsRooms>(
		() => restore?.guests ?? DEFAULT_GUESTS_ROOMS,
	);
	const [filters, setFilters] = useState<Filters>(
		() => restore?.filters ?? EMPTY_FILTERS,
	);
	const [page, setPage] = useState(() => restore?.page ?? 1);

	const [results, setResults] = useState<SearchResultItem[]>([]);
	const [total, setTotal] = useState(0);
	const [demo, setDemo] = useState<DemoBlock | null>(null);
	const [loading, setLoading] = useState(true);
	const [aelInfo, setAelInfo] = useState<AelUnsupportedError | null>(null);
	const [errorMsg, setErrorMsg] = useState<string | null>(null);
	const [discoveredLocalities, setDiscoveredLocalities] = useState<
		LocalityOption[]
	>([]);

	useEffect(() => {
		getLandmarks().then((res) => {
			if (res.ok) setLandmarks(res.data);
		});
	}, []);

	function updateLandmark(id: string) {
		setLandmarkId(id);
		if (id) {
			setLocality("");
			setCity("");
		}
		setPage(1);
	}
	function updateRadius(mi: number) {
		setRadiusMiles(mi);
		setPage(1);
	}
	function updateLocality(slug: string) {
		setLocality(slug);
		if (slug) {
			setLandmarkId("");
			setCity("");
		}
		setPage(1);
	}
	function updateCity(cityName: string) {
		setCity(cityName);
		if (cityName) {
			setLandmarkId("");
			setLocality("");
		}
		setPage(1);
	}
	function updateCheckIn(iso: string) {
		setCheckIn(iso);
		setPage(1);
	}
	function updateCheckOut(iso: string) {
		setCheckOut(iso);
		setPage(1);
	}
	function updateFilters(f: Filters) {
		setFilters(f);
		setPage(1);
	}
	function updateGuests(g: GuestsRooms) {
		setGuests(g);
		setPage(1);
	}

	const requestKey = useMemo(
		() =>
			JSON.stringify({
				landmarkId,
				radiusMiles,
				locality,
				city,
				checkIn,
				checkOut,
				guests,
				filters,
				page,
			}),
		[
			landmarkId,
			radiusMiles,
			locality,
			city,
			checkIn,
			checkOut,
			guests,
			filters,
			page,
		],
	);

	const hasDestination = Boolean(landmarkId || locality || city);

	// Handed to each HotelCard so its Link can carry the search back via location.state — see
	// lib/searchState.ts.
	const currentSearchState = useMemo<RestorableSearchState>(
		() => ({
			landmarkId,
			radiusMiles,
			locality,
			city,
			checkIn,
			checkOut,
			guests,
			filters,
			page,
		}),
		[
			landmarkId,
			radiusMiles,
			locality,
			city,
			checkIn,
			checkOut,
			guests,
			filters,
			page,
		],
	);

	useEffect(() => {
		// Nothing searched yet — first landing on the site, or the destination was cleared. Per
		// design.md's Level-1 "Customer mode" mock ("312 hotels found" only ever follows an actual
		// search), don't show all 900 hotels by default; wait for the user to pick a destination.
		if (!hasDestination) {
			setLoading(false);
			setResults([]);
			setTotal(0);
			setDemo(null);
			setCurrentQuery(null);
			setAelInfo(null);
			setErrorMsg(null);
			return;
		}

		let cancelled = false;
		setLoading(true);
		setErrorMsg(null);

		const bracket = PRICE_BRACKETS.find(
			(b) => b.value === filters.priceBracket,
		);

		search({
			landmarkId: landmarkId || undefined,
			radiusMiles: landmarkId ? radiusMiles : undefined,
			locality: locality || undefined,
			city: city || undefined,
			checkIn,
			checkOut,
			minRating: filters.minRating > 0 ? filters.minRating : undefined,
			propertyType: filters.propertyType || undefined,
			bed: filters.bed || undefined,
			amenities: filters.amenities.length > 0 ? filters.amenities : undefined,
			minPrice: bracket?.min,
			maxPrice: bracket?.max,
			adults: guests.adults,
			children: guests.children > 0 ? guests.children : undefined,
			rooms: guests.rooms,
			page,
			pageSize: PAGE_SIZE,
			// Always requested now, regardless of demoFlag/mode: the Presenter Panel shows the live
			// query (see DemoModeContext#currentQuery) even in Customer mode, where the inline DemoPanel
			// next to the results stays hidden (that one still checks demoFlag/showDemoPanel itself).
			demo: true,
		}).then((res) => {
			if (cancelled) return;
			setLoading(false);
			if (res.ok) {
				setAelInfo(null);
				setResults(res.data.results);
				setTotal(res.data.total);
				setDemo(res.data.demo);
				setCurrentQuery(res.data.demo);
				setDiscoveredLocalities((prev) =>
					mergeLocalities(prev, res.data.results),
				);
			} else if (res.kind === "ael_unsupported") {
				setAelInfo(res.info);
				setResults([]);
				setTotal(0);
				setDemo(null);
				setCurrentQuery(null);
			} else {
				setErrorMsg(res.message);
				setResults([]);
				setTotal(0);
				setDemo(null);
				setCurrentQuery(null);
			}
		});

		return () => {
			cancelled = true;
		};
		// requestKey captures every dependency below in one comparable value.
		// eslint-disable-next-line react-hooks/exhaustive-deps
	}, [requestKey]);

	// Every known {city, neighborhood} pair — the fixed Austin defaults plus whatever real search
	// results have revealed (Round Rock/San Marcos neighborhoods, once a search surfaces them).
	const allLocalityOptions = useMemo<LocalityOption[]>(() => {
		const defaults = DEFAULT_LOCALITY_CHIPS.map((d) => ({
			...d,
			slug: toLocalitySlug(d.city, d.neighborhood),
		}));
		const merged = [...defaults];
		for (const opt of discoveredLocalities) {
			if (!merged.some((m) => m.slug === opt.slug)) merged.push(opt);
		}
		return merged;
	}, [discoveredLocalities]);

	// The city the current search has actually narrowed down to — a whole-city destination search
	// names it directly; otherwise it's the selected landmark's own city (known client-side, no
	// request needed) or, once a neighborhood chip is picked, that chip's city. Null until one of
	// those happens, e.g. on first landing.
	const knownCity = useMemo<string | null>(() => {
		if (city) {
			return city;
		}
		if (landmarkId) {
			return landmarks.find((l) => l.landmarkId === landmarkId)?.city ?? null;
		}
		if (locality) {
			return allLocalityOptions.find((o) => o.slug === locality)?.city ?? null;
		}
		return null;
	}, [city, landmarkId, landmarks, locality, allLocalityOptions]);

	// "Browse a neighborhood" only makes sense once we know which city's neighborhoods to offer —
	// showing Austin chips while the user searched near Round Rock (or hasn't searched at all) reads
	// as a wrong-city suggestion, not a shortcut.
	const localityOptions = useMemo<LocalityOption[]>(() => {
		if (!knownCity) return [];
		return allLocalityOptions.filter((o) => o.city === knownCity).slice(0, 14);
	}, [allLocalityOptions, knownCity]);

	return (
		<div className="search-page">
			<div className="container search-page-inner">
				<SearchBar
					landmarks={landmarks}
					landmarkId={landmarkId}
					locality={locality}
					city={city}
					radiusMiles={radiusMiles}
					checkIn={checkIn}
					checkOut={checkOut}
					guests={guests}
					onLandmarkChange={updateLandmark}
					onCityChange={updateCity}
					onRadiusChange={updateRadius}
					onCheckInChange={updateCheckIn}
					onCheckOutChange={updateCheckOut}
					onGuestsChange={updateGuests}
				/>

				<LocalityChips
					options={localityOptions}
					selectedSlug={locality}
					onSelect={updateLocality}
				/>

				<div
					className={`search-page-body${hasDestination ? "" : " search-page-body-no-filters"}`}
				>
					{/* Nothing to filter until there's a destination — matches the results grid's own
              "no default results" rule below. */}
					{hasDestination && (
						<FilterSidebar filters={filters} onChange={updateFilters} />
					)}

					<div className="results-area">
						{!hasDestination ? (
							<div className="results-hero-wrap">
								<img
									src={heroImage}
									alt="TravelToads — your next adventure starts here"
									className="results-hero-img"
								/>
							</div>
						) : aelInfo ? (
							<AelUnsupportedBanner info={aelInfo} />
						) : errorMsg ? (
							<div className="results-error">{errorMsg}</div>
						) : (
							<>
								<div className="results-header">
									<h2 className="results-count">
										{loading
											? "Searching…"
											: `${total.toLocaleString()} ${total === 1 ? "hotel" : "hotels"} found`}
									</h2>
								</div>

								<DemoPanel title="This search" demo={demo} />

								{!loading && results.length === 0 && (
									<div className="results-empty">
										<p>No hotels match these filters.</p>
										<p className="results-empty-hint">
											Try widening the radius or clearing a filter.
										</p>
									</div>
								)}

								<div className="results-grid">
									{results.map((hotel, index) => (
										<HotelCard
											key={hotel.hotelId}
											hotel={hotel}
											checkIn={checkIn}
											checkOut={checkOut}
											// Position within *this page*, not a global index — every page re-starts at
											// 0, so page 2 shows the same photo ordering as page 1 rather than
											// continuing to advance through the pool. See hotelPhotoUrlForIndex.
											photoIndex={index}
											searchState={currentSearchState}
										/>
									))}
								</div>

								<Pagination
									page={page}
									pageSize={PAGE_SIZE}
									total={total}
									onPageChange={setPage}
								/>
							</>
						)}
					</div>
				</div>
			</div>
		</div>
	);
}

function mergeLocalities(
	prev: LocalityOption[],
	results: SearchResultItem[],
): LocalityOption[] {
	const seen = new Set(prev.map((p) => p.slug));
	const additions: LocalityOption[] = [];
	for (const r of results) {
		const slug = toLocalitySlug(r.city, r.neighborhood);
		if (!seen.has(slug)) {
			seen.add(slug);
			additions.push({ city: r.city, neighborhood: r.neighborhood, slug });
		}
	}
	return additions.length > 0 ? [...prev, ...additions] : prev;
}

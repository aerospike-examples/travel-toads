// Types mirroring the backend contract described in the frontend brief.
// Field presence on DemoBlock varies slightly by scenario (e.g. the booking
// scenario has `aelTemplate: null` and leans on `relevantCode` instead) so
// every field here is optional/nullable and consumers must code defensively.

export type LandmarkType =
	| "city_center"
	| "airport"
	| "convention_center"
	| "stadium"
	| "transit_hub";

export interface GeoPoint {
	lon: number;
	lat: number;
}

export interface Landmark {
	landmarkId: string;
	city: string;
	name: string;
	type: LandmarkType;
	location: GeoPoint;
}

export interface DemoBlock {
	userAction?: string | null;
	aerospikeAction?: string | null;
	queryMechanism?: string | null;
	index?: string | null;
	/**
	 * The actual query text, literal values substituted in for readability (see backend
	 * SearchService#renderForDisplay) — real-time as of whatever request most recently returned it,
	 * not a static per-scenario template. Named `aelTemplate` to match the backend's JSON field
	 * exactly (was previously mis-typed as `ael` here, which meant this never actually rendered
	 * anywhere despite the backend always sending it).
	 */
	aelTemplate?: string | null;
	relevantCode?: string | null;
	queryHint?: string | null;
	notes?: string | null;
}

export interface SearchRequest {
	landmarkId?: string;
	radiusMiles?: number;
	locality?: string;
	/** Whole-city destination search (e.g. "Austin") - mutually exclusive with landmarkId/locality. */
	city?: string;
	/** ISO date ("2026-09-16") at this type's call sites — converted to the
	 * backend's YYYYMMDD integer wire format by api/client.ts's `search()`,
	 * confirmed against backend/.../web/dto/SearchRequest.java's `long checkIn`. */
	checkIn: string;
	checkOut: string;
	minRating?: number;
	propertyType?: string;
	bed?: string;
	amenities?: string[];
	page: number;
	pageSize: number;
	demo: boolean;
	// Not in design.md's original documented contract, but the backend does act on these now —
	// see backend's ClassicFilters (minPriceAtLeast/minPriceBelow, roomCountAtLeast/
	// hasRoomForOccupancy) and GuestsRoomsPicker's doc comment for exactly what's filtered.
	minPrice?: number;
	maxPrice?: number;
	adults?: number;
	children?: number;
	rooms?: number;
}

export interface SearchResultItem {
	hotelId: string;
	name: string;
	city: string;
	neighborhood: string;
	stars: number;
	rating: number;
	reviewCount: number;
	minPrice: number;
	propertyType: string;
	location: GeoPoint;
}

export interface SearchResponse {
	results: SearchResultItem[];
	total: number;
	demo: DemoBlock | null;
}

export interface SuggestResultItem {
	hotelId: string;
	name: string;
}

export interface SuggestResponse {
	results: SuggestResultItem[];
	demo: DemoBlock | null;
}

export interface BookedEntry {
	day: number;
	reservationId: string;
	breakfast: boolean;
	flex: boolean;
	/** Only present on entries created through POST /bookings (see BookingService) — the
	 * background bookings datagen seeds directly into the dataset predate any real guest, so they
	 * have neither this nor `paid`. */
	guestName?: string;
	/** Always false on a real booking today — nothing in this demo collects payment (the
	 * confirmation page's "Payment" button is deliberately inert). Absent on seeded background
	 * bookings, same as `guestName`. */
	paid?: boolean;
}

export interface Rate {
	rateId: string;
	from: number;
	to: number;
	price: number;
	available: number[];
	booked: BookedEntry[];
}

export interface Room {
	bed: string;
	sqm: number;
	view: string;
	maxOccupancy: number;
	nonSmoking: boolean;
	amenities: string[];
	rates: Rate[];
	/**
	 * Whether every requested night is covered by *some* rate segment on this room - not whether
	 * any single segment covers the whole stay. A rate period is a contiguous weekday-only or
	 * weekend-only band, so a multi-night stay routinely needs nights from more than one; see
	 * backend's HotelRecordUtil#projectRooms for the empirical finding that made this a per-room,
	 * per-night check instead of a per-rate-row one (the earlier per-row version left almost no
	 * realistic stay bookable).
	 */
	available: boolean;
	/** Sum of each requested night's own segment price - null when `available` is false. */
	totalPrice: number | null;
}

export interface HotelDetail {
	hotelId: string;
	name: string;
	city: string;
	neighborhood: string;
	propertyType: string;
	stars: number;
	rating: number;
	reviewCount: number;
	amenities: string[];
	location: GeoPoint;
	rooms: Record<string, Room>;
	demo: DemoBlock | null;
}

export interface BookingRequest {
	hotelId: string;
	roomId: string;
	/** ISO date at this type's call sites — see SearchRequest's checkIn comment. */
	checkIn: string;
	checkOut: string;
	guestName: string;
	breakfast: boolean;
	flex: boolean;
	/** backend/.../web/dto/BookingRequest.java has this field (default false) —
	 * frontend/README.md's "no demo field" note was checked against the wrong
	 * DTO. Send the current UI mode so the confirmation screen's demo panel
	 * actually populates in Demo/Engineering mode. */
	demo: boolean;
}

export interface RateSegment {
	/** Position in the room's `rates` *list* — that bin is a CDT list, not a map, so this (not
	 * rateId alone) is what actually tells you which entry to expand in a tool like Voyager, which
	 * displays lists by numeric position. */
	index: number;
	rateId: string;
}

export interface BookingResponse {
	reservationId: string;
	status: "confirmed";
	/** Sum of each booked night's own rate-segment price — see Room#totalPrice. */
	totalPrice: number;
	/** Every rate segment this reservation actually landed on — almost always one element, but a
	 * stay spanning a rate-period boundary (see BookingService) touches two. */
	rateSegments: RateSegment[];
	demo: DemoBlock | null;
}

export interface AelUnsupportedError {
	error: "AEL_UNSUPPORTED";
	message: string;
	serverVersion: string;
	requiredVersion: string;
	// true: the cluster's version genuinely doesn't meet requiredVersion yet. false: the version
	// gate passed but the server rejected this specific query anyway (a feature-level gap, e.g.
	// /suggest's lowercase() on every build tested so far) - a version bump won't fix that case, so
	// the banner shouldn't show the version comparison as if it would. Optional only for backward
	// compatibility with any cached/older response shape; treat a missing value as true (the
	// original, version-gate-only behavior) rather than assuming the newer, more specific case.
	versionGateFailed?: boolean;
}

export interface RoomNotAvailableError {
	error: "ROOM_NOT_AVAILABLE";
	message: string;
}

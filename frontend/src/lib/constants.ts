import type { LandmarkType } from "../api/types";

export const LANDMARK_TYPE_LABEL: Record<LandmarkType, string> = {
	city_center: "City center",
	airport: "Airport",
	convention_center: "Convention center",
	stadium: "Stadium",
	transit_hub: "Transit hub",
};

export const LANDMARK_TYPE_ICON: Record<LandmarkType, string> = {
	city_center: "\u{1F3D9}️", // cityscape
	airport: "✈️", // airplane
	convention_center: "\u{1F3E2}", // office building
	stadium: "\u{1F3DF}️", // stadium
	transit_hub: "\u{1F686}", // train
};

// Fallback neighborhood chips shown before any search has produced real
// results, per design.md's confirmed Austin list. Round Rock / San Marcos
// neighborhood names are called out in design.md as "not sanity-checked" —
// we don't invent them here; those chips populate dynamically instead once
// real search results reveal them (see SearchResultsPage).
export const DEFAULT_LOCALITY_CHIPS: { city: string; neighborhood: string }[] =
	[
		{ city: "Austin", neighborhood: "Downtown" },
		{ city: "Austin", neighborhood: "South Congress" },
		{ city: "Austin", neighborhood: "East Austin" },
		{ city: "Austin", neighborhood: "Zilker" },
		{ city: "Austin", neighborhood: "Rainey Street" },
		{ city: "Austin", neighborhood: "The Domain" },
	];

// Confirmed against datagen/src/main/java/.../HotelGenerator.java's actual
// PRICE_BANDS map and NON_DORM_BEDS/dorm list during backend/frontend
// integration (the enum values frontend/README.md flagged as guessed were
// close but not exact — "bnb" should have been "bed_and_breakfast", and
// "single" isn't generated at all while "sofa_bed"/"bunk" are).
export const PROPERTY_TYPES: { value: string; label: string }[] = [
	{ value: "hotel", label: "Hotel" },
	{ value: "hostel", label: "Hostel" },
	{ value: "motel", label: "Motel" },
	{ value: "apartment", label: "Apartment" },
	{ value: "resort", label: "Resort" },
	{ value: "bed_and_breakfast", label: "B&B" },
];

export const BED_TYPES: { value: string; label: string }[] = [
	{ value: "king", label: "King" },
	{ value: "queen", label: "Queen" },
	{ value: "double", label: "Double" },
	{ value: "twin", label: "Twin" },
	{ value: "sofa_bed", label: "Sofa bed" },
	{ value: "bunk", label: "Bunk (dorm)" },
];

/** Single source of truth for displaying a raw `bed` value everywhere a room shows up (detail
 * page, booking modal, confirmation page) — derived from BED_TYPES rather than re-typed at each
 * call site, so there's one place to fix if a bed type's label needs to change. Every one of those
 * call sites used to just capitalize() the raw value, which mangled "sofa_bed" into "Sofa_bed"
 * instead of "Sofa bed" (see VIEW_LABEL's comment for the same underlying bug on room views). */
export const BED_TYPE_LABEL: Record<string, string> = Object.fromEntries(
	BED_TYPES.map((b) => [b.value, b.label]),
);

/**
 * Room view labels — matches datagen's VIEWS list (HotelGenerator.java) exactly. Every display
 * site (detail page, booking modal, confirmation page) appends the word "view" itself, so these
 * are just the readable noun. Confirmed as a real, user-reported bug: with no label map at all,
 * `room.view` rendered its raw value straight into the page — e.g. "hill_country view" instead of
 * "Hill Country view".
 */
export const VIEW_LABEL: Record<string, string> = {
	pool: "Pool",
	street: "Street",
	garden: "Garden",
	city: "City",
	courtyard: "Courtyard",
	lake: "Lake",
	hill_country: "Hill Country",
};

// Hotel-level amenity chips — taken directly from design.md's sample
// record. `wifi` / `parking` deliberately excluded per design.md ("a chip
// matching every record is a dead chip on camera"). This list must stay in
// sync with datagen's HOTEL_AMENITIES (HotelGenerator.java) — it doubles as
// both the filter chip list *and* the display-label lookup (HotelDetailPage's
// AMENITY_LABEL is derived from this array), so any hotel-level amenity value
// missing here previously rendered as its raw snake_case value on the detail
// page (e.g. "pet_friendly", "business_center") instead of a readable label.
export const AMENITY_OPTIONS: { value: string; label: string }[] = [
	{ value: "breakfast_included", label: "Breakfast included" },
	{ value: "free_cancellation", label: "Free cancellation" },
	{ value: "pool", label: "Pool" },
	{ value: "fitness", label: "Fitness center" },
	{ value: "restaurant", label: "Restaurant" },
	{ value: "room_service", label: "Room service" },
	{ value: "airport_shuttle", label: "Airport shuttle" },
	{ value: "spa", label: "Spa" },
	{ value: "business_center", label: "Business center" },
	{ value: "pet_friendly", label: "Pet friendly" },
	{ value: "laundry", label: "Laundry service" },
	{ value: "bar", label: "Bar" },
	{ value: "has_view", label: "Has view" },
];

// Room-level amenity display labels (not filterable, just used to render
// the property detail page's room amenity badges nicely).
export const ROOM_AMENITY_LABEL: Record<string, string> = {
	air_conditioning: "Air conditioning",
	private_bathroom: "Private bathroom",
	balcony: "Balcony",
	tv: "TV",
	coffee_maker: "Coffee maker",
	refrigerator: "Refrigerator",
	bath: "Bathtub",
	shared_bathroom: "Shared bathroom",
	kitchenette: "Kitchenette",
	desk: "Desk",
	safe: "In-room safe",
};

// Guest review score (0-100 in the data model, shown divided by 10 - e.g. 86 -> "8.6") - a
// separate scale from each hotel's 1-5 "stars" classification. See FilterSidebar.tsx's comment
// on this same group and design.md's data-model table ("next to stars: 4 this reads like a
// percentage - document it... so nobody 'fixes' it").
export const RATING_OPTIONS: { value: number; label: string }[] = [
	{ value: 0, label: "Any" },
	{ value: 70, label: "7.0+" },
	{ value: 80, label: "8.0+" },
	{ value: 90, label: "9.0+" },
];

// Speculative price brackets — see SearchRequest.minPrice/maxPrice comment.
export const PRICE_BRACKETS: {
	value: string;
	label: string;
	min?: number;
	max?: number;
}[] = [
	{ value: "any", label: "Any price" },
	{ value: "0-100", label: "Under $100", max: 100 },
	{ value: "100-200", label: "$100 – $200", min: 100, max: 200 },
	{ value: "200-300", label: "$200 – $300", min: 200, max: 300 },
	{ value: "300", label: "$300+", min: 300 },
];

export const PAGE_SIZE = 20;

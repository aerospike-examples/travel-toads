import bedroomSkyline from "../assets/hotel-photos/bedroom-skyline.jpg";
import bedroomSuite from "../assets/hotel-photos/bedroom-suite.jpg";
import buildingSunset from "../assets/hotel-photos/building-sunset.jpg";
import courtyard from "../assets/hotel-photos/courtyard.jpg";
import exterior from "../assets/hotel-photos/exterior.jpg";
import facade from "../assets/hotel-photos/facade.jpg";
import glassBuilding from "../assets/hotel-photos/glass-building.jpg";
import guestRoom from "../assets/hotel-photos/guest-room.jpg";
import loungeFireside from "../assets/hotel-photos/lounge-fireside.jpg";
import lounge from "../assets/hotel-photos/lounge.jpg";
import poolside from "../assets/hotel-photos/poolside.jpg";
import rooftopPool from "../assets/hotel-photos/rooftop-pool.jpg";

const HOTEL_PHOTOS = [
	exterior,
	rooftopPool,
	facade,
	lounge,
	guestRoom,
	courtyard,
	glassBuilding,
	bedroomSkyline,
	loungeFireside,
	buildingSunset,
	poolside,
	bedroomSuite,
];

/**
 * A "main image" for every listing — design.md left property images as an open question
 * ("permissively licensed stock, generated placeholders, or CSS-only cards"; the search cards and
 * detail page went with CSS-only art in the meantime). These are real stock photos, cut from
 * mosaics dropped at the project root (austin-1.jpeg, austin-4.png) into individual images.
 *
 * Several other mosaics were tried and rejected outright for resolution:
 *   - austin-2.jpeg: 51 tiles, roughly 60x80-150x170px native — removed entirely (see git history)
 *     after they visibly upscaled into blurry photos on the detail page's 880px-wide hero.
 *   - austin-3.png: a uniform 6-column grid over a 1672x941 canvas, so every tile tops out around
 *     277x164px — never added, well under the ~330x208px floor of the smallest photo already kept
 *     (courtyard.jpg).
 *   - austin-5.png: a much denser grid (72 tiles over 1536x1024), tiles around 170-220x100-150px —
 *     never added, same reason.
 *   - austin-4.png: same 1672x941 canvas and mostly the same too-small tiles as austin-3, EXCEPT
 *     its bottom row of 6 (roughly 269x206px each) comes close enough to courtyard.jpg's own floor
 *     to hold up at both card and hero size — those 6 are the ones imported above. The other 24
 *     tiles in this mosaic were left out for the same resolution reason as austin-3/5.
 *
 * There's no per-hotel photography, so which photo lands on which hotel is arbitrary. Two ways to
 * pick one, for two different needs:
 */

/**
 * For a page of search results: assigns photos by *position in that page* (0, 1, 2, ...), not by
 * hotel id — with only {@link HOTEL_PHOTOS}.length distinct photos, hashing every hotel's id
 * independently means any given page of results (PAGE_SIZE, currently 20) will very likely repeat
 * a photo across several cards purely by hash collision, sometimes right next to each other. Position
 * order across the same {@link HOTEL_PHOTOS}.length results is guaranteed collision-free instead —
 * every card on a page shows a different photo from its neighbors, up to that many results; a page
 * longer than that necessarily starts repeating (there just aren't more photos to show), but never
 * in a way that clusters — result N and result N + HOTEL_PHOTOS.length always differ from every
 * other repeat by that same fixed spacing.
 */
export function hotelPhotoUrlForIndex(index: number): string {
	return HOTEL_PHOTOS[
		((index % HOTEL_PHOTOS.length) + HOTEL_PHOTOS.length) % HOTEL_PHOTOS.length
	];
}

/**
 * For anywhere a hotel's photo is needed *without* a result list position to draw on — most
 * notably the detail page on a direct/bookmarked visit, where HotelCard didn't just hand it a
 * `photoUrl` via location.state (see HotelDetailPage). Hashes the hotel's id so it's still stable
 * (the same hotel shows the same photo on repeat direct visits) rather than random.
 */
export function hotelPhotoUrl(hotelId: string): string {
	let hash = 0;
	for (let i = 0; i < hotelId.length; i++) {
		hash = (hash * 31 + hotelId.charCodeAt(i)) | 0;
	}
	const index = Math.abs(hash) % HOTEL_PHOTOS.length;
	return HOTEL_PHOTOS[index];
}

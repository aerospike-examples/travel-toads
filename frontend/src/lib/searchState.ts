import type { Filters } from "../components/FilterSidebar";
import type { GuestsRooms } from "../components/GuestsRoomsPicker";

/**
 * Everything SearchResultsPage needs to reproduce a search exactly as the user left it —
 * destination, dates, guests, filters, and page. SearchResultsPage's search state is local
 * component state, not synced to the URL, so it resets to defaults on every fresh mount; without
 * this, navigating away (to a hotel's detail page, then to the booking confirmation page) and back
 * always landed on a blank search instead of the results the user was actually looking at.
 *
 * Threaded through React Router's `location.state` as `{ restoreSearch }`: SearchResultsPage
 * attaches the current search here on every HotelCard's Link, HotelDetailPage carries it forward
 * (both on its own "back to results" link and into the booking confirmation navigation), and
 * BookingConfirmationPage's "Back to results" does the same — so every one of those controls
 * actually returns to where the user was, not to "/" with nothing set.
 */
export interface RestorableSearchState {
	landmarkId: string;
	radiusMiles: number;
	locality: string;
	city: string;
	checkIn: string;
	checkOut: string;
	guests: GuestsRooms;
	filters: Filters;
	page: number;
}

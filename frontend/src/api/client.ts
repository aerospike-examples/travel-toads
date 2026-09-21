import type {
	AelUnsupportedError,
	BookingRequest,
	BookingResponse,
	HotelDetail,
	Landmark,
	RoomNotAvailableError,
	SearchRequest,
	SearchResponse,
	SuggestResponse,
} from "./types";
import { isoToYmdInt } from "../lib/format";

// Same-origin `/api/*` in production (nginx proxies to the backend
// container); in dev, Vite's dev server proxy forwards `/api` to the
// backend — see vite.config.ts.
const API_BASE = "/api";

/**
 * Discriminated result wrapper so every screen can render the
 * AEL_UNSUPPORTED state (and other errors) as first-class UI rather than
 * a thrown exception / blank page.
 */
export type ApiResult<T> =
	| { ok: true; data: T }
	| { ok: false; kind: "ael_unsupported"; info: AelUnsupportedError }
	| { ok: false; kind: "conflict"; message: string }
	| { ok: false; kind: "error"; message: string };

async function request<T>(
	path: string,
	init?: RequestInit,
): Promise<ApiResult<T>> {
	let res: Response;
	try {
		res = await fetch(`${API_BASE}${path}`, {
			headers: { "Content-Type": "application/json" },
			...init,
		});
	} catch {
		return {
			ok: false,
			kind: "error",
			message:
				"Couldn't reach the demo backend. Is it running? Check the network tab or docker-compose logs.",
		};
	}

	if (res.status === 503) {
		const info = (await safeJson(res)) as AelUnsupportedError | null;
		return {
			ok: false,
			kind: "ael_unsupported",
			info: info ?? {
				error: "AEL_UNSUPPORTED",
				message:
					"This screen relies on Aerospike's AEL query language, which this demo cluster doesn't support yet.",
				serverVersion: "unknown",
				requiredVersion: "8.1.3",
			},
		};
	}

	if (res.status === 409) {
		const info = (await safeJson(res)) as RoomNotAvailableError | null;
		return {
			ok: false,
			kind: "conflict",
			message:
				info?.message ?? "Someone else just booked that night — try again.",
		};
	}

	if (!res.ok) {
		const info = (await safeJson(res)) as { message?: string } | null;
		return {
			ok: false,
			kind: "error",
			message: info?.message ?? `Request failed (${res.status}).`,
		};
	}

	const data = (await safeJson(res)) as T;
	return { ok: true, data };
}

async function safeJson(res: Response): Promise<unknown> {
	try {
		return await res.json();
	} catch {
		return null;
	}
}

export function getLandmarks(): Promise<ApiResult<Landmark[]>> {
	return request<Landmark[]>("/landmarks");
}

export function search(
	body: SearchRequest,
): Promise<ApiResult<SearchResponse>> {
	// Confirmed against backend/.../web/dto/SearchRequest.java: checkIn/checkOut
	// are `long` (YYYYMMDD), not ISO strings — convert only at the wire
	// boundary so every internal call site can keep using ISO dates (date
	// inputs, formatting, nightsBetween, coverageCount all want ISO).
	const wire = {
		...body,
		checkIn: isoToYmdInt(body.checkIn),
		checkOut: isoToYmdInt(body.checkOut),
	};
	return request<SearchResponse>("/search", {
		method: "POST",
		body: JSON.stringify(wire),
	});
}

export function suggest(
	q: string,
	demo: boolean,
	signal?: AbortSignal,
): Promise<ApiResult<SuggestResponse>> {
	const params = new URLSearchParams({ q, demo: String(demo) });
	return request<SuggestResponse>(`/suggest?${params.toString()}`, { signal });
}

export function getHotel(
	hotelId: string,
	checkIn: string,
	checkOut: string,
	demo: boolean,
): Promise<ApiResult<HotelDetail>> {
	// HttpApi.java does `Long.parseLong(ctx.queryParam("checkIn"))` — must be
	// the YYYYMMDD integer as a string, not an ISO date.
	const params = new URLSearchParams({
		checkIn: String(isoToYmdInt(checkIn)),
		checkOut: String(isoToYmdInt(checkOut)),
		demo: String(demo),
	});
	return request<HotelDetail>(
		`/hotels/${encodeURIComponent(hotelId)}?${params.toString()}`,
	);
}

export function createBooking(
	body: BookingRequest,
): Promise<ApiResult<BookingResponse>> {
	// See search()'s comment — same YYYYMMDD wire format, confirmed against
	// backend/.../web/dto/BookingRequest.java.
	const wire = {
		...body,
		checkIn: isoToYmdInt(body.checkIn),
		checkOut: isoToYmdInt(body.checkOut),
	};
	return request<BookingResponse>("/bookings", {
		method: "POST",
		body: JSON.stringify(wire),
	});
}

export async function resetDemo(): Promise<ApiResult<{ status: string }>> {
	return request<{ status: string }>("/admin/reset", { method: "POST" });
}

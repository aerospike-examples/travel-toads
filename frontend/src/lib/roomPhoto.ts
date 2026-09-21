import doubleBed from "../assets/room-photos/double.jpg";
import king from "../assets/room-photos/king.jpg";
import queen from "../assets/room-photos/queen.jpg";
import sofaBed from "../assets/room-photos/sofa-bed.jpg";
import twin from "../assets/room-photos/twin.jpg";

/**
 * A room photo per bed type, cut from a mosaic dropped at the project root (tt-rooms.png) into
 * individual images — one per member of datagen's NON_DORM_BEDS list (HotelGenerator.java): king,
 * queen, twin, double, sofa_bed. Unlike lib/hotelPhoto.ts (one arbitrary "main image" per hotel,
 * since there's no per-hotel photography), this is a genuine, deliberate photo-per-bed-type
 * mapping — every "king" room across every hotel shows this same king-bed photo, on purpose,
 * because they really are meant to represent the same room type.
 *
 * "bunk" (the dorm-only bed type — hostels only, see HotelGenerator's `dorm` flag) has no source
 * photo; it falls back to the twin photo below as the closest visual analog (both are
 * single-occupant beds), rather than showing nothing or a random unrelated room.
 */
const ROOM_PHOTO_BY_BED: Record<string, string> = {
  king,
  queen,
  double: doubleBed,
  twin,
  sofa_bed: sofaBed,
  bunk: twin,
};

export function roomPhotoUrl(bed: string): string | undefined {
  return ROOM_PHOTO_BY_BED[bed];
}

import { Link } from "react-router-dom";
import type { SearchResultItem } from "../api/types";
import { formatMoney, formatRating, ratingLabel, stars } from "../lib/format";
import { PROPERTY_TYPES } from "../lib/constants";
import { hotelPhotoUrlForIndex } from "../lib/hotelPhoto";
import type { RestorableSearchState } from "../lib/searchState";
import "./HotelCard.css";

const PROPERTY_TYPE_LABEL: Record<string, string> = Object.fromEntries(
  PROPERTY_TYPES.map((p) => [p.value, p.label]),
);

interface HotelCardProps {
  hotel: SearchResultItem;
  checkIn: string;
  checkOut: string;
  /** This card's position within the current page of results (0-based) — see
   * lib/hotelPhoto.ts#hotelPhotoUrlForIndex for why the photo is chosen by position rather than by
   * hotel id: it's what keeps every card on a page showing a different photo from its neighbors. */
  photoIndex: number;
  /** Carried via location.state so the hotel detail page (and, from there, "back to results"
   * controls further down the flow) can return here with the exact same search — see
   * lib/searchState.ts. */
  searchState: RestorableSearchState;
}

export function HotelCard({ hotel, checkIn, checkOut, photoIndex, searchState }: HotelCardProps) {
  const photoUrl = hotelPhotoUrlForIndex(photoIndex);
  return (
    <Link
      to={`/hotels/${encodeURIComponent(hotel.hotelId)}?checkIn=${checkIn}&checkOut=${checkOut}`}
      state={{ restoreSearch: searchState, photoUrl }}
      className="hotel-card"
    >
      <div className="hotel-card-art">
        <img src={photoUrl} alt={hotel.name} className="hotel-card-photo" />
        <span className="hotel-card-art-type">
          {PROPERTY_TYPE_LABEL[hotel.propertyType] ?? hotel.propertyType}
        </span>
      </div>
      <div className="hotel-card-body">
        <div className="hotel-card-top">
          <h3 className="hotel-card-name">{hotel.name}</h3>
          <div className="hotel-card-stars" aria-label={`${hotel.stars} stars`}>
            {stars(hotel.stars)}
          </div>
        </div>
        <div className="hotel-card-location">
          {hotel.neighborhood}, {hotel.city}
        </div>

        <div className="hotel-card-bottom">
          <div className="hotel-card-rating">
            <span className="hotel-card-rating-badge">{formatRating(hotel.rating)}</span>
            <span className="hotel-card-rating-text">
              {ratingLabel(hotel.rating)} · {hotel.reviewCount.toLocaleString()} reviews
            </span>
          </div>
          <div className="hotel-card-price">
            <span className="hotel-card-price-value">{formatMoney(hotel.minPrice)}</span>
            <span className="hotel-card-price-suffix">/ night</span>
          </div>
        </div>
      </div>
    </Link>
  );
}

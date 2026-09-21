import { Link } from "react-router-dom";
import travelToadsMark from "../assets/travel-toads-mark.png";
import bannerAd from "../assets/banner-tt.png";
import { HeaderUtilityBar } from "./HeaderUtilityBar";
import "./Header.css";

/**
 * Just the site's own brand chrome now — the Customer/Demo/Engineering
 * switch and Reset Demo moved to PresenterPanel, since those are controls
 * for whoever is running the demo, not part of the "customer" site itself.
 * The promo banner on the right is a real travel-site touch (and fills what
 * was otherwise a wide strip of empty header) now that the header's own
 * right-side controls have moved out. The utility bar above it (currency,
 * support, sign-in) is the same kind of real-travel-site dressing, right
 * down to being non-functional chrome — see HeaderUtilityBar's own comment.
 */
export function Header() {
  return (
    <header className="site-header">
      <HeaderUtilityBar />
      <div className="container site-header-inner">
        <Link to="/" className="brand">
          <img src={travelToadsMark} alt="" className="brand-mark" />
          <span className="brand-text">
            <span className="brand-name">TravelToads</span>
            <span className="brand-tagline">Find the perfect lilypad.</span>
          </span>
        </Link>

        <img src={bannerAd} alt="Book now and get $500 off your reservation" className="header-banner-ad" />
      </div>
    </header>
  );
}

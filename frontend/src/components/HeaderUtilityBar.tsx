import { useState } from "react";
import "./HeaderUtilityBar.css";

const CURRENCIES = ["USD", "EUR", "GBP", "CAD", "AUD"] as const;

/**
 * Thin utility strip above the main brand row — the real-travel-site chrome (currency, support,
 * sign-in) that sits above the logo on sites like this one's real-world counterparts. Purely
 * decorative for this demo: currency is local UI state only (no price conversion anywhere - prices
 * displayed elsewhere are always the raw USD-ish numbers from the data model), and
 * Support/Chat with Agent/Sign in have no destination yet - inert buttons, not dead links.
 */
export function HeaderUtilityBar() {
	const [currency, setCurrency] = useState<(typeof CURRENCIES)[number]>("USD");

	return (
		<div className="header-utility-bar">
			<div className="container header-utility-bar-inner">
				<label className="utility-currency">
					<span className="sr-only">Currency</span>
					<select
						value={currency}
						onChange={(e) =>
							setCurrency(e.target.value as (typeof CURRENCIES)[number])
						}
					>
						{CURRENCIES.map((c) => (
							<option key={c} value={c}>
								{c}
							</option>
						))}
					</select>
				</label>

				<button type="button" className="utility-link">
					Support
				</button>
				<button type="button" className="utility-link">
					💬 Chat with Agent
				</button>
				<button type="button" className="utility-link utility-signin">
					Sign in
				</button>
			</div>
		</div>
	);
}

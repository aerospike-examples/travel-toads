import type { ReactNode } from "react";
import {
	AMENITY_OPTIONS,
	BED_TYPES,
	PRICE_BRACKETS,
	PROPERTY_TYPES,
	RATING_OPTIONS,
} from "../lib/constants";
import "./FilterSidebar.css";

export interface Filters {
	minRating: number;
	propertyType: string; // "" = any
	bed: string; // "" = any
	amenities: string[];
	priceBracket: string; // key into PRICE_BRACKETS, "any" = no filter
}

export const EMPTY_FILTERS: Filters = {
	minRating: 0,
	propertyType: "",
	bed: "",
	amenities: [],
	priceBracket: "any",
};

interface FilterSidebarProps {
	filters: Filters;
	onChange: (filters: Filters) => void;
}

export function FilterSidebar({ filters, onChange }: FilterSidebarProps) {
	const hasActiveFilters =
		filters.minRating > 0 ||
		filters.propertyType !== "" ||
		filters.bed !== "" ||
		filters.amenities.length > 0 ||
		filters.priceBracket !== "any";

	function toggleAmenity(value: string) {
		const has = filters.amenities.includes(value);
		onChange({
			...filters,
			amenities: has
				? filters.amenities.filter((a) => a !== value)
				: [...filters.amenities, value],
		});
	}

	return (
		<aside className="filter-sidebar card-surface">
			<div className="filter-sidebar-header">
				<h3>Filters</h3>
				{hasActiveFilters && (
					<button
						type="button"
						className="btn btn-ghost btn-sm"
						onClick={() => onChange(EMPTY_FILTERS)}
					>
						Clear all
					</button>
				)}
			</div>

			{/* Guest review score out of 10 (e.g. 8.6), not the 1-5 star classification shown
          separately on each card — design.md calls this out by name as an easy thing to
          misread ("next to stars: 4 this reads like a percentage"). Labeled explicitly here
          so "7.0+/8.0+/9.0+" doesn't read as an impossible star count. */}
			<FilterGroup title="Guest rating (out of 10)">
				<div className="filter-chip-row">
					{RATING_OPTIONS.map((opt) => (
						<button
							key={opt.value}
							type="button"
							className={`chip${filters.minRating === opt.value ? " active" : ""}`}
							onClick={() => onChange({ ...filters, minRating: opt.value })}
						>
							{opt.label}
						</button>
					))}
				</div>
			</FilterGroup>

			<FilterGroup title="Price per night">
				<div className="filter-chip-row filter-chip-row-column">
					{PRICE_BRACKETS.map((opt) => (
						<button
							key={opt.value}
							type="button"
							className={`chip${filters.priceBracket === opt.value ? " active" : ""}`}
							onClick={() => onChange({ ...filters, priceBracket: opt.value })}
						>
							{opt.label}
						</button>
					))}
				</div>
			</FilterGroup>

			<FilterGroup title="Property type">
				<div className="filter-chip-row filter-chip-row-column">
					<button
						type="button"
						className={`chip${filters.propertyType === "" ? " active" : ""}`}
						onClick={() => onChange({ ...filters, propertyType: "" })}
					>
						Any type
					</button>
					{PROPERTY_TYPES.map((opt) => (
						<button
							key={opt.value}
							type="button"
							className={`chip${filters.propertyType === opt.value ? " active" : ""}`}
							onClick={() => onChange({ ...filters, propertyType: opt.value })}
						>
							{opt.label}
						</button>
					))}
				</div>
			</FilterGroup>

			<FilterGroup title="Bed type">
				<div className="filter-chip-row filter-chip-row-column">
					<button
						type="button"
						className={`chip${filters.bed === "" ? " active" : ""}`}
						onClick={() => onChange({ ...filters, bed: "" })}
					>
						Any bed
					</button>
					{BED_TYPES.map((opt) => (
						<button
							key={opt.value}
							type="button"
							className={`chip${filters.bed === opt.value ? " active" : ""}`}
							onClick={() => onChange({ ...filters, bed: opt.value })}
						>
							{opt.label}
						</button>
					))}
				</div>
			</FilterGroup>

			<FilterGroup title="Amenities">
				<div className="filter-checkbox-list">
					{AMENITY_OPTIONS.map((opt) => (
						<label key={opt.value} className="filter-checkbox">
							<input
								type="checkbox"
								checked={filters.amenities.includes(opt.value)}
								onChange={() => toggleAmenity(opt.value)}
							/>
							{opt.label}
						</label>
					))}
				</div>
			</FilterGroup>
		</aside>
	);
}

function FilterGroup({
	title,
	children,
}: {
	title: string;
	children: ReactNode;
}) {
	return (
		<div className="filter-group">
			<h4 className="filter-group-title">{title}</h4>
			{children}
		</div>
	);
}

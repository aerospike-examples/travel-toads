import "./LocalityChips.css";

export interface LocalityOption {
  city: string;
  neighborhood: string;
  slug: string;
}

interface LocalityChipsProps {
  options: LocalityOption[];
  selectedSlug: string;
  onSelect: (slug: string) => void;
}

export function LocalityChips({ options, selectedSlug, onSelect }: LocalityChipsProps) {
  if (options.length === 0) return null;

  return (
    <div className="locality-chips">
      <span className="field-label locality-chips-label">Or browse a neighborhood</span>
      <div className="locality-chips-row">
        {options.map((opt) => (
          <button
            key={opt.slug}
            type="button"
            className={`chip${selectedSlug === opt.slug ? " active" : ""}`}
            onClick={() => onSelect(selectedSlug === opt.slug ? "" : opt.slug)}
          >
            {opt.neighborhood}
            <span className="locality-chip-city"> · {opt.city}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

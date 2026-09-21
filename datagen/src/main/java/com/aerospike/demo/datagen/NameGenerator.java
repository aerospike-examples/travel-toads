package com.aerospike.demo.datagen;

import java.util.List;
import java.util.Random;

/**
 * Fictional hotel names for real coordinates — see docs/design.md's "Real city, fictional
 * properties" section on why publishing invented names against real geography, rather than real
 * hotel names, is a legal requirement here and not a style choice.
 *
 * <p>A slice of names deliberately carries non-ASCII characters (precomposed accents), seeded per
 * the doc's "worth seeding a few non-ASCII property names" note for the Unicode string-search
 * beat.
 */
final class NameGenerator {

    private static final List<String> ADJECTIVES = List.of(
            "Lakeside", "Riverside", "Hill Country", "Cedar", "Bluebonnet", "Founders", "Heritage",
            "Landmark", "Crossroads", "Meridian", "Prairie", "Trailhead", "Ironwood", "Sagebrush",
            "Copper", "Longhorn", "Live Oak", "Stone Creek", "Twin Springs", "Sunset");

    private static final List<String> ADJECTIVES_ACCENTED = List.of(
            "Río Bravo", "El Camino", "Posada del Sol", "Café del Río", "Jardín", "Cañada");

    private static final List<String> NOUNS_BY_TYPE = List.of(
            "Grand", "Inn", "Suites", "Lodge", "Hotel", "Plaza", "House", "Court", "Gardens", "Retreat");

    private static final List<String> HOSTEL_NOUNS = List.of("Hostel", "Bunkhouse", "Backpackers");
    private static final List<String> APARTMENT_NOUNS = List.of("Residences", "Flats", "Lofts", "Suites");
    private static final List<String> MOTEL_NOUNS = List.of("Motor Inn", "Motel", "Lodge");
    private static final List<String> RESORT_NOUNS = List.of("Resort & Spa", "Resort", "Springs Resort");
    private static final List<String> BNB_NOUNS = List.of("Bed & Breakfast", "Guesthouse", "Cottage");

    private NameGenerator() {
    }

    static String forProperty(String propertyType, Random rng) {
        List<String> nouns = switch (propertyType) {
            case "hostel" -> HOSTEL_NOUNS;
            case "apartment" -> APARTMENT_NOUNS;
            case "motel" -> MOTEL_NOUNS;
            case "resort" -> RESORT_NOUNS;
            case "bed_and_breakfast" -> BNB_NOUNS;
            default -> NOUNS_BY_TYPE;
        };

        // ~4% of names draw from the accented word bank, to seed Unicode canonical-equivalence
        // coverage without making every third name look conspicuously foreign.
        String adjective = rng.nextInt(100) < 4
                ? pick(ADJECTIVES_ACCENTED, rng)
                : pick(ADJECTIVES, rng);
        String noun = pick(nouns, rng);
        return adjective + " " + noun;
    }

    private static String pick(List<String> options, Random rng) {
        return options.get(rng.nextInt(options.size()));
    }
}

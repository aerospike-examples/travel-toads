package com.aerospike.demo.datagen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-city generation parameters. Values come from docs/design.md's "Metro layout" and
 * "Demo dataset" tables. Neighborhood names for Round Rock and San Marcos are real places but
 * were not independently field-verified — the design doc explicitly flags them as needing a
 * sanity check before this is used for anything public.
 */
record CityConfig(
        String name,
        double centerLon,
        double centerLat,
        double hotelShare,
        List<String> neighborhoods,
        Map<String, Double> propertyTypeWeights) {

    /**
     * Austin: urban mix (hotels, hostels, apartments), 70% of hotel volume, 6 real neighborhoods
     * per the design doc's explicit list.
     */
    static final CityConfig AUSTIN = new CityConfig(
            "Austin", -97.7431, 30.2672, 0.70,
            List.of("Downtown", "South Congress", "East Austin", "Zilker", "Rainey Street", "The Domain"),
            weights(
                    "hotel", 0.45,
                    "apartment", 0.25,
                    "hostel", 0.20,
                    "bed_and_breakfast", 0.05,
                    "resort", 0.03,
                    "motel", 0.02));

    /**
     * Round Rock: suburban business travel — chain hotels and motels, thinner amenity lists.
     * ~18 mi north of downtown Austin, 15% of hotel volume.
     */
    static final CityConfig ROUND_ROCK = new CityConfig(
            "Round Rock", -97.6789, 30.5083, 0.15,
            List.of("Old Town", "La Frontera", "Teravista"),
            weights(
                    "hotel", 0.55,
                    "motel", 0.35,
                    "apartment", 0.05,
                    "bed_and_breakfast", 0.03,
                    "hostel", 0.01,
                    "resort", 0.01));

    /**
     * San Marcos: university + outlet-mall destination — budget hotels, B&Bs, resorts.
     * ~29 mi southwest of downtown Austin, 15% of hotel volume.
     */
    static final CityConfig SAN_MARCOS = new CityConfig(
            "San Marcos", -97.9414, 29.8833, 0.15,
            List.of("Downtown", "Blanco Gardens", "Sagewood"),
            weights(
                    "hotel", 0.30,
                    "bed_and_breakfast", 0.25,
                    "resort", 0.15,
                    "motel", 0.15,
                    "hostel", 0.10,
                    "apartment", 0.05));

    static final List<CityConfig> ALL = List.of(AUSTIN, ROUND_ROCK, SAN_MARCOS);

    private static Map<String, Double> weights(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return m;
    }
}

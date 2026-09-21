package com.aerospike.demo.datagen;

import com.aerospike.demo.datagen.model.GeoPoint;
import com.aerospike.demo.datagen.model.Landmark;

import java.util.List;

/**
 * The fixed landmark set. Real, named, real-world coordinates — landmarks are what make the
 * distance queries and the map render meaningful, so unlike hotels they are not fictional and not
 * randomly generated. AUS airport is a single record shared across all three cities: "near AUS"
 * legitimately spans Austin, Round Rock, and San Marcos, so there is no need for a per-city copy.
 */
final class LandmarkData {

    private LandmarkData() {
    }

    static final List<Landmark> ALL = List.of(
            // Austin — city_center, airport, convention_center, stadium (per docs/design.md).
            new Landmark("aus-downtown", "Austin", "Downtown Austin", "city_center",
                    GeoPoint.of(-97.7431, 30.2672)),
            new Landmark("aus-airport", "Austin", "Austin-Bergstrom Intl", "airport",
                    GeoPoint.of(-97.6664, 30.1975)),
            new Landmark("aus-convention-center", "Austin", "Austin Convention Center", "convention_center",
                    GeoPoint.of(-97.7392, 30.2637)),
            new Landmark("aus-stadium", "Austin", "Darrell K Royal-Texas Memorial Stadium", "stadium",
                    GeoPoint.of(-97.7325, 30.2839)),

            // Round Rock — two of its own.
            new Landmark("rr-old-town", "Round Rock", "Old Town Round Rock", "city_center",
                    GeoPoint.of(-97.6786, 30.5083)),
            new Landmark("rr-dell-diamond", "Round Rock", "Dell Diamond", "stadium",
                    GeoPoint.of(-97.6169, 30.5407)),

            // San Marcos — two of its own.
            new Landmark("sm-downtown", "San Marcos", "Downtown San Marcos", "city_center",
                    GeoPoint.of(-97.9414, 29.8833)),
            new Landmark("sm-bobcat-stadium", "San Marcos", "UFCU Stadium (Texas State Bobcats)", "stadium",
                    GeoPoint.of(-97.9269, 29.8894)));
}

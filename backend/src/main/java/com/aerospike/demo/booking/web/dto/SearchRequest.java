package com.aerospike.demo.booking.web.dto;

import java.util.List;

/** POST /search request body. All fields optional except checkIn/checkOut, per the API contract. */
public final class SearchRequest {
    public String landmarkId;
    public Double radiusMiles;
    public String locality;
    /** Whole-city destination search (e.g. "Austin") - mutually exclusive with landmarkId/locality. */
    public String city;
    public long checkIn;
    public long checkOut;
    public Integer minRating;
    public String propertyType;
    public String bed;
    public List<String> amenities;
    /** Nightly-price bracket, inclusive/exclusive respectively - either or both may be omitted. */
    public Long minPrice;
    public Long maxPrice;
    /**
     * Guests-and-rooms picker. Previously accepted-but-ignored (tolerated only by the global
     * Jackson unknown-fields fix - see backend/README.md's "Bug found and fixed during frontend
     * integration"); now actually used, see SearchService and ClassicFilters#hasRoomForOccupancy.
     */
    public Integer adults;
    public Integer children;
    public Integer rooms;
    public int page = 0;
    public int pageSize = 20;
    public boolean demo = false;
}

package com.aerospike.demo.booking.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.Key;
import com.aerospike.client.sdk.Session;
import com.aerospike.demo.booking.model.Hotel;
import com.aerospike.demo.booking.model.HotelMapper;
import com.aerospike.demo.booking.util.Json;

/**
 * Loads /data/hotels.ndjson (one JSON hotel object per line, written by the datagen container)
 * into the {@code hotels} set. Never regenerates the data - just reads what's already on disk,
 * per the brief.
 */
public final class HotelLoader {
    private static final Logger log = LoggerFactory.getLogger(HotelLoader.class);
    private static final HotelMapper MAPPER = new HotelMapper();

    private final Session session;
    private final DataSet hotels;

    public HotelLoader(Session session, DataSet hotels) {
        this.session = session;
        this.hotels = hotels;
    }

    /** Loads every line of hotels.ndjson, returning the number of records written. */
    public int load(Path dataDir) {
        Path file = dataDir.resolve("hotels.ndjson");
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read hotels file at " + file, e);
        }

        int count = 0;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            Hotel hotel;
            try {
                hotel = Json.MAPPER.readValue(line, Hotel.class);
            } catch (IOException e) {
                log.error("Skipping malformed hotel line in {}: {}", file, e.getMessage());
                continue;
            }
            writeHotel(hotel);
            count++;
        }
        log.info("Loaded {} hotels from {}", count, file);
        return count;
    }

    /**
     * Writes one hotel record. Per docs/design.md's GeoJSON gotcha (and this SDK's
     * OperationObjectBuilder/ObjectBuilder, which has no way to splice an explicit bin operation
     * into the same call as {@code .object(hotel).using(mapper)}): the mapped insert intentionally
     * excludes "location" (see HotelMapper), so it is always followed by one explicit
     * {@code setToGeoJson} write on the same key before this method returns. A hotel record is
     * never left without its GEO-typed location bin.
     */
    private void writeHotel(Hotel hotel) {
        Key key = hotels.id(hotel.hotelId);

        session.insert(hotels)
                .object(hotel)
                .using(MAPPER)
                .execute();

        session.upsert(key)
                .bin("location").setToGeoJson(hotel.location.toGeoJson())
                .execute();
    }
}

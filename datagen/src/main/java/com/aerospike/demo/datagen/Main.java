package com.aerospike.demo.datagen;

import com.aerospike.demo.datagen.model.Hotel;
import com.aerospike.demo.datagen.model.Landmark;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * CLI entry point for the demo dataset generator. Produces hotels.ndjson, landmarks.json, and
 * stats.json under --out, per docs/design.md's "Demo dataset" and "Reproducibility" sections.
 *
 * <p>Usage: {@code --start-date=2026-09-16 --hotels=900 --seed=42 --days=90 --out=./data}
 * All flags are optional; --start-date defaults to today, per the doc's reproducibility note, so
 * an SE running this six months from now sees current dates rather than sold-out history.
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        Map<String, String> opts = parseArgs(args);
        if (opts.containsKey("help")) {
            printHelp();
            return;
        }

        LocalDate startDate = opts.containsKey("start-date")
                ? DateUtil.parseIsoDate(opts.get("start-date"))
                : LocalDate.now();
        int totalHotels = Integer.parseInt(opts.getOrDefault("hotels", "900"));
        long seed = Long.parseLong(opts.getOrDefault("seed", "42"));
        int days = Integer.parseInt(opts.getOrDefault("days", "90"));
        Path outDir = Path.of(opts.getOrDefault("out", "./data"));

        Files.createDirectories(outDir);
        ObjectMapper mapper = new ObjectMapper();

        Random rng = new Random(seed);
        ReservationIdCounter reservationIds = new ReservationIdCounter();

        Map<String, Integer> perCityCount = new LinkedHashMap<>();
        Map<String, Integer> perPropertyType = new TreeMap<>();
        Map<String, Integer> perLocality = new TreeMap<>();
        long totalRooms = 0;
        long totalRatePeriods = 0;
        long totalBytes = 0;
        long minBytes = Long.MAX_VALUE;
        long maxBytes = 0;
        int outsideSweetSpot = 0;

        Path hotelsFile = outDir.resolve("hotels.ndjson");
        int hotelIndex = 1;

        try (var writer = Files.newBufferedWriter(hotelsFile, StandardCharsets.UTF_8)) {

            for (CityConfig city : CityConfig.ALL) {
                int cityHotelCount = Math.round(totalHotels * (float) city.hotelShare());
                perCityCount.put(city.name(), cityHotelCount);

                for (int i = 0; i < cityHotelCount; i++) {
                    Hotel hotel = HotelGenerator.generate(hotelIndex++, city, startDate, days, reservationIds, rng);
                    writer.write(mapper.writeValueAsString(hotel));
                    writer.write('\n');

                    perPropertyType.merge(hotel.propertyType(), 1, Integer::sum);
                    perLocality.merge(hotel.locality(), 1, Integer::sum);
                    totalRooms += hotel.rooms().size();
                    for (var room : hotel.rooms().values()) {
                        totalRatePeriods += room.rates().size();
                    }

                    long size = mapper.writeValueAsBytes(hotel).length;
                    totalBytes += size;
                    minBytes = Math.min(minBytes, size);
                    maxBytes = Math.max(maxBytes, size);
                    if (size < 1024 || size > 128 * 1024) {
                        outsideSweetSpot++;
                    }
                }
            }
        }

        int generatedHotels = hotelIndex - 1;

        Path landmarksFile = outDir.resolve("landmarks.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(landmarksFile.toFile(), LandmarkData.ALL);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("startDate", startDate.toString());
        stats.put("days", days);
        stats.put("seed", seed);
        stats.put("totalHotels", generatedHotels);
        stats.put("hotelsPerCity", perCityCount);
        stats.put("hotelsPerPropertyType", perPropertyType);
        stats.put("distinctLocalityValues", perLocality.size());
        stats.put("hotelsPerLocality", perLocality);
        stats.put("avgRoomsPerHotel", round2((double) totalRooms / generatedHotels));
        stats.put("avgRatePeriodsPerRoom", round2((double) totalRatePeriods / totalRooms));
        stats.put("recordSizeBytes", Map.of(
                "min", minBytes,
                "max", maxBytes,
                "avg", Math.round((double) totalBytes / generatedHotels)));
        stats.put("recordsOutside1to128KiB", outsideSweetSpot);
        stats.put("landmarkCount", LandmarkData.ALL.size());

        Path statsFile = outDir.resolve("stats.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(statsFile.toFile(), stats);

        System.out.println("Wrote " + generatedHotels + " hotels to " + hotelsFile);
        System.out.println("Wrote " + LandmarkData.ALL.size() + " landmarks to " + landmarksFile);
        System.out.println("Wrote summary stats to " + statsFile);
        System.out.printf("Avg record size: %.1f KiB (min %.1f, max %.1f); %d records outside the 1-128 KiB sweet spot%n",
                totalBytes / 1024.0 / generatedHotels, minBytes / 1024.0, maxBytes / 1024.0, outsideSweetSpot);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new LinkedHashMap<>();
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")) {
                opts.put("help", "true");
                continue;
            }
            if (!arg.startsWith("--")) continue;
            String body = arg.substring(2);
            int eq = body.indexOf('=');
            if (eq < 0) {
                opts.put(body, "true");
            } else {
                opts.put(body.substring(0, eq), body.substring(eq + 1));
            }
        }
        return opts;
    }

    private static void printHelp() {
        System.out.println("""
                Aerospike DX demo dataset generator

                Options:
                  --start-date=YYYY-MM-DD   Window start (default: today)
                  --days=N                  Date window length (default: 90)
                  --hotels=N                Total hotel count, split 70/15/15 across
                                             Austin/Round Rock/San Marcos (default: 900)
                  --seed=N                  RNG seed for reproducible runs (default: 42)
                  --out=PATH                Output directory (default: ./data)

                Writes hotels.ndjson (one Hotel JSON object per line), landmarks.json,
                and stats.json to the output directory.
                """);
    }
}

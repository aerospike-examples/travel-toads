package com.aerospike.demo.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.data.LandmarkStore;
import com.aerospike.demo.booking.data.ScenarioRegistry;
import com.aerospike.demo.booking.service.AdminService;
import com.aerospike.demo.booking.service.BookingService;
import com.aerospike.demo.booking.service.HotelDetailService;
import com.aerospike.demo.booking.service.SearchService;
import com.aerospike.demo.booking.service.SuggestService;
import com.aerospike.demo.booking.web.HttpApi;

public final class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        AppConfig config = AppConfig.fromEnv();
        log.info("Starting booking backend: aerospike={}:{} dataDir={} scenariosDir={} port={}",
                config.aerospikeHost, config.aerospikePort, config.dataDir, config.scenariosDir, config.serverPort);

        AerospikeService aerospike = connectWithRetry(config);

        LandmarkStore landmarks = new LandmarkStore();
        landmarks.load(config.dataDir);

        ScenarioRegistry scenarios = new ScenarioRegistry();
        scenarios.load(config.scenariosDir);

        AdminService adminService = new AdminService(aerospike, config.dataDir);

        // Indexes are always (re)verified on startup (idempotent - createIndex catches
        // INDEX_ALREADY_EXISTS). The hotel data load only runs when the set is empty, so a
        // backend restart against an Aerospike node that's still up (and so still holds its
        // in-memory data, and any live bookings) doesn't clobber it - see AdminService and
        // aerospike/aerospike.conf's "storage-engine memory".
        long existing = aerospike.hotelRecordCount();
        if (existing == 0) {
            log.info("hotels set is empty - loading from {}", config.dataDir);
            adminService.reloadAll();
        } else {
            log.info("hotels set already has {} records - skipping load, just (re)verifying indexes", existing);
            aerospike.createIndexes();
        }

        SearchService searchService = new SearchService(aerospike, landmarks, scenarios);
        SuggestService suggestService = new SuggestService(aerospike, scenarios);
        HotelDetailService hotelDetailService = new HotelDetailService(aerospike, scenarios);
        BookingService bookingService = new BookingService(aerospike, scenarios);

        HttpApi api = new HttpApi(aerospike, landmarks, searchService, suggestService,
                hotelDetailService, bookingService, adminService);
        api.start(config.serverPort);

        Runtime.getRuntime().addShutdownHook(new Thread(aerospike::close));
    }

    private static AerospikeService connectWithRetry(AppConfig config) throws InterruptedException {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return new AerospikeService(config);
            } catch (RuntimeException e) {
                if (attempts >= 30) {
                    throw e;
                }
                log.warn("Could not connect to Aerospike at {}:{} (attempt {}/30): {}. Retrying in 1s...",
                        config.aerospikeHost, config.aerospikePort, attempts, e.getMessage());
                Thread.sleep(1000);
            }
        }
    }

    private Main() {
    }
}

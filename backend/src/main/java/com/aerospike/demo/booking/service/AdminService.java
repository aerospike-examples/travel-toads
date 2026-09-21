package com.aerospike.demo.booking.service;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.data.HotelLoader;

/**
 * Backs POST /admin/reset and POST /admin/seed, and the identical startup sequence in Main.
 * Both endpoints are the same operation per the brief: truncate {@code hotels}, reload from
 * whatever is currently on disk at DATA_DIR (never regenerate - that's scripts/seed's job via the
 * separate datagen container), and (re)create/verify the four indexes through RW. Synchronous, so
 * the caller (scripts/reset, scripts/seed) can block on the HTTP response.
 */
public final class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AerospikeService aerospike;
    private final Path dataDir;

    public AdminService(AerospikeService aerospike, Path dataDir) {
        this.aerospike = aerospike;
        this.dataDir = dataDir;
    }

    /** Truncate + reload + (re)create indexes, waiting for every index to reach RW. Returns record count. */
    public synchronized int reloadAll() {
        log.info("Truncating hotels set and reloading from {}", dataDir);
        aerospike.truncateHotels();
        HotelLoader loader = new HotelLoader(aerospike.session(), aerospike.hotelsDataSet());
        int count = loader.load(dataDir);
        aerospike.createIndexes();
        log.info("Reload complete: {} hotels, indexes ready={}", count, aerospike.allIndexesReady());
        return count;
    }
}

package com.aerospike.demo.booking.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.demo.booking.aerospike.AelUnsupportedException;
import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.data.LandmarkStore;
import com.aerospike.demo.booking.model.Landmark;
import com.aerospike.demo.booking.service.AdminService;
import com.aerospike.demo.booking.service.BookingService;
import com.aerospike.demo.booking.service.HotelDetailService;
import com.aerospike.demo.booking.service.SearchService;
import com.aerospike.demo.booking.service.SuggestService;
import com.aerospike.demo.booking.web.dto.BookingRequest;
import com.aerospike.demo.booking.web.dto.SearchRequest;

import com.fasterxml.jackson.databind.DeserializationFeature;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

/** Wires every endpoint in the API contract. All JSON, permissive CORS, no path prefix. */
public final class HttpApi {
    private static final Logger log = LoggerFactory.getLogger(HttpApi.class);

    private final AerospikeService aerospike;
    private final LandmarkStore landmarks;
    private final SearchService searchService;
    private final SuggestService suggestService;
    private final HotelDetailService hotelDetailService;
    private final BookingService bookingService;
    private final AdminService adminService;

    public HttpApi(AerospikeService aerospike, LandmarkStore landmarks, SearchService searchService,
                    SuggestService suggestService, HotelDetailService hotelDetailService,
                    BookingService bookingService, AdminService adminService) {
        this.aerospike = aerospike;
        this.landmarks = landmarks;
        this.searchService = searchService;
        this.suggestService = suggestService;
        this.hotelDetailService = hotelDetailService;
        this.bookingService = bookingService;
        this.adminService = adminService;
    }

    public Javalin start(int port) {
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            // Jackson's default is FAIL_ON_UNKNOWN_PROPERTIES=true, which turned every request
            // body carrying a field a DTO doesn't declare (e.g. the frontend's speculative
            // minPrice/maxPrice/adults/children/rooms on POST /search - see frontend/README.md's
            // "Deviations" and GuestsRoomsPicker's doc comment, both of which assumed unknown
            // fields were silently ignored) into an uncaught UnrecognizedPropertyException -> a
            // 500 "Unexpected server error" on every such request. Disabling it globally here
            // means any DTO can safely ignore fields it doesn't (yet) use, matching what both
            // sides of this API already assumed was happening.
            config.jsonMapper(new JavalinJackson().updateMapper(
                    mapper -> mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)));
        });

        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });
        app.options("/*", ctx -> ctx.status(204));

        app.get("/landmarks", ctx -> {
            List<Map<String, Object>> out = landmarks.all().stream().map(Landmark::toApi).toList();
            ctx.json(out);
        });

        app.post("/search", ctx -> {
            SearchRequest req = ctx.bodyAsClass(SearchRequest.class);
            ctx.json(searchService.search(req));
        });

        app.get("/suggest", ctx -> {
            String q = ctx.queryParam("q");
            boolean demo = Boolean.parseBoolean(ctx.queryParam("demo"));
            ctx.json(suggestService.suggest(q, demo));
        });

        app.get("/hotels/{hotelId}", ctx -> {
            String hotelId = ctx.pathParam("hotelId");
            long checkIn = Long.parseLong(ctx.queryParam("checkIn"));
            long checkOut = Long.parseLong(ctx.queryParam("checkOut"));
            boolean demo = Boolean.parseBoolean(ctx.queryParam("demo"));
            ctx.json(hotelDetailService.get(hotelId, checkIn, checkOut, demo));
        });

        app.post("/bookings", ctx -> {
            BookingRequest req = ctx.bodyAsClass(BookingRequest.class);
            ctx.status(201).json(bookingService.book(req));
        });

        app.get("/health", ctx -> ctx.json(health()));

        app.post("/admin/reset", ctx -> {
            adminService.reloadAll();
            ctx.json(Map.of("status", "ok"));
        });

        app.post("/admin/seed", ctx -> {
            adminService.reloadAll();
            ctx.json(Map.of("status", "ok"));
        });

        app.exception(ApiException.class, (e, ctx) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", e.error);
            body.put("message", e.getMessage());
            ctx.status(e.status).json(body);
        });

        app.exception(AelUnsupportedException.class, (e, ctx) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "AEL_UNSUPPORTED");
            // Two different situations, deliberately worded differently - see
            // AelUnsupportedException's own Javadoc for why conflating them was a real bug, not
            // just an imprecise message: e.versionGateFailed=true means the version genuinely
            // isn't high enough yet; false means the version is fine but this particular query
            // still isn't - saying "needs 8.2.0+" when the cluster already reports 8.2.0.0 reads
            // as broken, because the fix in that second case isn't a version bump at all.
            body.put("message", e.versionGateFailed
                    ? "This query needs Aerospike server " + e.requiredVersion
                            + "+ for AEL; this cluster is running " + e.serverVersion + ". See docs/design.md."
                    : "This cluster (" + e.serverVersion + ") supports AEL, but this specific query isn't "
                            + "implemented on this server build yet (a feature gap, not a version problem) - "
                            + "see backend/README.md for which functions are confirmed missing.");
            body.put("serverVersion", e.serverVersion);
            body.put("requiredVersion", e.requiredVersion);
            body.put("versionGateFailed", e.versionGateFailed);
            ctx.status(503).json(body);
        });

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception on {} {}", ctx.method(), ctx.path(), e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "INTERNAL_ERROR");
            body.put("message", "Unexpected server error.");
            ctx.status(500).json(body);
        });

        app.start(port);
        log.info("Listening on :{}", port);
        return app;
    }

    private Map<String, Object> health() {
        long recordCount = aerospike.hotelRecordCount();
        List<Map<String, String>> indexes = aerospike.indexStates();
        boolean ok = recordCount > 0 && aerospike.allIndexesReady();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", ok ? "ok" : "starting");
        out.put("recordCount", recordCount);
        out.put("indexes", indexes);
        return out;
    }
}

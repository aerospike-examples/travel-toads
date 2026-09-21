package com.aerospike.demo.booking.aerospike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.client.sdk.AerospikeException;
import com.aerospike.client.sdk.Cluster;
import com.aerospike.client.sdk.ClusterDefinition;
import com.aerospike.client.sdk.DataSet;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.ResultCode;
import com.aerospike.client.sdk.Session;
import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.Expression;
import com.aerospike.client.sdk.exp.StringExp;
import com.aerospike.client.sdk.info.classes.IndexState;
import com.aerospike.client.sdk.info.classes.Sindex;
import com.aerospike.client.sdk.policy.Behavior;
import com.aerospike.client.sdk.operation.StringRegexFlags;
import com.aerospike.client.sdk.query.IndexCollectionType;
import com.aerospike.client.sdk.query.IndexType;
import com.aerospike.client.sdk.task.IndexTask;
import com.aerospike.demo.booking.AppConfig;

/**
 * Owns the Aerospike SDK Cluster/Session, the four secondary indexes, and the
 * AEL-support-detection gate used by every endpoint that issues a {@code .where(String ael, ...)}
 * query. See docs/design.md "Secondary indexes" and "Server version reality" in the brief.
 */
public final class AerospikeService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AerospikeService.class);
    // Must track whatever Cluster.supportsAel() actually gates on in the SDK commit
    // backend/Dockerfile's SDK_REF pins — not a fixed design target. This drifted silently once
    // already (design.md targets 8.1.3; the SDK's `stage` branch moved the real gate to 8.2.0 on
    // 2026-09-10 - see backend/README.md's "AEL verified against a real 8.1.3+ server"), so update
    // this in the same commit as any SDK_REF bump, not on a separate pass.
    private static final String REQUIRED_AEL_VERSION = "8.2.0";

    public static final String LOC_IDX = "hotel-loc-idx";
    public static final String LOCALITY_IDX = "hotel-locality-idx";
    public static final String RATING_IDX = "hotel-rating-idx";
    public static final String MINPRICE_IDX = "hotel-minprice-idx";
    private static final List<String> ALL_INDEXES = List.of(LOC_IDX, LOCALITY_IDX, RATING_IDX, MINPRICE_IDX);

    private final Cluster cluster;
    private final Session session;
    private final DataSet hotels;
    private final AtomicBoolean aelWarningLogged = new AtomicBoolean(false);
    private volatile Boolean classicStringOpsSupported;

    public AerospikeService(AppConfig config) {
        this.cluster = new ClusterDefinition(config.aerospikeHost, config.aerospikePort).connect();
        this.session = cluster.createSession(Behavior.DEFAULT);
        this.hotels = DataSet.of(config.namespace, "hotels");
        log.info("Connected to Aerospike at {}:{} (namespace={}, cluster min version={}, AEL supported={})",
                config.aerospikeHost, config.aerospikePort, config.namespace,
                cluster.getVersion(), cluster.supportsAel());
    }

    public Session session() {
        return session;
    }

    public DataSet hotelsDataSet() {
        return hotels;
    }

    // ---- AEL gate --------------------------------------------------------------------------

    public boolean supportsAel() {
        return cluster.supportsAel();
    }

    /**
     * Throws {@link AelUnsupportedException} if this cluster can't run AEL where-clauses yet.
     * Logs the situation once at first use (not on every request) per the brief.
     */
    public void requireAel() {
        if (!cluster.supportsAel()) {
            if (aelWarningLogged.compareAndSet(false, true)) {
                log.warn("AEL-based query requested but cluster minimum version is {} (need {}+). "
                        + "Every AEL-dependent endpoint will return 503 AEL_UNSUPPORTED until the "
                        + "Aerospike image is upgraded. See docs/design.md and backend/README.md.",
                        cluster.getVersion(), REQUIRED_AEL_VERSION);
            }
            throw new AelUnsupportedException(cluster.getVersion().toString(), REQUIRED_AEL_VERSION, true);
        }
    }

    /**
     * Builds (but does not execute) an AEL where-clause query against the hotels set, gated by
     * {@link #requireAel()}. Callers may chain {@code .withHint(...)} etc. before calling
     * {@link #executeAelQuery(com.aerospike.client.sdk.query.QueryBuilder)}.
     *
     * <p><b>Deliberately uses {@code .where(PreparedAel, Object...)}, not the plain
     * {@code .where(String, Object...)} overload</b> that design.md's "Parameterized AEL is real"
     * note describes. Reading this branch's actual current source
     * (AbstractFilterableBuilder#createWhereClauseProcessor): the plain string overload binds
     * parameters via {@code String.format(ael, params)} - i.e. raw text substitution with no
     * AEL-literal quoting/escaping at all, which is exactly the string-concatenation risk the
     * brief says to avoid. The safe {@code ?0}, {@code ?1}, ... placeholder binding
     * (`AelPlaceholderBinder`, which does quote/escape values into valid AEL literals) turned out
     * to live behind the separate {@link com.aerospike.client.sdk.query.PreparedAel} type in this
     * commit, not the plain string overload - the branch has evidently moved since design.md's
     * commit `c1f3cc0`. Every AEL string built in this backend (SearchService, SuggestService)
     * uses {@code ?N} placeholders and is passed through {@code PreparedAel.prepare(...)} here so
     * values are always bound as literals, never spliced into the query text.
     */
    public com.aerospike.client.sdk.query.QueryBuilder buildAelQuery(String ael, Object... params) {
        requireAel();
        // session.query(DataSet) is declared to return the IndexBasedQueryBuilderInterface
        // interface, which (unlike the concrete QueryBuilder class it always actually returns for
        // a plain DataSet) doesn't expose the .where(PreparedAel, Object...) overload - only
        // .where(String, Object...), the String.format one this method exists to avoid. Cast to
        // the concrete type to reach it.
        com.aerospike.client.sdk.query.QueryBuilder query =
                (com.aerospike.client.sdk.query.QueryBuilder) session.query(hotels);
        return query.where(com.aerospike.client.sdk.query.PreparedAel.prepare(ael), params);
    }

    /**
     * Executes an AEL query built via {@link #buildAelQuery}, defensively catching a server-side
     * rejection (a future server reports AEL support generally, but a particular where-clause
     * still can't run — confirmed to genuinely happen, not hypothetical: {@code /suggest}'s
     * {@code lowercase()} hits this on every build tested so far) and re-throwing it as
     * {@link AelUnsupportedException} with {@code versionGateFailed=false} rather than letting a
     * raw AerospikeException/stack trace reach the client. That flag matters: this is a genuinely
     * different situation from {@link #requireAel()}'s version-gate failure, and originally both
     * were reported with the identical "needs version X+" message even when the connected cluster
     * already met X — self-contradictory and confusing in practice, not just in theory (see
     * backend/README.md's "Fixing a self-contradictory error message").
     */
    public RecordStream executeAelQuery(com.aerospike.client.sdk.query.QueryBuilder query) {
        try {
            return query.execute();
        } catch (AerospikeException e) {
            if (isLikelyAelRejection(e)) {
                if (aelWarningLogged.compareAndSet(false, true)) {
                    log.warn("Server rejected an AEL where-clause even though the client believed "
                            + "AEL was supported (resultCode={}): {}", e.getResultCode(), e.getMessage());
                }
                throw new AelUnsupportedException(cluster.getVersion().toString(), REQUIRED_AEL_VERSION, false);
            }
            throw e;
        }
    }

    /**
     * One-time, cached probe: does this server correctly evaluate the new {@link StringExp}
     * regex/contains opcodes via a classic {@link Exp}-tree filter — independent of AEL entirely?
     * Confirmed empirically that this needs checking, not assuming: against a real 8.1.2.4 server,
     * {@code StringExp.regexCompare} neither throws nor errors, it just silently evaluates to
     * false for every record — a quieter, more dangerous failure than AEL's clean rejection, since
     * nothing here signals "unsupported."
     *
     * <p>The probe is a constant expression that doesn't depend on any bin or stored data at all:
     * does the literal {@code "Hello"} match {@code /.*ell.*}/i}? That's true under any correct
     * regex engine, so running it as a where-filter and checking whether the query returns any
     * record at all (any record in the set trivially "matches" a predicate that doesn't reference
     * that record) tells us definitively whether the opcode itself works on this server, with no
     * dependency on what's actually loaded.
     */
    public boolean supportsClassicStringOps() {
        Boolean cached = classicStringOpsSupported;
        if (cached != null) {
            return cached;
        }
        boolean result;
        try {
            Exp probe = StringExp.regexCompare(Exp.val(".*ell.*"), StringRegexFlags.CASE_INSENSITIVE, Exp.val("Hello"));
            try (RecordStream rs = session.query(hotels).where(probe).execute()) {
                result = rs.hasNext();
            }
        } catch (AerospikeException e) {
            result = false;
        }
        classicStringOpsSupported = result;
        log.info("Classic StringExp ops (regexCompare, independent of AEL) supported on this server: {}", result);
        return result;
    }

    /**
     * Builds (but does not execute) a classic {@link Exp}-tree query — see {@link ClassicFilters}
     * — used instead of AEL when {@link #supportsAel()} is false. Unlike the AEL path, this needs
     * no version gate: {@code Exp}/CDT filters aren't part of the AEL feature at all, so they work
     * against any server this SDK talks to. The tradeoff, straight from
     * {@code QueryBuilder#where(Exp)}'s own javadoc, is that it can't use a secondary index — this
     * always runs as a full scan with a filter. Fine for this demo's 900-record dataset; see
     * backend/README.md "Getting real results without 8.1.3".
     */
    public com.aerospike.client.sdk.query.QueryBuilder buildClassicQuery(Exp filter) {
        return session.query(hotels).where(filter);
    }

    /**
     * Marks a scenario's demo block as running in classic-Exp compatibility mode when
     * {@link #supportsAel()} is false, so the UI doesn't claim the AEL string it displays is what
     * actually ran. Leaves the block untouched (including its {@code aelTemplate}, still shown as
     * "what this becomes on 8.1.3+") once AEL is available. No-op on a null block (scenario not
     * found, or demo=false).
     */
    public Map<String, Object> annotateIfCompat(Map<String, Object> demo) {
        if (demo == null || supportsAel()) {
            return demo;
        }
        Object mechanism = demo.get("queryMechanism");
        demo.put("queryMechanism", "Classic Exp filter (full scan) — becomes \""
                + mechanism + "\" once this server reaches " + REQUIRED_AEL_VERSION + "+");
        Object notes = demo.get("notes");
        demo.put("notes", "Running in compatibility mode: this server (" + cluster.getVersion()
                + ") is pre-" + REQUIRED_AEL_VERSION + ", so this query ran as a classic Exp-tree "
                + "filter (no secondary index, see ClassicFilters.java) instead of the AEL shown "
                + "above, which is what actually runs once the server is upgraded."
                + (notes == null ? "" : " " + notes));
        return demo;
    }

    /**
     * Confirmed by actually running a {@code .where(String, ...)} query against a real
     * aerospike/aerospike-server:8.1.2.4 container (see backend/README.md "AEL and server version
     * 8.1.2 vs 8.1.3" for the exact transcript): the SDK detects the unsupported cluster version
     * client-side, before anything goes on the wire, and throws
     * {@code com.aerospike.client.sdk.AerospikeException$BinOpInvalidException} with result code
     * 26 ({@link ResultCode#OP_NOT_APPLICABLE}) and message "Aerospike Expression Language (AEL)
     * requires server version 8.1.3+. Server version is 8.1.2.4". {@link #requireAel()} already
     * catches this ahead of time via {@link Cluster#supportsAel()} so this query is normally never
     * even attempted; this check is only a defensive fallback (e.g. a where-clause AEL feature the
     * client doesn't gate as precisely as the top-level supportsAel() flag).
     */
    private static boolean isLikelyAelRejection(AerospikeException e) {
        int rc = e.getResultCode();
        return rc == ResultCode.OP_NOT_APPLICABLE || rc == ResultCode.UNSUPPORTED_FEATURE
                || rc == ResultCode.PARAMETER_ERROR;
    }

    // ---- Indexes -----------------------------------------------------------------------------

    /** Creates all four indexes (idempotent) and waits for each to reach RW. */
    public void createIndexes() {
        createIfMissing(LOC_IDX, () -> session.createIndex(hotels, LOC_IDX, "location",
                IndexType.GEO2DSPHERE, IndexCollectionType.DEFAULT));

        createIfMissing(LOCALITY_IDX, () -> session.createIndex(hotels, LOCALITY_IDX, "locality",
                IndexType.STRING, IndexCollectionType.DEFAULT));

        createIfMissing(RATING_IDX, () -> session.createIndex(hotels, RATING_IDX, "rating",
                IndexType.INTEGER, IndexCollectionType.DEFAULT));

        createIfMissing(MINPRICE_IDX, () -> session.createIndex(hotels, MINPRICE_IDX,
                IndexType.INTEGER, IndexCollectionType.DEFAULT, minPriceExpression()));
    }

    /**
     * Minimum nightly price across every rate of every room in the record - the one index in the
     * demo that needs an {@link Exp} tree instead of AEL, verbatim from docs/design.md's
     * "Creating them - SDK" (createIndex has no AEL overload for the expression form on this
     * server version; see backend/README.md deviations for the AEL-createIndex overload this SDK
     * branch actually ships, and why we don't use it today). {@link ClassicFilters#minPrice()} is
     * the same extraction, shared so the index, the price-bracket search filter, and the value
     * shown on every card can never disagree with each other.
     */
    private static Expression minPriceExpression() {
        return Exp.build(ClassicFilters.minPrice());
    }

    private void createIfMissing(String indexName, java.util.function.Supplier<IndexTask> creator) {
        try {
            creator.get().waitTillComplete();
            log.info("Index {} created and reached RW", indexName);
        } catch (AerospikeException e) {
            if (e.getResultCode() == ResultCode.INDEX_ALREADY_EXISTS) {
                log.info("Index {} already exists", indexName);
            } else {
                throw e;
            }
        }
    }

    /** Current state of the four demo indexes, for GET /health. Empty state means "not created yet". */
    public List<Map<String, String>> indexStates() {
        Map<String, IndexState> states = new LinkedHashMap<>();
        for (Sindex idx : session.info().secondaryIndexes()) {
            if (ALL_INDEXES.contains(idx.getIndexName())) {
                states.put(idx.getIndexName(), idx.getState());
            }
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (String name : ALL_INDEXES) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", name);
            IndexState state = states.get(name);
            m.put("state", state == null ? "MISSING" : state.name());
            out.add(m);
        }
        return out;
    }

    public boolean allIndexesReady() {
        List<Map<String, String>> states = indexStates();
        if (states.size() != ALL_INDEXES.size()) {
            return false;
        }
        for (Map<String, String> s : states) {
            if (!"RW".equals(s.get("state"))) {
                return false;
            }
        }
        return true;
    }

    // ---- Record count (for /health) ----------------------------------------------------------

    /**
     * Exact record count for the hotels set, via a plain primary-index scan (no where-clause, so
     * no AEL/index dependency - works today on 8.1.2). Deliberately not the set-info "objects"
     * stat: {@code truncate()} is asynchronous server-side ("may return before the truncation is
     * complete... new records can be written immediately because they'll have a newer last-update
     * time" per Session#truncate's Javadoc), so right after /admin/reset or /admin/seed that stat
     * can transiently double-count old not-yet-reaped records alongside the freshly reloaded ones
     * - observed directly (900 -> reset -> reload -> stat briefly read 1800). A scan never returns
     * truncated records regardless of physical reaping, so it doesn't have that lag. The dataset
     * is small (hundreds of records), so scanning on every /health poll is cheap.
     */
    public long hotelRecordCount() {
        long count = 0;
        try (RecordStream rs = session.query(hotels).execute()) {
            while (rs.hasNext()) {
                if (rs.next().isOk()) {
                    count++;
                }
            }
        }
        return count;
    }

    public void truncateHotels() {
        session.truncate(hotels);
    }

    @Override
    public void close() {
        cluster.close();
    }
}

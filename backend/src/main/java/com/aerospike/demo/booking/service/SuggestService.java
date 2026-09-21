package com.aerospike.demo.booking.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aerospike.client.sdk.Record;
import com.aerospike.client.sdk.RecordResult;
import com.aerospike.client.sdk.RecordStream;
import com.aerospike.client.sdk.exp.Exp;
import com.aerospike.client.sdk.exp.StringExp;
import com.aerospike.client.sdk.operation.StringRegexFlags;
import com.aerospike.client.sdk.query.QueryBuilder;
import com.aerospike.demo.booking.aerospike.AelUnsupportedException;
import com.aerospike.demo.booking.aerospike.AerospikeService;
import com.aerospike.demo.booking.data.ScenarioRegistry;

/**
 * GET /suggest - property name typeahead using the 8.1.3 Unicode-safe string API in AEL, per
 * docs/design.md step 5 and scenarios/strings.json. Deliberately unindexed (a full scan with a
 * string predicate), shown side by side with the indexed scenarios.
 *
 * <p>Three tiers, tried in order, each falling through to the next on rejection rather than
 * failing the request - AEL, then a classic Exp-tree filter using the same new string opcodes
 * independent of AEL, then a guaranteed-correct application-layer scan. This used to stop at
 * whichever tier {@code aerospike.supportsAel()} pointed at and fail outright if that one
 * specific tier's server-side function wasn't implemented (confirmed: {@code lowercase()} isn't,
 * on every build tested, AEL-capable or not) - a real bug, not a hypothetical, since it meant
 * hotel-name search returned zero results whenever that happened even though a working fallback
 * sat right below it in this same file. See backend/README.md's "Fixing hotel-name search's
 * fall-through" for the fix.
 *
 * <p>The {@link AerospikeService#supportsClassicStringOps()} probe (a one-time, cached check, not
 * a guess) is what tier 2 depends on - whether the new {@code StringExp} opcodes work via a
 * classic Exp-tree filter, independent of AEL entirely. Confirmed against a real 8.1.2.4 server:
 * they don't - the server accepts a {@code StringExp.regexCompare} filter without error and
 * silently matches nothing, not even a hotel literally named "Sunset Backpackers" queried for
 * "sunset". design.md's own SDK-source notes group {@code contains}/{@code lower}/
 * {@code regexCompare} together as "the 8.1.3 [string ops] surface... client-side wiring is ahead
 * of the server release" - these appear
 * to be new server opcodes in their own right, gated independently of the AEL parser, and this is
 * the confirmation of that. So this falls back further, to filtering in the application after a
 * full unfiltered scan - still correct results, just honestly *not* server-side filtering until
 * 8.1.3, and the demo panel says so explicitly rather than implying otherwise.
 */
public final class SuggestService {
    private static final int LIMIT = 10;

    private final AerospikeService aerospike;
    private final ScenarioRegistry scenarios;

    public SuggestService(AerospikeService aerospike, ScenarioRegistry scenarios) {
        this.aerospike = aerospike;
        this.scenarios = scenarios;
    }

    public Map<String, Object> suggest(String needle, boolean demo) {
        String needleLower = needle == null ? "" : needle.toLowerCase();
        List<Map<String, Object>> results = null;
        boolean serverSideFilter = false;
        boolean aelRejectedDespiteSupport = false;

        if (aerospike.supportsAel()) {
            String ael = "$.name.lowercase().indexOf(needle: ?0) >= 0";
            QueryBuilder query = aerospike.buildAelQuery(ael, needleLower);
            try {
                try (RecordStream rs = aerospike.executeAelQuery(query)) {
                    results = collect(rs);
                }
                serverSideFilter = true;
            } catch (AelUnsupportedException e) {
                // Reached only when the version gate already passed - we're inside
                // aerospike.supportsAel() == true, so e.versionGateFailed is guaranteed false
                // here. This is specifically "the server rejected this one function"
                // (lowercase(), confirmed missing on every build tested), not a version problem.
                // Fall through to the next tier instead of failing the whole request - see this
                // class's Javadoc for why that used to be a real bug, not just a style choice.
                aelRejectedDespiteSupport = true;
            }
        }

        if (results == null && aerospike.supportsClassicStringOps()) {
            Exp pattern = Exp.val(".*" + escapeRegex(needleLower) + ".*");
            Exp filter = StringExp.regexCompare(pattern, StringRegexFlags.CASE_INSENSITIVE, Exp.stringBin("name"));
            QueryBuilder query = aerospike.buildClassicQuery(filter);
            try (RecordStream rs = query.execute()) {
                results = collect(rs);
            }
            serverSideFilter = true;
        }

        if (results == null) {
            results = scanAndFilterInApp(needleLower);
            serverSideFilter = false;
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("results", results);
        Map<String, Object> demoBlock = demo
                ? scenarios.buildDemo("strings", Map.of("needle", needle == null ? "" : needle))
                : null;
        if (demoBlock != null) {
            if (!serverSideFilter) {
                // More specific than AerospikeService#annotateIfCompat's generic message - applied
                // instead of it, not in addition, so the two don't stack into duplicated text.
                demoBlock.put("queryMechanism", "Application-layer filter (full scan, matched in Java) — "
                        + demoBlock.get("queryMechanism") + " once this server reaches 8.1.3+");
                demoBlock.put("notes", "This server's new string-comparison opcodes (contains/lower/"
                        + "regexCompare) appear to need 8.1.3 regardless of AEL, not just AEL's own "
                        + "parser — confirmed via a one-time probe (AerospikeService#supportsClassicStringOps), "
                        + "not assumed: the server accepts the classic-Exp regex filter without error "
                        + "and silently matches nothing. So this result is filtered in the application "
                        + "after an unfiltered scan, not by Aerospike. " + demoBlock.get("notes"));
            } else if (aelRejectedDespiteSupport) {
                // A different case from the branch above: AEL is genuinely supported here (unlike
                // annotateIfCompat's pre-8.1.3 framing), but this specific function isn't - so
                // this falls back exactly one tier, to a classic Exp-tree filter, which still runs
                // server-side. Worth saying plainly rather than letting the scenario file's
                // AEL-shaped template stand uncorrected.
                demoBlock.put("queryMechanism", "Classic Exp filter (full scan) — this cluster supports "
                        + "AEL, but not the lowercase() function this query needs yet");
                demoBlock.put("notes", "This cluster supports AEL generally (confirmed elsewhere in this "
                        + "demo), but rejected this specific query's lowercase() function - a real, "
                        + "confirmed gap in this server build, not a version problem. Falling back one "
                        + "tier to a classic Exp-tree regex filter instead, which this server does "
                        + "implement — still a genuine server-side filter, just not via AEL. "
                        + demoBlock.get("notes"));
            } else if (!aerospike.supportsAel()) {
                // Classic Exp regex actually worked (a different server than the one this was
                // developed against might support it) - the generic compat message is accurate.
                aerospike.annotateIfCompat(demoBlock);
            }
        }
        response.put("demo", demoBlock);
        return response;
    }

    private List<Map<String, Object>> scanAndFilterInApp(String needleLower) {
        List<Map<String, Object>> results = new ArrayList<>();
        try (RecordStream rs = aerospike.session().query(aerospike.hotelsDataSet()).execute()) {
            while (rs.hasNext() && results.size() < LIMIT) {
                RecordResult rr = rs.next();
                if (!rr.isOk()) {
                    continue;
                }
                Record rec = rr.recordOrThrow();
                String name = rec.getString("name");
                if (name != null && name.toLowerCase().contains(needleLower)) {
                    results.add(item(rec));
                }
            }
        }
        return results;
    }

    private static List<Map<String, Object>> collect(RecordStream rs) {
        List<Map<String, Object>> results = new ArrayList<>();
        while (rs.hasNext() && results.size() < LIMIT) {
            RecordResult rr = rs.next();
            if (!rr.isOk()) {
                continue;
            }
            results.add(item(rr.recordOrThrow()));
        }
        return results;
    }

    private static Map<String, Object> item(Record rec) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("hotelId", rec.getString("hotelId"));
        item.put("name", rec.getString("name"));
        return item;
    }

    /** Escapes regex metacharacters so a literal needle can't break the pattern or inject one. */
    private static String escapeRegex(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (".^$|?*+()[]{}\\".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}

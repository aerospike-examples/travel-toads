package com.aerospike.demo.booking.data;

import java.util.List;

/**
 * One scenarios/*.json file. Field names match the JSON exactly (see scenarios/geo.json,
 * locality.json, path-expressions.json, strings.json, booking.json) - this is the canonical
 * source of the Demo Mode / Engineering Mode copy per docs/design.md; the backend loads these
 * verbatim at startup and never hardcodes this copy a second time in Java.
 */
public final class Scenario {
    public String id;
    public List<String> covers;
    public String userAction;
    public String aerospikeAction;
    public String queryMechanism;
    public String index;
    public String aelTemplate;
    public String relevantCode;
    public String queryHint;
    public String notes;

    public Scenario() {
    }
}

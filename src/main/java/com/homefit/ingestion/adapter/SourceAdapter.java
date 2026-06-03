package com.homefit.ingestion.adapter;

/**
 * One adapter per external source (NASA POWER, LANCE flood, OSM, OpenAQ, ACLED, agent submissions...).
 * Each fetches, normalizes onto the H3 grid, and writes raw observations + provenance.
 */
public interface SourceAdapter {
    /** Stable source code, e.g. "nasa_power", "osm", "agent_submission". */
    String sourceCode();

    /** Run one ingestion pass for the given admin region. Returns records written. */
    int ingest(long adminRegionId);
}

/**
 * ingestion — background pipeline: sources -> H3 cells -> precomputed sub-scores in PostGIS.
 * May depend only on {@code core}. Not internet-facing.
 */
@org.springframework.modulith.ApplicationModule(displayName = "ingestion", allowedDependencies = "core")
package com.homefit.ingestion;

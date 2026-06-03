/**
 * core — shared domain + scoring. Framework-light; depends on no other module.
 * Sub-score normalizers run offline (ingestion); the weighted aggregator runs per request (api).
 */
@org.springframework.modulith.ApplicationModule(displayName = "core")
package com.homefit.core;

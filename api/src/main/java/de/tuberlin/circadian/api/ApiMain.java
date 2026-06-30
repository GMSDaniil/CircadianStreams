package de.tuberlin.circadian.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin REST API over Postgres (a lightweight HTTP layer exposing
 * {@code agg_1min} / {@code circadian_metrics} for any custom dashboard panels Grafana cannot
 * render). Phase 0 ships this runnable placeholder so the module and its packaging exist.
 */
public final class ApiMain {

    private static final Logger LOG = LoggerFactory.getLogger(ApiMain.class);

    public static void main(String[] args) {
        LOG.info("circadian-api stub. REST endpoints land in Phase 3. Grafana queries Postgres directly until then.");
    }

    private ApiMain() { }
}

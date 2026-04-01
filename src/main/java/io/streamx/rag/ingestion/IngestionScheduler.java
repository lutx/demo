package io.streamx.rag.ingestion;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class IngestionScheduler {

    private static final Logger LOG = Logger.getLogger(IngestionScheduler.class);

    @Inject
    DocumentIngestionService ingestionService;

    @Scheduled(cron = "{rag.ingestion.full-sync-cron}")
    void fullSync() {
        LOG.info("Scheduled full sync triggered");
        try {
            var result = ingestionService.runFullSync();
            LOG.infof("Scheduled full sync finished: %d documents", result.documentCount());
        } catch (Exception e) {
            LOG.error("Scheduled full sync failed", e);
        }
    }

    @Scheduled(every = "{rag.ingestion.delta-sync-interval}")
    void deltaSync() {
        LOG.info("Scheduled delta sync triggered");
        try {
            var result = ingestionService.runDeltaSync();
            LOG.infof("Scheduled delta sync finished: %d documents", result.documentCount());
        } catch (Exception e) {
            LOG.error("Scheduled delta sync failed", e);
        }
    }
}

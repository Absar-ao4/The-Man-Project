package com.example.fitapp.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles duels that ended while nobody was looking, so the winner is paid and announced within the hour. Duels are also
 * settled whenever one of the two people lists their duels, so this is only a safety net.
 */
@Component
@EnableScheduling
public class DuelSweeper {

    private static final Logger log = LoggerFactory.getLogger(DuelSweeper.class);

    private final DuelService duels;

    public DuelSweeper(DuelService duels) {
        this.duels = duels;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT1H")
    public void sweep() {
        try {
            int settled = duels.settleAllDue();
            if (settled > 0) {
                log.info("Settled {} finished duels", settled);
            }
        } catch (RuntimeException e) {
            log.warn("Duel sweep failed: {}", e.getClass().getSimpleName());
        }
    }
}

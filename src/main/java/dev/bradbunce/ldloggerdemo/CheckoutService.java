package dev.bradbunce.ldloggerdemo;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A stand-in for ordinary application code.
 *
 * <p>Nothing here knows that LaunchDarkly exists. The logger is captured in a
 * {@code static final} field when the class loads - before the demo has even
 * built an {@code LDClient} - which is exactly the case that makes runtime level
 * control awkward. Because the library sets the level of the real logger rather
 * than wrapping it, this field still responds to every flag change.
 */
final class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private static final int FIRST_ORDER = 1000;

    // Incremented from both the timer loop and the interactive command thread.
    private final AtomicInteger orders = new AtomicInteger(FIRST_ORDER);

    /** Emits one line at each level, so the active level is visible as output. */
    void checkout() {
        String orderId = "ord-" + orders.incrementAndGet();

        log.trace("Entering checkout({})", orderId);
        log.debug("Cart contents resolved for {}: 3 items, $148.20", orderId);
        log.info("Checkout completed for {}", orderId);
        log.warn("Address for {} looks unverified; continuing anyway", orderId);
        log.error("Payment gateway returned a retryable error for {}", orderId);
    }

    /** How many rounds of messages have been logged. */
    int roundsLogged() {
        return orders.get() - FIRST_ORDER;
    }

    /** Which levels the logging backend will actually emit right now. */
    static String enabledLevels() {
        StringBuilder enabled = new StringBuilder();
        appendIf(enabled, log.isErrorEnabled(), "ERROR");
        appendIf(enabled, log.isWarnEnabled(), "WARN");
        appendIf(enabled, log.isInfoEnabled(), "INFO");
        appendIf(enabled, log.isDebugEnabled(), "DEBUG");
        appendIf(enabled, log.isTraceEnabled(), "TRACE");
        return enabled.isEmpty() ? "(none)" : enabled.toString();
    }

    private static void appendIf(StringBuilder target, boolean enabled, String level) {
        if (enabled) {
            if (!target.isEmpty()) {
                target.append(' ');
            }
            target.append(level);
        }
    }
}

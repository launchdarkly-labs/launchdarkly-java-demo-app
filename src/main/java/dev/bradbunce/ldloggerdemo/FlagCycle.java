package dev.bradbunce.ldloggerdemo;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.integrations.TestData;
import dev.bradbunce.ldlogger.ConsoleLogLevel;
import dev.bradbunce.ldlogger.SdkLogLevel;
import java.util.List;

/**
 * Drives the two flags locally in offline mode, using the SDK's
 * {@link TestData} source.
 *
 * <p>This is not a shortcut around the real code path: a {@code TestData} update
 * goes through the SDK's data store and fires the same {@code FlagTracker}
 * change events a streaming update from LaunchDarkly would, so the controller
 * cannot tell the difference. It just means the demo needs no credentials.
 */
final class FlagCycle {

    /** Cycles through every level so all six flag values get exercised. */
    private static final List<ConsoleLogLevel> CONSOLE_SEQUENCE = List.of(
            ConsoleLogLevel.ERROR,
            ConsoleLogLevel.WARN,
            ConsoleLogLevel.INFO,
            ConsoleLogLevel.DEBUG,
            ConsoleLogLevel.TRACE,
            ConsoleLogLevel.FATAL);

    private static final List<SdkLogLevel> SDK_SEQUENCE = List.of(
            SdkLogLevel.ERROR, SdkLogLevel.ERROR, SdkLogLevel.WARN,
            SdkLogLevel.ERROR, SdkLogLevel.INFO, SdkLogLevel.ERROR);

    private final TestData testData;
    private final DemoConfig config;
    private int step;

    private FlagCycle(TestData testData, DemoConfig config) {
        this.testData = testData;
        this.config = config;
    }

    /** Builds a data source with both flags seeded at their first value. */
    static FlagCycle seeded(DemoConfig config) {
        FlagCycle cycle = new FlagCycle(TestData.dataSource(), config);
        cycle.apply(0);
        return cycle;
    }

    TestData dataSource() {
        return testData;
    }

    /** Moves both flags to their next value in the cycle. */
    void advance() {
        apply(++step);
    }

    ConsoleLogLevel currentConsoleLevel() {
        return CONSOLE_SEQUENCE.get(step % CONSOLE_SEQUENCE.size());
    }

    SdkLogLevel currentSdkLevel() {
        return SDK_SEQUENCE.get(step % SDK_SEQUENCE.size());
    }

    private void apply(int step) {
        ConsoleLogLevel console = CONSOLE_SEQUENCE.get(step % CONSOLE_SEQUENCE.size());
        SdkLogLevel sdk = SDK_SEQUENCE.get(step % SDK_SEQUENCE.size());

        testData.update(testData.flag(config.consoleLogFlagKey())
                .valueForAll(LDValue.of(console.flagValue())));
        testData.update(testData.flag(config.sdkLogFlagKey())
                .valueForAll(LDValue.of(sdk.flagValue())));
    }
}

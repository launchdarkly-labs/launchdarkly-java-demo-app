package io.github.bradbunce.ldloggerdemo;

import com.launchdarkly.sdk.server.Components;
import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import io.github.bradbunce.ldlogger.ConsoleLogLevel;
import io.github.bradbunce.ldlogger.LDLogLevelController;
import io.github.bradbunce.ldlogger.LDSdkLogging;
import io.github.bradbunce.ldlogger.LogLevel;
import io.github.bradbunce.ldlogger.SdkLogLevel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Sample app for launchdarkly-java-logger.
 *
 * <p>It logs at every level on a timer while {@link LDLogLevelController} drives
 * the levels from feature flags, so changing a flag visibly changes the output
 * of a running process. Type {@code l} to log on demand instead of waiting for
 * the next tick, which is the useful way to watch a flag change land.
 *
 * <ul>
 *   <li>With an SDK key, it streams from LaunchDarkly - toggle the flags in the
 *       UI and watch this process react.
 *   <li>With no key, it drives the same flags locally via the SDK's TestData
 *       source, cycling through every level so the whole range is exercised.
 * </ul>
 *
 * <p>See {@code --help}.
 */
public final class DemoApp {

    private DemoApp() {
        throw new AssertionError("No instances");
    }

    public static void main(String[] args) throws Exception {
        DemoCli cli = DemoCli.parse(args);
        switch (cli.mode()) {
            case HELP -> {
                DemoCli.printUsage();
                return;
            }
            case INVALID -> {
                // parse() has already explained the problem.
                System.exit(2);
                return;
            }
            default -> { /* fall through to run or doctor */ }
        }

        DemoConfig config = DemoConfig.resolve(cli.overrides());

        // Instantiated before LaunchDarkly exists, so its static logger is
        // captured well before any flag is read.
        CheckoutService checkout = new CheckoutService();

        Optional<FlagCycle> cycle =
                config.isLive() ? Optional.empty() : Optional.of(FlagCycle.seeded(config));

        try (LDClient client = new LDClient(
                config.sdkKey().orElse("offline-demo-no-key"), ldConfig(config, cycle))) {

            if (cli.mode() == DemoCli.Mode.DOCTOR) {
                DemoDoctor.report(config, client);
                return;
            }

            try (LDLogLevelController logLevels = controller(client, config)) {
                printBanner(config, client, logLevels, cli.interactive());
                logLevels.start();
                int ticks = runUntilStopped(config, cycle, checkout, logLevels, cli.interactive());
                printExitSummary(config, checkout, logLevels, ticks);
            }
            // The controller is closed before the client, so no flag change can
            // arrive while the SDK is shutting down.
        }

        System.out.println(" Done.");
    }

    private static LDConfig ldConfig(DemoConfig config, Optional<FlagCycle> cycle) {
        LDConfig.Builder builder = new LDConfig.Builder()
                // Without this the SDK logs straight to the console at INFO and
                // the SDK log level flag has nothing to act on. Never combine it
                // with .level(...), which the SLF4J adapter ignores anyway.
                .logging(LDSdkLogging.slf4j())
                .startWait(Duration.ofSeconds(10));

        cycle.ifPresent(offline -> builder
                .dataSource(offline.dataSource())
                .events(Components.noEvents()));

        return builder.build();
    }

    private static LDLogLevelController controller(LDClient client, DemoConfig config) {
        LDLogLevelController.Builder builder = LDLogLevelController
                .builder(client, config.context())
                .consoleLogFlagKey(config.consoleLogFlagKey())
                .sdkLogFlagKey(config.sdkLogFlagKey())
                // Where a real service might report the change to its metrics.
                .onConsoleLogLevelChange(DemoApp::reportConsoleChange)
                .onSdkLogLevelChange(DemoApp::reportSdkChange);

        config.applicationLoggerName().ifPresent(builder::applicationLoggerName);
        config.sdkLoggerName().ifPresent(builder::sdkLoggerName);
        return builder.build();
    }

    /**
     * Runs the tick loop until stopped.
     *
     * @return how many ticks completed
     */
    private static int runUntilStopped(
            DemoConfig config,
            Optional<FlagCycle> cycle,
            CheckoutService checkout,
            LDLogLevelController logLevels,
            boolean interactive)
            throws InterruptedException {

        CountDownLatch stop = new CountDownLatch(1);
        Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop.countDown();
            try {
                // Give main a moment to close the controller and client cleanly.
                mainThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        if (interactive) {
            startCommandReader(config, checkout, logLevels, stop);
        }

        // Runs until Ctrl-C, SIGTERM, or the 'q' command.
        int completed = 0;
        for (int tick = 1; ; tick++) {
            if (tick > 1 && cycle.isPresent()) {
                // In offline mode, stand in for someone toggling the flag in the
                // UI, then let the change land before describing the tick.
                FlagCycle offline = cycle.get();
                offline.advance();
                awaitApplied(logLevels, offline);
            }

            printFlushed("%n--- tick %d | %s ---%n", tick, describeLevels(logLevels));
            checkout.checkout();
            completed = tick;

            if (stop.await(config.intervalSeconds(), TimeUnit.SECONDS)) {
                return completed;
            }
        }
    }

    /**
     * Prints a closing summary and quiets the SDK for shutdown.
     *
     * <p>With the SDK log flag at {@code debug}, closing the client otherwise
     * dumps a full analytics event payload as the last thing on screen. Raising
     * the SDK logger to INFO keeps the useful "Closing LaunchDarkly Client" line
     * while dropping the JSON - and doubles as a demonstration of using the
     * bridge directly.
     */
    private static void printExitSummary(
            DemoConfig config,
            CheckoutService checkout,
            LDLogLevelController logLevels,
            int ticks) {

        System.out.println();
        System.out.println("-".repeat(72));
        System.out.println(" Shutting down");
        System.out.printf("   ticks            %d%n", ticks);
        System.out.printf("   log rounds       %d%n", checkout.roundsLogged());
        System.out.printf("   final console    %s (flag %d)%n",
                logLevels.currentConsoleLogLevel(),
                logLevels.currentConsoleLogLevel().flagValue());
        System.out.printf("   final sdk        %s%n",
                logLevels.currentSdkLogLevel().map(SdkLogLevel::flagValue).orElse("n/a"));
        System.out.printf("   backend          %s%n", logLevels.bridge().backendName());
        System.out.println("-".repeat(72));
        System.out.flush();

        boolean sdkIsVerbose =
                logLevels.currentSdkLogLevel().orElse(SdkLogLevel.ERROR) == SdkLogLevel.DEBUG;
        if (sdkIsVerbose && logLevels.bridge().isAvailable()) {
            String sdkLogger =
                    config.sdkLoggerName().orElse(LDLogLevelController.DEFAULT_SDK_LOGGER_NAME);
            logLevels.bridge().setLevel(sdkLogger, LogLevel.INFO);
        }
    }

    /**
     * Reads single-letter commands from the terminal on a daemon thread.
     *
     * <p>Degrades quietly when there is no terminal - piped input, or a CI run -
     * rather than sitting on a dead stream.
     */
    private static void startCommandReader(
            DemoConfig config,
            CheckoutService checkout,
            LDLogLevelController logLevels,
            CountDownLatch stop) {

        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = in.readLine()) != null) {
                    switch (line.trim().toLowerCase()) {
                        case "l", "log" -> {
                            printFlushed("%n--- on demand | %s ---%n",
                                    describeLevels(logLevels));
                            checkout.checkout();
                        }
                        case "s", "status" -> printStatus(config, logLevels);
                        case "h", "help", "?" -> printCommands();
                        case "q", "quit", "exit" -> {
                            // The main loop prints the shutdown notice, so this
                            // only needs to signal it.
                            stop.countDown();
                            return;
                        }
                        case "" -> { /* bare Enter: ignore */ }
                        default -> System.out.printf(
                                "Unknown command '%s'. Type h for help.%n", line.trim());
                    }
                }
            } catch (IOException e) {
                System.out.println("! stopped reading commands: " + e.getMessage());
            }
        }, "demo-command-reader");

        reader.setDaemon(true);
        reader.start();
    }

    /**
     * Prints a banner and flushes it.
     *
     * <p>{@code System.out} and Logback's console appender are two streams onto
     * the same terminal. Without an explicit flush, a banner can appear after the
     * log lines it introduces.
     */
    private static void printFlushed(String format, Object... args) {
        System.out.printf(format, args);
        System.out.flush();
    }

    private static String describeLevels(LDLogLevelController logLevels) {
        return String.format("console %s (flag %d) | sdk %s | emitting %s",
                logLevels.currentConsoleLogLevel(),
                logLevels.currentConsoleLogLevel().flagValue(),
                logLevels.currentSdkLogLevel().map(SdkLogLevel::flagValue).orElse("n/a"),
                CheckoutService.enabledLevels());
    }

    private static void printStatus(DemoConfig config, LDLogLevelController logLevels) {
        System.out.printf("%n status  %s%n", describeLevels(logLevels));
        System.out.printf(" backend %s (%s)%n",
                logLevels.bridge().backendName(),
                logLevels.bridge().isAvailable() ? "available" : "NOT AVAILABLE");
        System.out.printf(" flags   %s (number), %s (string)%n",
                config.consoleLogFlagKey(), config.sdkLogFlagKey());
        System.out.printf(" logger  %s%n", config.applicationLoggerName().orElse("<root>"));
        System.out.printf(" mode    %s%n%n", config.isLive() ? "live" : "offline");
    }

    private static void printCommands() {
        System.out.println("""

                 l  log one round of messages at every level, right now
                 s  print current levels and configuration
                 h  this help
                 q  quit
                """);
    }

    /**
     * Waits for a flag update to reach the controller. Flag changes are dispatched
     * on the SDK's own thread, so without this the tick banner can describe a
     * level that is about to change underneath it.
     */
    private static void awaitApplied(LDLogLevelController logLevels, FlagCycle cycle)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            boolean consoleApplied =
                    logLevels.currentConsoleLogLevel() == cycle.currentConsoleLevel();
            boolean sdkApplied =
                    logLevels.currentSdkLogLevel().orElse(null) == cycle.currentSdkLevel();
            if (consoleApplied && sdkApplied) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        System.out.println(" ! timed out waiting for the flag change to be applied");
    }

    private static void reportConsoleChange(ConsoleLogLevel level) {
        System.out.printf(">> console log level is now %s (flag value %d, applied as %s)%n",
                level, level.flagValue(), level.logLevel());
    }

    private static void reportSdkChange(SdkLogLevel level) {
        System.out.printf(">> SDK log level is now %s%n", level.flagValue());
    }

    private static void printBanner(
            DemoConfig config,
            LDClient client,
            LDLogLevelController logLevels,
            boolean interactive) {

        String mode = config.isLive()
                ? "live (streaming from LaunchDarkly)"
                : "offline (SDK TestData - pass --sdk-key for live mode)";

        System.out.println("=".repeat(72));
        System.out.println(" LaunchDarkly Java Logger - demo app");
        System.out.println("=".repeat(72));
        System.out.printf(" mode           %s%n", mode);
        System.out.printf(" initialized    %s%n", client.isInitialized());
        System.out.printf(" console flag   %s%n", config.consoleLogFlagKey());
        System.out.printf(" sdk flag       %s%n", config.sdkLogFlagKey());
        System.out.printf(" context        service:%s (env=%s)%n",
                config.contextKey(), config.environment());
        System.out.printf(" app logger     %s%n",
                config.applicationLoggerName().orElse("<root>"));
        System.out.printf(" backend        %s%n", logLevels.bridge().backendName());
        System.out.printf(" interval       %ds%n", config.intervalSeconds());
        if (interactive) {
            System.out.println(" commands       l = log now, s = status, h = help, q = quit");
        }
        System.out.println("-".repeat(72));

        if (!logLevels.bridge().isAvailable()) {
            System.out.println(" ! No supported SLF4J backend is bound, so levels cannot change.");
            System.out.println("   Run 'doctor' for details.");
            System.out.println("-".repeat(72));
        }
    }
}

package io.github.bradbunce.ldloggerdemo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Command-line parsing for the demo.
 *
 * <p>Every option maps onto the same setting name as its environment variable,
 * so there is a single resolution path: command line shadows environment, which
 * shadows the built-in default.
 */
record DemoCli(Mode mode, Map<String, String> overrides, boolean interactive) {

    /** What the invocation asked for. */
    enum Mode {
        /** Run the ticking demo. */
        RUN,
        /** Print diagnostics and exit. */
        DOCTOR,
        /** Print usage and exit. */
        HELP,
        /** Usage error; a message has already been printed. */
        INVALID
    }

    /** Option name to the setting (environment variable) name it overrides. */
    private static final Map<String, String> OPTIONS = Map.of(
            "--sdk-key", "LD_SDK_KEY",
            "--console-flag", "LD_CONSOLE_LOG_FLAG_KEY",
            "--sdk-flag", "LD_SDK_LOG_FLAG_KEY",
            "--context-key", "LD_CONTEXT_KEY",
            "--env", "LD_ENV",
            "--app-logger", "LD_APP_LOGGER_NAME",
            "--sdk-logger", "LD_SDK_LOGGER_NAME",
            "--interval", "DEMO_INTERVAL_SECONDS");

    static DemoCli parse(String[] args) {
        Map<String, String> overrides = new LinkedHashMap<>();
        Mode mode = Mode.RUN;
        boolean interactive = true;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.equals("--help") || arg.equals("-h")) {
                return new DemoCli(Mode.HELP, Map.of(), false);
            }
            if (arg.equals("doctor")) {
                mode = Mode.DOCTOR;
                continue;
            }
            if (arg.equals("run")) {
                continue;
            }
            if (arg.equals("--no-interactive")) {
                interactive = false;
                continue;
            }

            // Accept both --option=value and --option value.
            String name = arg;
            String value = null;
            int equals = arg.indexOf('=');
            if (equals > 0) {
                name = arg.substring(0, equals);
                value = arg.substring(equals + 1);
            }

            String setting = OPTIONS.get(name);
            if (setting == null) {
                System.out.printf("Unknown option: %s%n%n", arg);
                printUsage();
                return new DemoCli(Mode.INVALID, Map.of(), false);
            }
            if (value == null) {
                // A value that looks like another option almost always means the
                // intended value went missing, so say so rather than silently
                // swallowing the next flag.
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    System.out.printf("Option %s needs a value%n%n", name);
                    printUsage();
                    return new DemoCli(Mode.INVALID, Map.of(), false);
                }
                value = args[++i];
            }
            overrides.put(setting, value);
        }
        return new DemoCli(mode, Map.copyOf(overrides), interactive);
    }

    static void printUsage() {
        System.out.println("""
                Usage: launchdarkly-java-demo-app [run|doctor] [options]

                Commands
                  run                  Log at every level on a timer while feature flags
                                       drive the levels. The default.
                  doctor               Print diagnostics - bound backend, flag values and
                                       their types, what each resolves to - and exit.

                Options                                        (environment variable)
                  --sdk-key KEY        Server-side SDK key.     (LD_SDK_KEY)
                                       Omit for offline mode.
                  --console-flag KEY   Numeric flag, 0-5.       (LD_CONSOLE_LOG_FLAG_KEY)
                  --sdk-flag KEY       String flag.             (LD_SDK_LOG_FLAG_KEY)
                  --context-key KEY    Context key.             (LD_CONTEXT_KEY)
                  --env NAME           Context 'env' attribute. (LD_ENV)
                  --app-logger NAME    Logger the console flag  (LD_APP_LOGGER_NAME)
                                       controls. Default: root.
                  --sdk-logger NAME    Logger the SDK flag      (LD_SDK_LOGGER_NAME)
                                       controls.
                  --interval SECONDS   Seconds between ticks.   (DEMO_INTERVAL_SECONDS)
                  --no-interactive     Do not read commands from the terminal.
                  -h, --help           Print this help.

                While running
                  l  log one round of messages at every level, right now
                  s  print current levels and configuration
                  h  list these commands
                  q  quit

                Examples
                  # Offline, ticking quickly
                  launchdarkly-java-demo-app --interval 1

                  # Live, targeting only your application's loggers
                  launchdarkly-java-demo-app --sdk-key $LD_SDK_KEY --app-logger com.example

                  # Diagnose a flag that is not behaving
                  launchdarkly-java-demo-app doctor --sdk-key $LD_SDK_KEY

                Run via Gradle with:  ./gradlew run --args="doctor"
                """);
    }
}

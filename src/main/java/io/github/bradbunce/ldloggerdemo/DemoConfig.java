package io.github.bradbunce.ldloggerdemo;

import com.launchdarkly.sdk.LDContext;
import java.util.Map;
import java.util.Optional;

/**
 * Demo settings. See {@code .env.example} and {@code --help}.
 *
 * <p>Resolved from one place: a command-line override, else the environment
 * variable of the same name, else a built-in default.
 *
 * <p>With no {@code LD_SDK_KEY} the demo runs offline against the SDK's
 * {@code TestData} source, which still exercises the real flag-change path.
 */
record DemoConfig(
        Optional<String> sdkKey,
        String consoleLogFlagKey,
        String sdkLogFlagKey,
        String contextKey,
        String environment,
        Optional<String> applicationLoggerName,
        Optional<String> sdkLoggerName,
        int intervalSeconds) {

    static DemoConfig resolve(Map<String, String> overrides) {
        Settings settings = new Settings(overrides);
        return new DemoConfig(
                settings.get("LD_SDK_KEY").filter(key -> !key.isBlank()),
                settings.get("LD_CONSOLE_LOG_FLAG_KEY").orElse("console-log-level"),
                settings.get("LD_SDK_LOG_FLAG_KEY").orElse("sdk-log-level"),
                settings.get("LD_CONTEXT_KEY").orElse("checkout-service"),
                settings.get("LD_ENV").orElse("development"),
                settings.get("LD_APP_LOGGER_NAME").filter(name -> !name.isBlank()),
                settings.get("LD_SDK_LOGGER_NAME").filter(name -> !name.isBlank()),
                settings.getInt("DEMO_INTERVAL_SECONDS", 5));
    }

    /** Whether to connect to LaunchDarkly rather than drive flags locally. */
    boolean isLive() {
        return sdkKey.isPresent();
    }

    /**
     * The context log levels are evaluated against. Targeting rules can vary the
     * level by environment, region or host without any code change here.
     */
    LDContext context() {
        return LDContext.builder(contextKey)
                .kind("service")
                .set("env", environment)
                .build();
    }

    /** Command line first, then environment, then the caller's default. */
    private record Settings(Map<String, String> overrides) {

        Optional<String> get(String name) {
            return Optional.ofNullable(overrides.get(name))
                    .or(() -> Optional.ofNullable(System.getenv(name)));
        }

        int getInt(String name, int fallback) {
            return get(name)
                    .filter(value -> !value.isBlank())
                    .map(value -> {
                        try {
                            return Integer.parseInt(value.trim());
                        } catch (NumberFormatException e) {
                            System.out.printf("  ! %s=%s is not a number, using %d%n",
                                    name, value, fallback);
                            return fallback;
                        }
                    })
                    .orElse(fallback);
        }
    }
}

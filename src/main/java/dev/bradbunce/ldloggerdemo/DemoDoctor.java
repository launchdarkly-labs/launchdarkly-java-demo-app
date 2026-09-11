package dev.bradbunce.ldloggerdemo;

import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.LDClient;
import dev.bradbunce.ldlogger.ConsoleLogLevel;
import dev.bradbunce.ldlogger.LDLogLevelController;
import dev.bradbunce.ldlogger.LogLevelBridge;
import dev.bradbunce.ldlogger.SdkLogLevel;
import java.util.Optional;
import org.slf4j.LoggerFactory;

/**
 * Prints diagnostics for the failure modes that look like bugs.
 *
 * <p>It reads each flag as a raw {@code LDValue} rather than through
 * {@code intVariation}/{@code stringVariation}, so it can report the value's
 * actual JSON type. That is what distinguishes the single most common setup
 * mistake - a numeric flag whose variations are strings - from a flag that is
 * simply missing.
 */
final class DemoDoctor {

    private DemoDoctor() {
        throw new AssertionError("No instances");
    }

    static void report(DemoConfig config, LDClient client) {
        LogLevelBridge bridge = LogLevelBridge.detect();

        System.out.println("=".repeat(72));
        System.out.println(" LaunchDarkly Java Logger - diagnostics");
        System.out.println("=".repeat(72));
        System.out.printf(" mode                %s%n",
                config.isLive() ? "live (streaming)" : "offline (TestData)");
        System.out.printf(" SLF4J binding       %s%n",
                LoggerFactory.getILoggerFactory().getClass().getName());
        System.out.printf(" detected backend    %s (%s)%n",
                bridge.backendName(), bridge.isAvailable() ? "available" : "NOT AVAILABLE");
        System.out.printf(" client initialized  %s%n", client.isInitialized());
        System.out.printf(" context             service:%s (env=%s)%n",
                config.contextKey(), config.environment());
        System.out.printf(" application logger  %s%n",
                config.applicationLoggerName().orElse("<root>"));
        System.out.printf(" sdk logger          %s%n",
                config.sdkLoggerName().orElse(LDLogLevelController.DEFAULT_SDK_LOGGER_NAME));
        System.out.println("-".repeat(72));

        boolean healthy = bridge.isAvailable();
        if (!bridge.isAvailable()) {
            System.out.println(" ! No supported SLF4J backend is bound, so no level can be");
            System.out.println("   applied. Add logback-classic, log4j-slf4j2-impl with");
            System.out.println("   log4j-core, or slf4j-jdk14 to the runtime classpath.");
            System.out.println("-".repeat(72));
        }

        healthy &= reportConsoleFlag(config, client);
        healthy &= reportSdkFlag(config, client);

        System.out.println("-".repeat(72));
        System.out.println(healthy
                ? " All checks passed."
                : " Some checks failed - see the notes above.");
    }

    private static boolean reportConsoleFlag(DemoConfig config, LDClient client) {
        LDValue raw = client.jsonValueVariation(
                config.consoleLogFlagKey(), config.context(), LDValue.ofNull());
        System.out.printf("%n %s%n", config.consoleLogFlagKey());
        System.out.printf("   raw value         %s (%s)%n", raw.toJsonString(), raw.getType());

        if (raw.isNull()) {
            System.out.println("   status            NOT FOUND - the flag does not exist, is not");
            System.out.println("                     available to this context, or the key is wrong.");
            System.out.println("                     The library would fall back to its default.");
            return false;
        }
        if (raw.getType() != LDValueType.NUMBER) {
            System.out.printf("   status            WRONG TYPE - expected NUMBER, got %s.%n",
                    raw.getType());
            System.out.println("                     This flag is read with intVariation, which does");
            System.out.println("                     not coerce. Recreate it as a number flag with");
            System.out.println("                     variations 0-5.");
            return false;
        }
        Optional<ConsoleLogLevel> level = ConsoleLogLevel.fromFlagValue(raw.intValue());
        if (level.isEmpty()) {
            System.out.println("   status            OUT OF RANGE - valid values are 0-5");
            System.out.println("                     (FATAL=0, ERROR=1, WARN=2, INFO=3, DEBUG=4, TRACE=5)");
            return false;
        }
        System.out.printf("   resolves to       %s, applied as %s%n",
                level.get(), level.get().logLevel());
        System.out.println("   status            OK");
        return true;
    }

    private static boolean reportSdkFlag(DemoConfig config, LDClient client) {
        LDValue raw = client.jsonValueVariation(
                config.sdkLogFlagKey(), config.context(), LDValue.ofNull());
        System.out.printf("%n %s%n", config.sdkLogFlagKey());
        System.out.printf("   raw value         %s (%s)%n", raw.toJsonString(), raw.getType());

        if (raw.isNull()) {
            System.out.println("   status            NOT FOUND - the flag does not exist, is not");
            System.out.println("                     available to this context, or the key is wrong.");
            return false;
        }
        if (raw.getType() != LDValueType.STRING) {
            System.out.printf("   status            WRONG TYPE - expected STRING, got %s.%n",
                    raw.getType());
            System.out.println("                     This flag is read with stringVariation. Use the");
            System.out.println("                     strings error, warn, info or debug.");
            return false;
        }
        Optional<SdkLogLevel> level = SdkLogLevel.fromFlagValue(raw.stringValue());
        if (level.isEmpty()) {
            System.out.println("   status            UNRECOGNIZED - valid values are error, warn,");
            System.out.println("                     info, debug. Note there is no 'trace': the SDK's");
            System.out.println("                     own LDLogLevel has no TRACE level.");
            return false;
        }
        System.out.printf("   resolves to       %s, applied as %s%n",
                level.get(), level.get().logLevel());
        System.out.println("   status            OK");

        if (!config.isLive()) {
            System.out.println("   note              offline mode produces little SDK output, so");
            System.out.println("                     this flag's effect is hard to see without a key.");
        }
        return true;
    }
}

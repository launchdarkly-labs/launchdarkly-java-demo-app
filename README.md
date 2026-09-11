# LaunchDarkly Java Demo App

A sample app for [launchdarkly-java-logger](https://github.com/bradbunce/launchdarkly-java-logger).
It logs at every level on a timer while the library drives those levels from
feature flags, so you can watch a running process change its own log verbosity
without a restart.

## Quick Start

No LaunchDarkly credentials needed. First install the logger into your local
Maven repository from its checkout:

```bash
cd ../launchdarkly-java-logger
./gradlew publishToMavenLocal
```

Then run the demo:

```bash
./gradlew run
```

With no `LD_SDK_KEY` set, the app drives the flags itself through the SDK's
`TestData` source, cycling through every level so the whole 0-5 range is
exercised. Press Ctrl-C to stop, or type `q`.

`./gradlew run --args="--help"` lists every command and option.

This is not a shortcut around the real code path. A `TestData` update goes
through the SDK's data store and fires the same `FlagTracker` change events a
streaming update from LaunchDarkly would, so the controller cannot tell the
difference — it just needs no credentials.

## Live Mode

Put a server-side SDK key in `.env` and the app streams from LaunchDarkly
instead. There is nothing to export - Gradle reads `.env` itself:

```bash
cp .env.example .env    # then fill in LD_SDK_KEY
./gradlew run
```

`.env` is gitignored, so a real key stays out of the repository.

Create the two flags first, matching the keys you configured:

| Flag | Type | Values |
| --- | --- | --- |
| `console-log-level` | number | `0`-`5` (FATAL=0, ERROR=1, WARN=2, INFO=3, DEBUG=4, TRACE=5) |
| `sdk-log-level` | string | `error`, `warn`, `info`, `debug` |

Now toggle either flag in the LaunchDarkly UI and watch this process react
within a second or two. These are the same two flags the React logger uses.

## The CLI

Two commands, and everything configurable by flag as well as by environment
variable. A command-line option shadows the environment variable of the same
name, which shadows the built-in default.

```bash
./gradlew run                    # the demo, reading .env automatically
./gradlew doctor                 # diagnostics, then exit
./gradlew run --args="--help"
./gradlew run --args="--interval 1"
./gradlew run --args="--app-logger com.example.checkout"
```

Installed as a script, it is less noisy than going through Gradle:

```bash
./gradlew installDist
build/install/launchdarkly-java-demo-app/bin/launchdarkly-java-demo-app doctor
```

### `doctor`

Run it with `./gradlew doctor`. It diagnoses the failure modes that otherwise
look like bugs, then exits. It reads
each flag as a raw `LDValue`, so it can report the value's actual JSON type -
which is what separates "the flag is the wrong type" from "the flag is missing":

```
 console-log-level
   raw value         0 (NUMBER)
   resolves to       FATAL, applied as FATAL
   status            OK

 sdk-log-level
   raw value         "info" (STRING)
   resolves to       INFO, applied as INFO
   status            OK
```

It also reports the bound SLF4J backend, whether a usable bridge was detected,
and whether the client initialized. Start here when something is not working.

### Stopping

`q`, Ctrl-C and `SIGTERM` all shut down the same way: the controller
unsubscribes from flag changes, then the client closes, and a summary prints.

```
------------------------------------------------------------------------
 Shutting down
   ticks            7
   log rounds       9
   final console    TRACE (flag 5)
   final sdk        debug
   backend          Logback
------------------------------------------------------------------------
15:32:13.780 INFO  LDClient - Closing LaunchDarkly Client
 Done.
```

If the SDK log flag is at `debug`, closing the client would otherwise dump a full
analytics event payload as the last thing on screen, so the demo raises the SDK
logger to INFO on the way out - which is also a small demonstration of using
`LogLevelBridge` directly.

### While running

The demo logs on a timer, but waiting for the next tick after toggling a flag is
tedious, so it also takes single-letter commands:

| Key | Does |
| --- | --- |
| `l` | Log one round of messages at every level, right now |
| `s` | Print current levels, backend and configuration |
| `h` | List these commands |
| `q` | Quit cleanly |

Pass `--no-interactive` to skip reading the terminal. The demo also degrades
quietly on its own when stdin is not a terminal, such as in CI.

## What To Look For

```
--- tick 4 | console DEBUG (flag 4) | sdk error | emitting ERROR WARN INFO DEBUG ---
13:48:25.080 DEBUG CheckoutService - Cart contents resolved for ord-1004: 3 items, $148.20
13:48:25.080 INFO  CheckoutService - Checkout completed for ord-1004
13:48:25.081 WARN  CheckoutService - Address for ord-1004 looks unverified; continuing anyway
13:48:25.081 ERROR CheckoutService - Payment gateway returned a retryable error for ord-1004
>> console log level is now TRACE (flag value 5, applied as SLF4J TRACE)
```

- **`CheckoutService` knows nothing about LaunchDarkly.** It holds an ordinary
  `private static final Logger`, captured when the class loaded — before the demo
  had even built an `LDClient`. It still responds to every flag change, because
  the library sets the level of the real logger rather than wrapping it.
- **The `emitting` list** is what the backend will actually output right now,
  read straight from SLF4J's `isDebugEnabled()`-style checks.
- **`>>` lines** come from the controller's change callbacks.
- **Flag value 0 (`FATAL`) emits as `ERROR`**, since SLF4J has no `FATAL` level.
  The tick banner still reports `FATAL`, so you can see both.

One honest caveat: the **SDK log level flag has little visible effect offline**,
because `TestData` is quiet once initialized. To see it work, use live mode and
set `sdk-log-level` to `debug` — the SDK's streaming and evaluation chatter will
start appearing.

## Configuration

All optional except `LD_SDK_KEY`, which selects live mode. See `.env.example`.

| Variable | Default | Description |
| --- | --- | --- |
| `LD_SDK_KEY` | none | Server-side SDK key. Unset means offline mode |
| `LD_CONSOLE_LOG_FLAG_KEY` | `console-log-level` | The numeric flag (0-5) |
| `LD_SDK_LOG_FLAG_KEY` | `sdk-log-level` | The string flag |
| `LD_CONTEXT_KEY` | `checkout-service` | Key of the `service`-kind context |
| `LD_ENV` | `development` | Set as the `env` attribute, for targeting rules |
| `LD_APP_LOGGER_NAME` | root logger | Logger the console flag controls |
| `LD_SDK_LOGGER_NAME` | `com.launchdarkly` | Logger the SDK flag controls |
| `DEMO_INTERVAL_SECONDS` | `5` | Seconds between ticks |

Each has a command-line equivalent - `--sdk-key`, `--console-flag`,
`--sdk-flag`, `--context-key`, `--env`, `--app-logger`, `--sdk-logger`,
`--interval`. See `--help`.

`LD_APP_LOGGER_NAME` is worth knowing about: by default the console flag sets the
**root** logger, so it affects every library in the app. Narrowing it to your own
package is usually what a real service wants.

For a quick non-interactive check, pipe the quit command in:

```bash
printf 'q\n' | ./gradlew run --args="--interval 1"
```

Settings resolve from the command line first, then `.env` / the environment,
then the built-in default.

## How It's Wired

Three things are worth copying into a real service:

1. **The SDK needs `LDSdkLogging.slf4j()`** in its `LDConfig`, or the SDK log
   level flag has nothing to act on. The Java SDK does not use SLF4J by default.
2. **The SDK and the logging backend are declared here, not inherited.** The
   logger declares both `compileOnly` on purpose, so it never forces a version on
   you. This app picks Logback; the library detects Log4j 2 and
   `java.util.logging` equally well, and the banner prints which backend it
   found. See [build.gradle.kts](build.gradle.kts).
3. **The controller is closed on shutdown**, via try-with-resources plus a
   shutdown hook. Ctrl-C or `SIGTERM` unsubscribes from flag changes and closes
   the client cleanly.

Log levels are evaluated against a single `service`-kind context, so targeting
rules can vary the level by environment, region, or host with no code change.

## Developing Against a Local Logger Checkout

By default the logger is resolved from `mavenLocal()`, which exercises the same
published artifact and POM a real consumer gets. While changing both projects at
once, build the logger from source instead:

```bash
./gradlew run -PuseLocalLogger
```

That turns the sibling `../launchdarkly-java-logger` checkout into an included
build, so library edits show up here with no republishing step.

## Troubleshooting

Run `doctor` first - it checks all of these and says which one applies.

| Symptom | Cause |
| --- | --- |
| `Could not find io.github.bradbunce:launchdarkly-java-logger` | Run `./gradlew publishToMavenLocal` in the logger checkout, or use `-PuseLocalLogger` |
| Banner says `backend none` and levels never change | No supported backend is bound to SLF4J. This app uses Logback; Log4j 2 and `java.util.logging` also work |
| `sdk-log-level` appears to do nothing | Either offline mode (expected) or the SDK is missing `LDSdkLogging.slf4j()` |
| Levels never change in live mode | Check the flag keys and that the flag is on for a `service`-kind context |

## Requirements

- Java 21 or later
- `launchdarkly-java-logger` 1.0.0 in `mavenLocal()`, or the sibling checkout

## License
MIT

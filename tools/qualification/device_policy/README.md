# Two-device policy qualification

The disposable Android instrumentation client exercises the real Room policy library, authenticated
carrier, proxy, simulator and breakpoint coordinator started by `DevicePolicySocketEndToEndTest`.
It never reads Companion credentials, changes pairing, installs a user CA, starts another VPN, or launches
the desktop application. Each fixture creates its own CA/credentials/database and origin listeners.
The client trusts only the fixture root supplied for that run.

Requirements: two authorized devices visible to ADB, Android SDK platform/build-tools 36, JDK 21, and
Kotlin 2.4.10 compiler artifacts in the Gradle cache. The Gradle task currently discovers ADB at its
standard local Homebrew path. Do not run concurrently with another invocation of this qualifier.

```sh
python3 tools/qualification/device_policy/build_probe.py /tmp/knet-device-policy-probe
KNET_POLICY_PROBE_APK=/tmp/knet-device-policy-probe/probe.apk \
KNET_POLICY_PROBE_REPORT=/tmp/knet-device-policy-physical-results.txt \
KNET_HALF_CLOSE_PROBE_REPORT=/tmp/knet-half-close-physical-results.txt \
./gradlew :products:desktop:devicePolicyPhysicalQualification --max-workers=1
```

The task installs `com.devuloopers.knet.policyqualifier`, forwards device ports 19291/19292 only to their disposable fixtures,
and measures inactive-versus-active sharing, independent simultaneous 100 kbps download/upload, payload
integrity and breakpoint ownership. Its `finally` block removes the exact reverse mapping and qualifier
package. Ordinary `:products:desktop:test` excludes this explicit hardware category without skipped tests.
Do not interpret these checks as physical verification of the desktop UI, the existing Companion control
session, Android TUN packet shaping, or an unavailable Apple provider. See the qualification report.

The same hardware category also runs `CompanionConnectionEofEndToEndTest`: two phones open and
half-close 24 authenticated raw tunnels whose origin intentionally never closes its output. A short
fixture-only idle grace must retire each tunnel with its exact cancellation cause, then repeatedly admit
new connections through a four-slot budget. This is physical coverage of TLS carrier/engine cleanup;
it does not replace a long run through the installed Companion VPN.

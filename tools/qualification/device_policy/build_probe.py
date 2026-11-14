"""Build an isolated Kotlin Android instrumentation client using installed SDK/cache tools."""
from pathlib import Path
import subprocess
import zipfile
import sys

source_directory = Path(__file__).parent
directory = Path(sys.argv[1]) if len(sys.argv) > 1 else source_directory / "build"
directory.mkdir(parents=True, exist_ok=True)
cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
sdk = Path.home() / "Library/Android/sdk"
tools = sdk / "build-tools/36.0.0"
android = sdk / "platforms/android-36/android.jar"

def jar(group, artifact, version):
    return next(path for path in (cache / group / artifact / version).glob("**/*.jar") if not any(value in path.name for value in ["-sources", "-javadoc", "-all"]))

stdlib = jar("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.10")
libraries = [stdlib]
compiler_paths = [jar("org.jetbrains.kotlin", name, "2.4.10") for name in ["kotlin-compiler-embeddable", "kotlin-build-tools-api", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-daemon-embeddable"]]
compiler_paths += [jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"), jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.8.0"), jar("org.jetbrains", "annotations", "23.0.0")]
subprocess.run(["java", "-Xmx512m", "-cp", ":".join(map(str, compiler_paths)), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "11", "-classpath", ":".join(map(str, [android] + libraries)), "-d", str(directory / "probe-classes.jar"), str(source_directory / "Probe.kt")], check=True)
resources = directory / "res/xml"
resources.mkdir(parents=True, exist_ok=True)
(resources / "network_security_config.xml").write_text('<network-security-config><base-config cleartextTrafficPermitted="true"><trust-anchors><certificates src="system"/><certificates src="user"/></trust-anchors></base-config></network-security-config>')
manifest = directory / "AndroidManifest.xml"
manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.devuloopers.knet.policyqualifier"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/><uses-permission android:name="android.permission.INTERNET"/><application android:label="KNet qualification client" android:networkSecurityConfig="@xml/network_security_config" android:allowBackup="false"><activity android:name="com.devuloopers.knet.policyqualifier.ProbeActivity" android:exported="true"/></application><instrumentation android:name="com.devuloopers.knet.policyqualifier.Probe" android:targetPackage="com.devuloopers.knet.policyqualifier" android:functionalTest="true"/></manifest>''')
subprocess.run([str(tools / "aapt2"), "compile", "--dir", str(directory / "res"), "-o", str(directory / "resources.zip")], check=True)
subprocess.run([str(tools / "aapt2"), "link", "-I", str(android), "--manifest", str(manifest), "-o", str(directory / "probe-unsigned.apk"), str(directory / "resources.zip")], check=True)
dex = directory / "dex"
dex.mkdir(exist_ok=True)
subprocess.run([str(tools / "d8"), "--min-api", "26", "--lib", str(android), "--output", str(dex), str(directory / "probe-classes.jar")] + list(map(str, libraries)), check=True)
with zipfile.ZipFile(directory / "probe-unsigned.apk", "a") as output:
    for path in dex.glob("*.dex"):
        output.write(path, path.name)
subprocess.run([str(tools / "zipalign"), "-f", "4", str(directory / "probe-unsigned.apk"), str(directory / "probe-aligned.apk")], check=True)
subprocess.run([str(tools / "apksigner"), "sign", "--ks", str(Path.home() / ".android/debug.keystore"), "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", str(directory / "probe.apk"), str(directory / "probe-aligned.apk")], check=True)
print("Built isolated qualification APK.")

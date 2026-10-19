# Third-party components

- Lucide 0.468.0 (ISC), bundled in assets/lucide.min.js. License: assets/LUCIDE-LICENSE.txt.
- Android JavaMail 1.6.7 and Activation 1.6.7 (CDDL 1.1 / GPLv2 with Classpath Exception), downloaded for builds. Project and license: https://github.com/javaee/javamail and https://github.com/javaee/activation.
- linkedom 0.18.12 (ISC), used only by UI tests. License supplied in the npm package.
- org.json 20240303 (public domain), used only by Java tests. https://github.com/stleary/JSON-java

- OkHttp 4.12.0 and Okio JVM 3.6.0 (Apache 2.0): https://github.com/square/okhttp and https://github.com/square/okio.
- Kotlin standard libraries 1.9.10 (Apache 2.0): https://github.com/JetBrains/kotlin. Used by OkHttp/Okio; jar versions and SHA-256 are in dependencies.lock.json.

- lib-recur 0.17.1, rfc5545-datetime 0.3 and jems2 2.23.1 (Apache 2.0), used for local recurrence expansion: https://github.com/dmfs/lib-recur, https://github.com/dmfs/rfc5545-datetime and https://github.com/dmfs/jems. License: assets/APACHE-2.0-LICENSE.txt. Versions and checksums are fixed in dependencies.lock.json.
- Playwright 1.56.1 (Apache 2.0), used only by browser layout tests. https://github.com/microsoft/playwright


- esbuild 0.28.2 (MIT), build-time only; not bundled into the Android APK. Project/license: https://github.com/evanw/esbuild.

## Gradle wrapper

Gradle 8.9 wrapper (Apache-2.0), from https://github.com/gradle/gradle/tree/v8.9.0.
Wrapper jar SHA-256: `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`, verified against the official services.gradle.org checksum.
The Gradle distribution checksum is pinned in native-ui/gradle/wrapper/gradle-wrapper.properties.

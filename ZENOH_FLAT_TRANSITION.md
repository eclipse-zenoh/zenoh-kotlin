# zenoh-flat transition

This branch (`zenoh-flat-transition`) is the **integration branch** for rebuilding
zenoh-kotlin on top of the generated JNI/Kotlin bindings, replacing the external
`zenoh-jni-runtime` dependency (zenoh-java `common-jni` branch) that the base
`external-jni` branch introduced. It exists so the transition can land as a series
of reviewable PRs targeting this branch instead of `main`; when the transition is
complete, this branch merges to `main` as a whole (and this file is removed).

## Architecture

```
zenoh (Rust)
  └─ zenoh-flat              flat #[prebindgen]-annotated Rust API
       └─ zenoh-flat-jni     generated JNI externs + Kotlin classes (prebindgen-jni JniGen)
            └─ zenoh-kotlin  Kotlin SDK wrapper (this repo)
```

- **prebindgen** — <https://github.com/milyin/prebindgen> (generator)
- **zenoh-flat** — <https://github.com/eclipse-zenoh/zenoh-flat> (flat Rust API)
- **zenoh-flat-jni** — <https://github.com/eclipse-zenoh/zenoh-flat-jni> (generated
  bindings, consumed as the Maven artifact `org.eclipse.zenoh:zenoh-flat-jni`;
  CI and local coordinated development substitute a sibling checkout through a
  Gradle composite build with `-PuseLocalFlatJni=true`)

The same `zenoh-flat-jni` artifact is the shared tier for **both** zenoh-java
(see its `zenoh-flat-transition` branch) and zenoh-kotlin: generated typed
wrappers plus hand-written shared logic (`SerializationCodec`, `EncodingCodec`,
`ZenohIdCodec`, native-library loading). This repo keeps only the established
`Result`-based public API facade.

## Error model

zenoh-flat-jni **never throws**: a fallible generated wrapper takes trailing
error-sink arguments and *returns* a sink's value on failure. Since #673 there
are two channels — `onBindingError` (a `JniErrorHandler`: UTF-8 decode, closed
handle, …) and `onError` (the typed domain `ErrorHandler` carrying the
decomposed zenoh message); wrappers that can only fail in the binding take just
the first. Because zenoh-kotlin's public API is `Result`-based, the SDK records
the failure directly inside the sink — a native error never crosses the
boundary as an exception, so nothing is caught-and-rewrapped.

The `zCall*` helpers additionally run the whole call block inside
`runCatching`: JVM-side exceptions (argument preparation, user-supplied
`IntoZBytes.into()` conversions, native-library loading during class init)
surface as `Result.failure`, preserving the public `Result` contract of the
pre-flat API on `main`. That `runCatching` never observes a native error —
the sink reports those without throwing.

## Constituent PRs

| PR | Scope | Status |
| --- | --- | --- |
| [#651](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/651) | `external-jni`: drop the in-repo `zenoh-jni` crate for an external runtime artifact | merged |
| [#668](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/668) | Port zenoh-kotlin to the zenoh-flat-jni generated bindings | merged |
| [#670](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/670) | `Parameters` becomes a facade over the shared string-backed implementation (Rust `parameters.rs` semantics: no percent-decoding, infallible parse, first-match-wins get) | merged |
| [#673](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/673) | Migrate to the split error-handler API (`onBindingError` + typed `onError`) | merged |
| [#675](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/675) | Serialize via the pure-Kotlin `SerializationCodec` — no JNI crossing | merged |
| [#676](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/676) | Add the Gradle wrapper (8.12.1) | merged |
| [#678](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/678) | Advanced pub/sub: `AdvancedPublisher`/`AdvancedSubscriber`, matching + sample-miss listeners | merged |
| `5977bb1` | Realign with zenoh-flat HEAD; a timestamp carries its clock's id | direct commit |
| [#692](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/692) | CI: build zenoh-flat-jni against published prebindgen | merged |
| [#695](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/695) | Release preparation: repair the release path, document publishing | merged |

Serialization ended up **entirely off the JNI path**: `zSerialize`/`zDeserialize`
build a `SerializationCodec.SerdeType` from the full `KType` (`KTypeSerde.kt`)
and run the shared pure-Kotlin codec in zenoh-flat-jni, which is byte-identical
to the native serializer. The earlier plan — a KType-aware serializer reached
*through* JNI — was superseded by #675.

Remaining before this branch merges to `main`:

- Bump the CI pins to the final upstream `zenoh-flat` / `zenoh-flat-jni` commits.
- Release `org.eclipse.zenoh:zenoh-flat-jni` so `gradle.properties` can name a
  published version rather than a rehearsal snapshot (see `PUBLISHING.md`).
- Delete this file.

## CI pinning

`.github/workflows/ci.yml` on the constituent branches pins the exact
`zenoh-flat-jni` / `zenoh-flat` commits the code was written against, while
`prebindgen` resolves from crates.io (since #692). Pins are bumped as the
upstream PRs land.

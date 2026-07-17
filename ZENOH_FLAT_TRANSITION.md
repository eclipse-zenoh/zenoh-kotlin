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
       └─ zenoh-flat-jni     generated JNI externs + Kotlin classes (prebindgen lang::JniGen)
            └─ zenoh-kotlin  Kotlin SDK wrapper (this repo)
```

- **prebindgen** — <https://github.com/milyin/prebindgen> (generator)
- **zenoh-flat** — <https://github.com/ZettaScaleLabs/zenoh-flat> (flat Rust API)
- **zenoh-flat-jni** — <https://github.com/ZettaScaleLabs/zenoh-flat-jni> (generated bindings,
  consumed as a sibling checkout in CI and via Gradle composite build locally;
  as a Maven artifact once published)

The same `zenoh-flat-jni` artifact is the shared tier for **both** zenoh-java
(see its `zenoh-flat-transition` branch) and zenoh-kotlin: generated typed
wrappers plus hand-written shared logic (`EncodingCodec`, `ZenohIdCodec`,
native-library loading). This repo keeps only the established `Result`-based
public API facade.

## Error model

zenoh-flat-jni **never throws**: every fallible generated wrapper takes a
trailing error-sink argument and *returns* `onError.run(...)` on failure.
Because zenoh-kotlin's public API is `Result`-based, the SDK builds
`Result.failure` directly inside the sink — there is no `try`/`catch` or
`runCatching` anywhere on the JNI path.

## Constituent PRs

| PR | Scope | Status |
| --- | --- | --- |
| [#668](https://github.com/eclipse-zenoh/zenoh-kotlin/pull/668) | Port zenoh-kotlin to zenoh-flat-jni generated bindings | open |

Planned follow-ups on this branch:

- KType-aware serializer in the zenoh-flat-jni shared tier
  (restores `UByte`/`UShort`/`UInt`/`ULong`/`Pair`/`Triple` support in
  `zSerialize`/`zDeserialize`).
- Advanced pub/sub (`AdvancedPublisher`/`AdvancedSubscriber`, matching and
  sample-miss listeners) surface in zenoh-flat → zenoh-flat-jni, replacing the
  temporary failing stubs.
- Release packaging (multi-platform native bundling, publish workflows).

## CI pinning

`.github/workflows/ci.yml` on the constituent branches pins the exact
`zenoh-flat-jni` / `zenoh-flat` commits the code was written against, while
`prebindgen` resolves from its `main`. Pins are bumped as the upstream PRs land.

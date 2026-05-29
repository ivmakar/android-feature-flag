<!-- Commit/PR format: `ACT scope -- what`. Codes: ADD UPD DEL FEA FAC FIX FRM DOC. -->

## What

<!-- What changed. -->

## Why

<!-- The reason / linked issue. -->

Closes #

## Checklist

- [ ] `./gradlew :feature-flags:testDebugUnitTest` passes (the gate)
- [ ] `./gradlew :feature-flags:lint` clean
- [ ] New behavior has tests; bug fixes have a regression test that fails before the fix
- [ ] New `Feature` subtype (if any) has a `FeatureCodec` branch + round-trip tests
- [ ] Stays app-agnostic — no `:app`/vendor references in code or comments

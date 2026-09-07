# Distribution assets

Each app's directory contains its public product metadata and master icon:

- `icon.png`: app artwork used by distribution channels.
- `store-listing/en-US.md`, `de-DE.md`, `es-ES.md`: localized public store copy.
- `zapstore.yaml`, where present: app metadata consumed by Zapstore tooling.

Keep names, capabilities, identifiers, screenshots, and legal links accurate for
the owning app. Maintain English, German, and Spanish copy together. Screenshots
must depict the current native UI; do not reuse retired multi-wallet screens.

Rayl's distribution identifier is `com.nicolasusca.rayl`. Public privacy policies
and terms live under [`docs/legal`](../docs/legal).

`app-signing-certificate.sha256` is a build/distribution input verified by the APK
tooling. Changes to signing identity require explicit owner review.

# Distribution assets

Each app owns its public metadata and master icon under `distribution/<app>/`.
Store screenshots must be captured from the final Release candidates; do not
reuse the removed legacy multi-wallet screenshots. A new app's public
distribution directory must be reviewed and completed before its first public
candidate is published.

Before public release:

1. Add the suite Zapstore publisher `pubkey` to every app's Zapstore
   configuration.
2. Capture current phone screenshots in English, German, and Spanish.
3. Export each store's required screenshot sizes from those captures without
   changing the represented UI.
4. Have the account owner review the privacy and financial-feature answers.
5. Record the Play app-signing certificate SHA-256 and artifact SHA-256 in the
   copy of `RELEASE_EVIDENCE_TEMPLATE.md`.

Rayl’s draft metadata lives in `distribution/rayl`. Its distribution application
and bundle identifier is `com.nicolasusca.rayl`. No candidate has been published
as part of adding this app.

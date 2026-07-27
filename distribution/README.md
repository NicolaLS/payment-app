# Distribution assets

`blip/` and `lasr/` contain the app-specific public metadata and master icons.
Store screenshots must be captured from the final 1.0 Release candidates; do
not reuse the removed legacy multi-wallet screenshots.

Before public release:

1. Add the suite Zapstore publisher `pubkey` to both Zapstore configurations.
2. Capture current phone screenshots in English, German, and Spanish.
3. Export each store's required screenshot sizes from those captures without
   changing the represented UI.
4. Have the account owner review the privacy and financial-feature answers.
5. Record the Play app-signing certificate SHA-256 and artifact SHA-256 in the
   copy of `RELEASE_EVIDENCE_TEMPLATE.md`.

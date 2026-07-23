# Foundation UI

`foundation/ui` owns the RAYL product presentation: themes, resources,
components, complete screens, and UI state contracts.

During the first extraction it intentionally contains UI that may later prove
to be Blip- or Lasr-specific. Visual ownership is centralized first; provider
ownership is classified only after both extracted apps use the same baseline.

The frozen `papp-final` presentation is the source of truth. Extraction work
may adapt state and callbacks underneath it, but must not redesign its layouts,
controls, navigation journeys, theme, typography, wording, or animation.

#!/bin/zsh
set -euo pipefail

# Only do this on Xcode Cloud
if [ "${CI_XCODE_CLOUD:-}" = "TRUE" ]; then
  echo "== ci_post_clone.sh: installing OpenJDK 17 via Homebrew =="
  brew install openjdk@17
fi


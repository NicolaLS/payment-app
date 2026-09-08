#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<'EOF'
Usage: scripts/build-hub-ios-debug.sh

Build Rayl Debug for an arm64 iOS simulator against a local Hub backend.
Use JDK 21 from the caller's environment.

Environment:
  RAYL_HUB_BASE_URL          Default: http://127.0.0.1:8080
  RAYL_HUB_IOS_DERIVED_DATA  Default: build/hub-ios (relative to repository root)
EOF
}

if [[ $# -ne 0 ]]; then
    if [[ $# -eq 1 && "$1" == "--help" ]]; then
        usage
        exit 0
    fi
    usage >&2
    exit 64
fi

hub_repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
hub_derived_data="${RAYL_HUB_IOS_DERIVED_DATA:-build/hub-ios}"
if [[ "$hub_derived_data" != /* ]]; then
    hub_derived_data="$hub_repo/$hub_derived_data"
fi
export RAYL_HUB_BASE_URL="${RAYL_HUB_BASE_URL:-http://127.0.0.1:8080}"

hub_temp_dir="$(mktemp -d "${TMPDIR:-/tmp}/rayl-hub-ios.XXXXXX")"
hub_temp_dir="$(cd "$hub_temp_dir" && pwd -P)"
hub_temp_plist="$hub_temp_dir/Info.plist"
trap 'rm -f -- "$hub_temp_plist"; rmdir -- "$hub_temp_dir"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# iOS 17+ supports exact IP entries in NSExceptionDomains. Keep these exceptions
# in a temporary Debug input; the app's committed plist is never changed.
# https://developer.apple.com/documentation/bundleresources/information-property-list/nsapptransportsecurity/nsexceptiondomains
python3 - "$hub_repo/apps/rayl/iosApp/iosApp/Info.plist" "$hub_temp_plist" <<'PY'
import plistlib
import sys

with open(sys.argv[1], "rb") as source:
    plist = plistlib.load(source)
plist["NSAppTransportSecurity"] = {
    "NSExceptionDomains": {
        host: {"NSExceptionAllowsInsecureHTTPLoads": True}
        for host in ("localhost", "127.0.0.1")
    }
}
with open(sys.argv[2], "wb") as destination:
    plistlib.dump(plist, destination, sort_keys=False)
PY

xcodebuild \
    -project "$hub_repo/apps/rayl/iosApp/iosApp.xcodeproj" \
    -scheme iosApp \
    -configuration Debug \
    -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$hub_derived_data" \
    ARCHS=arm64 \
    ONLY_ACTIVE_ARCH=YES \
    CODE_SIGNING_ALLOWED=NO \
    "INFOPLIST_FILE=$hub_temp_plist" \
    "RAYL_HUB_BASE_URL=$RAYL_HUB_BASE_URL" \
    build

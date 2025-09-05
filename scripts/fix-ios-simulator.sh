#!/usr/bin/env bash
set -euo pipefail

# fix-ios-simulator.sh
# Helper to resolve CoreSimulator service version mismatches and common iOS Simulator issues.
# Usage:
#   ./scripts/fix-ios-simulator.sh                       # just reset services/simulators/DerivedData
#   sudo ./scripts/fix-ios-simulator.sh /Applications/Xcode.app         # also switch xcode-select
#   sudo ./scripts/fix-ios-simulator.sh /Applications/Xcode_16.2.app    # switch to a specific Xcode

XCODE_PATH="${1:-}"

function info() { echo "[fix-ios-simulator] $*"; }

info "Current xcode-select: $(xcode-select -p 2>/dev/null || echo 'unknown')"
if [[ -n "${XCODE_PATH}" ]]; then
  info "Switching active Xcode to: ${XCODE_PATH}"
  xcode-select -s "${XCODE_PATH}"
  info "Now using: $(xcode-select -p)"
fi

info "Killing Simulator app and CoreSimulator services (ignore 'No matching processes' messages)"
killall -9 Simulator 2>/dev/null || true
# CoreSimulator service names vary by OS; try multiple
killall -9 com.apple.CoreSimulator.CoreSimulatorService 2>/dev/null || true
launchctl remove com.apple.CoreSimulator.CoreSimulatorService 2>/dev/null || true

info "Shutting down all booted simulators"
xcrun simctl shutdown all 2>/dev/null || true

info "Erasing all simulators (this removes device data; skip if you want to preserve)"
xcrun simctl erase all 2>/dev/null || true

DERIVED="$HOME/Library/Developer/Xcode/DerivedData"
if [[ -d "${DERIVED}" ]]; then
  info "Removing DerivedData at ${DERIVED}"
  rm -rf "${DERIVED}"
fi

info "Printing Xcode and CoreSimulator diagnostics"
xcodebuild -version || true
xcrun simctl list runtimes || true
xcrun simctl list devices || true

info "Done. Recommended next steps:"
cat <<'EOF'
1) Reopen Xcode and the Simulator (or run: open -a Simulator)
2) In Android Studio: Tools > Kotlin Multiplatform > Sync project (or ./gradlew clean :composeApp:podInstall if applicable)
3) Re-run the iOS app. Ensure the Simulator runtime matches your active Xcode.

If the error persists, ensure only one Xcode is active (xcode-select -p) and no auto-updates are running.
EOF

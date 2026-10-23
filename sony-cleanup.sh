#!/bin/sh
# Turns off the ads, promos and dead services that Sony BRAVIA Android TVs ship with, asking first
# for each one. Apps are turned off, never uninstalled, and saved to the same list as install.sh,
# so undo.sh turns them all back on.
#
#   sh sony-cleanup.sh <TV_IP>          asks y/N for each app
#   YES=1 sh sony-cleanup.sh <TV_IP>    turns off every app below that is installed
#
# Run install.sh first. Sony's stock home screen breaks ("Content failed to load") without
# Google's recommendations app, so that one is only offered once Home opens the new home screen.
# Checked on an XBR-55X900C (2015, Android 8.0). Apps your TV doesn't have are skipped.
set -e

TARGET="$1"
[ -n "$TARGET" ] || { echo "Usage: sh sony-cleanup.sh <TV_IP>" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb is missing. Mac: brew install android-platform-tools" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || {
  echo "Can't reach $TV. Check the TV is on, on the same Wi-Fi, and has USB/network debugging on." >&2; exit 1; }

sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
LIST=/data/local/tmp/tvhome-disabled.txt
RECS=com.google.android.tvrecommendations

# package|what it is. Only apps that were turned off on a real BRAVIA with no side effects
# (other than the recommendations note above).
APPS='com.google.android.tvrecommendations|Recommended and sponsored rows on the stock home screen
com.sony.dtv.sonyselect|Sony Select, a row of promoted apps
com.sony.dtv.sonyselect.overlay|Sony Select overlay
com.sony.dtv.discovery|Sony Discover bar (content suggestions)
com.google.android.play.games|Google Play Games
com.google.android.music|Google Play Music (shut down)
com.google.android.videos|Google Play Movies (replaced by Google TV)
com.crackle.androidtv|Crackle (shut down)
air.com.vudu.air.DownloaderTablet|Vudu
com.sony.rvu|DirecTV client (RVU)
com.sony.dtv.imanual|i-Manual, the built-in manual
com.sony.dtv.smarthelp|Smart Help
com.sony.dtv.customersupport|Customer Support
com.google.android.tvtutorials|Android TV tutorials
com.sony.dtv.touchpad.tutorial|Touchpad remote tutorial
com.google.android.feedback|Google feedback
com.google.android.tv.bugreportsender|Bug report sender
com.android.printspooler|Printing
com.bigflix.Movies|BigFlix (partner app)
com.cellfishmedia.moods|Moods (partner app)
com.epix.epix|Epix (partner app)
com.eros.now|Eros Now (partner app)
com.opera.tv.browser.sony.dia|Opera TV browser (partner app)'

home=$(sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
printf '%s\n' "$APPS" | while IFS='|' read -r pkg what; do
  sh_tv pm list packages "$pkg" | grep -Fqx "package:$pkg" || continue
  if sh_tv pm list packages -d "$pkg" | grep -Fqx "package:$pkg"; then echo "Already off: $what"; continue; fi
  if [ "$pkg" = "$RECS" ]; then
    case "$home" in com.google.android.tvlauncher/*)
      echo "Leaving on: $what. Sony's stock home screen needs it; run install.sh first."; continue ;;
    esac
  fi
  case "$(sh_tv dumpsys package "$pkg" | sed -n '/pkgFlags=\[/{p;q;}')" in
    *PERSISTENT*) echo "Leaving on: $what. Android keeps it running."; continue ;;
  esac
  if [ "$YES" = 1 ]; then answer=y; else
    printf 'Turn off %s (%s)? [y/N] ' "$what" "$pkg"
    read -r answer </dev/tty || answer=n
  fi
  case "$answer" in y|Y|yes) ;; *) continue ;; esac
  # Saved for undo.sh first; if it can't be saved, it stays on.
  if ! adb -s "$TV" shell "echo $pkg >> $LIST" </dev/null; then echo "Leaving $pkg on: couldn't save it for undo.sh." >&2; continue; fi
  sh_tv pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || true
  if sh_tv pm list packages -d "$pkg" | grep -Fqx "package:$pkg"; then
    echo "Turned off: $what"
  else
    echo "Couldn't turn off $pkg." >&2
  fi
done
echo "Done. undo.sh $TARGET turns everything back on. Restart the TV once to check it starts normally."

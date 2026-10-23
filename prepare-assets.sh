#!/bin/sh
# Copies CleanFrame's photos, blurred glass materials, brand marks and Manrope into assets/,
# sized for a 1920x1080 TV. Run once before build.sh; the results are checked in.
#   sh prepare-assets.sh <path to a CleanFrame checkout>
# Needs: sips (macOS), rsvg-convert (brew install librsvg), and a Python with fonttools + brotli
# (set PY=/path/to/python if it isn't python3).
set -e
cd "$(dirname "$0")"
CF="${1:?Usage: sh prepare-assets.sh <CleanFrame checkout>}/app/assets"
PY=${PY:-python3}
rm -rf assets; mkdir -p assets/wallpapers assets/glass assets/thumbs assets/marks assets/fonts assets/notices

# The same fifteen photos, in CleanFrame's order. Cropped to 16:9 around the centre, like its
# 50% 50% cover; the glass material is already 1920x1080 and lines up with that crop.
for id in $(sed -n 's/.*"id": "\([a-z]*\)".*/\1/p' "$CF/wallpapers/SOURCES.json"); do
  sips -s format jpeg -s formatOptions 82 --cropToHeightWidth 1080 1920 "$CF/wallpapers/$id.jpg" \
    --out "assets/wallpapers/$id.jpg" >/dev/null
  cp "$CF/materials/$id-field-blur.jpg" "assets/glass/$id.jpg"
  sips -s format jpeg -s formatOptions 80 --cropToHeightWidth 288 512 "$CF/wallpapers/$id-thumb.jpg" \
    --out "assets/thumbs/$id.jpg" >/dev/null
  sips -z 72 128 "assets/thumbs/$id.jpg" >/dev/null
  echo "$id" >> assets/wallpapers/order.txt
done

# White brand marks, by Android package. Apps without one show their own icon instead.
mark() { rsvg-convert -h 120 "$CF/icons/$1.svg" -o "assets/marks/$2.png"; }
mark youtube com.google.android.youtube.tv
mark netflix com.netflix.ninja
mark primevideo com.amazon.amazonvideo.livingroom
mark disneyplus com.disney.disneyplus
mark hbomax com.wbd.stream
mark appletv com.apple.atve.androidtv.appletv
mark spotify com.spotify.tv.android
mark tubi com.tubitv
mark stremio com.stremio.one

# Manrope is variable (200-800) and defaults to ExtraLight; Android 8 is happier with fixed weights.
"$PY" - "$CF/manrope.woff2" assets/fonts <<'EOF'
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
for name, wght in (("manrope-medium", 500), ("manrope-semibold", 600)):
    f = instantiateVariableFont(TTFont(sys.argv[1]), {"wght": wght})
    f.flavor = None
    f.save(f"{sys.argv[2]}/{name}.ttf")
EOF

cp "$CF/manrope-OFL.txt" assets/notices/manrope-OFL.txt
cp "$CF/icons/SIMPLE-ICONS-LICENSE.txt" "$CF/icons/CUSTOM-BRAND-ICONS-LICENSE.txt" "$CF/icons/SOURCES.json" assets/notices/
cp "$CF/wallpapers/SOURCES.json" assets/notices/photos.json
du -sh assets

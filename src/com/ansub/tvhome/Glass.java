package com.ansub.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

// CleanFrame's glass, without live blur: the dock shows its own slice of a blurred copy of the
// wallpaper (made ahead of time, the same size as the screen), then a scrim, a veil and a hairline
// along the top edge. The plate is the lighter glass behind the focused item.
final class Glass extends Drawable {
    private final Bitmap blur; // only the dock's slice, or null for a plain tint
    private final float radius;
    private final boolean plate;
    private final int veil;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrim = new Paint();
    private final Path clip = new Path();
    private final RectF box = new RectF();

    // The dock: blur is the slice under it; light photos get a stronger veil, as in CleanFrame.
    static Glass dock(Bitmap blur, float radius, boolean lightPhoto) {
        return new Glass(blur, radius, false, lightPhoto ? 0x45080E12 : 0x29080E12);
    }

    // The focus plate: white glass, brighter at the top and bottom than in the middle.
    static Glass plate(float radius) {
        return new Glass(null, radius, true, 0);
    }

    private Glass(Bitmap blur, float radius, boolean plate, int veil) {
        this.blur = blur;
        this.radius = radius;
        this.plate = plate;
        this.veil = veil;
        edge.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        box.set(b);
        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        float h = b.height();
        if (plate) {
            paint.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                    new int[] {0x4DFFFFFF, 0x29FFFFFF, 0x38FFFFFF}, new float[] {0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        } else {
            scrim.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0x00040A0C, 0x45040A0C, Shader.TileMode.CLAMP));
        }
        // A top hairline that follows the radius and fades out down the sides.
        edge.setStrokeWidth(Math.max(1f, h / 160f));
        edge.setShader(new LinearGradient(0, b.top, 0, b.top + radius,
                plate ? 0x57FFFFFF : 0x3DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        c.save();
        c.clipPath(clip);
        if (plate) {
            c.drawRect(box, paint);
        } else {
            if (blur != null) c.drawBitmap(blur, null, box, paint);
            // The scrim is 520px tall in CleanFrame and starts at the dock's top: the dock sees its first
            // 232px, so it darkens from clear to about a quarter towards the bottom.
            c.drawRect(box, scrim);
            c.drawColor(veil);
        }
        c.restore();
        float inset = edge.getStrokeWidth() / 2;
        c.drawRoundRect(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset, radius, radius, edge);
    }

    @Override public void setAlpha(int a) { paint.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter f) { paint.setColorFilter(f); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    // Outline for clipToOutline, so children stay inside the rounded corners.
    @Override
    public void getOutline(android.graphics.Outline o) {
        o.setRoundRect(getBounds(), radius);
    }
}

package ml.fomi.apps.coloringbook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.github.DavidLDawes.photoview.OnMatrixChangedListener;
import com.github.DavidLDawes.photoview.OnPhotoTapListener;
import com.github.DavidLDawes.photoview.PhotoView;
import com.github.DavidLDawes.photoview.PhotoViewAttacher;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ml.fomi.apps.coloringbook.db.DataBaseHelper;
import ml.fomi.apps.coloringbook.db.SectorsDAO;

/**
 * Created by buz on 17.06.16.
 * Central image
 */
public class PhilImageView extends VectorImageView implements PhotoView

        .OnTouchListener, VectorImageView.OnImageCallbackListener, OnMatrixChangedListener, OnPhotoTapListener {

    private static final String TAG = "PhilImageView";
    private static final float MAX_SCALE = 18f;
    private static final float MID_SCALE = 6f;
    private static final float MIN_SCALE = 1f;

    private PhotoViewAttacher photoViewAttacher;
    private PhilImageView philImageView;

    private Context mContext;

    private int curSector = -1;
    private Paint paint;
    private int curColor = 0;

    private int prevColor = -1;

    private Matrix curMatrix;
    private final Matrix curMatrixInverse = new Matrix();
    private ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public PhilImageView(Context context) {
        super(context);
        this.mContext = context;
        initThis();
    }

    public PhilImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        initThis();
    }

    public PhilImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mContext = context;
        initThis();
    }

    @Override
    public void cleanup() {
        if (photoViewAttacher != null) {
            photoViewAttacher.cleanup();
        }
        ioExecutor.shutdown();
        super.cleanup();
    }

    private static DataBaseHelper.SECTORS sectorsForFile(String filename) {
        switch (filename) {
            case "alien.svg":                        return DataBaseHelper.SECTORS.SECTORS_ALIEN;
            case "ul.svg":                           return DataBaseHelper.SECTORS.SECTORS_UL;
            case "roller-skates-svgrepo-com.svg":    return DataBaseHelper.SECTORS.SECTORS_SKATES;
            case "skate-panda.svg":                  return DataBaseHelper.SECTORS.SECTORS_PANDA;
            default:                                 return DataBaseHelper.SECTORS.SECTORS_PHIL;
        }
    }

    @Override
    public void loadAsset(String string) {
        if (ioExecutor.isShutdown()) {
            ioExecutor = Executors.newSingleThreadExecutor();
        }

        // setSectorsDAO must be called BEFORE super.loadAsset() because the SVG parsing
        // triggered by super.loadAsset() calls onSvgStart() which needs sectorsDAO.
        setSectorsDAO(new SectorsDAO(mContext, sectorsForFile(string)));

        super.loadAsset(string);

        curMatrix = new Matrix();
        photoViewAttacher = new PhotoViewAttacher(philImageView);
        photoViewAttacher.getDisplayMatrix(curMatrix);
        curMatrix.invert(curMatrixInverse);
        photoViewAttacher.setMaximumScale(MAX_SCALE);
        photoViewAttacher.setMediumScale(MID_SCALE);
        photoViewAttacher.setMinimumScale(MIN_SCALE);
        photoViewAttacher.setOnPhotoTapListener(philImageView);
        photoViewAttacher.setOnMatrixChangeListener(philImageView);

        paint = new Paint();
        paint.setStrokeWidth(1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
    }

    @Override
    public void initThis() {
        philImageView = this;
    }

    public void undoColor() {
        if (curSector >= 0 && prevColor != -1) {
            setSectorColor(curSector, prevColor);
            prevColor = -1;
            updatePicture();
        }
    }

    // ── Zoom helpers ────────────────────────────────────────────────────────────

    /** Returns the current zoom scale (≥ 1.0). */
    public float getZoom() {
        if (photoViewAttacher == null) return MIN_SCALE;
        return photoViewAttacher.getScale();
    }

    /** Returns the maximum allowed zoom scale. */
    public float getMaxZoom() {
        return MAX_SCALE;
    }

    /**
     * Applies a new zoom level centred on the middle of the currently visible image area.
     * The display matrix is updated immediately, so hit-testing via {@code onMatrixChanged}
     * stays correct automatically.
     *
     * @param scale desired scale in the range [MIN_SCALE, MAX_SCALE]
     * @param animate whether to animate the transition
     */
    public void setZoom(float scale, boolean animate) {
        if (photoViewAttacher == null) return;
        float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

        // Centre the zoom on the middle of the currently displayed image rect.
        RectF displayRect = photoViewAttacher.getDisplayRect();
        if (displayRect != null) {
            float focalX = displayRect.centerX();
            float focalY = displayRect.centerY();
            photoViewAttacher.setScale(clamped, focalX, focalY, animate);
        } else {
            photoViewAttacher.setScale(clamped, animate);
        }
    }

    // ────────────────────────────────────────────────────────────────────────────

    public interface ShareCallback {
        void onSharePrepared(Uri uri);
    }

    public void doShare(final ShareCallback callback) {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            callback.onSharePrepared(null);
            return;
        }

        // Creating the bitmap and drawing to it must happen on the UI thread
        final Bitmap bmp = getShareBitmap(drawable);

        ioExecutor.execute(() -> {
            Uri uri = null;
            try {
                File path = mContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                File bmpFile = File.createTempFile("BigPhil", ".png", path);

                try (FileOutputStream out = new FileOutputStream(bmpFile)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                }

                uri = FileProvider.getUriForFile(mContext,
                        "ml.fomi.apps.coloringbook.fileprovider", bmpFile);

                final String absolutePath = bmpFile.getAbsolutePath();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(mContext, String.format("Extracted into: %s", absolutePath), Toast.LENGTH_LONG).show();
                });

            } catch (Exception t) {
                Log.e(TAG, "Error sharing", t);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(mContext, "Error occured while extracting bitmap", Toast.LENGTH_SHORT).show();
                });
            } finally {
                bmp.recycle();
            }

            final Uri finalUri = uri;
            new Handler(Looper.getMainLooper()).post(() -> callback.onSharePrepared(finalUri));
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setColor(curColor);
        canvas.drawRect(
                0, 0,
                philImageView.getMeasuredWidth() - 1,
                philImageView.getMeasuredHeight() - 1,
                paint
        );
    }

    @Override
    public void imageCallback() {
        photoViewAttacher.update();
        curColor = getOnImageCommandsListener().getCurrentColor();
    }

    @Override
    public void onMatrixChanged(RectF rect) {
        // Keep curMatrix and its inverse in sync so hit-testing is always correct,
        // regardless of zoom level or whether the color picker is visible.
        photoViewAttacher.getDisplayMatrix(curMatrix);
        curMatrix.invert(curMatrixInverse);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    @Override
    public void onPhotoTap(android.widget.ImageView view, float x, float y) {
        // x and y are fractions (0.0–1.0) of the displayed image rect within the view.
        // Map them to raw view-pixel coordinates using the display rect, then apply the
        // inverse display matrix to get SVG-space pixel coordinates for hit-testing.
        RectF displayRect = photoViewAttacher.getDisplayRect();
        if (displayRect == null) {
            Log.d(TAG, "onPhotoTap: null displayRect");
            return;
        }

        // Convert fraction → view pixel
        float viewX = displayRect.left + x * displayRect.width();
        float viewY = displayRect.top  + y * displayRect.height();

        // Map view pixel → SVG pixel via the inverse of the current display matrix
        float[] pt = {viewX, viewY};
        curMatrixInverse.mapPoints(pt);
        float svgX = pt[0];
        float svgY = pt[1];

        int sect = getSector(svgX, svgY);
        if (sect >= 0) {
            Log.d(TAG, "onPhotoTap: Sector: " + sect + " svgPt=(" + svgX + "," + svgY + ")");
            prevColor = getColorFromSector(sect);
            curSector = sect;
            setSectorColor(sect, getOnImageCommandsListener().getCurrentColor());
            this.updatePicture();
        } else {
            Log.d(TAG, "onPhotoTap: Invalid sector at svgPt=(" + svgX + "," + svgY + ")");
        }
    }
}

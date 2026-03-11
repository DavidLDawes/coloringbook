package ml.fomi.apps.coloringbook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.pixplicity.sharp.OnSvgElementListener;
import com.pixplicity.sharp.Sharp;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;

import ml.fomi.apps.coloringbook.db.SectorsDAO;
import ml.formi.apps.R;

/**
 * Created by Rius on 29.03.17.
 * VectorImageView class
 */
public abstract class VectorImageView extends AppCompatImageView implements OnSvgElementListener {

    private Context context;

    private PictureDrawable sharpDrawable;

    private final VectorImageView vectorImageView;

    private OnImageCommandsListener onImageCommandsListener;
    private OnImageCallbackListener onImageCallbackListener;

    private Bitmap bitmapMap;

    private int actW;
    private int actH;

    private ArrayList<Boolean> sectorsFlags;

    private ArrayList<Integer> sectorsColors;
    private ArrayList<Path> sectorsPaths;

    private ArrayList<Integer> bckgSectorsColors;
    private ArrayList<Path> bckgSectorsPaths;

    private ArrayList<Float> brushSectors;

    private SectorsDAO sectorsDAO;
    private ExecutorService dbExecutor;

    private boolean isEmptyDB = false;

    public VectorImageView(Context context) {
        super(context);
        vectorImageView = this;
        vectorImageView.context = context;
    }

    public VectorImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        vectorImageView = this;
        vectorImageView.context = context;
    }

    public VectorImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        vectorImageView = this;
        vectorImageView.context = context;
    }

    public void loadAsset(String string) {
        sectorsFlags = new ArrayList<>();
        sectorsPaths = new ArrayList<>();
        bckgSectorsPaths = new ArrayList<>();
        bckgSectorsColors = new ArrayList<>();
        brushSectors = new ArrayList<>();

        Sharp mSharp = Sharp.loadAsset(context.getAssets(), string);
        mSharp.setOnElementListener(vectorImageView);

        mSharp.getDrawable(vectorImageView, sd -> {
            sharpDrawable = sd;
            vectorImageView.setImageDrawable(sharpDrawable);

            if (onImageCallbackListener != null)
                onImageCallbackListener.imageCallback();

            createMap();
            updatePicture();
        });

    }

    private int sectorId = 0;

    @Override
    public void onSvgStart(@NonNull Canvas canvas, @Nullable RectF bounds) {
        sectorId = 0;
        sectorsColors = sectorsDAO.getSectors();
        if (sectorsColors.isEmpty())
            isEmptyDB = true;
        bckgSectorsPaths.clear();
        bckgSectorsColors.clear();
        sectorsPaths.clear();
        brushSectors.clear();
    }

    @Override
    public void onSvgEnd(@NonNull Canvas canvas, @Nullable RectF bounds) {
        if (isEmptyDB) {
            final WeakReference<Activity> activityRef = new WeakReference<>((Activity) context);
            dbExecutor.execute(() -> {
                long result = sectorsDAO.init();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Activity activity = activityRef.get();
                    if (activity != null && !activity.isFinishing()) {
                        if (result == -1)
                            Log.e("MLogs", "Handsof: Error to save sectorsColors.");
                        else
                            Log.d("MLogs", "Handsof: Sectors saved.");
                    }
                });
            });
            isEmptyDB = false;
        }
    }

    @Override
    public <T> T onSvgElement(@Nullable String id, @NonNull T element, @Nullable RectF
            elementBounds, @NonNull Canvas canvas, @Nullable RectF canvasBounds, @Nullable Paint paint) {

        if (paint != null && (element instanceof Path)) {
            int color;
            if (id == null) {
                color = paint.getColor();
                sectorsFlags.add(false);
                bckgSectorsPaths.add((Path) element);
                bckgSectorsColors.add(color);
            } else {
                sectorsFlags.add(true);
                sectorsPaths.add((Path) element);

                if (onImageCommandsListener == null) {
                    float elB = elementBounds != null ? elementBounds.left : -1;
                    float canB = canvasBounds != null ? canvasBounds.width() : -1;
                    brushSectors.add(elB / canB);
                }

                if (isEmptyDB || sectorId >= sectorsColors.size()) {
                    // DB has no entry for this sector — generate a random color
                    Random random = new Random();
                    color = Color.argb(255, random.nextInt(256),
                            random.nextInt(256), random.nextInt(256));
                    sectorsColors.add(color);
                    isEmptyDB = true;
                    sectorId++;
                } else {
                    color = sectorsColors.get(sectorId++);
                }
            }
            paint.setColor(color);
        }
        return element;
    }

    @Override
    public <T> void onSvgElementDrawn(@Nullable String id, @NonNull T element, @NonNull Canvas
            canvas, @Nullable Paint paint) {
    }

    /**
     * Look up the sector at the given SVG-space pixel coordinates.
     * The caller is responsible for converting view/screen coordinates into
     * SVG-space pixels (e.g. via the inverse of the PhotoView display matrix).
     *
     * @param svgX X coordinate in SVG pixel space
     * @param svgY Y coordinate in SVG pixel space
     * @return sector index (≥ 0), or 0xFFFFFFFF if outside all sectors
     */
    int getSector(float svgX, float svgY) {
        int lX = Math.round(svgX);
        int lY = Math.round(svgY);
        if (lX >= 0 && lY >= 0 && lX < bitmapMap.getWidth() && lY < bitmapMap.getHeight()) {
            int pixel = bitmapMap.getPixel(lX, lY);
            int curSector = ((pixel << 16) >>> 16) - 1;
            return curSector;
        }
        return 0xFFFFFFFF;
    }

    int getSector(final ImageView imageView, float x, float y) {

        float paddingEventX = x / imageView.getWidth();

        int sectorId = -1;
        for (float fl : brushSectors) {
            if (paddingEventX < fl) break;
            sectorId++;
        }

        return sectorId;
    }

    void setSectorColor(int i, int c) {
        if (sectorsColors != null && c != sectorsColors.get(i)) {
            sectorsColors.set(i, c);
            dbExecutor.execute(() -> sectorsDAO.update(i, c));
        }
    }

    int getColorFromSector(int i) {
        if (i == 0xFFFFFFFF) return sectorsColors.get(0);
        return sectorsColors.get(i);
    }

    int getSizeSectors() {
        return sectorsColors.size();
    }

    void setOnImageCommandsListener(OnImageCommandsListener onImageCommandsListener) {
        vectorImageView.onImageCommandsListener = onImageCommandsListener;
    }

    OnImageCommandsListener getOnImageCommandsListener() {
        return vectorImageView.onImageCommandsListener;
    }

    interface OnImageCommandsListener {
        int getCurrentColor();
    }

    public void setOnImageCallbackListener(OnImageCallbackListener onImageCallbackListener) {
        this.onImageCallbackListener = onImageCallbackListener;
    }

    interface OnImageCallbackListener {
        void imageCallback();
    }

    private void createMap() {
        actW = sharpDrawable.getPicture().getWidth();
        actH = sharpDrawable.getPicture().getHeight();

        // Draw each sector path as a solid filled region with its index+1 as the color.
        // Anti-aliasing is OFF so boundary pixels are unambiguously one sector or another.
        // We draw directly onto a fresh bitmap — NOT via sharpDrawable.draw() — so only
        // filled interiors are encoded, never strokes or SVG decoration.
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setStyle(Paint.Style.FILL);

        bitmapMap = Bitmap.createBitmap(actW, actH, Bitmap.Config.ARGB_8888);
        bitmapMap.eraseColor(Color.TRANSPARENT);

        Canvas bitmapCanvas = new Canvas(bitmapMap);
        for (int i = 0; i < sectorsPaths.size(); i++) {
            // Encode sector index as color: sector 0 → 0x00000001, sector 1 → 0x00000002, …
            // Alpha must be 0xFF so the pixel is opaque and readable via getPixel().
            paint.setColor((i + 1) | 0xFF000000);
            bitmapCanvas.drawPath(sectorsPaths.get(i), paint);
        }
    }

    public abstract void initThis();

    public Bitmap getShareBitmap(Drawable drawable) {
        int w = getResources().getDimensionPixelSize(ml.formi.apps.R.dimen.share_image_width_px);
        int iw = drawable.getIntrinsicWidth();
        int ih = drawable.getIntrinsicHeight();
        float ar = (float) iw / w;
        int ah = (int) (ih / ar);
        int aw = (int) (iw / ar);

        Bitmap btm = Bitmap.createBitmap(aw, ah, Bitmap.Config.ARGB_8888);
        btm.eraseColor(0xFFFFFFFF);
        Canvas canvas = new Canvas(btm);
        int p = getResources().getDimensionPixelSize(R.dimen.share_image_padding_px);
        drawable.setBounds(p, p, aw - p, ah - p);
        drawable.draw(canvas);
        return btm;
    }

    public void setSectorsDAO(SectorsDAO sectorsDAO) {
        this.sectorsDAO = sectorsDAO;
        dbExecutor = Executors.newSingleThreadExecutor();
    }

    public void cleanup() {
        if (dbExecutor != null) {
            dbExecutor.shutdown();
        }
    }

    public void clearAll() {
        Collections.fill(sectorsColors, 0xFFFFFFFF);
        dbExecutor.execute(() -> sectorsDAO.clearAllWhite());
        updatePicture();
    }

    public void colorAllWhite() {
        Random random = new Random();
        for (int i = 0; i < sectorsColors.size(); i++) {
            if (sectorsColors.get(i) == Color.WHITE) {
                int c = Color.argb(255, random.nextInt(256),
                        random.nextInt(256), random.nextInt(256));
                sectorsColors.set(i, c);
            }
        }
        final List<Integer> snapshot = new ArrayList<>(sectorsColors);
        dbExecutor.execute(() -> sectorsDAO.updateBatch(snapshot));
        updatePicture();
    }

    public void updatePicture() {

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        Canvas canvas = sharpDrawable.getPicture().beginRecording(
                sharpDrawable.getPicture().getWidth(),
                sharpDrawable.getPicture().getHeight()
        );

        int j = 0, k = 0;
        for (int i = 0; i < sectorsFlags.size(); i++)
            if (sectorsFlags.get(i)) {
                paint.setColor(sectorsColors.get(j));
                canvas.drawPath(sectorsPaths.get(j++), paint);
            } else {
                paint.setColor(bckgSectorsColors.get(k));
                canvas.drawPath(bckgSectorsPaths.get(k++), paint);
            }
        sharpDrawable.getPicture().endRecording();
        vectorImageView.invalidate();
    }
}
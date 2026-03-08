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
    private PhotoViewAttacher photoViewAttacher;
    private PhilImageView philImageView;

    private Context mContext;

    private int curSector = -1;
    private Paint paint;
    private int curColor = 0;

    private int prevColor = -1;

    private Matrix curMatrix;
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
        photoViewAttacher.setMaximumScale(18);
        photoViewAttacher.setMediumScale(6);
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
        photoViewAttacher.getDisplayMatrix(curMatrix);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return false;
    }

    @Override
    public void onPhotoTap(android.widget.ImageView view, float x, float y) {
        // x and y are percentages (0.0 to 1.0) of the drawable dimensions
        int sect = getSector(x, y);
        if (sect >= 0) {  // Valid sector
            Log.d(TAG, "onPhotoTap: Sector: " + sect);
            prevColor = getColorFromSector(sect);
            curSector = sect;
            setSectorColor(sect, getOnImageCommandsListener().getCurrentColor());
            this.updatePicture();
        } else {
            Log.d(TAG, "onPhotoTap: Invalid sector at (" + x + ", " + y + ")");
        }
    }
}
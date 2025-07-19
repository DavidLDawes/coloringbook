package ml.fomi.apps.coloringbook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Environment;
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

    void cleanup() {
        photoViewAttacher.cleanup();
    }

    @Override
    public void loadAsset(String string) {
        super.loadAsset(string);

        setSectorsDAO(new SectorsDAO(mContext, DataBaseHelper.SECTORS.SECTORS_PHIL));

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
        if (prevColor != -1 && prevColor != curSector) {
            setSectorColor(curSector, prevColor);
            updatePicture();
        }
    }

    public Uri doShare() {
        Uri uri = null;
        try {

            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File bmpFile = File.createTempFile("BigPhil", ".png", path);
            FileOutputStream out = new FileOutputStream(bmpFile);

            Bitmap bmp = getShareBitmap(philImageView.getDrawable());

            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);

            out.close();
            uri = Uri.fromFile(bmpFile);
            Toast.makeText(mContext, String.format("Extracted into: %s", bmpFile.getAbsolutePath()), Toast.LENGTH_LONG).show();

        } catch (Throwable t) {
            t.printStackTrace();
            Toast.makeText(mContext, "Error occured while extracting bitmap", Toast.LENGTH_SHORT).show();
        }
        return uri;
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
        // Handle photo tap - can be used to trigger coloring at the tapped location
        // x and y are percentages (0.0 to 1.0) of the drawable dimensions
        int sect = getSector(x, y);
        Log.d(TAG, "onPhotoTap: Sector: " + sect);
        setSectorColor(sect, -1);
        this.updatePicture();
    }
}
package ml.fomi.apps.coloringbook;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.Toolbar;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;

import ml.formi.apps.R;

public class MainActivity extends AppCompatActivity implements OnTouchListener {

    private PhilImageView centerImageView;
    private BrushImageView brushImageView;

    private ImageView imageViewLeft;

    private int currentPixelColor = 0;

    SharedPreferences sPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Objects.requireNonNull(getSupportActionBar()).setDisplayShowHomeEnabled(true);
        getSupportActionBar().setLogo(R.mipmap.ic_launcher);
        getSupportActionBar().setDisplayUseLogoEnabled(true);

        TextView textViewLeftBlack = findViewById(R.id.textView_black);
        ImageView imageViewGray = findViewById(R.id.imageView_gray);
        TextView textViewLeftWhite = findViewById(R.id.textView_white);
        imageViewLeft = findViewById(R.id.imageView_left);


        final GradientDrawable drawableGray = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF000000, 0xFFFFFFFF});
        drawableGray.setShape(GradientDrawable.RECTANGLE);
        drawableGray.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        imageViewGray.setImageDrawable(drawableGray);
        imageViewGray.setDrawingCacheEnabled(true);

        final GradientDrawable drawableLeft = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF000000, 0xFF00FF00, 0xFFFFFFFF});
        drawableLeft.setShape(GradientDrawable.RECTANGLE);
        drawableLeft.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        imageViewLeft.setImageDrawable(drawableLeft);
        imageViewLeft.setDrawingCacheEnabled(true);

        ImageView imageViewRight = findViewById(R.id.imageView_right);
        final GradientDrawable drawableRight = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFFFF0000, 0xFFFF7F00,
                        0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                        0xFF0000FF, 0xFFFF00FF});

        drawableRight.setShape(GradientDrawable.RECTANGLE);
        drawableRight.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        imageViewRight.setImageDrawable(drawableRight);
        imageViewRight.setDrawingCacheEnabled(true);

        imageViewRight.setOnTouchListener((v, event) -> {

            touchView(v, event);

            imageViewLeft.setDrawingCacheEnabled(false);
            final GradientDrawable drawableLeft1 = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[]{0xFF000000, currentPixelColor, 0xFFFFFFFF});
            drawableLeft1.setShape(GradientDrawable.RECTANGLE);
            drawableLeft1.setGradientType(GradientDrawable.LINEAR_GRADIENT);

            imageViewLeft.setDrawingCacheEnabled(true);

            imageViewLeft.setImageDrawable(drawableLeft1);

            return true;
        });

        imageViewLeft.setOnTouchListener(this);

        assert textViewLeftWhite != null;
        textViewLeftWhite.setOnTouchListener(this);

        assert textViewLeftBlack != null;
        textViewLeftBlack.setOnTouchListener(this);

        imageViewGray.setOnTouchListener(this);

        brushImageView = findViewById(R.id.imageView_brush);
        brushImageView.loadAsset("brush7.svg");

        centerImageView = findViewById(R.id.imageView_center);
        centerImageView.loadAsset("Gerald_G_Beach_Trip_2.svg");
        // centerImageView.setScaleX(1.25f);
        // centerImageView.setScaleY(1.25f);
        centerImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        centerImageView.setOnImageCommandsListener(brushImageView);
        centerImageView.setOnImageCallbackListener(centerImageView);

        centerImageView.post(this::helpOnStartWindow);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        touchView(v, event);
        return true;
    }

    private void touchView(View v, MotionEvent event) {

        final int fieldWidth = 15;

        int y = (int) event.getY();
        int yImg = v.getMeasuredHeight();

        int x = (int) event.getX();
        int xImg = v.getMeasuredWidth();

        if ((y >= -fieldWidth) && (y < (yImg + fieldWidth)) && (x >= -fieldWidth) && (x < (xImg + fieldWidth))) {

            if (y >= 0 && y < yImg && x >= 0 && x < xImg) {

                if (v instanceof ImageView) {

                    currentPixelColor = v.getDrawingCache().getPixel(x, y);

                } else if (v instanceof TextView) {
                    //TextView section
                    if (((TextView) v).getText().toString().equals("B"))
                        currentPixelColor = Color.BLACK;

                    if (((TextView) v).getText().toString().equals("W"))
                        currentPixelColor = Color.WHITE;
                }

                brushImageView.pushColor(currentPixelColor);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        centerImageView.cleanup();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_share) {
            Uri uri = centerImageView.doShare();
            if (uri != null) {
                Intent myShareIntent = new Intent(Intent.ACTION_SEND);
                myShareIntent.setType("image/png");
                myShareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                startActivity(Intent.createChooser(myShareIntent, "Share to ..."));
            }
            return true;
        } else if (itemId == R.id.action_undo) {
            centerImageView.undoColor();
            return true;
        } else if (itemId == R.id.action_clear_all) {
            new AlertDialog.Builder(this)
                    .setTitle("Clearing all cells")
                    .setMessage("Do you really want to clear all cells? All cells will be white.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        brushImageView.clearAll();
                        centerImageView.clearAll();
                        Toast.makeText(MainActivity.this, "All cells cleared!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.no, null).show();
            return true;
        } else if (itemId == R.id.action_color_white_cells) {
            new AlertDialog.Builder(this)
                    .setTitle("Coloring all cells.")
                    .setMessage("Do you really want to color all white cells? All white cells will be painted random color.")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, (dialog, whichButton) -> {
                        brushImageView.colorAllWhite();
                        centerImageView.colorAllWhite();
                        Toast.makeText(MainActivity.this, "All white cells colored!", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.no, null).show();
            return true;
        } else if (itemId == R.id.action_zoom) {
            showZoomDialog();
            return true;
        } else if (itemId == R.id.action_about) {
            AboutWindow();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void AboutWindow() {

        final Dialog aboutWindow = new Dialog(this);

        LinearLayout linearLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.about_dialog, null);

        aboutWindow.requestWindowFeature(Window.FEATURE_NO_TITLE);

        aboutWindow.setContentView(linearLayout);

        final TextView tx = linearLayout.findViewById(R.id.about_textView);

        String[] about_ar = getResources().getStringArray(R.array.text_about);
        String about_string = "";
        for (String str : about_ar) {
            about_string += str;
        }
        about_string += "Version injection removed, breaking a build.  ";

        tx.setAutoLinkMask(Linkify.EMAIL_ADDRESSES);
        tx.setText(about_string);

        linearLayout.setOnTouchListener((view, motionEvent) -> {
            aboutWindow.dismiss();
            return false;
        });
        aboutWindow.show();
    }

    public void helpOnStartWindow() {

        final String isShowStr = "isShowPref";
        sPref = getPreferences(MODE_PRIVATE);
        boolean isShow = sPref.getBoolean(isShowStr, true);

        if (isShow) {

            final AppCompatDialog helpWindow = new AppCompatDialog(this);
            final LinearLayout linearLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.help_on_start, null);
            helpWindow.requestWindowFeature(Window.FEATURE_NO_TITLE);
            helpWindow.setContentView(linearLayout);

            final AppCompatCheckBox checkBox = linearLayout.findViewById(R.id.help_view_checkBox);

            checkBox.setChecked(isShow);

            checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
                SharedPreferences.Editor editor = sPref.edit();
                editor.putBoolean(isShowStr, b);
                editor.apply();
            });

            linearLayout.setOnTouchListener((view, motionEvent) -> {
                helpWindow.dismiss();
                return false;
            });

            helpWindow.show();
        }
    }

    public void showZoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Zoom Level");

        // Create the slider layout
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextView label = new TextView(this);
        label.setText("Adjust zoom level:");
        layout.addView(label);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100); // 0-100 scale
        
        // Get current zoom level and convert to slider scale (0-100)
        float currentScale = centerImageView.getPhotoViewAttacher().getScale();
        float minScale = centerImageView.getPhotoViewAttacher().getMinimumScale();
        float maxScale = centerImageView.getPhotoViewAttacher().getMaximumScale();
        
        // Convert current scale to 0-100 range
        int currentProgress = (int) ((currentScale - minScale) / (maxScale - minScale) * 100);
        seekBar.setProgress(currentProgress);

        TextView valueLabel = new TextView(this);
        valueLabel.setText(String.format("%.1fx", currentScale));
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Convert 0-100 range back to actual scale
                    float scale = minScale + (progress / 100.0f) * (maxScale - minScale);
                    valueLabel.setText(String.format("%.1fx", scale));
                    centerImageView.getPhotoViewAttacher().setScale(scale, true);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        layout.addView(seekBar);
        layout.addView(valueLabel);

        builder.setView(layout);
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Reset", (dialog, which) -> {
            centerImageView.getPhotoViewAttacher().setScale(minScale, true);
        });

        builder.show();
    }
}

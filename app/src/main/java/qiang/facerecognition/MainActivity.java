/*
* Copyright 2016 The TensorFlow Authors. All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package qiang.facerecognition;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import androidx.appcompat.app.AlertDialog;

import qiang.facerecognition.camera.CameraActivity;
import qiang.facerecognition.env.BorderedText;
import qiang.facerecognition.env.Device;
import qiang.facerecognition.env.TTS;
import qiang.facerecognition.face.FaceInfo;
import qiang.facerecognition.face.FaceProcess;
import qiang.facerecognition.face.FaceUtils;
import qiang.facerecognition.tracking.MultiBoxTracker;
import qiang.facerecognition.utils.ImageUtils;
import qiang.facerecognition.env.Logger;
import qiang.facerecognition.utils.FileUtils;
import qiang.facerecognition.view.OverlayView;

import static qiang.facerecognition.face.FaceUtils.isExistPerson;

/**
* An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
* objects.
*/
public class MainActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();
    private static final int CROP_SIZE = 300;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final float TEXT_SIZE_DIP = 10;
    private Integer sensorOrientation;
    private BorderedText borderedText;

    private final Device device = Device.GPU;
    private final int numThreads = 4;
    private Map<String, float[]> NameFacesDBMap; //get the faces data, save them in array
    private FaceUtils faceUtils;
    private String person_name;
    private TTS tts;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;
    private OverlayView trackingOverlay;
    private OverlayView debugOverlay;

    private byte[] luminanceCopy;



    private Snackbar initSnackbar;
    private Snackbar addSnackbar;
    private FloatingActionButton button;

    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private boolean initialized = false;
    private boolean adding = false;
    private boolean computingDetection = false;

    private int detectedFaceNum; //待添加图片中检测到的人脸数量

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initialized = false;
        FrameLayout container = findViewById(R.id.container);
        initSnackbar = Snackbar.make(container, "Initializing...", Snackbar.LENGTH_INDEFINITE);
        runOnUiThread(() -> initSnackbar.show());
        addSnackbar = Snackbar.make(container, "Updating data...", Snackbar.LENGTH_INDEFINITE);

        tts = new TTS(this, 4);
        try {
            NameFacesDBMap = FaceProcess.getNameFacesDBMap(FileUtils.DATA_FILE);
            faceUtils = new FaceUtils(this, device, numThreads, NameFacesDBMap);
        } catch (Exception e) {
            LOGGER.e("Exception initializing faceUtils!", e);
            finish();
        }
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edittext, null);
        EditText editText = dialogView.findViewById(R.id.edit_text);
        AlertDialog editDialog = new AlertDialog.Builder(MainActivity.this)
                .setTitle(R.string.enter_name)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.dialog_btn_confirm_text), (dialogInterface, i) -> {
                    if(!editText.getText().toString().trim().equals("")) {
//                        int idx = faceClassifier.getNameListSize();
                        int idx = faceUtils.getShowList().length;
                        person_name = editText.getText().toString();
                        editText.setText("");
                        if(!isExistPerson(person_name)) {
                            performFileSearch(idx);
                        }
                        else{
                            Toast.makeText(MainActivity.this, R.string.person_name_existed,Toast.LENGTH_SHORT);
                        }
                    }
                    else{
                        Toast.makeText(MainActivity.this, R.string.name_not_empty, Toast.LENGTH_SHORT);
                    }
                })
                .create();

        button = findViewById(R.id.add_button);
        button.setOnClickListener(view ->
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.select_name))
                        .setItems(faceUtils.getShowList(), (dialogInterface, i) -> {
                            if (i == 0) {
                                editDialog.show();
                            }
                            else {
                                new AlertDialog.Builder(this)
                                        .setTitle(getString(R.string.dialog_delete_face_title))
                                        .setIcon(R.mipmap.delete_alert)
                                        .setMessage(getString(R.string.dialog_delete_face_message))
                                        .setPositiveButton(getString(R.string.dialog_btn_confirm_text)
                                                , (dialog, which) -> {
                                                    FileUtils.deleteTheFace(i-1);
//                                                    FaceProcess.getSavedPersons(FileUtils.DATA_FILE).remove(faceUtils.getShowList()[i]);
                                                    NameFacesDBMap.remove(faceUtils.getShowList()[i-1]);
                                                    Toast.makeText(MainActivity.this,getString(R.string.dialog_have_deleted_text)
                                                            ,Toast.LENGTH_SHORT).show();
                                                    dialog.dismiss();
                                                })
                                        .setNegativeButton(getString(R.string.dialog_btn_cancel_text)
                                                , (dialog, which) -> dialog.dismiss())
                                        .create()
                                        .show();

//                                performFileSearch(i - 1);
                            }
                        })
                        .show());

        runOnUiThread(() -> initSnackbar.dismiss());
        initialized = true;


    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
//        if (!initialized)
//            new Thread(this::init).start();

        final float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
//        croppedBitmap = Bitmap.createBitmap(CROP_SIZE, CROP_SIZE, Config.ARGB_8888);
//
//        frameToCropTransform =
//                ImageUtils.getTransformationMatrix(
//                        previewWidth, previewHeight,
//                        CROP_SIZE, CROP_SIZE,
//                        sensorOrientation, false);
//
//        cropToFrameTransform = new Matrix();
//        frameToCropTransform.invert(cropToFrameTransform);

        addTrackCallback();
        addDebugCallback();
        faceUtils.setDrawSpeakCfg(tracker,trackingOverlay, tts);
    }


    @Override
    protected void processImage() {

        if (!initialized || adding) {
            readyForNextImage();
            return;
        }

        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        tracker.onFrame(
                previewWidth,
                previewHeight,
                getLuminanceStride(),
                sensorOrientation,
                originalLuminance,
                timestamp);
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }

        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);

        readyForNextImage();
        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

//        final Canvas canvas = new Canvas(croppedBitmap);
//        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
////         For examining the actual TF input.
//        if (SAVE_PREVIEW_BITMAP) {
//            ImageUtils.saveBitmap(croppedBitmap);
//        }

        runInBackground(
                () -> {
                    LOGGER.i("Running detection on image " + currTimestamp);
                    final long startTime = SystemClock.uptimeMillis();

//                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                    List<FaceInfo> personList = null;
                    try {
                        if(faceUtils != null){
                            personList  = faceUtils.inference(rgbFrameBitmap, luminanceCopy,
                                currTimestamp);
                        }

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    tracker.trackFaces(personList, luminanceCopy, currTimestamp,true);
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                    trackingOverlay.postInvalidate();
                    debugOverlay.postInvalidate();
                    computingDetection = false;
                });
    }

    private void addTrackCallback() {
        /**
         * @description:   画框的回调函数
         * @author:        JianboZhu
         * @date:          2019/7/23
         * @update:
         */
        tracker = new MultiBoxTracker(this, tts);
        Log.d("RealSense",tracker.toString());
        trackingOverlay = findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> {
                    tracker.draw(canvas);
                });
        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    private void addDebugCallback() {
        /**
         * @description:   显示调试信息的回调函数
         * @author:        JianboZhu
         * @date:          2019/7/23
         * @update:
         */
        debugOverlay = findViewById(R.id.debug_overlay);
        debugOverlay.addCallback(
                canvas -> {
                    final Vector<String> lines = new Vector<>();
                    lines.add("time cost: " + lastProcessingTimeMs + "ms");
                    borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
                });

    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if(faceUtils != null){
            faceUtils.close();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!initialized) {
            Snackbar.make(
                    getWindow().getDecorView().findViewById(R.id.container),
                    "Try it again later", Snackbar.LENGTH_SHORT)
                    .show();
            return;
        }

        if (resultCode == RESULT_OK) {
            addSnackbar.show();
            button.setEnabled(false);
            adding = true;
            Uri dataUri = data.getData();

            new Thread(() -> {
                try {
                    detectedFaceNum = faceUtils.addPerson(person_name, getContentResolver(), dataUri);
                } catch (Exception e) {
                    LOGGER.e(e, "Exception!");
                } finally {
                    adding = false;
                }
                runOnUiThread(() -> {
                    addSnackbar.dismiss();
                    if(detectedFaceNum == 1){
                        showDialog("人脸添加成功！");
                    }
                    else if(detectedFaceNum >=2 ){
                        showDialog("图片为多人合照，请重新选择！");
                    }
                    else if(detectedFaceNum == 0){
                        showDialog("图片中未检测到人脸，请重新选择！");
                    }
                    else if(detectedFaceNum == -1){
                        showDialog("人脸添加失败！");
                    }
                    button.setEnabled(true);
                });
            }).start();

        }
    }

    private void performFileSearch(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, requestCode);
    }

    public void showDialog(String message){
        AlertDialog alertDialog1 = new AlertDialog.Builder(this)
                .setTitle("提示")//标题
                .setMessage(message)//内容
                .setIcon(R.mipmap.ic_launcher)//图标
                .create();
        alertDialog1.show();
    }
}

/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package qiang.facerecognition.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.widget.Toast;


import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Vector;

import qiang.facerecognition.env.BorderedText;
import qiang.facerecognition.env.ObjectInfo;
import qiang.facerecognition.env.ResultFilter;
import qiang.facerecognition.env.TTS;
import qiang.facerecognition.face.FaceAttr;
import qiang.facerecognition.face.FaceInfo;
import qiang.facerecognition.utils.ImageUtils;


/**
 * A tracker that handles non-max suppression and matches existing objects to new detections.
 */
public class MultiBoxTracker {
    private static final float TEXT_SIZE_DIP = 13;
    private static final float MIN_SIZE = 16.0f;

    // Consider object to be lost if correlation falls below this threshold.
    private static final float MIN_CORRELATION = 0.3f;
    // Allow replacement of the tracked box with new results if
    // correlation has dropped below this level.
    private static final float MARGINAL_CORRELATION = 0.8f;
    // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
    // the lower scored box (new or old) will be removed.
    private static final float MAX_OVERLAP = 0.3f;

    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"),
            Color.parseColor("#FF8888"), Color.parseColor("#AAAAFF"),
            Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };
    private String trackName = "";  //
    private static class TrackedRecognition {
        int color;
        RectF location;
        String name;
        float nameConfidence;
        float depth;
        ObjectTracker.TrackedObject trackedObject;
        ResultFilter resultFilter;
        boolean isSpeak = true;

        TrackedRecognition(){}

        TrackedRecognition(ObjectInfo recognition, ResultFilter resultFilter){
            this.location = recognition.getBbox();
            this.resultFilter = resultFilter;

            Pair<String,Float> pair = this.resultFilter.filter(recognition.getLabel(),recognition.getScore());
            this.name = pair.first;
            this.nameConfidence = pair.second;

            this.depth = recognition.getDepth();
        }
        TrackedRecognition(FaceInfo recognition, ResultFilter resultFilter){
            this.resultFilter = resultFilter;
            setResult(recognition);
        }
        TrackedRecognition(TrackedRecognition recognition){
            if(recognition!=null){
                this.location = recognition.location;
                this.name = recognition.name;
                this.nameConfidence = recognition.nameConfidence;
                this.color = recognition.color;
                this.trackedObject = recognition.trackedObject;
                this.resultFilter = recognition.resultFilter;
                this.depth = recognition.depth;
            }
        }
        private void setResult(FaceInfo result){
            checkState(result);
            this.location = result.getBbox().first;
            if(!result.getPerson().first.equals("") || this.name==null){
                Pair<String,Float> pair = this.resultFilter.filter(result.getPerson().first,result.getPerson().second);
                this.name = pair.first;
                this.nameConfidence = pair.second;
            }

        }
        private void setResult(ObjectInfo result){
            this.location = result.getBbox();
            Pair<String,Float> pair = this.resultFilter.filter(result.getLabel(),result.getScore());
            this.name = pair.first;
            this.nameConfidence = pair.second;
            this.depth = result.getDepth();
//            this.name = result.getLabel();
//            this.nameConfidence = result.getScore();
        }
        private void checkState(FaceInfo result){
            this.isSpeak = (!result.getPerson().first.equals(this.name));
        }
    }

    private final Queue<Integer> availableColors = new LinkedList<>();
    private final List<TrackedRecognition> trackedObjects = new LinkedList<>();
    private final Paint boxPaint = new Paint();
    private final float textSizePx;
    private final BorderedText borderedText;
    private Matrix frameToCanvasMatrix;
    private int frameWidth;
    private int frameHeight;
    private int sensorOrientation;
    private Context context;
    private ObjectTracker objectTracker;
    private boolean initialized = false;
    private TTS tts;
    private boolean speechAvailable = true;
    private String speakText="";

    public MultiBoxTracker(final Context context, TTS tts) {
        this.context = context;
        this.tts = tts;
        for (final int color : COLORS) {
            availableColors.add(color);
        }
        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(10.0f);
        boxPaint.setStrokeCap(Cap.ROUND);
        boxPaint.setStrokeJoin(Join.ROUND);
        boxPaint.setStrokeMiter(100);

        textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);

        new Thread() {
            @Override
            public void run() {
                while(true){
                    if(speechAvailable){
                        tts.say(speakText);
                    }
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    public void clear(){
        trackedObjects.clear();
        for (final int color : COLORS) {
            availableColors.add(color);
        }
        trackName="";
    }

    public synchronized void setFrameConfiguration(
            final int width, final int height, final int sensorOrientation) {
        frameWidth = width;
        frameHeight = height;
        this.sensorOrientation = sensorOrientation;
    }

    public synchronized void draw(final Canvas canvas) {
        final boolean rotated = sensorOrientation % 180 == 90;
        final float multiplier =
                Math.min(
                        canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                        canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        frameWidth,
                        frameHeight,
                        (int) (multiplier * (rotated ? frameHeight : frameWidth)),
                        (int) (multiplier * (rotated ? frameWidth : frameHeight)),
                        sensorOrientation,
                        false);
        speechAvailable=true;
        switch (trackName){
            case "face":
                drawFaces(canvas);
                break;
            default:
                speechAvailable=false;
                break;
        }
    }

    private void drawFaces(final Canvas canvas){
        for (final TrackedRecognition recognition : trackedObjects) {
            final RectF trackedPos =
                    (objectTracker != null && recognition.trackedObject!=null)
                            ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
                            : new RectF(recognition.location);
//            final RectF trackedPos =new RectF(recognition.location);
            getFrameToCanvasMatrix().mapRect(trackedPos);
            boxPaint.setColor(recognition.color);

            float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
            canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
            drawBrokenLine(trackedPos, canvas, boxPaint);
            final Vector<String> labelStringVector = new Vector<>();

            if (!recognition.name.equals("")) {
                labelStringVector.add(String.format("%s %.2f", recognition.name, recognition.nameConfidence));
                speakText = "前方识别到熟人"+recognition.name;
//                String text = faceEn2Zh(recognition);
//                if(!text.equals("")) speech.say(text);
            }

            borderedText.drawLines(canvas, trackedPos.right - trackedPos.width() / 4, trackedPos.top - trackedPos.height() / 4, labelStringVector);

        }
    }
    private synchronized void drawBrokenLine(RectF rect, Canvas canvas, Paint paint) {
        float pointX1 = rect.centerX();
        float pointY1 = rect.centerY() - rect.height() / 2;
        float pointX2 = pointX1 + rect.width() / 4;
        float pointY2 = pointY1 - rect.height() / 4;
        float pointX3 = pointX2 + rect.width() / 2;
        float pointY3 = pointY2;
        float[] points = new float[]{pointX1, pointY1, pointX2, pointY2, pointX2, pointY2, pointX3, pointY3};
        canvas.drawLines(points, paint);
    }


    public synchronized void trackBbox(final List<FaceAttr> results, final byte[] frame, final long timestamp){
        List<FaceInfo> newResults = new LinkedList<>();
        for(FaceAttr result:results){
            Pair<RectF,Float> bbox = new Pair<>(result.getBbox(),result.getProb());

            newResults.add(new FaceInfo(bbox));
        }
        trackFaces(newResults, frame,timestamp,false);
    }

    public synchronized void trackFaces(final List<FaceInfo> results){
        trackName = "face";
        trackedObjects.clear();
        for (final FaceInfo result : results) {
            final TrackedRecognition trackedRecognition = new TrackedRecognition(result, new ResultFilter());
            trackedRecognition.color = COLORS[trackedObjects.size()];
            trackedRecognition.trackedObject = null;
            trackedObjects.add(trackedRecognition);
            if (trackedObjects.size() >= COLORS.length) {
                break;
            }
        }
    }

    public synchronized void trackFaces(final List<FaceInfo> results, final byte[] frame, final long timestamp, boolean isUpdate) {
        trackName = "face";
        if (objectTracker == null) {
            trackedObjects.clear();

            for (final FaceInfo result : results) {
                final TrackedRecognition trackedRecognition = new TrackedRecognition(result, new ResultFilter());
                trackedRecognition.color = COLORS[trackedObjects.size()];
                trackedRecognition.trackedObject = null;
                trackedObjects.add(trackedRecognition);

                if (trackedObjects.size() >= COLORS.length) {
                    break;
                }
            }
            return;
        }

        for (final FaceInfo result : results) {
            handleDetection(frame, timestamp, result, isUpdate);
        }
    }

    private void handleDetection(
            final byte[] frameCopy, final long timestamp, final FaceInfo result, boolean isUpdate) {
        /**
         * @description:   处理每一个检测到的结果，去掉不要的，剩下的放到trackedObjects中
         * @param:         isUpdate：是否将检测到的label传给已经在跟踪的框中。
         *                           主要是用在只画框不显示label的时候，因为仅画框的时候label是空.
         * @author:        JianboZhu
         * @date:          2019/7/25
         * @update:
         */
        final ObjectTracker.TrackedObject potentialObject =
                objectTracker.trackObject(result.getBbox().first, timestamp, frameCopy);

        final float potentialCorrelation = potentialObject.getCurrentCorrelation();
        if (potentialCorrelation < MARGINAL_CORRELATION) {
            potentialObject.stopTracking();
            return;
        }

        final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

        float maxIntersect = 0.0f;

        // This is the current tracked object whose color we will take. If left null we'll take the
        // first one from the color queue.
        TrackedRecognition recogToReplace = null;

        // Look for intersections that will be overridden by this object or an intersection that would
        // prevent this one from being placed.
        int objectNum = -1;
        for (final TrackedRecognition trackedRecognition : trackedObjects) {
            objectNum++;
            final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
            final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
            final RectF intersection = new RectF();
            final boolean intersects = intersection.setIntersect(a, b);

            final float intersectArea = intersection.width() * intersection.height();
            final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
            final float intersectOverUnion = intersectArea / totalArea;

            // If there is an intersection with this currently tracked box above the maximum overlap
            // percentage allowed, either the new recognition needs to be dismissed or the old
            // recognition needs to be removed and possibly replaced with the new one.
            //  在已经跟踪到的objects中寻找iou大于阈值的
            if (intersects && intersectOverUnion > MAX_OVERLAP) {
                if (trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
                    // If track for the existing object is still going strong, reject this new object.
                    potentialObject.stopTracking();

                    if(isUpdate){
                        //  将新结果给已经跟踪到object
                        trackedObjects.get(objectNum).setResult(result);
                    }

                    return;
                } else {
                    //  iou大于阈值但是已存在的跟踪情况不佳，则将其替换
                    removeList.add(trackedRecognition);
                    // Let the previously tracked object with max intersection amount donate its color to
                    // the new object.
                    if (intersectOverUnion > maxIntersect) {
                        maxIntersect = intersectOverUnion;
                        recogToReplace = trackedRecognition;
                    }
                }
            }
        }

        // Remove everything that got intersected.
        for (final TrackedRecognition trackedRecognition : removeList) {
            trackedRecognition.trackedObject.stopTracking();
            trackedObjects.remove(trackedRecognition);
            if (trackedRecognition != recogToReplace) {
                availableColors.add(trackedRecognition.color);
            }
        }

        if (recogToReplace == null && availableColors.isEmpty()) {
            potentialObject.stopTracking();
            return;
        }

        final TrackedRecognition trackedRecognition;
        if(recogToReplace!=null){
            trackedRecognition = new TrackedRecognition(result,recogToReplace.resultFilter);
        }else {
            trackedRecognition = new TrackedRecognition(result,new ResultFilter());
        }
        trackedRecognition.trackedObject = potentialObject;
        // Use the color from a replaced object before taking one from the color queue.
        trackedRecognition.color =
                recogToReplace != null ? recogToReplace.color : availableColors.poll();
        trackedObjects.add(trackedRecognition);

    }

    private void handleDetection(
            final byte[] frameCopy, final long timestamp, final ObjectInfo result, boolean isUpdate) {
        /**
         * @description:   处理每一个检测到的结果，去掉不要的，剩下的放到trackedObjects中
         * @param:         isUpdate：是否将检测到的label传给已经在跟踪的框中。
         *                           主要是用在只画框不显示label的时候，因为仅画框的时候label是空.
         * @author:        JianboZhu
         * @date:          2019/7/25
         * @update:
         */
        final ObjectTracker.TrackedObject potentialObject =
                objectTracker.trackObject(result.getBbox(), timestamp, frameCopy);

        final float potentialCorrelation = potentialObject.getCurrentCorrelation();
        if (potentialCorrelation < MARGINAL_CORRELATION) {
            potentialObject.stopTracking();
            return;
        }

        final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

        float maxIntersect = 0.0f;

        // This is the current tracked object whose color we will take. If left null we'll take the
        // first one from the color queue.
        TrackedRecognition recogToReplace = null;

        // Look for intersections that will be overridden by this object or an intersection that would
        // prevent this one from being placed.
        int objectNum = -1;
        for (final TrackedRecognition trackedRecognition : trackedObjects) {
            objectNum++;
            final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
            final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
            final RectF intersection = new RectF();
            final boolean intersects = intersection.setIntersect(a, b);

            final float intersectArea = intersection.width() * intersection.height();
            final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
            final float intersectOverUnion = intersectArea / totalArea;

            // If there is an intersection with this currently tracked box above the maximum overlap
            // percentage allowed, either the new recognition needs to be dismissed or the old
            // recognition needs to be removed and possibly replaced with the new one.
            //  在已经跟踪到的objects中寻找iou大于阈值的
            if (intersects && intersectOverUnion > MAX_OVERLAP) {
                if (trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
                    // If track for the existing object is still going strong, reject this new object.
                    potentialObject.stopTracking();

                    if(isUpdate){
                        //  将新结果给已经跟踪到object
                        trackedObjects.get(objectNum).setResult(result);
                    }

                    return;
                } else {
                    //  iou大于阈值但是已存在的跟踪情况不佳，则将其移除
                    removeList.add(trackedRecognition);
                    // Let the previously tracked object with max intersection amount donate its color to
                    // the new object.
                    if (intersectOverUnion > maxIntersect) {
                        maxIntersect = intersectOverUnion;
                        recogToReplace = trackedRecognition;
                    }
                }
            }
        }

        // Remove everything that got intersected.
        for (final TrackedRecognition trackedRecognition : removeList) {
            trackedRecognition.trackedObject.stopTracking();
            trackedObjects.remove(trackedRecognition);
            if (trackedRecognition != recogToReplace) {
                availableColors.add(trackedRecognition.color);
            }
        }

        if (recogToReplace == null && availableColors.isEmpty()) {
            potentialObject.stopTracking();
            return;
        }
        final TrackedRecognition trackedRecognition;
        if(recogToReplace!=null){
            trackedRecognition = new TrackedRecognition(result,recogToReplace.resultFilter);
        }else {
            trackedRecognition = new TrackedRecognition(result,new ResultFilter());
        }
        trackedRecognition.trackedObject = potentialObject;
        // Use the color from a replaced object before taking one from the color queue.
        trackedRecognition.color =
                recogToReplace != null ? recogToReplace.color : availableColors.poll();
        trackedObjects.add(trackedRecognition);

    }
    private Matrix getFrameToCanvasMatrix() {
        return frameToCanvasMatrix;
    }

    public synchronized void onFrame(
            final int w,
            final int h,
            final int rowStride,
            final int sensorOrienation,
            final byte[] frame,
            final long timestamp) {
        if (objectTracker == null && !initialized) {
            ObjectTracker.clearInstance();

            objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
            frameWidth = w;
            frameHeight = h;
            this.sensorOrientation = sensorOrienation;
            initialized = true;

            if (objectTracker == null) {
                String message =
                        "Object tracking support not found. ";
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        }

        if (objectTracker == null) {
            return;
        }

        objectTracker.nextFrame(frame, null, timestamp, null, true);

        // Clean up any objects not worth tracking any more.
        final LinkedList<TrackedRecognition> copyList =
                new LinkedList<TrackedRecognition>(trackedObjects);
        for (final TrackedRecognition recognition : copyList) {
            final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
            final float correlation = trackedObject.getCurrentCorrelation();
            if (correlation < MIN_CORRELATION) {
                trackedObject.stopTracking();
                trackedObjects.remove(recognition);

                availableColors.add(recognition.color);
            }
        }
    }


}

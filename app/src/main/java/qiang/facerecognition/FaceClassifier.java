/* Copyright 2015 The TensorFlow Authors. All Rights Reserved.

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

package qiang.facerecognition;

import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;


import qiang.facerecognition.utils.FileUtils;
import qiang.facerecognition.faceCompare.DetectedFace;
import qiang.facerecognition.faceCompare.FaceFeature;
import qiang.facerecognition.wrapper.FaceNet;
import qiang.facerecognition.wrapper.MTCNN;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static qiang.facerecognition.utils.FileUtils.DATA_FILE;
import static qiang.facerecognition.utils.FileUtils.EMBEDDING_SIZE;

//import androidx.core.util.Pair;


/**
 * Generic interface for interacting with different recognition engines.
 */
public class FaceClassifier {
    /**
     * An immutable result returned by a FaceClassifier describing what was recognized.
     */
    public class Recognition {
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private final String id;

        /**
         * Display name for the recognition.
         */
        private final String title;

        /**
         * A sortable score for how good the recognition is relative to others. Higher should be better.
         */
        private final Float faceConfidence;

        /**
         * The calculation result of searching in the face DB. Lower means more similar.
         */
        private final Float cmpResult;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        private final Bitmap face;


        Recognition(
                final String id, final String title, final Float confidence, Float cmpResult, final RectF location,
                final Bitmap face) {
            this.id = id;
            this.title = title;
            this.faceConfidence = confidence;
            this.cmpResult = cmpResult;
            this.location = location;
            this.face = face;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getFaceConfidence() {
            return faceConfidence;
        }

        public Float getCmpResult() {
            return cmpResult;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        void setLocation(RectF location) {
            this.location = location;
        }

        public Bitmap getFace(){
            return face;
        }

        @Override
        public String toString() {
            String resultString = "";
            if (id != null) {
                resultString += "[" + id + "] ";
            }

            if (title != null) {
                resultString += title + " ";
            }

            if (faceConfidence != null) {
                resultString += String.format("(%.1f%%) ", faceConfidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            if (face != null) {
                resultString += face + " ";
            }

            return resultString.trim();
        }
    }

    private static FaceClassifier faceClassifier;


    private MTCNN mtcnn;
    private FaceNet faceNet;

    private List<String> personNames;

    private FaceClassifier(){}
//    private String DATA_PATH = FileUtils.ROOT + File.separator + DATA_FILE;
    static FaceClassifier getInstance (AssetManager assetManager,
                                       int inputHeight,
                                       int inputWidth) throws Exception {
        if (faceClassifier != null) return faceClassifier;

        faceClassifier = new FaceClassifier();

        faceClassifier.mtcnn = MTCNN.create(assetManager);
        faceClassifier.faceNet = FaceNet.create(assetManager, inputHeight, inputWidth);
        faceClassifier.personNames = FileUtils.readFileByLine(FileUtils.LABEL_FILE);
        return faceClassifier;
    }

    /**
     * @return 获取人名列表
     */
    List<String> getPersonNames(){
        return personNames;
    }


    /**
     * @return获取AlertDialog对话框列表items
     */
    CharSequence[] getShowList() {
        CharSequence[] cs = new CharSequence[personNames.size() + 1];
        int idx = 1;

        cs[0] = "+ 添加新的人脸";
        for (String name : personNames) {
            cs[idx++] = name;
        }

        return cs;
    }

    private Bitmap resizeImage(Bitmap bitmap, int x, int y, int w, int h, int targetW, int targetH) {

        x = x<0?0:x;
        y = y<0?0:y;
        w = w+x > bitmap.getWidth()?bitmap.getWidth()-x : w;
        h = h+y > bitmap.getHeight()?bitmap.getHeight()-y : h;
        float scaleWidth = ((float) targetW) / w;
        float scaleHeight = ((float) targetH) / h;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        // if you want to rotate the Bitmap
        // matrix.postRotate(45);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmap, x, y, w, h, matrix, true);

        return resizedBitmap;
    }

    /**
     * 选择变换
     *
     * @param origin 原图
     * @param alpha  旋转角度，可正可负
     * @return 旋转后的图片
     */
    private Bitmap rotateBitmap(Bitmap origin, float alpha) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(alpha);
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    private float[] normalizer(float[] embeddings){
        float[] normalizeEmbeddings = new float[EMBEDDING_SIZE];
        float sum = 0.f;
        for(int i=0;i<embeddings.length;i++){
            sum += Math.pow(embeddings[i],2);
        }
        sum = (float)Math.sqrt(sum);
        for(int i=0;i<embeddings.length;i++){
            normalizeEmbeddings[i] = embeddings[i]/sum;
        }
        return normalizeEmbeddings;
    }


    private long mtcnnTimes = 0, faceNetTimes = 0;
    private int mtcnnCnt = 0, FaceNetCnt = 0;
    List<Recognition> recognizeImage(Bitmap bitmap, Matrix matrix) throws FileNotFoundException {
        synchronized (this) {
            long startTime;
            long lastTime;
            startTime = SystemClock.uptimeMillis();
            ArrayList<DetectedFace> facesList = mtcnn.detect(bitmap);
            lastTime = SystemClock.uptimeMillis() - startTime;
            mtcnnTimes += lastTime;
            mtcnnCnt += 1;
            if(mtcnnCnt == 100){
                Log.d("TimeCalculate","mtcnn: "+mtcnnTimes/mtcnnCnt+"ms");
                mtcnnTimes = 0;
                mtcnnCnt = 0;
            }

            final List<Recognition> mappedRecognitions = new LinkedList<>();
            if(facesList!=null) {
                for (DetectedFace face : facesList) {
                    float faceConfidence = face.getProbability();
                    if(faceConfidence >= 0.98) {  //只对人脸概率大于0.98的face进行识别
                        RectF rectF = face.getRectF();

                        float[] embeddingsArray = new float[EMBEDDING_SIZE];
                        Rect rect = new Rect();
                        rectF.round(rect);

                        startTime = SystemClock.uptimeMillis();
                        FloatBuffer buffer = faceNet.getEmbeddings(bitmap, rect);
                        lastTime = SystemClock.uptimeMillis() - startTime;
                        faceNetTimes += lastTime;
                        FaceNetCnt += 1;
                        if (FaceNetCnt == 100) {
                            Log.d("TimeCalculate", "faceNet: " + faceNetTimes / FaceNetCnt + "ms");
                            faceNetTimes = 0;
                            FaceNetCnt = 0;
                        }

                        // get face bitmap and resize to 64*64
                        Bitmap faceBitmap = resizeImage(bitmap, rect.left, rect.top, rect.width(), rect.height(), 64, 64);

                        buffer.get(embeddingsArray, 0, embeddingsArray.length);
//                        embeddingsArray = normalizer(embeddingsArray);
                        //                Pair<Integer, Float> pair = svm.predict(buffer);
                        android.util.Pair<Integer, Float> pair = FaceFeature.search(embeddingsArray, DATA_FILE);

                        matrix.mapRect(rectF);

                        String name;
                        int label = pair.first;
                        double cmpResult = pair.second;
                        if (label >= 0){
                            name = personNames.get(label);
                        }
                        else
                            name = "Unknown";

                        Recognition resultRec =
                                new Recognition("" + name, name, faceConfidence, (float) cmpResult, rectF, faceBitmap);
                        mappedRecognitions.add(resultRec);
                    }
                }
            }
            return mappedRecognitions;
        }

    }

    void updateData(String person_name, ContentResolver contentResolver, ArrayList<Uri> uris) throws Exception {
        synchronized (this) {
            ArrayList<float[]> list = new ArrayList<>();

            for (Uri uri : uris) {
                Bitmap bitmap = getBitmapFromUri(contentResolver, uri);
                ArrayList<DetectedFace> facesList = mtcnn.detect(bitmap);
                Log.d("faceUpdate",""+facesList.size());
                float max = 0f;
                Rect rect = new Rect();

                for (DetectedFace face : facesList) {
                    Float prob = face.getProbability();
                    if (prob > max) {
                        max = prob;

                        RectF rectF = face.getRectF();
                        rectF.round(rect);
                    }
                }

                float[] emb_array = new float[EMBEDDING_SIZE];
                faceNet.getEmbeddings(bitmap, rect).get(emb_array);
//                emb_array = normalizer(emb_array);
                list.add(emb_array);
            }

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                float[] array = list.get(i);
//                builder.append(label);
                for (int j = 0; j < array.length; j++) {
                    builder.append(array[j]).append(" ");
                }
                builder.deleteCharAt(builder.length()-1);
                if (i < list.size() - 1) builder.append(System.lineSeparator());
            }
            addPerson(person_name); //此处再更新name_list
            FileUtils.appendText(builder.toString(), DATA_FILE);

        }
    }

    /**
     * @param name: The name to check
     * @return if not exists return 1,else return 0;
     */
    boolean ifNoSameName(String name){
        for(int i = 0; i < personNames.size(); i++){
            if(name.equals(personNames.get(i))){
                return false;
            }
        }
        return true;
    }
    /**
     * @param name The name to add
     *             update the NameList
     */
    void addPerson(String name) {
        personNames.add(name);
        FileUtils.appendText(name, FileUtils.LABEL_FILE);

    }

    /**
     * @return Number of Name
     */
    int getNameListSize(){
        return personNames.size();
    }

    private Bitmap getBitmapFromUri(ContentResolver contentResolver, Uri uri) throws Exception {
        ParcelFileDescriptor parcelFileDescriptor =
                contentResolver.openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();

        return bitmap;
    }

    void enableStatLogging(final boolean debug){
    }

    String getStatString() {
        return faceNet.getStatString();
    }

    void close() {
        mtcnn.close();
        faceNet.close();
    }
}

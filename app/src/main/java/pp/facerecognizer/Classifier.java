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

package pp.facerecognizer;

import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import pp.facerecognizer.env.FileUtils;
import pp.facerecognizer.faceCompare.DetectedFace;
import pp.facerecognizer.wrapper.FaceNet;
import pp.facerecognizer.wrapper.MTCNN;
import pp.facerecognizer.faceCompare.FaceFeature;

import static pp.facerecognizer.env.FileUtils.DATA_FILE;

/**
 * Generic interface for interacting with different recognition engines.
 */
public class Classifier {
    /**
     * An immutable result returned by a Classifier describing what was recognized.
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
        private final Float confidence;

        /** Optional location within the source image for the location of the recognized object. */
        private RectF location;

        Recognition(
                final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return new RectF(location);
        }

        void setLocation(RectF location) {
            this.location = location;
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

            if (confidence != null) {
                resultString += String.format("(%.1f%%) ", confidence * 100.0f);
            }

            if (location != null) {
                resultString += location + " ";
            }

            return resultString.trim();
        }
    }

    public static final int EMBEDDING_SIZE = 512;
    private static Classifier classifier;

    private MTCNN mtcnn;
    private FaceNet faceNet;

    private List<String> classNames;


    private Classifier() {}
    static Classifier getInstance (AssetManager assetManager,
                                   int inputHeight,
                                   int inputWidth) throws Exception {
        if (classifier != null) return classifier;

        classifier = new Classifier();

        classifier.mtcnn = MTCNN.create(assetManager);
        classifier.faceNet = FaceNet.create(assetManager, inputHeight, inputWidth);

        classifier.classNames = FileUtils.readFileByLine(FileUtils.LABEL_FILE);

        return classifier;
    }

    CharSequence[] getClassNames() {
        CharSequence[] cs = new CharSequence[classNames.size() + 1];
        int idx = 1;

        cs[0] = "+ Add new person";
        for (String name : classNames) {
            cs[idx++] = name;
        }

        return cs;
    }

    List<Recognition> recognizeImage(Bitmap bitmap, Matrix matrix) throws FileNotFoundException {
        synchronized (this) {
            List<DetectedFace> detectedFaceList = mtcnn.detect(bitmap);

            final List<Recognition> mappedRecognitions = new LinkedList<>();
            if(detectedFaceList!=null){
                for (DetectedFace detectedFace : detectedFaceList) {
                    RectF rectF = detectedFace.getRectF();
                    float[] landmarks = detectedFace.getLandmarks();
                    float[] embeddingsArray = new float[Classifier.EMBEDDING_SIZE];
                    Rect rect = new Rect();
                    assert rectF != null;
                    rectF.round(rect);

                    FloatBuffer buffer = faceNet.getEmbeddings(bitmap, rect, landmarks);
                    buffer.get(embeddingsArray,0 ,embeddingsArray.length);
                    android.util.Pair<Integer, Double> pair = FaceFeature.search(embeddingsArray, DATA_FILE);
                    // first - label; second - cmpResult
                    matrix.mapRect(rectF);
                    String name;
                    int label = pair.first;
                    double result = pair.second;
                    if (label >= 0 )
                        name = classNames.get(label);
                    else
                        name = "Unknown";

                    Recognition resultRec =
                            new Recognition("" + name, name, (float) result, rectF);
                    mappedRecognitions.add(resultRec);
                }
            }    
            return mappedRecognitions;
        }

    }

    void updateData(int label, ContentResolver contentResolver, ArrayList<Uri> uris) throws Exception {
        synchronized (this) {
            ArrayList<float[]> list = new ArrayList<>();

            for (Uri uri : uris) {
                Bitmap bitmap = getBitmapFromUri(contentResolver, uri);
                List<DetectedFace> detectedFaceList = mtcnn.detect(bitmap);

                float max = 0f;
                Rect rect = new Rect();
                DetectedFace maxFace = null;

                for (DetectedFace face : detectedFaceList) {
                    Float prob =  face.getProbability();
                    if (prob > max) {
                        max = prob;
                        maxFace = face;
                        RectF rectF =  face.getRectF();
                        rectF.round(rect);
                    }
                }

                float[] emb_array = new float[EMBEDDING_SIZE];
                faceNet.getEmbeddings(bitmap, rect, maxFace.getLandmarks()).get(emb_array);
                list.add(emb_array);
            }

            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                float[] array = list.get(i);
                builder.append(label);
                for (int j = 0; j < array.length; j++) {
                    builder.append(" ").append(j).append(":").append(array[j]);
                }
                if (i < list.size() - 1) builder.append(System.lineSeparator());
            }
            FileUtils.appendText(builder.toString(), DATA_FILE);

        }
    }

    int addPerson(String name) {
        FileUtils.appendText(name, FileUtils.LABEL_FILE);
        classNames.add(name);

        return classNames.size();
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

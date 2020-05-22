/* Copyright 2016 The TensorFlow Authors. All Rights Reserved.
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

package qiang.facerecognition.wrapper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.RectF;

import org.tensorflow.Graph;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;

import qiang.facerecognition.face.FaceAttr;

public class MTCNN {
    private static final String MODEL_FILE = "file:///android_asset/mtcnn.pb";
    private static final float FACE_PROB_THRESHOLD = 0.98f;     // 只返回概率大于0.98的人脸
    // Only return this many results.
    private static final float FACE_SIZE_THRESHOLD = 30;  //过滤掉过小的人脸
    private static final int MAX_RESULTS = 100;
    private static final int NUM_LANDMARKS = 10;
    private static final int BYTE_SIZE_OF_FLOAT = 4;
    // Config values.
    private String inputName;
    // Pre-allocated buffers.
    private String[] outputNames;
    private TensorFlowInferenceInterface inferenceInterface;

    public MTCNN(Activity activity) {

        inferenceInterface = new TensorFlowInferenceInterface(activity.getAssets(), MODEL_FILE);

        final Graph g = inferenceInterface.graph();

        inputName = "input";
        if (g.operation(inputName) == null)
            throw new RuntimeException("Failed to find input Node '" + inputName + "'");

        outputNames = new String[] {"prob", "landmarks", "box"};
        if (g.operation(outputNames[0]) == null)
            throw new RuntimeException("Failed to find output Node '" + outputNames[0] + "'");

        if (g.operation(outputNames[1]) == null)
            throw new RuntimeException("Failed to find output Node '" + outputNames[1] + "'");

        if (g.operation(outputNames[2]) == null)
            throw new RuntimeException("Failed to find output Node '" + outputNames[2] + "'");

        // Pre-allocate buffers.
    }


    public ArrayList<FaceAttr> detect(Bitmap bitmap) {
        FloatBuffer outputProbs = ByteBuffer.allocateDirect(MAX_RESULTS * BYTE_SIZE_OF_FLOAT)
                .order(ByteOrder.nativeOrder()).
                        asFloatBuffer();
        FloatBuffer outputLandmarks = ByteBuffer.allocateDirect(MAX_RESULTS * BYTE_SIZE_OF_FLOAT * 10)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        FloatBuffer outputBoxes = ByteBuffer.allocateDirect(MAX_RESULTS * BYTE_SIZE_OF_FLOAT * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] intValues = new int[w * h];
        float[] floatValues = new float[w * h * 3];

        bitmap.getPixels(intValues, 0, w, 0, 0, w, h);

        // BGR
        for (int i = 0; i < intValues.length; ++i) {
            int p = intValues[i];

            floatValues[i * 3 + 0] = p & 0xFF;
            floatValues[i * 3 + 1] = (p >> 8) & 0xFF;
            floatValues[i * 3 + 2] = (p >> 16) & 0xFF;
        }

        inferenceInterface.feed(inputName, floatValues, h, w, 3);

        inferenceInterface.run(outputNames, false);

        inferenceInterface.fetch(outputNames[0], outputProbs);
        inferenceInterface.fetch(outputNames[1], outputLandmarks);
        inferenceInterface.fetch(outputNames[2], outputBoxes);

        outputProbs.flip();
        outputLandmarks.flip();
        outputBoxes.flip();

        int len = outputProbs.remaining();
        ArrayList<FaceAttr> facesList = new ArrayList<>();
        FaceAttr detectedFaceTmp;

        for (int i = 0; i < len; i++) {
            float prob = outputProbs.get();
            if (prob < FACE_PROB_THRESHOLD){
                continue;
            }
            float top = outputBoxes.get();
            float left = outputBoxes.get();
            float bottom = outputBoxes.get();
            float right = outputBoxes.get();

//            if(Math.abs(top-bottom) < FACE_SIZE_THRESHOLD || Math.abs(left-right) < FACE_SIZE_THRESHOLD){
//                continue;
//            }

            float[] landmarks = new float[NUM_LANDMARKS] ;
            float[] landmarksCoordinate = new float[NUM_LANDMARKS] ;
            for(int j = 0; j < NUM_LANDMARKS; j++){
                //  前5个点是y坐标，后5个是x坐标
                landmarks[j] = outputLandmarks.get();
            }

            for(int j = 0; j < NUM_LANDMARKS; j++){
                // 变成(x,y)的坐标
                if(j % 2 == 0){
                    landmarksCoordinate[j] = landmarks[NUM_LANDMARKS/2+j/2];
                }
                else {
                    landmarksCoordinate[j] = landmarks[j/2];
                }

            }

            detectedFaceTmp = new FaceAttr(prob, landmarksCoordinate, new RectF(left, top, right, bottom));
            facesList.add(detectedFaceTmp);
        }

        if (outputBoxes.hasRemaining())
            outputBoxes.position(outputBoxes.limit());

        outputProbs.compact();
        outputLandmarks.compact();
        outputBoxes.compact();

        return facesList;
    }

    public String getStatString() {
        return inferenceInterface.getStatString();
    }

    public void close() {
        inferenceInterface.close();
    }
}

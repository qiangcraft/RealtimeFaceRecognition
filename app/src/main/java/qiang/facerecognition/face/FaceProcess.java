package qiang.facerecognition.face;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Pair;


import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import qiang.facerecognition.utils.FileUtils;

import static qiang.facerecognition.utils.FileUtils.EMBEDDING_SIZE;


public class FaceProcess {

    //计算两个特征向量之间的欧氏距离
    private static float EuclideanDistance(float[] fea1, float[] fea2){
        float dist = 0;
        for (int i = 0; i < EMBEDDING_SIZE; i++)
            dist += Math.pow(fea1[i]-fea2[i], 2);
        dist = (float)Math.sqrt(dist);
        return dist;
    }
    //计算两个特征向量之间的余弦相似度
    public static float CosineSimilarity(float [] fea1, float[] fea2){
        float similarity = 0;
        for(int i = 0; i < EMBEDDING_SIZE; i++){
            similarity += fea1[i]*fea2[i];   // |fea1|=|fea2|=1
        }
        return similarity;
    }

    /**
     * @Description: initialize the FacesArrayList; avoid to read the file and process the data
     * frequently in each compare.
     * @Author: qiangz
     * @Date: 2019/11/12
     */
    public static Map<String, float[]> getNameFacesDBMap(String FaceDBPath){
        ArrayList<String> faceDbList = null;
        Map<String, float[]> NameFacesDBMap = new HashMap<>();
        try {
            faceDbList = FileUtils.readFileByLine(FaceDBPath);
        }catch (Exception e){
            e.printStackTrace();
        }
        if(faceDbList != null && !faceDbList.isEmpty()) {
            int index;
            String[] embeddings;
            String name = "";
            for (String faceStr : faceDbList) {
                String[] tmp = faceStr.split(":");
                float[] faceArray = new float[EMBEDDING_SIZE];
                name = tmp[0];
                embeddings = tmp[1].split(" ");
                index = 0;
                for (String embedding : embeddings){
                    faceArray[index++] = Float.parseFloat(embedding);
                }
                NameFacesDBMap.put(name, faceArray);
            }

        }
        Log.d("getNameFacesDBMap", "is OK");
        return NameFacesDBMap;
    }


    //在熟人数据库中搜索，计算两个人脸的FaceFeature之间的欧式距离，若小于FACE_COMPARE_THRESHOLD则继续找出距离最小值对应的FaceIndex。
    public static Pair<String,Float> search(float[] embeddingsArray,  Map<String, float[]> NameFacesDBMap, float threshold){
//        float possibility = 0.0f;
        String nameResult = "unknown";     //寻找最小结果对应的label
        float distanceResult = 100;     //用于寻找最小计算结果
        float similarity = 0;

        if(NameFacesDBMap != null  && !NameFacesDBMap.isEmpty()){
            String tempName;
            float[] faceArray;
            float tempDistance;//初始化计算结果

            Iterator<Map.Entry<String, float[]>> entries = NameFacesDBMap.entrySet().iterator();
            while (entries.hasNext()){
                Map.Entry<String, float[]> entry = entries.next();
                faceArray = entry.getValue();
                tempName = entry.getKey();
                tempDistance = EuclideanDistance(embeddingsArray, faceArray);

                if (tempDistance < distanceResult) {
                    distanceResult = tempDistance;
                    similarity = CosineSimilarity(embeddingsArray, faceArray);
                    if (tempDistance < threshold) {
                        nameResult = tempName;
                    }
                }
            }
        }
        return new Pair<>(nameResult, similarity);
    }

    public static List<String> getSavedPersons(String FaceDBPath){
        List<String> names = new ArrayList<>();
        try {
            List<String> datas = FileUtils.readFileByLine(FaceDBPath);
            for(String data:datas){
                names.add(data.split(":")[0]);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return names;
    }

    public static Bitmap getBitmapFromUri(ContentResolver contentResolver, Uri uri) throws Exception{
        ParcelFileDescriptor parcelFileDescriptor =
                contentResolver.openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();

        return bitmap;
    }

}

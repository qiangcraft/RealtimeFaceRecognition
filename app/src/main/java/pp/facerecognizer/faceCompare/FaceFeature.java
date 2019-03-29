package pp.facerecognizer.faceCompare;
import android.util.Log;
import android.util.Pair;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import pp.facerecognizer.env.FileUtils;

import static android.content.ContentValues.TAG;
import static pp.facerecognizer.env.FileUtils.EMBEDDING_SIZE;

/**
 * Created by caydencui on 2018/9/6.
 * 人脸特征(512维特征值)
 * 相似度取特征向量之间的欧式距离.
 */
public class FaceFeature {
    public static final int DIMS=512;
//    private float fea[];
//    public FaceFeature(){
//        fea=new float[DIMS];
//    }
//    public float[] getFeature(){
//        return fea;
//    }
//
//    public void setFea(float[] fea) {
//        this.fea = fea;
//    }

    //比较当前特征和另一个特征之间的相似度
    public static double compare(float fea1[], float fea2[]){
        double dist=0;
        for (int i=0;i<DIMS;i++)
            dist+=(fea1[i]-fea2[i])*(fea1[i]-fea2[i]);
        dist= Math.sqrt(dist);
        return dist;
    }

    //在熟人数据库中搜索，计算两个人脸的FaceFeature之间的欧式距离，若小于1.1则认为寻找成功。
    public static Pair<Integer,Double> search(float[] embbedingsArray, String FaceDBpath) throws FileNotFoundException {
        ArrayList<String> faceDbList;
        Pair<Integer, Double>  pair;
        faceDbList = FileUtils.readFileByLine(FaceDBpath);
        int label = -1; // return unknown
        double result = 1000;
        pair = new Pair<>(label, result);
        int p=1;
        for(String faceStr: faceDbList){
//            Log.d(TAG, "search faceStr: "+faceStr);
            int i = 0;
            float[] faceArray = new float[EMBEDDING_SIZE];
            ArrayList<String> embeddingList = new ArrayList<>();
            embeddingList.clear();
            for(String tmp: faceStr.split(" ")){
                if(i == 0) {
                    label = Integer.parseInt(tmp); //第一个数字为label,获取label
                    i++;
                } else{
                    embeddingList.add(tmp);
//                    Log.d(TAG, "search tmp: "+tmp);
                }
            }
            for(String embeddingStr:embeddingList){
                int k = 0; // embeddingStr 如 0:0.014449
                int index=0;
                float embeddingNum = 0;
                for(String tmp:embeddingStr.split(":")){
                    if(k == 0) {
                        index = Integer.parseInt(tmp);
//                        Log.d(TAG, "embedding index: "+index);
                        k++;
                    }else{
                         embeddingNum= Float.parseFloat(tmp);
//                         Log.d(TAG, "embedding num: "+embeddingNum);
                         break;
                    }
                }
                faceArray[index] = embeddingNum;
            }
//            Log.d(TAG, "search faceArray: "+faceArray.length);
//            for(int n = 0; n < faceArray.length/16; n++) {
//               Log.d(TAG, "faceArray["+n+"]:"+faceArray[n]);
//
//            }
            result = compare(faceArray, embbedingsArray);
            Log.d(TAG, "search Result"+p+":"+result);
            p++;
            if(result < 1.1){
                pair = new Pair<>(label, result);
                return pair;
            }
        }
        return pair;
    }
}

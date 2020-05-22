package qiang.facerecognition.face;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;


import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import qiang.facerecognition.env.Device;
import qiang.facerecognition.env.TTS;
import qiang.facerecognition.tracking.MultiBoxTracker;
import qiang.facerecognition.utils.FileUtils;
import qiang.facerecognition.view.OverlayView;
import qiang.facerecognition.wrapper.FaceRecognizer;
import qiang.facerecognition.wrapper.MTCNN;

import static qiang.facerecognition.env.ImageUtils.resizeImage;


public class FaceUtils {
    /**
      * @ClassName:      FaceUtils
      * @Description:    将人脸检测、人脸识别和表情识别三部分封装到该类中。
      * @Author:         JianboZhu
      * @CreateDate:     2019/7/19
      * @Update:
     */
    private final boolean isSaveBitmap = false;
    private static final int FACE_SIZE = 112;
    private static final int FRAME_SIZE_W = 720;
    private static final int FRAME_SIZE_H = 360;

    //    private FaceDetection faceDetection;
    private MTCNN faceDetection;
    private FaceRecognizer faceRecognizer;

    private MultiBoxTracker tracker;
    private OverlayView overlayView;
    private TTS tts;
    private Map<String, float[]> NameFacesDBMap;

    public FaceUtils(Activity activity, Device device, int numThreads, Map<String, float[]> nameFacesDBMap){
        this.faceDetection = new MTCNN(activity);
        this.faceRecognizer = new FaceRecognizer(activity,device,numThreads);
        this.NameFacesDBMap = nameFacesDBMap;
        createAndroidFile(activity);
    }
    public void  setDrawSpeakCfg(MultiBoxTracker tracker, OverlayView overlayView){
        this.tracker = tracker;
        this.overlayView = overlayView;
    }
    public void  setDrawSpeakCfg(MultiBoxTracker tracker, OverlayView overlayView, TTS tts){
        this.tracker = tracker;
        this.overlayView = overlayView;
        this.tts = tts;
    }

    public static CharSequence[] getShowList() {
        /**
         * @description:   获取AlertDialog对话框列表items
         * @param:         [names]需要显示的字符串列表
         * @return:        java.lang.CharSequence[]
         * @author:        JianboZhu
         * @date:          2019/7/20
         * @update:
         */
        List<String> personNames = FaceProcess.getSavedPersons(FileUtils.DATA_FILE);
        CharSequence[] cs = new CharSequence[personNames.size() + 1];
        int idx = 1;

        cs[0] = "+ 添加新的人脸";
        for (String name : personNames) {
            cs[idx++] = name;
        }

        return cs;
    }

    public static boolean isExistPerson(String name){
        boolean flag = false;
        List<String> personNames = FaceProcess.getSavedPersons(FileUtils.DATA_FILE);
        for(String temp:personNames){
            if(name.equals(temp)){
                flag = true;
                break;
            }
        }
        return flag;
    }


    private void createAndroidFile(Activity activity){
        /**
         * @description:   将程序自带的人脸信息assets/data文件复制到手机中
         * @author:        JianboZhu
         * @date:          2019/7/23
         * @update:
         */
        File dir = new File(FileUtils.ROOT);
        if (!dir.isDirectory()) {
            if (dir.exists()) dir.delete();
            dir.mkdirs();

            AssetManager mgr = activity.getAssets();
            FileUtils.copyAsset(mgr, FileUtils.DATA_FILE);
        }
    }

    public int addPerson(String personName, ContentResolver contentResolver, Uri dataUri){
        /**
         * @description:   添加人脸图片。排除合照，避免获取到不匹配的人脸特征，保证低库人脸数据质量，提取特征保存到文件
         * @param:         [personName名字, contentResolver, dataUri图片路径]
         * @return:        boolean, 添加成功返回true
         * @author:        JianboZhu
         * @date:          2019/7/23
         * @update:
         */
//        boolean flag = false;
        int faceNum = -1;
        try {

            Bitmap bitmap = FaceProcess.getBitmapFromUri(contentResolver, dataUri);
            List<FaceAttr> facesList = detectFace(bitmap);
//            float maxProb = 0f;
            Rect rect = new Rect();
            faceNum = facesList.size();

            //排除合照，保证底库质量
            if(faceNum == 1){
                RectF rectF = facesList.get(0).getBbox();
                rectF.round(rect);

                Bitmap faceBitmap = resizeImage(bitmap,rect.left,rect.top,
                        rect.width(),rect.height(),FACE_SIZE,FACE_SIZE);
                float[] embeddings = this.faceRecognizer.getFaceEmbeddings(faceBitmap);

                StringBuilder builder = new StringBuilder();
                //  保存的信息形式为 姓名:0.1 0.1 0.1 ……
                builder.append(personName).append(":");
                for(float embedding:embeddings){
                    builder.append(embedding).append(" ");
                }
                builder.deleteCharAt(builder.length()-1);   // 去掉最后的空格
                FileUtils.appendText(builder.toString(), FileUtils.DATA_FILE);
                NameFacesDBMap.put(personName, embeddings);

                if(isSaveBitmap)
                    FileUtils.saveBitmap(faceBitmap, personName+".png");
                return faceNum;
            }
            else{
                return faceNum;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return faceNum;

    }
    private Bitmap rotateBitmap(Bitmap origin, float angel){
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(angel);

        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        return newBM;
    }

    private Bitmap cropDoubleBbox(Bitmap origin, RectF bbox){
        int x = (int)(bbox.centerX()-bbox.width());
        int y = (int)(bbox.centerY()-bbox.height());
        int w = (int)bbox.width()*2;
        int h = (int)bbox.height()*2;
        x = x<0?0:x;
        y = y<0?0:y;
        w = w+x > origin.getWidth()?origin.getWidth()-x : w;
        h = h+y > origin.getHeight()?origin.getHeight()-y : h;
        Matrix m = new Matrix();
        return Bitmap.createBitmap(origin, x, y, w, h, m,false);
    }
    public Bitmap alignFace(Bitmap origin, FaceAttr faceAttr) {
        if (origin == null) {
            return null;
        }
        Bitmap cropBM = cropDoubleBbox(origin, faceAttr.getBbox());


        float[] landmark = faceAttr.getLandmark();
        float angel = getEyeAngel(landmark);
        Log.d("Align", "angel: "+angel);
        if(isSaveBitmap) FileUtils.saveBitmap(cropBM,"crop.png");
        int width = cropBM.getWidth();
        int height = cropBM.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(angel);

        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(cropBM, 0, 0, width, height, matrix, false);
        if(isSaveBitmap) FileUtils.saveBitmap(newBM,"rotate.png");

        List<FaceAttr> newFace = detectFace(newBM);
        RectF newBox;
        if (newFace.size()>0){
            newBox = newFace.get(0).getBbox();
        }else {
            newBox = new RectF(newBM.getWidth()/4f,newBM.getHeight()/4f,newBM.getWidth()*3/4f,newBM.getHeight()*3/4f);
        }
        int x = (int)(newBox.left);
        int y = (int)(newBox.top);
        int w = (int)(newBox.width());
        int h = (int)(newBox.height());
        x = x<0?0:x;
        y = y<0?0:y;
        w = w+x > newBM.getWidth()?newBM.getWidth()-x : w;
        h = h+y > newBM.getHeight()?newBM.getHeight()-y : h;

        Matrix m = new Matrix();
        Bitmap alignBM = Bitmap.createBitmap(newBM, x, y, w, h, m,false);
        if(isSaveBitmap) FileUtils.saveBitmap(alignBM,"align.png");

        return alignBM;
    }


    private float getEyeAngel(float[] landmark){
        float leftEyeX = landmark[0];
        float leftEyeY = landmark[1];
        float rightEyeX = landmark[2];
        float rightEyeY = landmark[3];
        float acr =  (float) Math.atan2(rightEyeY-leftEyeY,rightEyeX-leftEyeX);
        return (float)(-180*acr/Math.PI);
    }

    public void recoverScale(List<FaceAttr> faces, float ratioW, float ratioH){
//        //  深拷贝
//        List<FaceAttr> reFaces = new ArrayList<>();
//        for(FaceAttr face:faces){
//            FaceAttr faceAttr = new FaceAttr(face.getProb(),face.getLandmark(),face.getBbox());
//            reFaces.add(faceAttr);
//        }

        for(int i = 0; i < faces.size(); i++){
            RectF box = faces.get(i).getBbox();
            RectF newBox = new RectF(box.left*ratioW, box.top*ratioH,
                    box.right*ratioW, box.bottom*ratioH);
            faces.get(i).setBbox(newBox);

            float[] landmark = faces.get(i).getLandmark();
            float[] newLandmark = new float[10];
            for(int j = 0; j < 10; j++){
                if(j % 2 == 0){
                    newLandmark[j] = landmark[j]*ratioW;
                }
                else {
                    newLandmark[j] = landmark[j]*ratioH;
                }
            }
            faces.get(i).setLandmark(newLandmark);
        }

    }
    public boolean isFrontFace(float[] landmark, RectF rectF, float threshold, float scale){
        float leftEyeX = landmark[0];
        float rightEyeX = landmark[2];
        float ratio = Math.abs(0.5f*(leftEyeX+rightEyeX)-scale*rectF.centerX())/(rectF.width()*scale);   // 0.2
//        float ratio = Math.abs(leftEyeX+rightEyeX-rectF.centerX()/(rightEyeX-leftEyeX));   // 1100
        Log.d("Align",""+ratio);
        return ratio<threshold;
    }
    public synchronized List<FaceAttr> detectFace(Bitmap bitmap){
        ArrayList<FaceAttr> detectedFaces = this.faceDetection.detect(bitmap);
//                long timeDetect=0, startTime, endTimeCost;
//        for(int i=0;i<105;i++){
//            startTime= SystemClock.uptimeMillis();
//            detectedFaces = this.faceDetection.detect(bitmap);
//            endTimeCost = SystemClock.uptimeMillis()-startTime;
//            if(i>4){
//                timeDetect += endTimeCost;
//            }
//        }
//        timeDetect /= 100.f;
//        Log.d("TestTimeCost","Face Detect: "+timeDetect);
        return detectedFaces;
    }

    public Pair<String,Float> recognizePerson(Bitmap bitmap, Map<String, float[]> NameFacesDBMap) throws FileNotFoundException {
//        Bitmap faceBitmap = resizeImage(bitmap,rect.left,rect.top,rect.width(),rect.height(),FACE_SIZE,FACE_SIZE);
        Bitmap faceBitmap = resizeImage(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),FACE_SIZE,FACE_SIZE);
        if(isSaveBitmap)
            FileUtils.saveBitmap(faceBitmap, "Person.png");

        Pair<String,Float> result = this.faceRecognizer.recognizeImage(faceBitmap, NameFacesDBMap);
//        long timeCost=0, startTime, endTimeCost;
//        for(int i=0;i<105;i++){
//            startTime= SystemClock.uptimeMillis();
//            result = this.faceRecognizer.recognizeImage(faceBitmap);
//            endTimeCost = SystemClock.uptimeMillis()-startTime;
//            if(i>4){
//                timeCost += endTimeCost;
//            }
//        }
//        timeCost /= 100.f;
//        Log.d("TestTimeCost","Face Recognize: "+timeCost);
        return result;
    }


    public List<FaceInfo> inference(Bitmap bitmap, byte[] luminanceCopy, long timestamp) throws FileNotFoundException {
        /**
         * @description:   对输入图像进行检测、识别
         * @param:         [bitmap相机获取到的图像输入],
         *                 后面两个参数是为了在识别之前，先进行画框跟踪。
         * @return:        图像中获取到的人脸信息列表
         * @author:        JianboZhu
         * @date:          2019/7/23
         * @update:
         */
        List<FaceInfo> faceinfoList = new LinkedList<>();
        //  先将相机获取到的图像进行下采样再进行人脸检测
        Bitmap sampleBitmap = resizeImage(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),
                FRAME_SIZE_W,FRAME_SIZE_H);
        long startTime = SystemClock.uptimeMillis();
        List<FaceAttr> detectedFaces = detectFace(sampleBitmap);
//        List<FaceAttr> detectedFaces = detectFace(bitmap);
        Log.d("timecost","face detect time: "+(SystemClock.uptimeMillis()-startTime));
        //  将框的位置映射到原图像
        recoverScale(detectedFaces, (float)bitmap.getWidth()/FRAME_SIZE_W,
                (float)bitmap.getHeight()/FRAME_SIZE_H);

        // 先将人脸框画出，后面的识别结果出来后再画一次
        tracker.trackBbox(detectedFaces,luminanceCopy,timestamp);
        overlayView.postInvalidate();

        for(FaceAttr faceAttr:detectedFaces){
//            boolean flag = isFrontFace(faceAttr.getLandmark(),rectF,0.2f,1);
            boolean flag = true;
            long starttime = SystemClock.uptimeMillis();
            Bitmap alignedBitmap = alignFace(bitmap, faceAttr);
            long endtime = SystemClock.uptimeMillis();
            Log.d("timecost","align time: "+(endtime-starttime));

            Pair<RectF,Float> bbox = new Pair<>(faceAttr.getBbox(),faceAttr.getProb());
            Pair<String,Float> person = new Pair<>("",0f);
            if(flag){
                starttime = SystemClock.uptimeMillis();
                person = recognizePerson(alignedBitmap, NameFacesDBMap);
                endtime = SystemClock.uptimeMillis();
                Log.d("timecost","recognize time: "+(endtime-starttime));
            }
            FaceInfo faceInfo = new FaceInfo(bbox, person);
            faceinfoList.add(faceInfo);

            Log.d("results",person.first);
//            if(!person.first.equals("unknown")){
//                speech.speak(person.first);
//            }

        }

        return faceinfoList;
    }

    public void close(){
        this.faceRecognizer.close();
        this.faceDetection.close();
    }
}


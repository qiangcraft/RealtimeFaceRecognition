package qiang.facerecognition.utils;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Scanner;

import qiang.facerecognition.env.Logger;

public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final Logger LOGGER = new Logger();
    public static final String ROOT =
            Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "facerecognizer";

    public static final String DATA_FILE = "data";
    public static final int EMBEDDING_SIZE = 512;

    /**
     * Saves a Bitmap object to disk for analysis.
     *
     * @param bitmap The bitmap to save.
     * @param filename The location to save the bitmap to.
     */
    public static void saveBitmap(final Bitmap bitmap, final String filename) {
        LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), ROOT);
        final File myDir = new File(ROOT);

        if (!myDir.mkdirs()) {
            LOGGER.i("Make dir failed");
        }

        final File file = new File(myDir, filename);
        if (file.exists()) {
            file.delete();
        }
        try {
            final FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 99, out);
            out.flush();
            out.close();
        } catch (final Exception e) {
            LOGGER.e(e, "Exception!");
        }
    }

    public static void copyAsset(AssetManager mgr, String filename) {
        InputStream in = null;
        OutputStream out = null;

        try {
            File file = new File(ROOT + File.separator + filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            in = mgr.open(filename);
            out = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int read;
            while((read = in.read(buffer)) != -1){
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            LOGGER.e(e, "Excetion!");
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    LOGGER.e(e, "IOExcetion!");
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    LOGGER.e(e, "IOExcetion!");
                }
            }
        }
    }

    public static void appendText(String text, String filename) {
        try {
            if(readFileByLine(filename).size()>0){
                text = System.lineSeparator()+text;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        try(FileWriter fw = new FileWriter(ROOT + File.separator + filename, true);
            PrintWriter out = new PrintWriter(new BufferedWriter(fw))) {
            out.print(text);
        } catch (IOException e) {
            //exception handling left as an exercise for the reader
            Log.e(TAG, "AppendText IOExcetion!");
        }
    }

    //用于读取assets中的data和label
    public static ArrayList<String> readFileByLine(String filename) throws FileNotFoundException{
        Scanner s = new Scanner(new File(ROOT + File.separator + filename));
        ArrayList<String> list = new ArrayList<>();
        while (s.hasNextLine()){
            list.add(s.nextLine());
        }
        s.close();
        return list;
    }

    //删除此人对应的data和label中的第index行
    public static void deleteTheFace(int index){
        String dataPath = ROOT + File.separator + DATA_FILE; // 数据文件路径
//        String labelPath = ROOT + File.separator + LABEL_FILE; //标签（姓名）路径
        FileModify obj1 = new FileModify();
//        FileModify obj2 = new FileModify();

        obj1.write(dataPath, obj1.read(dataPath, index)); // 读取数据文件
//        obj2.write(labelPath, obj2.read(labelPath, index)); //读取标签文件
    }
}

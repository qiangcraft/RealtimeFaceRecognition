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

package qiang.facerecognition.env;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;

/** Utility class for manipulating images. */
public class ImageUtils {
  // This value is 2 ^ 18 - 1, and is used to clamp the RGB values before their ranges
  // are normalized to eight bits.
  static final int kMaxChannelValue = 262143;

  @SuppressWarnings("unused")
  private static final Logger LOGGER = new Logger();

  /**
   * Utility method to compute the allocated size in bytes of a YUV420SP image of the given
   * dimensions.
   */
  public static int getYUVByteSize(final int width, final int height) {
    // The luminance plane requires 1 byte per pixel.
    final int ySize = width * height;

    // The UV plane works on 2x2 blocks, so dimensions with odd size must be rounded up.
    // Each 2x2 block takes 2 bytes to encode, one each for U and V.
    final int uvSize = ((width + 1) / 2) * ((height + 1) / 2) * 2;

    return ySize + uvSize;
  }

  /**
   * Saves a Bitmap object to disk for analysis.
   *
   * @param bitmap The bitmap to save.
   */
  public static void saveBitmap(final Bitmap bitmap) {
    saveBitmap(bitmap, "preview.png");
  }

  /**
   * Saves a Bitmap object to disk for analysis.
   *
   * @param bitmap The bitmap to save.
   * @param filename The location to save the bitmap to.
   */
  public static void saveBitmap(final Bitmap bitmap, final String filename) {
    final String root =
        Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "faceUtils";
    LOGGER.i("Saving %dx%d bitmap to %s.", bitmap.getWidth(), bitmap.getHeight(), root);
    final File myDir = new File(root);

    if (!myDir.mkdirs()) {
      LOGGER.i("Make dir failed");
    }

    final String fname = filename;
    final File file = new File(myDir, fname);
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
  public static Bitmap resizeImage(Bitmap bitmap, int x, int y, int w, int h, int targetW, int targetH) {
//        Log.d("Align","resize origin xywh: "+ x+" "+y+ " "+w+" "+h);
    x = x<0?0:x;
    y = y<0?0:y;
    w = w+x > bitmap.getWidth()?bitmap.getWidth()-x : w;
    h = h+y > bitmap.getHeight()?bitmap.getHeight()-y : h;
//        Log.d("Align","resize xywh: "+ x+" "+y+" "+w+" "+h);
    float scaleWidth = ((float) targetW) / w;
    float scaleHeight = ((float) targetH) / h;

    Matrix matrix = new Matrix();
    matrix.postScale(scaleWidth, scaleHeight);
    // if you want to rotate the Bitmap
//         matrix.postRotate(angel);

    return Bitmap.createBitmap(bitmap, x, y, w, h, matrix, true);
  }
  public static void convertYUV420SPToARGB8888(byte[] input, int width, int height, int[] output) {
    final int frameSize = width * height;
    for (int j = 0, yp = 0; j < height; j++) {
      int uvp = frameSize + (j >> 1) * width;
      int u = 0;
      int v = 0;

      for (int i = 0; i < width; i++, yp++) {
        int y = 0xff & input[yp];
        if ((i & 1) == 0) {
          v = 0xff & input[uvp++];
          u = 0xff & input[uvp++];
        }

        output[yp] = YUV2RGB(y, u, v);
      }
    }
  }
  /**
   * @方法描述 将RGB字节数组转换成Bitmap，
   */
  static public Bitmap rgb2Bitmap(byte[] data, int width, int height) {
    int[] colors = convertByteToColor(data);    //取RGB值转换为int数组
    if (colors == null) {
      return null;
    }

    Bitmap bmp = Bitmap.createBitmap(colors, 0, width, width, height,
            Bitmap.Config.ARGB_8888);
    return bmp;
  }
  public static byte[][] rgb2YUV420Grop(int[] pixels, int width, int height) {
    int len = width * height;
    // yuv格式数组大小，y亮度占len长度，u,v各占len/4长度。
    byte[][] yuv = new byte[3][len];
    int y, u, v;
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {

        // 屏蔽ARGB的透明度值
        int rgb = pixels[i * width + j] & 0x00FFFFFF;
        // 像素的颜色顺序为bgr，移位运算。
        int r = rgb & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = (rgb >> 16) & 0xFF;
        // 套用公式
        y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
        u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
        v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
        // rgb2yuv
        // y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        // u = (int) (-0.147 * r - 0.289 * g + 0.437 * b);
        // v = (int) (0.615 * r - 0.515 * g - 0.1 * b);
        // RGB转换YCbCr
        // y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        // u = (int) (-0.1687 * r - 0.3313 * g + 0.5 * b + 128);
        // if (u > 255)
        // u = 255;
        // v = (int) (0.5 * r - 0.4187 * g - 0.0813 * b + 128);
        // if (v > 255)
        // v = 255;
        // 调整
        y = y < 16 ? 16 : (y > 255 ? 255 : y);
        u = u < 0 ? 0 : (u > 255 ? 255 : u);
        v = v < 0 ? 0 : (v > 255 ? 255 : v);
        // 赋值
        yuv[0][i * width + j] = (byte) y;
        yuv[1][i * width + j] = (byte) u;
        yuv[2][i * width + j] = (byte) v;

      }
    }
    return yuv;
  }

  public static byte[] rgb2YUV420(int[] pixels, int width, int height) {
    int len = width * height;
    // yuv格式数组大小，y亮度占len长度，u,v各占len/4长度。
    byte[] yuv = new byte[len * 3 / 2];
    int y, u, v;
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        // 屏蔽ARGB的透明度值
        int rgb = pixels[i * width + j] & 0x00FFFFFF;
        // 像素的颜色顺序为bgr，移位运算。
        int r = rgb & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = (rgb >> 16) & 0xFF;
        // 套用公式
        y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
        u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
        v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
        // rgb2yuv
        // y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        // u = (int) (-0.147 * r - 0.289 * g + 0.437 * b);
        // v = (int) (0.615 * r - 0.515 * g - 0.1 * b);
        // RGB转换YCbCr
        // y = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        // u = (int) (-0.1687 * r - 0.3313 * g + 0.5 * b + 128);
        // if (u > 255)
        // u = 255;
        // v = (int) (0.5 * r - 0.4187 * g - 0.0813 * b + 128);
        // if (v > 255)
        // v = 255;
        // 调整
        y = y < 16 ? 16 : (y > 255 ? 255 : y);
        u = u < 0 ? 0 : (u > 255 ? 255 : u);
        v = v < 0 ? 0 : (v > 255 ? 255 : v);
        // 赋值
        yuv[i * width + j] = (byte) y;
        yuv[len + (i >> 1) * width + (j & ~1) + 0] = (byte) u;
        yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
      }
    }
    return yuv;
  }
  // 将一个byte数转成int
  // 实现这个函数的目的是为了将byte数当成无符号的变量去转化成int
  public static int convertByteToInt(byte data) {

    int heightBit = (int) ((data >> 4) & 0x0F);
    int lowBit = (int) (0x0F & data);
    return heightBit * 16 + lowBit;
  }

  // 将纯RGB数据数组转化成int像素数组
  public static int[] convertByteToColor(byte[] data) {
    int size = data.length;
    if (size == 0) {
      return null;
    }

    int arg = 0;
    if (size % 3 != 0) {
      arg = 1;
    }

    // 一般RGB字节数组的长度应该是3的倍数，
    // 不排除有特殊情况，多余的RGB数据用黑色0XFF000000填充
    int[] color = new int[size / 3 + arg];
    int red, green, blue;
    int colorLen = color.length;
    if (arg == 0) {
      for (int i = 0; i < colorLen; ++i) {
        red = convertByteToInt(data[i * 3]);
        green = convertByteToInt(data[i * 3 + 1]);
        blue = convertByteToInt(data[i * 3 + 2]);

        // 获取RGB分量值通过按位或生成int的像素值
        color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
      }
    } else {
      for (int i = 0; i < colorLen - 1; ++i) {
        red = convertByteToInt(data[i * 3]);
        green = convertByteToInt(data[i * 3 + 1]);
        blue = convertByteToInt(data[i * 3 + 2]);
        color[i] = (red << 16) | (green << 8) | blue | 0xFF000000;
      }

      color[colorLen - 1] = 0xFF000000;
    }

    return color;
  }

  private static int YUV2RGB(int y, int u, int v) {
    // Adjust and check YUV values
    y = (y - 16) < 0 ? 0 : (y - 16);
    u -= 128;
    v -= 128;

    // This is the floating point equivalent. We do the conversion in integer
    // because some Android devices do not have floating point in hardware.
    // nR = (int)(1.164 * nY + 2.018 * nU);
    // nG = (int)(1.164 * nY - 0.813 * nV - 0.391 * nU);
    // nB = (int)(1.164 * nY + 1.596 * nV);
    int y1192 = 1192 * y;
    int r = (y1192 + 1634 * v);
    int g = (y1192 - 833 * v - 400 * u);
    int b = (y1192 + 2066 * u);

    // Clipping RGB values to be inside boundaries [ 0 , kMaxChannelValue ]
    r = r > kMaxChannelValue ? kMaxChannelValue : (r < 0 ? 0 : r);
    g = g > kMaxChannelValue ? kMaxChannelValue : (g < 0 ? 0 : g);
    b = b > kMaxChannelValue ? kMaxChannelValue : (b < 0 ? 0 : b);

    return 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
  }

  public static void convertYUV420ToARGB8888(
      byte[] yData,
      byte[] uData,
      byte[] vData,
      int width,
      int height,
      int yRowStride,
      int uvRowStride,
      int uvPixelStride,
      int[] out) {
    int yp = 0;
    for (int j = 0; j < height; j++) {
      int pY = yRowStride * j;
      int pUV = uvRowStride * (j >> 1);

      for (int i = 0; i < width; i++) {
        int uv_offset = pUV + (i >> 1) * uvPixelStride;

        out[yp++] = YUV2RGB(0xff & yData[pY + i], 0xff & uData[uv_offset], 0xff & vData[uv_offset]);
      }
    }
  }

  /**
   * Returns a transformation matrix from one reference frame into another. Handles cropping (if
   * maintaining aspect ratio is desired) and rotation.
   *
   * @param srcWidth Width of source frame.
   * @param srcHeight Height of source frame.
   * @param dstWidth Width of destination frame.
   * @param dstHeight Height of destination frame.
   * @param applyRotation Amount of rotation to apply from one frame to another. Must be a multiple
   *     of 90.
   * @param maintainAspectRatio If true, will ensure that scaling in x and y remains constant,
   *     cropping the image if necessary.
   * @return The transformation fulfilling the desired requirements.
   */
  public static Matrix getTransformationMatrix(
      final int srcWidth,
      final int srcHeight,
      final int dstWidth,
      final int dstHeight,
      final int applyRotation,
      final boolean maintainAspectRatio) {
    final Matrix matrix = new Matrix();

    if (applyRotation != 0) {
      if (applyRotation % 90 != 0) {
        LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
      }

      // Translate so center of image is at origin.
      matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

      // Rotate around origin.
      matrix.postRotate(applyRotation);
    }

    // Account for the already applied rotation, if any, and then determine how
    // much scaling is needed for each axis.
    final boolean transpose = (Math.abs(applyRotation) + 90) % 180 == 0;

    final int inWidth = transpose ? srcHeight : srcWidth;
    final int inHeight = transpose ? srcWidth : srcHeight;

    // Apply scaling if necessary.
    if (inWidth != dstWidth || inHeight != dstHeight) {
      final float scaleFactorX = dstWidth / (float) inWidth;
      final float scaleFactorY = dstHeight / (float) inHeight;

      if (maintainAspectRatio) {
        // Scale by minimum factor so that dst is filled completely while
        // maintaining the aspect ratio. Some image may fall off the edge.
        final float scaleFactor = Math.max(scaleFactorX, scaleFactorY);
        matrix.postScale(scaleFactor, scaleFactor);
      } else {
        // Scale exactly to fill dst from src.
        matrix.postScale(scaleFactorX, scaleFactorY);
      }
    }

    if (applyRotation != 0) {
      // Translate back from origin centered reference to destination frame.
      matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
    }

    return matrix;
  }
}

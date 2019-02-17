package org.alie.bitmapcachemyselfwork;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Created by Alie on 2019/2/17.
 * 类描述  图片缩放类
 * 版本
 */
public class ImageResize {

    public ImageResize() {
    }

    /**
     * 缩放bitmpa
     *
     * @param context
     * @param id
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    public Bitmap resizeBitmap(Context context, int id, int maxWidth, int maxHeight,Bitmap inBitmap) {
        Resources resources = context.getResources();
        BitmapFactory.Options options = new BitmapFactory.Options();
        // 作用，获取图片属性，设置此方法，仅仅解码出outxxx的参数，而不直接返回bitmap对象
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(resources, id, options);
        int outWidth = options.outWidth;
        int outHeight = options.outHeight;

        options.inSampleSize = calculateInSimpleSize(outWidth, outHeight, maxWidth, maxHeight);
        Log.i(MainActivity.TAG,"图片的inSampleSize："+options.inSampleSize);
        // 设置编码格式，采用 rgb565
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inJustDecodeBounds = false;

        /**
         * 此处设置为inMuteble = true可异变的时，才可使用inBitmap复用机制
         */
        options.inMutable = true;
        options.inBitmap = inBitmap;
        return BitmapFactory.decodeResource(resources, id, options);
    }

    private int calculateInSimpleSize(int outWidth, int outHeight, int maxWidth, int maxHeight) {
        int simpleSize = 1;

        if (outWidth > maxWidth && outHeight > maxHeight) {
            simpleSize += 1;
            while (outWidth / simpleSize > maxWidth && outHeight / simpleSize > maxHeight) {
                simpleSize += 1;
//                simpleSize *= 2;
            }
        }
        return simpleSize;
    }


    /**
     * 个人认为 本计算方案才是最优压缩方案
     * @param outWidth
     * @param outHeight
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    private int calculateInSimpleSize2(int outWidth, int outHeight, int maxWidth, int maxHeight) {
        int simpleSize = 1;

        if (outWidth > maxWidth && outHeight > maxHeight) {

            int i = outWidth / maxWidth;
            int i1 = outHeight / maxHeight;

            simpleSize = Math.min(i,i1);
            Log.i(MainActivity.TAG,"宽缩放："+i+" 高缩放："+i1+" 最小值："+simpleSize);

        }


        return simpleSize;
    }


}

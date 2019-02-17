package org.alie.bitmapcachemyselfwork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import java.util.List;

/**
 * Created by Alie on 2019/1/20.
 * 类描述
 * 版本
 */
public class MyAdapter extends BaseAdapter {

    private Context context;
    private ImageResize imageResize;

    public MyAdapter(Context context) {
        this.context = context;
        this.imageResize = new ImageResize();
    }

    @Override
    public int getCount() {
        return 100;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        if (null == convertView) {
            convertView = LayoutInflater.from(this.context).inflate(R.layout.item, null);
            viewHolder = new ViewHolder(convertView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();

        }

        Bitmap bitmap;

//        // 未优化：
//       bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.lance);

//        // 第一次优化：
//        bitmap = imageResize.resizeBitmap(this.context, R.drawable.lance, 80, 80);

        // 第二次优化：
        /**
         * 1.先从内存缓存中获取bitmap
         * 2.如果内存缓存中没有，则再从复用翅中获取允许被复用的图片，使用复用的bitmap去加载获取图片磁盘缓存的图片
         * 3.如果磁盘缓存中没有，则使用复用池中允许被复用的图片去加载获取图片
         */
        //1.先从内存中获取bitmap
        bitmap = ImageCache.getInstance().getBitmapFromMemoryCache(String.valueOf(position));
        Log.i("adapter","从内存缓存中获取："+bitmap);
        if (null == bitmap) {

            // 磁盘缓存中没有，再从复用池中获取允许复用的图片，并使复用的bitmap去加载新的bitmap
            Bitmap reuseableFromInBitmapSet = ImageCache.getInstance().getReuseableFromInBitmapSet(80, 80, 1);
            // 从磁盘缓存中获取数据
            bitmap = ImageCache.getInstance().getBitmapFromDisk(String.valueOf(position),reuseableFromInBitmapSet);
            Log.i("adapter","从磁盘缓存中获取："+bitmap);
            if (null == bitmap) {

                Log.i("adapter","复用内存："+reuseableFromInBitmapSet);
                bitmap = imageResize.resizeBitmap(this.context, R.drawable.lance, 80, 80, reuseableFromInBitmapSet);
                ImageCache.getInstance().putBitmapToMemoryCache(String.valueOf(position),bitmap);
                ImageCache.getInstance().putBitmapToDiskCache(String.valueOf(position),bitmap);
            }
        }
        viewHolder.iv.setImageBitmap(bitmap);
        return convertView;
    }


    class ViewHolder {

        ImageView iv;

        public ViewHolder(View view) {
            iv = view.findViewById(R.id.iv);
        }
    }


    /**
     * 将px值转换为dip或dp值，保证尺寸大小不变
     *
     * @param context
     * @param pxValue
     * @return
     */
    private int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (pxValue / scale + 0.5f);
    }
}

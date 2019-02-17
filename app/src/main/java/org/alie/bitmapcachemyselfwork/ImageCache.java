package org.alie.bitmapcachemyselfwork;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.LruCache;

import org.alie.bitmapcachemyselfwork.disk.DiskLruCache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 类描述 负责管理 图片内存缓存+InBitmap缓存池+磁盘缓存
 * 版本
 */
public class ImageCache {
    private static ImageCache mInstance;
    private LruCache<String, Bitmap> memoryCache;
    private DiskLruCache diskLruCache;

    /**
     * 使用一个Bitmap复用池来进行InBitmap复用优化
     */
    private Set<WeakReference<Bitmap>> reUseInBitmapPool;


    /**
     * 弱引用队列，进入到这个队列中的数据都是要被释放掉的
     */
    private ReferenceQueue<Bitmap> referenceQueue;

    /**
     * 使用一个线程来移除弱引用队列中的数据
     */
    private Thread clearReferenceQueueThread;

    private boolean shutDown;

    private ReferenceQueue<Bitmap> getReferenceQueue() {
        if (null == referenceQueue) {
            // 当弱引用需要被回收的时候，会放入此队列
            referenceQueue = new ReferenceQueue<>();
            clearReferenceQueueThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!shutDown) {
                        try {
                            /**
                             * 这是一个阻塞的方法
                             */
                            Reference<Bitmap> bitmapReference = (Reference<Bitmap>) referenceQueue.remove();
                            Bitmap bitmap = bitmapReference.get();
                            if (null != bitmap && !bitmap.isRecycled()) {
                                bitmap.recycle();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            clearReferenceQueueThread.start();
        }
        return referenceQueue;
    }


    public static ImageCache getInstance() {
        if (null == mInstance) {
            synchronized (ImageCache.class) {
                if (null == mInstance) {
                    mInstance = new ImageCache();
                }
            }
        }
        return mInstance;
    }


    public void init(String dir) {

        /**
         * InBitmap复用池,这里使用一个线程安全的类来实例化这个复用池
         */
        reUseInBitmapPool = Collections.synchronizedSet(new HashSet<WeakReference<Bitmap>>());
        Context applicationContext = App.getInstance().getApplicationContext();
        ActivityManager am = (ActivityManager) applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass(); // 单位：M
        // 此处将单位转化成字节的，因为后面在使用图片的时候 也是使用字节的
        memoryCache = new LruCache<String, Bitmap>(memoryClass / 8 * 1024 * 1024) {
            /**
             *
             * @param key
             * @param value
             * @return 返回单个value信息（其中value.getBytecount获取次这个）
             */
            @Override
            protected int sizeOf(String key, Bitmap value) {
                int byteCount;
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    byteCount = value.getAllocationByteCount();
                } else {
                    byteCount = value.getByteCount();
                }
                return byteCount;
            }


            /**
             * 当元素从Lrucache中移除的时候，回调此方法
             * @param evicted
             * @param key
             * @param oldValue
             * @param newValue
             */
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                super.entryRemoved(evicted, key, oldValue, newValue);
                if (oldValue.isMutable()) {
                    //
                    /**
                     * 这里涉及到一个 bitmap、弱引用回收的问题,这里我们使用弱引用队列
                     * 使用弱引用队列后，此队列可帮助我们判断这个weakpreferce是不是要被回收了，
                     * 如果此引用队列中含有弱引用对象，这说明，这个弱引用对象要被回收了
                     * 通过这个判断，间接来进行图片的回收
                     */
                    // 3.0以下，bitmap内存数据在native方法中
                    // 3.0以及3.0以上 bitmap内存数据在java堆中
                    // 8.0以后，bitmap内存数据在native方法中
                    reUseInBitmapPool.add(new WeakReference<>(oldValue, getReferenceQueue()));
                } else {
                    oldValue.recycle();
                }
            }
        };


        /**
         * 磁盘缓存
         * valueCount 表示一个key对应valueCount个文件，一般的我们都出传1
         */
        try {
            diskLruCache = DiskLruCache.open(new File(dir), BuildConfig.VERSION_CODE, 1, 10 * 1024 * 1024);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 可被复用的Bitmap必须设置inMutable为true；
     * Android4.4(API 19)之前只有格式为jpg、png，同等宽高（要求苛刻），inSampleSize为1的Bitmap才可以复用；
     * Android4.4(API 19)之前被复用的Bitmap的inPreferredConfig会覆盖待分配内存的Bitmap设置的inPreferredConfig；
     * Android4.4(API 19)之后被复用的Bitmap的内存必须大于等于需要申请内存的Bitmap的内存；
     * 根据以上限制来从复用池中获取图片
     *
     * @param width
     * @param height
     * @param simpleSize
     * @return
     */
    public Bitmap getReuseableFromInBitmapSet(int width, int height, int simpleSize) {
        Bitmap bitmapTarget = null;
        // 3.0之前无InBitmap特性，直接返回null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            return bitmapTarget;
        }

        Iterator<WeakReference<Bitmap>> iterator = reUseInBitmapPool.iterator();
        while (iterator.hasNext()) {
            Bitmap bitmap = iterator.next().get();
            if (null != bitmap) {
                if (checkInBitmap(bitmap, width, height, simpleSize)) {
                    bitmapTarget = bitmap;
                    // 被拿去复用后，就应该从复用池中移除
                    iterator.remove();
                }
            } else {
                iterator.remove();
            }
        }
        return bitmapTarget;
    }

    private boolean checkInBitmap(Bitmap bitmap, int width, int height, int inSimpleSize) {

        /**
         * 4.4之前
         */
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return bitmap.getWidth() == bitmap.getHeight() && inSimpleSize == 1;
        }

        /**
         * 4.4之后
         */
        // 若果缩放系数大于1，则获取缩放后的宽高，这里的入参是图片原来的宽高
        if (inSimpleSize > 1) {
            width /= inSimpleSize;
            height /= inSimpleSize;
        }

        // 获取bitmap的内存大小
        int byteCount = width * height * getBitmapConfigTypeCount(bitmap.getConfig());
        return byteCount < bitmap.getAllocationByteCount();

    }

    private int getBitmapConfigTypeCount(Bitmap.Config config) {
        if (config == Bitmap.Config.ARGB_8888) {
            return 4;
        }
        return 2;
    }

    /**
     * 内存缓存
     *
     * @param key
     * @param bitmap
     */
    public void putBitmapToMemoryCache(String key, Bitmap bitmap) {
        memoryCache.put(key, bitmap);
    }

    /**
     * 磁盘缓存
     *
     * @param key
     * @param bitmap
     */
    public void putBitmapToDiskCache(String key, Bitmap bitmap) {
        DiskLruCache.Snapshot snapshot = null;
        OutputStream os = null;
        try {
            snapshot = diskLruCache.get(key);
            // 如果缓存有，对应key的文件
            if (null != snapshot) {
                DiskLruCache.Editor edit = diskLruCache.edit(key);
                if (null != edit) {
                    os = edit.newOutputStream(0);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, os);
                    edit.commit();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != snapshot) {
                snapshot.close();
            }
            if (null != os) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Bitmap getBitmapFromMemoryCache(String key) {
        return memoryCache.get(key);
    }

    /**
     * 从磁盘缓存中获取对应的key图片
     *
     * @param key
     * @return
     */
    public Bitmap getBitmapFromDisk(String key ,Bitmap inBitmap) {
        DiskLruCache.Snapshot snapshot = null;
        Bitmap bitmap = null;
        try {
            snapshot = diskLruCache.get(key);
            if(null == snapshot) {
                return bitmap;
            }
            // 获得文件输入流 读取bitmap
            InputStream is = snapshot.getInputStream(0);

            // 为了从磁盘中加载出来的图片能够被复用，为它设置下inMutable
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = true;
            options.inBitmap = inBitmap;
            bitmap = BitmapFactory.decodeStream(is,null,options);
            if(null != bitmap){
                memoryCache.put(key,bitmap);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(null != snapshot) {
                snapshot.close();
            }
        }
        return bitmap;
    }

    public void clearMemory() {
        memoryCache.evictAll();
    }

}

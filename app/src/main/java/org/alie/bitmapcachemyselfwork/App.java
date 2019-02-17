package org.alie.bitmapcachemyselfwork;

import android.app.Application;
import android.os.Environment;

/**
 * Created by Alie on 2019/1/20.
 * 类描述
 * 版本
 */
public class App extends Application {


    private static App mInstance;

    public static App getInstance() {
        if (null == mInstance) {
            synchronized (App.class) {
                if (null == mInstance) {
                    mInstance = new App();
                }
            }
        }
        return mInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        ImageCache.getInstance().init(Environment.getExternalStorageDirectory()+"/aliecache");
    }
}

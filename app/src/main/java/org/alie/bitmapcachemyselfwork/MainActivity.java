package org.alie.bitmapcachemyselfwork;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ListView;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    private ListView lv;
    private MyAdapter myAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lv = findViewById(R.id.lv);
        myAdapter = new MyAdapter(this);
        lv.setAdapter(myAdapter);

        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.lance);
        i(bitmap);
    }

    void i (Bitmap bitmap) {

        Log.i(TAG,"图片的宽："+bitmap.getWidth()+" 图片的高度："+bitmap.getHeight()+
        "内存大小："+bitmap.getByteCount());
    }

}

package com.thea.uploadfrombreak;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String BASE_PATH = Environment.getExternalStorageDirectory() + "/test";
    private static final String KEY_BEGIN = "begin";
    private static final String KEY_END = "end";
    private static final String KEY_FINISHED = "finished";

    private static final int MSG_INIT_PROGRESS = 7;
    private static final int MSG_UPDATE_PROGRESS = 9;
    private static final int MSG_FILE_NOT_EXIST = 13;
    private static final int MSG_WRONG_URL = 14;

    private EditText mEtUrl;
    private Button mBtnDownload;
    private ContentLoadingProgressBar mProgressBar;

    private URL mUrl;
    private File mFile;
    private boolean mDownloading = false;
    private int mTotal = 0;
    private int mFileLength;
    private List<HashMap<String, Integer>> mDownloadList = new ArrayList<>();

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INIT_PROGRESS:
                    mProgressBar.setMax(mFileLength);
                    mProgressBar.setProgress(mTotal);
                    break;
                case MSG_UPDATE_PROGRESS:
                    mProgressBar.setProgress(msg.arg1);
                    if (msg.arg1 == mFileLength)
                        Toast.makeText(MainActivity.this, "下载完成", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_FILE_NOT_EXIST:
                    Toast.makeText(MainActivity.this, "文件不存在！", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_WRONG_URL:
                    Toast.makeText(MainActivity.this, "URL 不正确！", Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private Runnable mStartRunnable = () -> {
        try {
            mUrl = new URL(mEtUrl.getText().toString());
            HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; " +
                    "Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727");

            mFileLength = conn.getContentLength();
            Log.i(TAG, "File length: " + mFileLength);
            mHandler.sendEmptyMessage(MSG_INIT_PROGRESS);
            if (mFileLength < 0)
                mHandler.sendEmptyMessage(MSG_FILE_NOT_EXIST);
            else {
                mFile = createNewFile(getFilename(mEtUrl.getText().toString()));
                RandomAccessFile randomAccessFile = new RandomAccessFile(mFile, "rw");
                randomAccessFile.setLength(mFileLength);

                int blockSize = mFileLength / 3;
                for (int i = 0; i < 3; i++) {
                    int begin = i * blockSize;
                    int end = i == 2 ? mFileLength : ((i + 1) * blockSize - 1);

                    HashMap<String, Integer> map = new HashMap<>();
                    map.put(KEY_BEGIN, begin);
                    map.put(KEY_END, end);
                    map.put(KEY_FINISHED, 0);
                    mDownloadList.add(map);

                    new Thread(new DownloadRunnable(i, begin, end, mFile, mUrl)).start();
                }
            }
        } catch (MalformedURLException e) {
            mHandler.sendEmptyMessage(MSG_WRONG_URL);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    private View.OnClickListener mClickListener = v -> {
        if (mDownloading) {
            mDownloading = false;
            mBtnDownload.setText(R.string.action_download);
        }
        else {
            mDownloading = true;
            mBtnDownload.setText(R.string.action_pause);
            if (mDownloadList.isEmpty())
                new Thread(mStartRunnable).start();
            else {
                //恢复下载
                for (int i = 0; i < mDownloadList.size(); i++) {
                    HashMap<String, Integer> map = mDownloadList.get(i);
                    int begin = map.get(KEY_BEGIN);
                    int end = map.get(KEY_END);
                    int finished = map.get(KEY_FINISHED);
                    new Thread(new DownloadRunnable(i, begin + finished, end, mFile, mUrl)).start();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mEtUrl = (EditText) findViewById(R.id.et_url);
        mBtnDownload = (Button) findViewById(R.id.btn_download);
        mProgressBar = (ContentLoadingProgressBar) findViewById(R.id.clpb_loading);

        mBtnDownload.setOnClickListener(mClickListener);
    }

    private File createNewFile(String name) {
        File dir = new File(BASE_PATH);
        if (dir.exists() && !dir.isDirectory())
            dir.delete();
        if (!dir.exists())
            dir.mkdir();
        File file = new File(BASE_PATH + "/" + name);
        if (file.exists())
            file.delete();

        return file;
    }

    private String getFilename(String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    private synchronized void updateProgress(int add) {
        mTotal += add;
        mHandler.obtainMessage(MSG_UPDATE_PROGRESS, mTotal, add).sendToTarget();
    }

    public class DownloadRunnable implements Runnable {
        private int id;
        private int begin;
        private int end;
        private File file;
        private URL url;

        public DownloadRunnable(int id, int begin, int end, File file, URL url) {
            this.id = id;
            this.begin = begin;
            this.end = end;
            this.file = file;
            this.url = url;
        }

        @Override
        public void run() {
            if (begin < end) {
                try {
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/4.0 (compatible; MSIE 8.0; " +
                            "Windows NT 5.1; Trident/4.0; .NET CLR 2.0.50727");
                    conn.setRequestProperty("Range", "bytes=" + begin + "-" + end);

                    InputStream is = conn.getInputStream();
                    byte[] buf = new byte[1024 * 1024];
                    RandomAccessFile randomFile = new RandomAccessFile(file, "rw");

                    int len;
                    HashMap<String, Integer> map = mDownloadList.get(id);
                    while ((len = is.read(buf)) != -1 && mDownloading) {
                        randomFile.write(buf, 0, len);
                        updateProgress(len);
                        map.put(KEY_FINISHED, map.get(KEY_FINISHED) + len);
                        Log.i(TAG, "Download: " + mTotal);
                    }

                    is.close();
                    randomFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}

package com.example.yhj.mobilesafe;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.yhj.mobilesafe.activity.HomeActivity;
import com.example.yhj.mobilesafe.utils.StreamUtils;
import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static android.R.attr.x;
import static android.os.Build.VERSION_CODES.O;
import static com.example.yhj.mobilesafe.utils.StreamUtils.readFromStream;
import static java.lang.System.currentTimeMillis;

/*
* 主页面(版本更新)
* */

public class SplashActivity extends AppCompatActivity {

    private static final int CODE_UPDATE_DIALOG =0 ;
    private static final int CODE_URL_ERROR =1 ;
    private static final int CODE_NET_ERROR =2 ;
    private static final int CODE_JSON_ERROR =3 ;
    private static final int CODE_ENTER_HOME =4 ;

    private TextView tvVersion;
    private TextView tvProgress;//下载进度

    //服务器返回的信息
    private  String mVersionName;//版本名
    private String mDesc;//版本描述
    private int mVersionCode;//版本号
    private String mDownloadUrl;//下载链接

    private Handler mHandler=new Handler(){
        @Override
        public void handleMessage(Message msg) {
           switch (msg.what){
               case CODE_UPDATE_DIALOG:
                   showUpdateDialog();
                   break;
               case CODE_URL_ERROR:
                   Toast.makeText(SplashActivity.this,"URL错误",Toast.LENGTH_SHORT).show();
                   break;
               case CODE_NET_ERROR:
                   Toast.makeText(SplashActivity.this,"网络错误",Toast.LENGTH_SHORT).show();
                   break;
               case CODE_JSON_ERROR:
                   Toast.makeText(SplashActivity.this,"数据解析错误",Toast.LENGTH_SHORT).show();
                   break;
               case CODE_ENTER_HOME:
                   enterHome();
           }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        tvVersion = (TextView) findViewById(R.id.tv_version);
        tvProgress= (TextView) findViewById(R.id.tv_progress );
        tvVersion.setText("版本号:"+getVersionName());
        checkVersion();
    }

    /*
    * 获取版本名以便输出到闪屏页
    * */
    private String getVersionName() {
        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);//获取包信息
            String versionName = packageInfo.versionName;
            return versionName;
        } catch (PackageManager.NameNotFoundException e) {//没有包名会走此异常
            e.printStackTrace();
        }
        return "";
    }

    /*
   * 获取本地版本号
   * */
    private int getVersionCode() {
        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);//获取包信息
            int versionCode = packageInfo.versionCode;

            return versionCode;
        } catch (PackageManager.NameNotFoundException e) {//没有包名会走此异常
            e.printStackTrace();
        }
        return -1;
    }

    /*
    * 从服务器获取版本信息进行校验
    * */
    private void checkVersion(){
        final long startTimes=System.currentTimeMillis();
        new Thread(){//启动子线程异步加载数据
            @Override
            public void run() {

                Message msg=Message.obtain();//拿到一个消息
                HttpURLConnection conn=null;
                try {
                    URL url=new URL("http://172.28.36.55:8080/update.json");
                     conn= (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");//设置请求方法
                    conn.setConnectTimeout(5000);//设置连接超时
                    conn.setReadTimeout(5000);//设置响应超时
                    conn.connect();//连接服务器
                    int responseCode=conn.getResponseCode();
                    if(responseCode==200){
                        InputStream inputStream=conn.getInputStream();
                        String result=StreamUtils.readFromStream(inputStream);

                        //解析json
                        JSONObject jo=new JSONObject(result);
                         mVersionName=jo.getString("versionName");
                         mDesc=jo.getString("description");
                         mVersionCode=jo.getInt("versionCode");
                         mDownloadUrl=jo.getString("downloadUrl");

                        if(mVersionCode>getVersionCode()){//服务器版本号大于本地版本号，可更新。弹出升级对话框
                            msg.what=CODE_UPDATE_DIALOG;
                        }else{
                            //已是最新版本
                           msg.what=CODE_ENTER_HOME;
                        }
                    }
                } catch (MalformedURLException e) {//url错误异常
                    msg.what=CODE_URL_ERROR;
                    enterHome();
                    e.printStackTrace();
                }catch (IOException e) {//网络错误异常
                    msg.what=CODE_NET_ERROR;
                    enterHome();
                    e.printStackTrace();
                } catch (JSONException e) {
                    msg.what=CODE_JSON_ERROR;
                    enterHome();
                    e.printStackTrace();
                }finally {
                    long endTimes=System.currentTimeMillis();
                    long usedTimes=endTimes-startTimes;//访问网络花费的时间
                    if(usedTimes<2000){
                        try {//由于网速过快，让其休眠一段时间，保证显示闪屏页
                            Thread.sleep(2000-usedTimes);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    mHandler.sendMessage(msg);
                    if(conn!=null){
                        conn.disconnect();//关闭网络连接
                    }
                }
            }


        }.start();
    }

    /*
    * 升级对话框
    * */
    private void showUpdateDialog(){
        AlertDialog.Builder builder=new AlertDialog.Builder(this);
        builder.setTitle("最新版本"+mVersionName);
        builder.setMessage(mDesc);
        builder.setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //Toast.makeText(SplashActivity.this,"升级成功",Toast.LENGTH_SHORT).show();
                download();
            }
        });

        builder.setNegativeButton("下次再说", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                enterHome();
            }
        });
        builder.show();//显示对话框
    }

    /*
    * 进入主页面
    * */

    public void enterHome(){
        Intent intent=new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();//直接退出
    }

    /*
    * 下载新版apk
    * */
    public void download(){
       if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
           tvProgress.setVisibility(View.VISIBLE);//显示下载进度
           String target=Environment.getExternalStorageDirectory()+"/update.apk";
           //xUtils
           HttpUtils utils=new HttpUtils();
           utils.download(mDownloadUrl, target, new RequestCallBack<File>() {
               @Override//下载成功
               public void onSuccess(ResponseInfo<File> responseInfo) {
                   Toast.makeText(SplashActivity.this,"下载成功",Toast.LENGTH_SHORT).show();
               }

               @Override//下载失败
               public void onFailure(HttpException e, String s) {
                   System.out.println(mDownloadUrl);
                   Toast.makeText(SplashActivity.this,"下载失败",Toast.LENGTH_SHORT).show();
                   enterHome();
               }

               @Override//下载进度
               public void onLoading(long total, long current, boolean isUploading) {
                   System.out.println(current*100/total);
                   tvProgress.setText("下载进度"+current*100/total+"%");
               }
           });
       }else{
           Toast.makeText(SplashActivity.this,"抱歉，没有内存卡",Toast.LENGTH_SHORT).show();
       }
    }

}

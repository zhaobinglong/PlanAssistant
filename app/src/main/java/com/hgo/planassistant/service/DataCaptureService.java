package com.hgo.planassistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.avos.avoscloud.AVGeoPoint;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.AVUser;
import com.hgo.planassistant.R;
import com.hgo.planassistant.activity.MainActivity;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static androidx.constraintlayout.widget.Constraints.TAG;

public class DataCaptureService extends Service {

    private SharedPreferences SP_setting;
    private TencentLocationManager tLocationManager = null;
    private TencentLocationListener tLocationListener = null;
    //声明AMapLocationClient类对象
    private AMapLocationClient aLocationClient = null;
    //声明AMap定位回调监听器
    private AMapLocationListener aLocationListener = null;
    //声明AMapLocationClientOption对象
    public AMapLocationClientOption aLocationOption = null;

    // 步测器传感器
//    private SensorManager StepDetectorSensorManager;
//    private Sensor StepDetectorSensor;
//    private SensorEventListener StepDetectorSensorListener;

    // 计步器传感器
    private SensorManager StepCounterSensorManager;
    private Sensor StepCounterSensor;
    private SensorEventListener StepCounterSensorListener;

    private Boolean LocationServiceStart = null; // 位置服务运行标记
    private int CurrentStep = 0; // 标记当前步数
    private int AddStep = 0; // 增加的步数

    private StorageTimeCount storagetimeCount; // 存储计时器
    private NoSportTimeCount noSportTimeCount = null; // 无运动计时器
    // 5*60 秒进行一次存储
    private static int saveDuration = 300000;
    // 5*60 秒无运动则关闭位置记录
    private static int noStopDuration = 300000;

    public DataCaptureService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    //创建服务时调用
    @Override
    public void onCreate() {
        super.onCreate();
        SP_setting = this.getSharedPreferences("setting",MODE_MULTI_PROCESS);
//        Log.d(TAG, "onCreate");
    }

    //服务执行的操作
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.d(TAG, "onStartCommand");

        // android 8.0后需要给notification一个channel one id
        String CHANNEL_ONE_ID = "com.primedu.cn";
        String CHANNEL_ONE_NAME = "Channel One";
        NotificationChannel notificationChannel = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationChannel = new NotificationChannel(CHANNEL_ONE_ID,
                    CHANNEL_ONE_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setShowBadge(true);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(notificationChannel);
        }

        // 在API11之后构建Notification的方式
        Notification.Builder builder = new Notification
                .Builder(this.getApplicationContext()) //获取一个Notification构造器
                .setChannelId(CHANNEL_ONE_ID);
        Intent nfIntent = new Intent(this, MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher_round)) // 设置下拉列表中的图标(大图标)
                .setContentTitle("规划助手") // 设置下拉列表里的标题
                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
                .setContentText("位置服务正在运行！") // 设置上下文内容
                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
        Notification notification = builder.build(); // 获取构建好的Notification

        if(SP_setting.getBoolean("pref_location_background_switch",false)){
            Log.i("DataCaptureService","后台服务开关为开，启动后台位置获取");
            startForeground(110, notification);// 开始前台服务
            initLocationCapture();
        }

        initStepDetector();
        return super.onStartCommand(intent, flags, startId);
    }

    //销毁服务时调用
    @Override
    public void onDestroy() {
        super.onDestroy();
//        Log.d(TAG, "onDestroy");


        DestroyLocationCapture();
        stopForeground(true);// 停止前台服务--参数：表示是否移除之前的通知
    }

    private void initLocationCapture(){
        LocationServiceStart = true; //标记为已开启
//        SP_setting = this.getSharedPreferences("setting",MODE_MULTI_PROCESS);
        if(SP_setting.getString("settings_location_server","Amap").equals("Amap")){
            Log.i("DataCaptureService","启动高德位置服务。");
            initAMapLocation();
        }else{
            Log.i("DataCaptureService","启动腾讯位置服务。");
            initTencentLocation();
        }
    }
    private void DestroyLocationCapture(){
        LocationServiceStart = false; //标记为false
        destroyAMapLocation();
        destroyTencentLocation();
    }

    private void initTencentLocation(){
        tLocationManager = TencentLocationManager.getInstance(this);
        /* 保证调整坐标系前已停止定位 */
        tLocationManager.removeUpdates(null);
        // 设置 wgs84 坐标系
        tLocationManager
                .setCoordinateType(TencentLocationManager.COORDINATE_TYPE_WGS84);

        // 创建定位请求
        TencentLocationRequest request = TencentLocationRequest.create();
        // 修改定位请求参数, 定位周期 3000 ms
//        request.setInterval(3000);

        //读取设置信息初始化定位设置
        SP_setting = this.getSharedPreferences("setting",MODE_MULTI_PROCESS);
//        interval =  SP_setting.getInt("pref_list_location_time",4000);
        request.setAllowCache(true); //允许缓存
        request.setAllowGPS(SP_setting.getBoolean("pref_location_tencent_usegps",false));
        request.setIndoorLocationMode(SP_setting.getBoolean("pref_location_tencent_indoor",false));
        request.setInterval(Integer.parseInt(SP_setting.getString("pref_list_location_time","4000")));

        tLocationListener = new TencentLocationListener() {
            @Override
            public void onLocationChanged(TencentLocation tencentLocation, int i, String s) {
                Log.i("DataCaptureService","腾讯位置服务，位置改变回调！");
                String msg = null;
                if (TencentLocation.ERROR_OK == i) {
                    // 定位成功
                    Calendar now = Calendar.getInstance();
                    String now_str;
                    now_str = now.get(Calendar.YEAR)+"-";
                    if((now.get(Calendar.MONTH)+1)<10){
                        now_str+="0"+(now.get(Calendar.MONTH)+1)+"-";
                    }else{
                        now_str +=(now.get(Calendar.MONTH)+1)+"-";
                    }
                    if(now.get(Calendar.DATE)<10){
                        now_str+="0"+now.get(Calendar.DATE)+"";
                    }else{
                        now_str+=now.get(Calendar.DATE)+"";
                    }//格式化格式，保证2位
                    // 定位成功
                    StringBuilder sb = new StringBuilder();
                    sb.append("(纬度=").append(tencentLocation.getLatitude()).append(",经度=")
                            .append(tencentLocation.getLongitude()).append(",精度=")
                            .append(tencentLocation.getAccuracy()).append("), 来源=")
                            .append(tencentLocation.getProvider()).append(", 地址=")
                            // 注意, 根据国家相关法规, wgs84坐标下无法提供地址信息
                            .append("{84坐标下不提供地址!}");
                    msg = sb.toString();

                    // 构造方法传入的参数，对应的就是控制台中的 Class Name
                    AVGeoPoint point = new AVGeoPoint(tencentLocation.getLatitude(), tencentLocation.getLongitude()); //获取经纬度
                    AVObject track_record = new AVObject("trajectory");
                    track_record.put("UserId", AVUser.getCurrentUser().getObjectId());// 设置用户ID
                    track_record.put("time",new Date()); //设置时间戳
                    track_record.put("geo_coordinate","WGS-84");//坐标系
                    track_record.put("point",point); //设置坐标
                    track_record.put("altitude",tencentLocation.getAltitude()); //设置高程
                    track_record.put("type",tencentLocation.getProvider()); //设置类型
                    track_record.put("precision",tencentLocation.getAccuracy()); //精度
//            track_record.put("interval",SP_setting.getString("pref_list_location_time","4000")); //定位时间间隔
//            track_record.put("interval", 4000); //定位时间间隔
                    track_record.put("createDate",now_str); //文本日期
//            track_record.saveInBackground();// 保存到服务端
                    track_record.saveEventually();// 离线保存
                } else {
                    // 定位失败
                    msg = "定位失败: " + s;
                }
                Log.i("DataCaptureService",msg);
            }

            @Override
            public void onStatusUpdate(String s, int i, String s1) {

            }
        };

        // 开始定位
        int error = tLocationManager.requestLocationUpdates(request, tLocationListener);

        Log.i("DataCaptureService","注册位置监听服务器状态码:" + error);
    }

    private void destroyTencentLocation(){
        if(tLocationManager!=null)
            tLocationManager.removeUpdates(tLocationListener);
    }

    private void initAMapLocation(){
        if(aLocationClient!=null){
            aLocationListener = new AMapLocationListener() {
                @Override
                public void onLocationChanged(AMapLocation aMapLocation) {
                    if (aMapLocation != null) {
                        if (aMapLocation.getErrorCode() == 0) {
                            // 构造方法传入的参数，对应的就是控制台中的 Class Name
                            AVGeoPoint point = new AVGeoPoint(aMapLocation.getLatitude(), aMapLocation.getLongitude()); //获取经纬度
                            AVObject track_record = new AVObject("trajectory");
                            track_record.put("UserId", AVUser.getCurrentUser().getObjectId());// 设置用户ID
                            track_record.put("time",new Date()); //设置时间戳
                            track_record.put("geo_coordinate","GCJ02");//坐标系
                            track_record.put("point",point); //设置坐标
                            track_record.put("altitude",aMapLocation.getAltitude()); //设置高程
                            if(aMapLocation.getLocationType()==1){
                                track_record.put("type","GPS定位结果"); //设置类型
                            }else if(aMapLocation.getLocationType()==2){
                                track_record.put("type","前次定位结果"); //设置类型
                            }else if(aMapLocation.getLocationType()==4){
                                track_record.put("type","缓存定位结果"); //设置类型
                            }else if(aMapLocation.getLocationType()==5){
                                track_record.put("type","Wifi定位结果"); //设置类型
                            }else if(aMapLocation.getLocationType()==6){
                                track_record.put("type","基站定位结果"); //设置类型
                            }else if(aMapLocation.getLocationType()==8){
                                track_record.put("type","离线定位结果"); //设置类型
                            }else if(aMapLocation.getLocationType()==9){
                                track_record.put("type","最后位置缓存"); //设置类型
                            }else{
                                track_record.put("type","定位失败"); //设置类型
                            }
                            track_record.put("precision",aMapLocation.getAccuracy()); //精度
                            track_record.put("speed",aMapLocation.getSpeed());//获取速度信息
                            track_record.put("bearing",aMapLocation.getBearing());////获取方向角信息
                            track_record.put("address",aMapLocation.getAddress());//地址，如果option中设置isNeedAddress为false，则没有此结果，网络定位结果中会有地址信息，GPS定位不返回地址信息。
                            track_record.put("country",aMapLocation.getCountry());//国家信息
                            track_record.put("province",aMapLocation.getProvince());//省信息
                            track_record.put("city",aMapLocation.getCity());//城市信息
                            track_record.put("district",aMapLocation.getDistrict());//城区信息
                            track_record.put("street",aMapLocation.getStreet());//街道信息
                            track_record.put("streetnum",aMapLocation.getStreetNum());//街道门牌号信息
                            track_record.put("citycode",aMapLocation.getCityCode());//城市编码
                            track_record.put("adcode",aMapLocation.getAdCode());//地区编码
                            track_record.put("poi",aMapLocation.getPoiName());//获取当前定位点的POI信息
                            track_record.put("aoi",aMapLocation.getAoiName());//获取当前定位点的AOI信息
                            track_record.put("buildingid",aMapLocation.getBuildingId());//获取当前室内定位的建筑物Id
                            track_record.put("floor",aMapLocation.getFloor());//获取当前室内定位的楼层
                            track_record.put("gpsaccuracystatus",aMapLocation.getGpsAccuracyStatus());//获取GPS的当前状态
                            // 保存到服务端
                            track_record.saveInBackground();
                            track_record.saveInBackground();
//                        track_record.saveEventually();// 离线保存
                            //获取定位时间
//                        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
//                        Date date = new Date(aMapLocation.getTime());
//                        df.format(date);
//                        aMapLocation.getLocationDetail();//定位信息描述
//                        StringBuilder sb = new StringBuilder();
//                        sb.append("结果来源=").append(aMapLocation.getLocationType())
//                                .append("，纬度=").append(aMapLocation.getLatitude())
//                                .append("，经度=").append(aMapLocation.getLongitude())
//                                .append("，精度=").append(aMapLocation.getAccuracy())
//                                .append("，海拔=").append(aMapLocation.getAltitude())
//                                .append("，速度=").append(aMapLocation.getSpeed())
//                                .append("，方向角=").append(aMapLocation.getBearing())
//                                .append("，地址=").append(aMapLocation.getAddress())
//                                .append("，国家=").append(aMapLocation.getCountry())
//                                .append("，省=").append(aMapLocation.getProvince())
//                                .append("，城市=").append(aMapLocation.getCity())
//                                .append("，城区=").append(aMapLocation.getDistrict())
//                                .append("，街道=").append(aMapLocation.getStreet())
//                                .append("，门牌号=").append(aMapLocation.getStreetNum())
//                                .append("，城市编码=").append(aMapLocation.getCityCode())
//                                .append("，地区编码=").append(aMapLocation.getAdCode())
//                                .append("，POI=").append(aMapLocation.getPoiName())
//                                .append("，AOI=").append(aMapLocation.getAoiName())
//                                .append("，建筑物id=").append(aMapLocation.getBuildingId())
//                                .append("，楼层=").append(aMapLocation.getFloor())
//                                .append("，GPS状态=").append(aMapLocation.getGpsAccuracyStatus());
//                        String msg = sb.toString();
//                        Log.i("DataCaptureService","定位成功，位置描述：" + aMapLocation.getLocationDetail());
//                        Log.i("DataCaptureService","定位成功，位置信息：" + msg);
//                        Log.i("DataCaptureService","定位成功，数据信息：" + aMapLocation.toString());
                            Log.i("DataCaptureService","定位成功,当前位置：" + aMapLocation.getDescription());
                        }else {
                            //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
                            Log.e("AmapError","location Error, ErrCode:"
                                    + aMapLocation.getErrorCode() + ", errInfo:"
                                    + aMapLocation.getErrorInfo());
                        }
                    }
                }
            };
            //初始化定位
            aLocationClient = new AMapLocationClient(getApplicationContext());
            //设置定位回调监听
            aLocationClient.setLocationListener(aLocationListener);
            //初始化AMapLocationClientOption对象
            aLocationOption = new AMapLocationClientOption();

            //读取设置信息初始化定位设置
            SP_setting = this.getSharedPreferences("setting",MODE_MULTI_PROCESS);
            //设置定位模式
            if(SP_setting.getString("pref_list_location_ali_type","Battery_Saving").equals("Device_Sensors")){
                aLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Device_Sensors);
                Log.i("DataCaptureService","设置高德定位模式为仅设备。");
            } else if(SP_setting.getString("pref_list_location_ali_type","Battery_Saving").equals("Hight_Accuracy")){
                aLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                Log.i("DataCaptureService","设置高德定位模式为高精度。");
            }else{
                aLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Battery_Saving);
                Log.i("DataCaptureService","设置高德定位模式为低功耗。");
            }

            //设置定位间隔,单位毫秒,默认为2000ms，最低1000ms。
            aLocationOption.setInterval(Integer.parseInt(SP_setting.getString("pref_list_location_time","4000")));
            //设置是否返回地址信息（默认返回地址信息）
            aLocationOption.setNeedAddress(true);
            //设置是否允许模拟位置,默认为true，允许模拟位置
            aLocationOption.setMockEnable(true);

            //给定位客户端对象设置定位参数
            aLocationClient.setLocationOption(aLocationOption);
            //启动定位
            aLocationClient.startLocation();
        }
    }

    private void destroyAMapLocation(){
        if(aLocationClient!=null){
            aLocationClient.stopLocation();//停止定位后，本地定位服务并不会被销毁
            aLocationClient.onDestroy();//销毁定位客户端，同时销毁本地定位服务。
        }
    }

    private void initStepDetector(){
//        StepDetectorSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
//        StepDetectorSensor = StepDetectorSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR); // 步测器传感器
//
//        StepDetectorSensorListener = new SensorEventListener() {
//            @Override
//            public void onSensorChanged(SensorEvent sensorEvent) {
//                Log.i("DataCaptureService","步测器传感器数据回调，数据："+sensorEvent);
//            }
//
//            @Override
//            public void onAccuracyChanged(Sensor sensor, int i) {
////                Log.i("DataCaptureService","步测器传感器精度变化回调接口"+sensor.toString()+","+i);
//            }
//        };
//
//        StepDetectorSensorManager.registerListener(StepDetectorSensorListener,StepDetectorSensor,SensorManager.SENSOR_DELAY_NORMAL);

        startStorageTimeCount(); //开始数据存储倒计时

        StepCounterSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        StepCounterSensor = StepCounterSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER); // 计步器传感器

        StepCounterSensorListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                Log.i("DataCaptureService","计步器步数改变，启动位置记录，" + "当前计步器步数：" + sensorEvent.values[0]);
                if(SP_setting.getBoolean("pref_location_background_switch",false)){
                    Log.i("DataCaptureService","后台服务开关为开，不重复启动位置记录！");
                }else{
                    initLocationCapture();
                    Log.i("DataCaptureService","后台位置服务获取未启动，检测到用户开始步行，启动位置记录！");
                }
                reStartNoSportTimeCount();
                if(CurrentStep==0){
                    // 当前步数为0，判为第一次打开软件
                    CurrentStep = (int)sensorEvent.values[0];
                    Log.i("DataCaptureService","当前步数为0，判为数据服务刚刚启动，当前步数赋新值："+ CurrentStep);
                } else if(CurrentStep<(int)sensorEvent.values[0]){
                    // 当前步数小于更新步数，判为步数增加
                    if(AddStep!=0){
                        AddStep += (int)sensorEvent.values[0] - CurrentStep;
                        Log.i("DataCaptureService","当前步数非0，当前新增步数："+ AddStep);
                    }else{
                        AddStep = (int)sensorEvent.values[0];
                        Log.i("DataCaptureService","当前步数非0，为新增步数赋值，当前新增步数："+ AddStep);
                    }
                } else if(CurrentStep>(int)sensorEvent.values[0]){
                    // 当前步数大于更新部署，判为计步器清零，重置当前步数
                }

            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
//                Log.i("DataCaptureService","计步传感器精度变化回调接口"+sensor.toString()+","+i);

            }
        };

        StepCounterSensorManager.registerListener(StepCounterSensorListener,StepCounterSensor,SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void saveStepData(){
        Log.i("DataCaptureService","存储新增步数到服务器");
    }

    private void startStorageTimeCount(){
        storagetimeCount = new StorageTimeCount(saveDuration, 1000);
        storagetimeCount.start();
    }

    private void startNoSportTimeCount(){
        noSportTimeCount = new NoSportTimeCount(noStopDuration, 1000);
        noSportTimeCount.start();
    }

    private void reStartNoSportTimeCount(){
        if(noSportTimeCount==null){
            noSportTimeCount = new NoSportTimeCount(noStopDuration, 1000);
            noSportTimeCount.start();
        }else{
            noSportTimeCount.cancel();
        }
    }

    // 存储计时器，五分钟更新一次新增步数
    private class StorageTimeCount extends CountDownTimer {
        public StorageTimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }
        @Override
        public void onTick(long millisUntilFinished) {
        }
        @Override
        public void onFinish() {
            // 如果计时器正常结束，则五分钟后将新增步数同步至服务器
            storagetimeCount.cancel();
            saveStepData();
            startStorageTimeCount();
        }
    }
    // 无运动计时器，五分钟无步数新增则关闭位置记录
    private class NoSportTimeCount extends CountDownTimer {
        public NoSportTimeCount(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }
        @Override
        public void onTick(long millisUntilFinished) {
        }
        @Override
        public void onFinish() {
            // 如果计时器正常结束，则停止位置记录服务
            storagetimeCount.cancel();
            if(SP_setting.getBoolean("pref_location_background_switch",false)){
                Log.i("DataCaptureService","后台服务开关为开，不关闭位置记录！");
            }else{
                Log.i("DataCaptureService","后台位置服务获取未启动，检测到用户一段时间未运动，关闭位置记录！");
                DestroyLocationCapture();
            }
//            startStorageTimeCount();
            Log.i("DataCaptureService","无运动计时器时间到，关闭位置记录！");
        }
    }
}

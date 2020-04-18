package com.hgo.planassistant.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdate;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.CoordinateConverter;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.HeatmapTileProvider;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.TileOverlayOptions;
import com.amap.api.maps.utils.SpatialRelationUtil;
import com.amap.api.maps.utils.overlay.SmoothMoveMarker;
import com.avos.avoscloud.AVException;
import com.avos.avoscloud.AVGeoPoint;
import com.avos.avoscloud.AVObject;
import com.avos.avoscloud.AVQuery;
import com.avos.avoscloud.AVUser;
import com.avos.avoscloud.CountCallback;
import com.avos.avoscloud.FindCallback;
import com.avos.avoscloud.GetCallback;
import com.avos.avoscloud.SaveCallback;
import com.google.android.material.snackbar.Snackbar;
import com.hgo.planassistant.App;
import com.hgo.planassistant.R;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.expressions.Expression;
import com.mapbox.mapboxsdk.style.layers.HeatmapLayer;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.layers.Property;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;
import com.umeng.analytics.MobclickAgent;

import org.geotools.geojson.geom.GeometryJSON;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.mapbox.mapboxsdk.style.expressions.Expression.heatmapDensity;
import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.linear;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.expressions.Expression.rgb;
import static com.mapbox.mapboxsdk.style.expressions.Expression.rgba;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapIntensity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.heatmapRadius;
import static java.net.Proxy.Type.HTTP;

public class TrackActivity extends BaseActivity implements View.OnClickListener{

    static private int PrecisionLessThen = 50; // 轨迹精度查询最高限制
    private   String HEATMAP_SOURCE_ID = "HEATMAP_SOURCE_ID";
    private   String HEATMAP_LAYER_ID = "HEATMAP_LAYER_ID";
    private Expression[] listOfHeatmapColors;
    private Expression[] listOfHeatmapRadiusStops;
    private Float[] listOfHeatmapIntensityStops;
//    private MapView mapView;
//    private MapboxMap mapboxmap;
//    private Style map_style;
//    private int index;
    private com.amap.api.maps.MapView aMapView = null;
    private AMap amap = null;

    private Calendar start_time;
    private Calendar end_time;

    private Button BT_save,BT_quare,BT_theme;
    private TextView TV_start_calendar, TV_start_time,TV_stop_calendar,TV_stop_time,TV_info;

    private List<AVObject> now_list;

    private Context track_context;

    //MarkerQuare
    public int range_point_num_max = 0; //范围查询时的最大点个数，中间变量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //设置accessToken
        Mapbox.getInstance(App.getContext(), getString(R.string.mapbox_access_token));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_track);

        Toolbar toolbar = findViewById(R.id.toolbar_track);
        setToolbar(toolbar);
        getWindow().setNavigationBarColor(getResources().getColor(R.color.colorPrimary));

        initView(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，销毁地图
        aMapView.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        MobclickAgent.onResume(this); // umeng+ 统计 //AUTO页面采集模式下不调用

        //在activity执行onResume时执行mMapView.onResume ()，重新绘制加载地图
        aMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
//        MobclickAgent.onPause(this);  // umeng+ 统计 //AUTO页面采集模式下不调用
        //在activity执行onPause时执行mMapView.onPause ()，暂停地图的绘制
        aMapView.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        aMapView.onSaveInstanceState(outState);
    }

    void initView(Bundle savedInstanceState){


        aMapView = findViewById(R.id.card_track_amapView);
//        mapView = (MapView) findViewById(R.id.card_track_mapView);
        BT_save = findViewById(R.id.card_activity_track_info_button_save);
        BT_quare = findViewById(R.id.card_activity_track_info_button_quare);
        BT_theme = findViewById(R.id.card_activity_track_info_button_theme);
        TV_start_calendar = findViewById(R.id.card_activity_track_info_start_calendar);
        TV_stop_calendar = findViewById(R.id.card_activity_track_info_end_calendar);
        TV_start_time = findViewById(R.id.card_activity_track_info_start_time);
        TV_stop_time = findViewById(R.id.ccard_activity_track_info_end_calendar_time);
        TV_info = findViewById(R.id.card_activity_track_info_info_description);

        BT_theme.setOnClickListener(this);
        BT_quare.setOnClickListener(this);
        BT_save.setOnClickListener(this);
        TV_info.setOnClickListener(this);
        TV_stop_time.setOnClickListener(this);
        TV_start_time.setOnClickListener(this);
        TV_stop_calendar.setOnClickListener(this);
        TV_start_calendar.setOnClickListener(this);

        start_time = Calendar.getInstance();
        end_time = Calendar.getInstance();
        start_time.add(Calendar.HOUR_OF_DAY, -6); //讲起始时间推算为当前时间前n小时

        track_context = this;

        refresh();
        //Log.i("TrackActivity",start_time.get(Calendar.YEAR) + "-" + start_time.get(Calendar.MONTH) + "-" + start_time.get(Calendar.DATE));
        //Log.i("TrackActivity",start_time.get(Calendar.HOUR_OF_DAY) + ":" + start_time.get(Calendar.MINUTE));

        // 高德地图可视化
        //在activity执行onCreate时执行mMapView.onCreate(savedInstanceState)，创建地图
        aMapView.onCreate(savedInstanceState);
        //初始化地图控制器对象
        if (amap == null) {
            amap = aMapView.getMap();
        }

        // 显示定位小蓝点
        MyLocationStyle myLocationStyle;
        myLocationStyle = new MyLocationStyle();
        //初始化定位蓝点样式类
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_SHOW);//只定位一次。
        // myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE);
        // 连续定位、且将视角移动到地图中心点，定位点依照设备方向旋转，并且会跟随设备移动。（1秒1次定位）如果不设置myLocationType，默认也会执行此种模式。
        myLocationStyle.interval(2000); //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
        amap.setMyLocationStyle(myLocationStyle);//设置定位蓝点的Style
//aMap.getUiSettings().setMyLocationButtonEnabled(true);设置默认定位按钮是否显示，非必需设置。
        amap.setMyLocationEnabled(true);// 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。

        AVQuery<AVObject> query = new AVQuery<>("trajectory");
        // 启动查询缓存
        query.setCachePolicy(AVQuery.CachePolicy.NETWORK_ELSE_CACHE);
        query.setMaxCacheAge(24 * 3600 * 1000); //设置为一天，单位毫秒
        query.whereEqualTo("UserId", AVUser.getCurrentUser().getObjectId());
        query.whereGreaterThan("time",start_time.getTime());
        query.whereLessThan("time",end_time.getTime());
        query.whereGreaterThan("precision",1);
        query.whereLessThan("precision",PrecisionLessThen);
        query.selectKeys(Arrays.asList("point", "time", "precision"));
        query.limit(1000);
        query.findInBackground(new FindCallback<AVObject>() {
            @Override
            public void done(List<AVObject> list, AVException e) {
                if(list!=null&&list.size()>0){
                    Log.i("TrackActivity","共查询到：" + list.size() + "条数据。");
                    Toast.makeText(App.getContext(),"共查询到：" + list.size() + "条数据。",Toast.LENGTH_LONG).show();
                    now_list = list;//暂时存储当前查询结果
                    TV_info.setText("开始时间:"+DateFormat.getDateTimeInstance().format(start_time.getTime())+"\n"+
                            "结束时间: " + DateFormat.getDateTimeInstance().format(end_time.getTime())+"\n"+
                            "数据总数: "+ list.size());
                    // 构建热力图 HeatmapTileProvider
                    HeatmapTileProvider.Builder builder = new HeatmapTileProvider.Builder();
                    builder.data(Arrays.asList(GenetateLatLngArratFromAvobject(list))); // 设置热力图绘制的数据
//                            .gradient(ALT_HEATMAP_GRADIENT); // 设置热力图渐变，有默认值 DEFAULT_GRADIENT，可不设置该接口
                    // Gradient 的设置可见参考手册
                    // 构造热力图对象
                    HeatmapTileProvider heatmapTileProvider = builder.build();
                    // 初始化 TileOverlayOptions
                    TileOverlayOptions tileOverlayOptions = new TileOverlayOptions();
                    tileOverlayOptions.tileProvider(heatmapTileProvider); // 设置瓦片图层的提供者
                    // 向地图上添加 TileOverlayOptions 类对象
                    amap.addTileOverlay(tileOverlayOptions);

                    // 全幅显示
                    com.amap.api.maps.model.LatLngBounds bounds = getLatLngBounds(now_list);
                    amap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));

                    // 显示轨迹线
                    Polyline polyline =amap.addPolyline(new PolylineOptions().
                            addAll(Arrays.asList(GenetateLatLngArratFromAvobject(list))).width(10).color(Color.argb(255, 1, 1, 1)));

//                    List<com.amap.api.maps.model.LatLng> points = GenetateLatLngListFromAvobject(now_list);
//                    amap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));
//
//                    SmoothMoveMarker smoothMarker = new SmoothMoveMarker(amap);
//                    // 设置滑动的图标
//                    smoothMarker.setDescriptor(BitmapDescriptorFactory.fromResource(R.drawable.walk));
//
//                    com.amap.api.maps.model.LatLng drivePoint = points.get(0);
//                    Pair<Integer, com.amap.api.maps.model.LatLng> pair = SpatialRelationUtil.calShortestDistancePoint(points, drivePoint);
//                    points.set(pair.first, drivePoint);
//                    List<com.amap.api.maps.model.LatLng> subList = points.subList(pair.first, points.size());
//                    // 设置滑动的轨迹左边点
//                    smoothMarker.setPoints(subList);
//                    // 设置滑动的总时间
//                    smoothMarker.setTotalDuration(40);
//                    // 开始滑动
//                    smoothMarker.startSmoothMove();
                }
            }
        });

//        index = 0;
//        mapView.onCreate(savedInstanceState);
//        mapView.getMapAsync(new OnMapReadyCallback() {
//            @Override
//            public void onMapReady(@NonNull MapboxMap mapboxMap) {
//                mapboxmap = mapboxMap;
//                mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
//                    @Override
//                    public void onStyleLoaded(@NonNull Style style) {
//                        // Map is set up and the style has loaded. Now you can add data or make other map adjustments
//                        map_style = style;
//                        CameraPosition cameraPositionForFragmentMap = new CameraPosition.Builder()
//                                .target(new LatLng(34.833774, 113.537698))
//                                .zoom(11.047)
//                                .build();
////                        mapboxMap.animateCamera(
////                                CameraUpdateFactory.newCameraPosition(cameraPositionForFragmentMap), 2600);
//
////                        AVQuery<AVObject> query = new AVQuery<>("trajectory");
////                        // 启动查询缓存
////                        query.setCachePolicy(AVQuery.CachePolicy.NETWORK_ELSE_CACHE);
////                        query.setMaxCacheAge(24 * 3600 * 1000); //设置为一天，单位毫秒
////                        query.whereEqualTo("UserId", AVUser.getCurrentUser().getObjectId());
////                        query.whereGreaterThan("time",start_time.getTime());
////                        query.whereLessThan("time",end_time.getTime());
////                        query.whereLessThan("precision",50);
////                        query.selectKeys(Arrays.asList("point", "time", "precision"));
////                        query.limit(1000);
////                        query.findInBackground(new FindCallback<AVObject>() {
////                            @Override
////                            public void done(List<AVObject> list, AVException e) {
////                                if(list!=null){
////                                Log.i("TrackActivity","共查询到：" + list.size() + "条数据。");
////                                Toast.makeText(App.getContext(),"共查询到：" + list.size() + "条数据。",Toast.LENGTH_LONG).show();
////                                now_list = list;//暂时存储当前查询结果
////                                TV_info.setText("开始时间:"+DateFormat.getDateTimeInstance().format(start_time.getTime())+"\n"+
////                                        "结束时间: " + DateFormat.getDateTimeInstance().format(end_time.getTime())+"\n"+
////                                        "数据总数: "+ list.size());
////
////                                map_style.removeSource(HEATMAP_SOURCE_ID);
////                                map_style.addSource(new GeoJsonSource(HEATMAP_SOURCE_ID,
////                                        FeatureCollection.fromFeatures(genetateGeoStringFromAvobject(list))));
////                                CreateLineLayer(genetatePointsFromAvobject(list));//创建线
////                            }
////                            }
////                        });
////                        initHeatmapColors();
////                        initHeatmapRadiusStops();
////                        initHeatmapIntensityStops();
////                        addHeatmapLayer(style);
//                    }
//                });
//            }
//        });

    }
    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.card_activity_track_info_button_quare:
                mapfresh();
                break;
            case R.id.card_activity_track_info_button_save:
                //以当前时间命名地图
                String start_time_string = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SS")).format(start_time.getTime());
                String end_time_string = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SS")).format(end_time.getTime());
                SaveToMymap(now_list,start_time_string+" 至 "+end_time_string + "轨迹点");
                break;
//            case R.id.card_activity_track_info_button_theme:
//                index++;
//                if (index == listOfHeatmapColors.length - 1) {
//                    index = 0;
//                }
//                if (map_style.getLayer(HEATMAP_LAYER_ID) != null) {
//                    map_style.getLayer(HEATMAP_LAYER_ID).setProperties(
//                            heatmapColor(listOfHeatmapColors[index]),
//                            heatmapRadius(listOfHeatmapRadiusStops[index]),
//                            heatmapIntensity(listOfHeatmapIntensityStops[index])
//                    );
//                }
//                break;
            case R.id.card_activity_track_info_start_calendar:
                DatePickerDialog start_datePickerDialog = new DatePickerDialog(this, (view1, year, monthOfYear, dayOfMonth) -> {
                    start_time.set(Calendar.YEAR,year);
                    start_time.set(Calendar.MONTH,monthOfYear);
                    start_time.set(Calendar.DATE,dayOfMonth);
//                    monthOfYear++;//月份＋１
                    Log.i("TrackActivity",start_time.get(Calendar.YEAR) + "-" + (start_time.get(Calendar.MONTH)+1) + "-" + start_time.get(Calendar.DATE));
                    TV_start_calendar.setText(year+"年"+(monthOfYear+1)+"月"+dayOfMonth+"日");
                    refresh();
                    }, start_time.get(Calendar.YEAR), start_time.get(Calendar.MONTH), start_time.get(Calendar.DAY_OF_MONTH));
                start_datePickerDialog.show();
                break;
            case R.id.card_activity_track_info_start_time:
                TimePickerDialog start_timePickerDialog = new TimePickerDialog(this,(view1, hour, minute) -> {
                    start_time.set(Calendar.HOUR_OF_DAY,hour);
                    start_time.set(Calendar.MINUTE,minute);
                    Log.i("TrackActivity",start_time.get(Calendar.HOUR_OF_DAY) + ":" + start_time.get(Calendar.MINUTE));
                    TV_start_time.setText(hour+" 时 "+minute +"分");
                    refresh();
                }, start_time.get(Calendar.HOUR_OF_DAY), start_time.get(Calendar.MINUTE),true);
                start_timePickerDialog.show();
                break;
            case R.id.card_activity_track_info_end_calendar:
                DatePickerDialog end_datePickerDialog = new DatePickerDialog(this, (view1, year, monthOfYear, dayOfMonth) -> {
                    end_time.set(Calendar.YEAR,year);
                    end_time.set(Calendar.MONTH,monthOfYear);
                    end_time.set(Calendar.DATE,dayOfMonth);
//                    monthOfYear++;//月份＋１
                    Log.i("TrackActivity",end_time.get(Calendar.YEAR) + "-" + (end_time.get(Calendar.MONTH)+1) + "-" + end_time.get(Calendar.DATE));
                    TV_stop_calendar.setText(year+"年"+(monthOfYear+1)+"月"+dayOfMonth+"日");
                    refresh();
                }, end_time.get(Calendar.YEAR), end_time.get(Calendar.MONTH), end_time.get(Calendar.DAY_OF_MONTH));
                end_datePickerDialog.show();
                break;
            case R.id.ccard_activity_track_info_end_calendar_time:
                TimePickerDialog end_timePickerDialog = new TimePickerDialog(this,(view1, hour, minute) -> {
                    end_time.set(Calendar.HOUR_OF_DAY,hour);
                    end_time.set(Calendar.MINUTE,minute);
                    Log.i("TrackActivity",end_time.get(Calendar.HOUR_OF_DAY) + ":" + end_time.get(Calendar.MINUTE));
                    TV_stop_time.setText(hour+" 时 "+minute +"分");
                    refresh();
                }, end_time.get(Calendar.HOUR_OF_DAY), end_time.get(Calendar.MINUTE),true);
                end_timePickerDialog.show();
                break;
        }
    }

    //根据自定义内容获取缩放bounds
    private com.amap.api.maps.model.LatLngBounds getLatLngBounds(List<AVObject> Geolist) {
        com.amap.api.maps.model.LatLngBounds.Builder b = com.amap.api.maps.model.LatLngBounds.builder();

        for (AVObject obj: Geolist){
            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
            double x = geopoint.getLatitude();
            double y = geopoint.getLongitude();
            b.include(new com.amap.api.maps.model.LatLng(x, y));
        }

        return b.build();
    }
    private void mapfresh(){

        amap.clear();

        AVQuery<AVObject> query = new AVQuery<>("trajectory");
        // 启动查询缓存
        query.setCachePolicy(AVQuery.CachePolicy.NETWORK_ELSE_CACHE);
        query.setMaxCacheAge(24 * 3600 * 1000); //设置为一天，单位毫秒
        query.whereEqualTo("UserId", AVUser.getCurrentUser().getObjectId());
        query.whereGreaterThan("time",start_time.getTime());
        query.whereLessThan("time",end_time.getTime());
        query.whereGreaterThan("precision",1);
        query.whereLessThan("precision",PrecisionLessThen);
        query.selectKeys(Arrays.asList("point", "time", "precision"));
        query.limit(1000);
        query.findInBackground(new FindCallback<AVObject>() {
            @Override
            public void done(List<AVObject> list, AVException e) {
                if(list!=null&&list.size()>0){
                    Log.i("TrackActivity","共查询到：" + list.size() + "条数据。");
                    Toast.makeText(App.getContext(),"共查询到：" + list.size() + "条数据。",Toast.LENGTH_LONG).show();
                    now_list = list;//暂时存储当前查询结果
                    TV_info.setText("开始时间:"+DateFormat.getDateTimeInstance().format(start_time.getTime())+"\n"+
                            "结束时间: " + DateFormat.getDateTimeInstance().format(end_time.getTime())+"\n"+
                            "数据总数: "+ list.size());
                    // 构建热力图 HeatmapTileProvider
                    HeatmapTileProvider.Builder builder = new HeatmapTileProvider.Builder();
                    builder.data(Arrays.asList(GenetateLatLngArratFromAvobject(list))); // 设置热力图绘制的数据
//                            .gradient(ALT_HEATMAP_GRADIENT); // 设置热力图渐变，有默认值 DEFAULT_GRADIENT，可不设置该接口
                    // Gradient 的设置可见参考手册
                    // 构造热力图对象
                    HeatmapTileProvider heatmapTileProvider = builder.build();
                    // 初始化 TileOverlayOptions
                    TileOverlayOptions tileOverlayOptions = new TileOverlayOptions();
                    tileOverlayOptions.tileProvider(heatmapTileProvider); // 设置瓦片图层的提供者
                    // 向地图上添加 TileOverlayOptions 类对象
                    amap.addTileOverlay(tileOverlayOptions);

                    // 显示轨迹线
                    Polyline polyline =amap.addPolyline(new PolylineOptions().
                            addAll(Arrays.asList(GenetateLatLngArratFromAvobject(list))).width(20).color(Color.argb(255, 1, 1, 1)));

                    // 全幅显示
                    com.amap.api.maps.model.LatLngBounds bounds = getLatLngBounds(now_list);
                    amap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 50));


                }
            }
        });

//        mapboxmap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
//            @Override
//            public void onStyleLoaded(@NonNull Style style) {
//                // Map is set up and the style has loaded. Now you can add data or make other map adjustments
//                map_style = style;
//                CameraPosition cameraPositionForFragmentMap = new CameraPosition.Builder()
//                        .target(new LatLng(34.833774, 113.537698))
//                        .zoom(11.047)
//                        .build();
//                mapboxmap.animateCamera(
//                        CameraUpdateFactory.newCameraPosition(cameraPositionForFragmentMap), 2600);
//                AVQuery<AVObject> query = new AVQuery<>("trajectory");
//                query.whereEqualTo("UserId", AVUser.getCurrentUser().getObjectId());
//                query.whereGreaterThan("time",start_time.getTime());
//                query.whereLessThan("time",end_time.getTime());
//                query.whereLessThan("precision",50);
//                query.selectKeys(Arrays.asList("point", "time", "precision"));
//                query.limit(1000);
//                query.findInBackground(new FindCallback<AVObject>() {
//                    @Override
//                    public void done(List<AVObject> list, AVException e) {
//                        Log.i("TrackActivity","共查询到：" + list.size() + "条数据。");
//                        Toast.makeText(App.getContext(),"共查询到：" + list.size() + "条数据。",Toast.LENGTH_LONG).show();
//
//                        now_list = list;//暂时存储当前查询结果
//
//                        TV_info.setText("开始时间:"+DateFormat.getDateTimeInstance().format(start_time.getTime())+"\n"+
//                                "结束时间: " + DateFormat.getDateTimeInstance().format(end_time.getTime())+"\n"+
//                                "数据总数: "+ list.size());
//
//                        map_style.removeSource(HEATMAP_SOURCE_ID);
//                        map_style.addSource(new GeoJsonSource(HEATMAP_SOURCE_ID,
//                                FeatureCollection.fromFeatures(genetateGeoStringFromAvobject(list))));
//                        CreateLineLayer(genetatePointsFromAvobject(list));//创建线
////                        for (AVObject obj: list){
//////                            AVObject point = obj.getAVObject("point");
////                            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
//////                            Log.i("TrackActivity",geopoint.toString());
////                            mapboxMap.addMarker(new MarkerOptions()
////                                    .position(new LatLng(geopoint.getLatitude(),geopoint.getLongitude())));
////
////                        }
//                    }
//                });
//                initHeatmapColors();
//                initHeatmapRadiusStops();
//                initHeatmapIntensityStops();
//                addHeatmapLayer(style);
//            }
//        });

    }


//    public List<com.amap.api.maps.model.LatLng> optimizePoints(List<com.amap.api.maps.model.LatLng> inPoint) {
//        int size = inPoint.size();
//        List<com.amap.api.maps.model.LatLng> outPoint;
//
//        int i;
//        if (size < 5) {
//            return inPoint;
//        } else {
//            // Latitude
//            inPoint.get(0).latitude = ((3.0 * inPoint.get(0).latitude + 2.0 * inPoint.get(1).latitude + inPoint.get(2).latitude - inPoint.get(4).latitude) / 5.0);
//            inPoint.get(0).l
//            inPoint.get(1)
//                    .setLat((4.0 * inPoint.get(0).getLat() + 3.0
//                            * inPoint.get(1).getLat() + 2
//                            * inPoint.get(2).getLat() + inPoint.get(3).getLat()) / 10.0);
//
//            inPoint.get(size - 2).setLat(
//                    (4.0 * inPoint.get(size - 1).getLat() + 3.0
//                            * inPoint.get(size - 2).getLat() + 2
//                            * inPoint.get(size - 3).getLat() + inPoint.get(
//                            size - 4).getLat()) / 10.0);
//            inPoint.get(size - 1).setLat(
//                    (3.0 * inPoint.get(size - 1).getLat() + 2.0
//                            * inPoint.get(size - 2).getLat()
//                            + inPoint.get(size - 3).getLat() - inPoint.get(
//                            size - 5).getLat()) / 5.0);
//
//            // Longitude
//            inPoint.get(0)
//                    .setLng((3.0 * inPoint.get(0).getLng() + 2.0
//                            * inPoint.get(1).getLng() + inPoint.get(2).getLng() - inPoint
//                            .get(4).getLng()) / 5.0);
//            inPoint.get(1)
//                    .setLng((4.0 * inPoint.get(0).getLng() + 3.0
//                            * inPoint.get(1).getLng() + 2
//                            * inPoint.get(2).getLng() + inPoint.get(3).getLng()) / 10.0);
//
//            inPoint.get(size - 2).setLng(
//                    (4.0 * inPoint.get(size - 1).getLng() + 3.0
//                            * inPoint.get(size - 2).getLng() + 2
//                            * inPoint.get(size - 3).getLng() + inPoint.get(
//                            size - 4).getLng()) / 10.0);
//            inPoint.get(size - 1).setLng(
//                    (3.0 * inPoint.get(size - 1).getLng() + 2.0
//                            * inPoint.get(size - 2).getLng()
//                            + inPoint.get(size - 3).getLng() - inPoint.get(
//                            size - 5).getLng()) / 5.0);
//        }
//        return inPoint;
//    }

    private void refresh(){
        TV_start_calendar.setText(start_time.get(Calendar.YEAR)+"年"+(start_time.get(Calendar.MONTH)+1)+"月"+start_time.get(Calendar.DATE)+"日");
        TV_start_time.setText(start_time.get(Calendar.HOUR_OF_DAY)+" 时 "+start_time.get(Calendar.MINUTE) +"分");
        TV_stop_calendar.setText(end_time.get(Calendar.YEAR)+"年"+(end_time.get(Calendar.MONTH)+1)+"月"+end_time.get(Calendar.DATE)+"日");
        TV_stop_time.setText(end_time.get(Calendar.HOUR_OF_DAY)+" 时 "+end_time.get(Calendar.MINUTE) +"分");
    }
    private Feature[] genetateGeoStringFromAvobject(List<AVObject> list){
        if(list!=null){
        Feature[] features = new Feature[list.size()];
        int i=0;

        LatLngBounds.Builder latLngBoundsBuilder = new LatLngBounds.Builder();
//        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
        for (AVObject obj: list){
            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
            features[i] = Feature.fromGeometry(Point.fromLngLat(
                    geopoint.getLongitude(),
                    geopoint.getLatitude()));
            latLngBoundsBuilder.include(new LatLng(geopoint.getLatitude(),geopoint.getLongitude()));
            i++;
        }
        if(list.size()>2){
            LatLngBounds latLngBounds = latLngBoundsBuilder.build();//创建边界
//            mapboxmap.easeCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 50), 5000);//全幅显示
        }

            return features;
        }
        return null;
    }
    private ArrayList<Point> genetatePointsFromAvobject(List<AVObject> list){
        ArrayList<Point> routeCoordinates = new ArrayList<Point>();

        for (AVObject obj: list){
            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
            routeCoordinates.add(Point.fromLngLat(geopoint.getLongitude(), geopoint.getLatitude()));
        }
        Log.i("TrackActivity","为生成线读取到"+routeCoordinates.size()+"条数据");
        return routeCoordinates;
    }
    private com.amap.api.maps.model.LatLng[] GenetateLatLngArratFromAvobject(List<AVObject> list){
        int sum = list.size();
        com.amap.api.maps.model.LatLng[] latlngs = new com.amap.api.maps.model.LatLng[sum];

        int i=0;
        for (AVObject obj: list){
            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
            double x = geopoint.getLatitude();
            double y = geopoint.getLongitude();
            latlngs[i] = new com.amap.api.maps.model.LatLng(x, y);

            // 将WGS-84坐标转换为高德坐标
            CoordinateConverter converter  = new CoordinateConverter(track_context);
            // CoordType.GPS 待转换坐标类型
            converter.from(CoordinateConverter.CoordType.GPS);
            // sourceLatLng待转换坐标点 LatLng类型
            converter.coord(latlngs[i]);
            // 执行转换操作
            latlngs[i] = converter.convert();

            i++;
        }

        return latlngs;
    }

    private List<com.amap.api.maps.model.LatLng> GenetateLatLngListFromAvobject(List<AVObject> list){
        int sum = list.size();
        List<com.amap.api.maps.model.LatLng> latlngs = new ArrayList<>(sum);

        int i=0;
        for (AVObject obj: list){
            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
            double x = geopoint.getLatitude();
            double y = geopoint.getLongitude();
            latlngs.add(new com.amap.api.maps.model.LatLng(x, y));

            // 将WGS-84坐标转换为高德坐标
            CoordinateConverter converter  = new CoordinateConverter(track_context);
            // CoordType.GPS 待转换坐标类型
            converter.from(CoordinateConverter.CoordType.GPS);
            // sourceLatLng待转换坐标点 LatLng类型
            converter.coord(latlngs.get(i));
            // 执行转换操作
            latlngs.set(i,converter.convert());

            i++;
        }

        return latlngs;
    }



//    private void CreateLineLayer(ArrayList<Point> routeCoordinates){
//        // Create the LineString from the list of coordinates and then make a GeoJSON
//        // FeatureCollection so we can add the line to our map as a layer.
//
//        // 尝试使用高德地图轨迹纠偏api进行纠偏
//        final String URL = "https://restapi.amap.com/v4/grasproad/driving" ;
////
////        try {
////            HttpPost request = new HttpPost(URL);                       // 提交路径
////            List<NameValuePair> params = new ArrayList<NameValuePair>();// 设置提交参数
////            params.add(new BasicNameValuePair("id", "100"));    // 设置id参数
////            params.add(new BasicNameValuePair("password", "111111"));// 设置password参数
////            request.setEntity(new UrlEncodedFormEntity(params,
////                    HTTP.UTF_8));                                       // 设置编码
////            HttpResponse response = new DefaultHttpClient()
////                    .execute(request);                                      // 接收回应
////            if (response.getStatusLine().getStatusCode() != 404) {      // 请求正常
////                flag = Boolean.parseBoolean(EntityUtils.toString(
////                        response.getEntity()).trim());                  // 接收返回的信息
////            }
////        } catch (Exception e) {
////            e.printStackTrace() ;
////            info.setText("WEB服务器连接失败。") ;
////        }
//
//
//        LineString lineString = LineString.fromLngLats(routeCoordinates);
//
//        FeatureCollection featureCollection =
//                FeatureCollection.fromFeatures(new Feature[]{Feature.fromGeometry(lineString)});
//
//        Source geoJsonSource = new GeoJsonSource("line-source", featureCollection);
//
//        map_style.addSource(geoJsonSource);
//
//        LineLayer lineLayer = new LineLayer("linelayer", "line-source");
//
//    // The layer properties for our line. This is where we make the line dotted, set the
//    // color, etc.
//        lineLayer.setProperties(
//                PropertyFactory.lineDasharray(new Float[]{0.01f, 2f}),
//                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
//                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
//                PropertyFactory.lineWidth(5f),
//                PropertyFactory.lineColor(Color.parseColor("#e55e5e"))
//        );
//
////        map_style.addLayerAbove(lineLayer, HEATMAP_LAYER_ID);
//        map_style.addLayer(lineLayer);
//
//    }
    private void SaveToMymap(List<AVObject> map_list, String map_name){
        // 第一步：创建空的个人地图
        // 第二步：将数据提交到当前的个人地图
        // 第三步：存储当前地图风格

        // 构造方法传入的参数，对应的就是控制台中的 Class Name
        AVObject mymap = new AVObject("personalmap");
//        AVObject mappoint = new AVObject("mappoint");
        ArrayList<AVObject> mappoints = new ArrayList<AVObject>();

        // no.1
        mymap.put("name",map_name);//地图名称
        mymap.put("UserId",AVUser.getCurrentUser().getObjectId());//用户编号
        mymap.put("mapstyle_index",0);//风格编号
        mymap.saveInBackground(new SaveCallback() {
            @Override
            public void done(AVException e) {
                if (e == null) {
                    // 存储成功
//                    Toast.makeText(track_context,map_name + "地图创建成功!",Toast.LENGTH_LONG).show();

                    for (AVObject mappoint : map_list) {
                        AVObject point = new AVObject("mappoint");
                        point.put("point",mappoint.getAVGeoPoint("point"));
                        point.put("altutude",mappoint.get("altitude"));
                        point.put("MapId",mymap.getObjectId());
                        mappoints.add(point);
                    }

                    AVObject.saveAllInBackground(mappoints, new SaveCallback() {
                        @Override
                        public void done(AVException e) {
                            if (e != null) {
                                // 出现错误
                                mymap.deleteInBackground();
                                Toast.makeText(track_context,"地图创建失败!\n 失败原因: "+e.toString(),Toast.LENGTH_LONG).show();
                            } else {
                                // 保存成功
//                                Toast.makeText(track_context,"地图存储成功!",Toast.LENGTH_LONG).show();
                            }
                        }
                    });


                } else {
                    // 失败的话，请检查网络环境以及 SDK 配置是否正确
                    Toast.makeText(track_context,map_name + "地图创建失败!\n 失败原因: "+e.toString(),Toast.LENGTH_LONG).show();
                }
            }
        });

//        ArrayList<AVObject> save_mappoints = (ArrayList<AVObject>) map_list;


        

//        for (AVObject obj: map_list){
//            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
//            geopoint.getLongitude();
//            geopoint.getLatitude();
//
//
//
//        }

    }
    private void RagesQuare(List<AVObject> list){
        int i=0;
        double range_num = 0.2;

//        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory(null);
        Log.i("TrackActivity","开始分析时空点个数");
        for (AVObject obj: list){
            //查询指定范围内个数，并更新到字段ranges中
            AVGeoPoint geopoint = obj.getAVGeoPoint("point");
            AVQuery<AVObject> query = new AVQuery<>("trajectory");
            AVGeoPoint point = new AVGeoPoint(geopoint.getLatitude(), geopoint.getLongitude());
            query.limit(1000); //最多为1000
            query.whereGreaterThan("time",start_time.getTime());
            query.whereLessThan("time",end_time.getTime());
            query.whereWithinKilometers("point", point, range_num);//查询范围
            // 得到点总个数
            query.countInBackground(new CountCallback() {
                @Override
                public void done(int i, AVException e) {
                    if (e == null) {
                        // 查询成功，输出计数
//                        Log.d("TrackActivity", "该点"+ range_num +"范围内共有" + i + "个点.");
                        // 第一参数是 className,第二个参数是 objectId
                        AVObject point_range = AVObject.createWithoutData("trajectory", obj.getObjectId());
                        // 修改 content
                        point_range.put("ranges",i);
                        // 保存到云端
                        point_range.saveInBackground();
                    } else {
                        // 查询失败
                    }

                }
            });
        }
//        MarkerQuare();
    }



    private String loadGeoJsonFromAsset(String filename) {
        try {
            // Load GeoJSON file
            InputStream is = getAssets().open(filename);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            return new String(buffer, "UTF-8");

        } catch (Exception exception) {
//            Timber.e("Exception loading GeoJSON: %s", exception.toString());
            Log.e("TrackActivity","Exception loading GeoJSON: %s"+  exception.toString());
            exception.printStackTrace();
            return null;
        }
    }
//    public void initHeatmapColors() {
//        listOfHeatmapColors = new Expression[] {
//                // 0
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.25), rgba(224, 176, 63, 0.5),
//                        literal(0.5), rgb(247, 252, 84),
//                        literal(0.75), rgb(186, 59, 30),
//                        literal(0.9), rgb(255, 0, 0)
//                ),
//                // 1
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(255, 255, 255, 0.4),
//                        literal(0.25), rgba(4, 179, 183, 1.0),
//                        literal(0.5), rgba(204, 211, 61, 1.0),
//                        literal(0.75), rgba(252, 167, 55, 1.0),
//                        literal(1), rgba(255, 78, 70, 1.0)
//                ),
//                // 2
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(12, 182, 253, 0.0),
//                        literal(0.25), rgba(87, 17, 229, 0.5),
//                        literal(0.5), rgba(255, 0, 0, 1.0),
//                        literal(0.75), rgba(229, 134, 15, 0.5),
//                        literal(1), rgba(230, 255, 55, 0.6)
//                ),
//                // 3
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(135, 255, 135, 0.2),
//                        literal(0.5), rgba(255, 99, 0, 0.5),
//                        literal(1), rgba(47, 21, 197, 0.2)
//                ),
//                // 4
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(4, 0, 0, 0.2),
//                        literal(0.25), rgba(229, 12, 1, 1.0),
//                        literal(0.30), rgba(244, 114, 1, 1.0),
//                        literal(0.40), rgba(255, 205, 12, 1.0),
//                        literal(0.50), rgba(255, 229, 121, 1.0),
//                        literal(1), rgba(255, 253, 244, 1.0)
//                ),
//                // 5
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.05), rgba(0, 0, 0, 0.05),
//                        literal(0.4), rgba(254, 142, 2, 0.7),
//                        literal(0.5), rgba(255, 165, 5, 0.8),
//                        literal(0.8), rgba(255, 187, 4, 0.9),
//                        literal(0.95), rgba(255, 228, 173, 0.8),
//                        literal(1), rgba(255, 253, 244, .8)
//                ),
//                //6
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.3), rgba(82, 72, 151, 0.4),
//                        literal(0.4), rgba(138, 202, 160, 1.0),
//                        literal(0.5), rgba(246, 139, 76, 0.9),
//                        literal(0.9), rgba(252, 246, 182, 0.8),
//                        literal(1), rgba(255, 255, 255, 0.8)
//                ),
//
//                //7
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.1), rgba(0, 2, 114, .1),
//                        literal(0.2), rgba(0, 6, 219, .15),
//                        literal(0.3), rgba(0, 74, 255, .2),
//                        literal(0.4), rgba(0, 202, 255, .25),
//                        literal(0.5), rgba(73, 255, 154, .3),
//                        literal(0.6), rgba(171, 255, 59, .35),
//                        literal(0.7), rgba(255, 197, 3, .4),
//                        literal(0.8), rgba(255, 82, 1, 0.7),
//                        literal(0.9), rgba(196, 0, 1, 0.8),
//                        literal(0.95), rgba(121, 0, 0, 0.8)
//                ),
//                // 8
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.1), rgba(0, 2, 114, .1),
//                        literal(0.2), rgba(0, 6, 219, .15),
//                        literal(0.3), rgba(0, 74, 255, .2),
//                        literal(0.4), rgba(0, 202, 255, .25),
//                        literal(0.5), rgba(73, 255, 154, .3),
//                        literal(0.6), rgba(171, 255, 59, .35),
//                        literal(0.7), rgba(255, 197, 3, .4),
//                        literal(0.8), rgba(255, 82, 1, 0.7),
//                        literal(0.9), rgba(196, 0, 1, 0.8),
//                        literal(0.95), rgba(121, 0, 0, 0.8)
//                ),
//                // 9
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.1), rgba(0, 2, 114, .1),
//                        literal(0.2), rgba(0, 6, 219, .15),
//                        literal(0.3), rgba(0, 74, 255, .2),
//                        literal(0.4), rgba(0, 202, 255, .25),
//                        literal(0.5), rgba(73, 255, 154, .3),
//                        literal(0.6), rgba(171, 255, 59, .35),
//                        literal(0.7), rgba(255, 197, 3, .4),
//                        literal(0.8), rgba(255, 82, 1, 0.7),
//                        literal(0.9), rgba(196, 0, 1, 0.8),
//                        literal(0.95), rgba(121, 0, 0, 0.8)
//                ),
//                // 10
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.01),
//                        literal(0.1), rgba(0, 2, 114, .1),
//                        literal(0.2), rgba(0, 6, 219, .15),
//                        literal(0.3), rgba(0, 74, 255, .2),
//                        literal(0.4), rgba(0, 202, 255, .25),
//                        literal(0.5), rgba(73, 255, 154, .3),
//                        literal(0.6), rgba(171, 255, 59, .35),
//                        literal(0.7), rgba(255, 197, 3, .4),
//                        literal(0.8), rgba(255, 82, 1, 0.7),
//                        literal(0.9), rgba(196, 0, 1, 0.8),
//                        literal(0.95), rgba(121, 0, 0, 0.8)
//                ),
//                // 11
//                interpolate(
//                        linear(), heatmapDensity(),
//                        literal(0.01), rgba(0, 0, 0, 0.25),
//                        literal(0.25), rgba(229, 12, 1, .7),
//                        literal(0.30), rgba(244, 114, 1, .7),
//                        literal(0.40), rgba(255, 205, 12, .7),
//                        literal(0.50), rgba(255, 229, 121, .8),
//                        literal(1), rgba(255, 253, 244, .8)
//                )
//        };
//    }

//    public void initHeatmapRadiusStops() {
//        listOfHeatmapRadiusStops = new Expression[] {
//                // 0
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(30),
//                        literal(6), literal(60)
//                ),
////                interpolate(
////                        linear(), zoom(),
////                        literal(6), literal(50),
////                        literal(20), literal(100)
////                ),
//                // 1
//                interpolate(
//                        linear(), zoom(),
//                        literal(12), literal(70),
//                        literal(20), literal(100)
//                ),
//                // 2
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(7),
//                        literal(5), literal(50)
//                ),
//                // 3
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(7),
//                        literal(5), literal(50)
//                ),
//                // 4
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(7),
//                        literal(5), literal(50)
//                ),
//                // 5
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(7),
//                        literal(15), literal(200)
//                ),
//                // 6
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(10),
//                        literal(8), literal(70)
//                ),
//                // 7
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(10),
//                        literal(8), literal(200)
//                ),
//                // 8
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(10),
//                        literal(8), literal(200)
//                ),
//                // 9
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(10),
//                        literal(8), literal(200)
//                ),
//                // 10
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(10),
//                        literal(8), literal(200)
//                ),
//                // 11
//                interpolate(
//                        linear(), zoom(),
//                        literal(1), literal(10),
//                        literal(8), literal(200)
//                ),
//        };
//    }

//    public void initHeatmapIntensityStops() {
//        listOfHeatmapIntensityStops = new Float[] {
//                // 0
//                0.06f,
//                // 1
//                0.3f,
//                // 2
//                1f,
//                // 3
//                1f,
//                // 4
//                1f,
//                // 5
//                1f,
//                // 6
//                1.5f,
//                // 7
//                0.8f,
//                // 8
//                0.25f,
//                // 9
//                0.8f,
//                // 10
//                0.25f,
//                // 11
//                0.5f
//        };
//    }
//    public void addHeatmapLayer(@NonNull Style loadedMapStyle) {
//        // Create the heatmap layer
//        HeatmapLayer layer = new HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID);
//
//        // Heatmap layer disappears at whatever zoom level is set as the maximum
//        layer.setMaxZoom(18);
//
//        layer.setProperties(
//                // Color ramp for heatmap.  Domain is 0 (low) to 1 (high).
//                // Begin color ramp at 0-stop with a 0-transparency color to create a blur-like effect.
//                heatmapColor(listOfHeatmapColors[index]),
//
//                // Increase the heatmap color weight weight by zoom level
//                // heatmap-intensity is a multiplier on top of heatmap-weight
//                heatmapIntensity(listOfHeatmapIntensityStops[index]),
//
//                // Adjust the heatmap radius by zoom level
//                heatmapRadius(listOfHeatmapRadiusStops[index]
//                ),
//
//                heatmapOpacity(1f)
//        );
//
//        // Add the heatmap layer to the map and above the "water-label" layer
//        loadedMapStyle.addLayerAbove(layer, "waterway-label");
//    }


}

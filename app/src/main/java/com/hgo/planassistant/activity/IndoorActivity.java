package com.hgo.planassistant.activity;

import androidx.appcompat.widget.Toolbar;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hgo.planassistant.R;
import com.umeng.analytics.MobclickAgent;

public class IndoorActivity extends BaseActivity {

    WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_indoor);

        Toolbar toolbar = findViewById(R.id.toolbar_indoor);
        setToolbar(toolbar);

        webView = findViewById(R.id.webView1);
        webView.loadUrl("https://www.esmap.cn/escopemap/mapshow/weixin/index.html?bids=haut_build_3&s=1560747&v=7c1123be80335e9e&token=VWJhX1hUVnBkWlIxV214Ulh3Tj1NWnRzWW93");
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();
//        MobclickAgent.onResume(this); // umeng+ 统计 //AUTO页面采集模式下不调用
    }

    @Override
    protected void onPause() {
        super.onPause();
//        MobclickAgent.onPause(this);  // umeng+ 统计 //AUTO页面采集模式下不调用
    }

    @Override
    public void onBackPressed() {

        super.onBackPressed();
    }
}

package com.qy.reader.home;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.TextView;

import com.qy.reader.R;
import com.qy.reader.common.base.BaseActivity;
import com.qy.reader.common.utils.Nav;
import com.qy.reader.common.utils.StatusBarCompat;

/**
 * Created by yuyuhang on 2018/1/8.
 */
public class SplashActivity extends BaseActivity {

    private TextView mTvSkip;
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            end();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        StatusBarCompat.compatTransNavigationBar(this);

        setContentView(R.layout.activity_splash);

        mTvSkip = findViewById(R.id.tv_splash_skip);
        mTvSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                end();
            }
        });

        mTvSkip.postDelayed(runnable, 500);

    }

    private void end() {
        Nav.from(this).start("qyreader://home");

        finish();
    }

    @Override
    public void finish() {
        mTvSkip.removeCallbacks(runnable);
        super.finish();
    }
}

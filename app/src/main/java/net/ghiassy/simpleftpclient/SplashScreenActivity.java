package net.ghiassy.simpleftpclient;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

public class SplashScreenActivity extends AppCompatActivity {

    public static final String TAG = "net.ghiassy.simpleftpclient.SplashScreenActivity";
    SharedPreferences sharedPreferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        sharedPreferences = this.getSharedPreferences("net.ghiassy.simpleftpclient", Context.MODE_PRIVATE);

        new CountDownTimer(3000, 2000)
        {

            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {

                Intent intent = new Intent(SplashScreenActivity.this, MainActivity.class);

                if(sharedPreferences.getBoolean("firstRun", true))
                {
                    //Toast.makeText(MainActivity.this, "First Time", Toast.LENGTH_SHORT).show();
                    sharedPreferences.edit().putBoolean("firstRun" , false).apply();
                    intent.putExtra("LoadSettings", false);

                }else
                {
                    //Toast.makeText(MainActivity.this, "Load Settings", Toast.LENGTH_SHORT).show();
                    intent.putExtra("LoadSettings", true);

                }
                startActivity(intent);
                finish();

            }
        }.start();

    }
}

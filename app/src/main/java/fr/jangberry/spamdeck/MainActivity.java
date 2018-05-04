package fr.jangberry.spamdeck;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;

public class MainActivity extends AppCompatActivity {
    protected static final String clientID = "6ndb4f2gou52g7zdmk32e89m4gk4iq";
    private static final String savedLayout_Location = "fr.jangberry.spamdeck.layout";
    private static final String apiScopes = "chat_login";
    String channel;
    String token;
    Boolean changingChannel = false;
    int buttonChangingId;
    int currentView;    /*
                        0 = login (webview)
                        1 = choosing channel
                        2 = buttons view
                        3 = editing button view
                        */
    private SharedPreferences savedLayout;
    private SocketService socketservice;
    private InterstitialAd bigad;
    private AdView adview;

    protected ServiceConnection serviceconnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            SocketService.LocalBinder binder = (SocketService.LocalBinder) service;
            socketservice = binder.getService();
            //mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            //mBound = false;
        }

    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.toolbar_aboutButton:
                Intent aboutActivityCall = new Intent(this, AboutTab.class);
                startActivity(aboutActivityCall);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    Boolean checkLogged() {
        return socketservice.logged;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentView == 0) {                                      //0 = login (webview)
                finish();
            } else if (currentView == 1) {                               //1 = choosing channel
                finish();
            } else if (currentView == 2) {                               //2 = buttons view
                setContentView(R.layout.activity_main_chosechannel);
                currentView = 1;
                Toolbar toolbar = findViewById(R.id.toolbar_main);
                setSupportActionBar(toolbar);
                setTitle(R.string.chosenewchannel);
                changingChannel = true;
            } else if (currentView == 3) {                               //3 = editing button view
                findViewById(R.id.buttons).setVisibility(View.VISIBLE);
                findViewById(R.id.buttonsmodifier).setVisibility(View.GONE);
                currentView = 2;
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, SocketService.class));
        bindService(new Intent(this, SocketService.class),
                serviceconnection,
                Context.BIND_AUTO_CREATE);
        savedLayout = this.getSharedPreferences(savedLayout_Location, MODE_PRIVATE);
        setContentView(R.layout.activity_main_login);
        currentView = 0;
        setTitle(R.string.app_name);
        String uri = "https://id.twitch.tv/oauth2/authorize" +
                "?client_id=" + clientID +
                "&scope=" + apiScopes +
                "&redirect_uri=http://localhost" +
                "&response_type=token";
        WebView webview = findViewById(R.id.Login);
        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient());
        webview.loadUrl(uri);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                //Log.v("URL", url);                         // Commented because of security issues
                if (url.contains("localhost/#")) {
                    Log.i("TwitchLogin",
                            "Logged, recovering Token, connecting to chat and setting up button view...");
                    token = url.substring(url.indexOf("=") + 1, url.indexOf("&"));
                    //Log.v("Token", token);                 // Commented because of security issues
                    setContentView(R.layout.activity_main_chosechannel);
                    currentView = 1;
                    Toolbar toolbar = findViewById(R.id.toolbar_channel);
                    setSupportActionBar(toolbar);
                    setTitle(R.string.app_name);
                    socketservice.socketConnect(token);
                }
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (!failingUrl.contains("localhost/#")) {
                    String htmlData =
                            "<html>" +
                                    "<body>" +
                                    "<h1>" +
                                    getString(R.string.connectionErrorHTML) +
                                    "</h1>" +
                                    getString(R.string.twitchUnjoinableHTML) +
                                    "</body>" +
                                    "</html>";
                    view.loadUrl("about:blank");
                    view.loadDataWithBaseURL(
                            null, htmlData, "text/html", "UTF-8", null);
                    view.invalidate();
                }
            }
        });
        MobileAds.initialize(MainActivity.this, "ca-app-pub-2767919419358345~6397374931");
        bigad = new InterstitialAd(this);
        bigad.setAdUnitId("ca-app-pub-2767919419358345/7371545250");
        AdRequest.Builder bigadrequest = new AdRequest.Builder();
        if (BuildConfig.DEBUG) {
            bigadrequest.addTestDevice("3D0266CF596BA090B74E9D85DE74822E");
            Log.i("Debug", "Screening ad");
        }
        bigad.loadAd(bigadrequest.build());
        bigad.setAdListener(new AdListener() {
            @Override
            public void onAdClosed() {
                AdRequest.Builder bigadrequest = new AdRequest.Builder();
                if (BuildConfig.DEBUG) {
                    bigadrequest.addTestDevice("3D0266CF596BA090B74E9D85DE74822E");
                    Log.i("Debug", "Screening ad");
                }
                bigad.loadAd(bigadrequest.build());
            }
            @Override
            public void onAdFailedToLoad(int errorCode) {
                if(BuildConfig.DEBUG){
                    Log.i("Big ad", "failed loading error code:"+errorCode);
                }
            }
            @Override
            public void onAdOpened() {
                Toast.makeText(MainActivity.this, getString(R.string.bigadthank), Toast.LENGTH_LONG).show();
                if(BuildConfig.DEBUG){
                    Log.i("Big ad", "opened");
                }
            }
            @Override
            public void onAdLoaded() {
                if(BuildConfig.DEBUG){
                    Log.i("Big ad", "loaded");
                }
            }
        });
    }

    public void onChannelChosen(View view) {
        /*if(channel.equals("custom")){*/
        EditText channelField = findViewById(R.id.channelField);
        channel = channelField.getText().toString();
        //}
        if (!channel.equals("")) {
            if (!changingChannel) {
                socketservice.setChannel(channel);
                findViewById(R.id.channelloading).setVisibility(View.VISIBLE);
                findViewById(R.id.channeltexture).setVisibility(View.VISIBLE);
                new ChangeViewChecker().start();
            } else {
                socketservice.newChannel(channel);
                findViewById(R.id.channelloading).setVisibility(View.VISIBLE);
                findViewById(R.id.channeltexture).setVisibility(View.VISIBLE);
                new ChangeViewChecker().start();
                changingChannel = false;
            }
        } else {
            findViewById(R.id.textviewchannelnamerror).setVisibility(View.VISIBLE);
        }
    }

    public void onButtonClick(View view) {
        Log.v("Main",
                "Button" + view.getId() +
                        " with command " + view.getContentDescription().toString() +
                        " short pressed");
        if (!view.getContentDescription().toString()
                .substring(0, view.getContentDescription().toString().lastIndexOf("/"))
                .equals("")) {
            String temp = view.getContentDescription().toString();
            socketservice.send(temp.substring(0, temp.lastIndexOf("/")),
                    temp.substring(temp.lastIndexOf("/")+1).equals("1"));
        } else {
            Log.d("Main", "Button unmapped");
        }
    }

    public Boolean onButtonLongClick(View view) {
        Log.v("Main",
                "Button" + view.getId() +
                        " with command " + view.getContentDescription().toString() +
                        " long pressed");
        findViewById(R.id.buttonsmodifier).setVisibility(View.VISIBLE);
        currentView = 3;
        TextView name = findViewById(R.id.text_newcommandname);
        TextView old = findViewById(view.getId());
        TextView message = findViewById(R.id.text_newcommandmessage);
        Switch switchspam = findViewById(R.id.switchspam);
        name.setText(old.getText());
        message.setText(old.getContentDescription().toString().substring(0,
                old.getContentDescription().toString().lastIndexOf("/")));
        switchspam.setChecked(
                old.getContentDescription().toString().substring(
                        old.getContentDescription().toString().lastIndexOf("/") + 1).equals("1"));
        buttonChangingId = view.getId();
        findViewById(R.id.buttons).setVisibility(View.GONE);
        return true;
    }

    public void onButtonSaveChanges(View view) {
        Log.v("ChangingButton", "Saving changes for button" + buttonChangingId);
        findViewById(R.id.buttons).setVisibility(View.VISIBLE);
        currentView = 2;
        TextView buttonToChange = findViewById(buttonChangingId);
        TextView newName = findViewById(R.id.text_newcommandname);
        TextView newCommand = findViewById(R.id.text_newcommandmessage);
        Switch newSpam = findViewById(R.id.switchspam);
        if (!newName.getText().toString().equals(getString(R.string.unmapped)) && !newCommand.getText().toString().equals("")) {
            SharedPreferences.Editor editor = savedLayout.edit();
            CharSequence newText = newName.getText();
            buttonToChange.setText(newText);
            editor.putString("Text" + buttonChangingId, newText.toString());
            CharSequence newContent;
            if(newSpam.isChecked()){
                newContent = newCommand.getText() + "/1";
            }else{
                newContent = newCommand.getText() + "/0";
            }
            buttonToChange.setContentDescription(newContent);
            editor.putString("Command" + buttonChangingId, newContent.toString());
            editor.apply();
        }
        findViewById(R.id.buttonsmodifier).setVisibility(View.GONE);

    }

    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceconnection);
    }

    class ChangeViewChecker extends Thread {
        @Override
        public void run() {
            while (!checkLogged()) {
                try {
                    sleep(100);
                } catch (Exception e) {
                    Log.v("Waiting process", "interrupted", e);
                }
            }
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    setContentView(R.layout.activity_main);
                    currentView = 2;
                    int i;
                    Boolean end = false;
                    for (i = 0; !end; i++) {
                        try {
                            TextView currentButton = findViewById(R.id.button0 + i);
                            currentButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    onButtonClick(view);
                                }
                            });
                            currentButton.setOnLongClickListener(new View.OnLongClickListener() {
                                @Override
                                public boolean onLongClick(View view) {
                                    return onButtonLongClick(view);
                                }
                            });
                            currentButton.setText(savedLayout.getString(
                                    "Text" + currentButton.getId(), getString(R.string.unmapped)));
                            currentButton.setContentDescription(savedLayout.getString(
                                    "Command" + currentButton.getId(), "/0"));
                            /*Log.v("Main",
                                    "Both listeners has been set, " +
                                            "and button is now " + currentButton.getText() +
                                            " with command " + currentButton.getContentDescription());
                        */                              //Commented because of the spam created with
                        } catch (NullPointerException e) {
                            end = true;
                            Log.v("ButtonView", "All listeners has been set, " +
                                    "and all buttons are restored");
                        }
                    }
                    Toolbar toolbar = findViewById(R.id.toolbar_main);
                    setSupportActionBar(toolbar);
                    setTitle(R.string.app_name);
                    adview = findViewById(R.id.adView);
                    AdRequest.Builder adRequest = new AdRequest.Builder();
                    if (BuildConfig.DEBUG) {
                        adRequest.addTestDevice("3D0266CF596BA090B74E9D85DE74822E");
                        Log.i("Debug", "Screening ad");
                    }
                    adview.loadAd(adRequest.build());
                    adview.setAdListener(new AdListener(){
                        @Override
                        public void onAdClicked() {
                            Toast.makeText(MainActivity.this, getString(R.string.bigadthank), Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onAdFailedToLoad(int i) {
                            if(BuildConfig.DEBUG){
                                Log.w("little ad", "error : "+i);
                            }
                        }
                    });
                }
            });
        }
    }

    public void onBigAdShow(View view) {
         if (bigad.isLoaded()) {
            bigad.show();
        } else {
            Log.d("TAG", "The interstitial wasn't loaded yet.");
            AdRequest.Builder bigadrequest = new AdRequest.Builder();
             if (BuildConfig.DEBUG) {
                 bigadrequest.addTestDevice("3D0266CF596BA090B74E9D85DE74822E");
                 Log.i("Debug", "Screening ad");
             }
             bigad.loadAd(bigadrequest.build());
        }
    }
}

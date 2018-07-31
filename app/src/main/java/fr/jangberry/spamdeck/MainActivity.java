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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    static final String clientID = "6ndb4f2gou52g7zdmk32e89m4gk4iq";
    private static final String savedLayout_Location = "fr.jangberry.spamdeck.layout";
    private static final String SharedPreferencesLocation = "fr.jangberry.spamdeck";
    private static final String apiScopes = "chat_login";
    private String channel;
    private String token;
    private boolean changingChannel = false;
    private int buttonChangingId;
    private int currentView;    /*
                        0 = login (webview)
                        1 = choosing channel
                        2 = buttons view
                        3 = editing button view
                        */
    private SharedPreferences savedLayout;
    private SocketService socketservice;
    private SharedPreferences sharedPreferences;

    private final ServiceConnection serviceconnection = new ServiceConnection() {

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

    private Boolean checkLogged() {
        return socketservice.logged;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            switch (currentView) {
                case 0:                                       //0 = login (webview)
                    finish();
                    break;
                case 1:                                //1 = choosing channel
                    finish();
                    break;
                case 2:                                //2 = buttons view
                    setContentView(R.layout.activity_main_chosechannel);
                    currentView = 1;
                    Toolbar toolbar = findViewById(R.id.toolbar_channel);
                    setSupportActionBar(toolbar);
                    setTitle(R.string.chosenewchannel);
                    TextView channelfield = findViewById(R.id.channelField);
                    channelfield.setText(sharedPreferences.getString("Channel", null));
                    changingChannel = true;
                    resetRecentsChannels();
                    break;
                case 3:                                //3 = editing button view
                    findViewById(R.id.buttons).setVisibility(View.VISIBLE);
                    findViewById(R.id.buttonsmodifier).setVisibility(View.GONE);
                    currentView = 2;
                    break;
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
        sharedPreferences = this.getSharedPreferences(SharedPreferencesLocation, MODE_PRIVATE);
        currentView = 0;

        if (sharedPreferences.getBoolean("firststart", true)) {
            setContentView(R.layout.activity_main_firststart);
            Toolbar toolbar = findViewById(R.id.toolbar_firststart);
            setSupportActionBar(toolbar);
            setTitle(R.string.titlefirststart);
        } else {
            loadWebView(null);
        }
    }

    public void loadWebView(View view) {
        setContentView(R.layout.activity_main_login);
        Toolbar toolbar = findViewById(R.id.toolbar_login);
        setSupportActionBar(toolbar);
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
                if (BuildConfig.DEBUG) {
                    Log.v("URL", url);
                }
                if (url.contains("localhost/#")) {
                    if (BuildConfig.DEBUG) {
                        Log.d("TwitchLogin",
                                "Logged, recovering Token, connecting to chat and setting up button view...");
                    }
                    token = url.substring(url.indexOf("=") + 1, url.indexOf("&"));
                    if (BuildConfig.DEBUG) {
                        Log.v("Token", token);
                    }
                    setContentView(R.layout.activity_main_chosechannel);
                    currentView = 1;
                    Toolbar toolbar = findViewById(R.id.toolbar_channel);
                    setSupportActionBar(toolbar);
                    setTitle(R.string.chosechannel);
                    TextView channelfield = findViewById(R.id.channelField);
                    channelfield.setText(sharedPreferences.getString("Channel", null));
                    socketservice.socketConnect(token);
                    resetRecentsChannels();
                    new ChangeViewChecker(true).start();
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
                                    "<br>" +
                                    description +
                                    "</body>" +
                                    "</html>";
                    view.loadUrl("about:blank");
                    view.loadDataWithBaseURL(
                            null, htmlData, "text/html", "UTF-8", null);
                    view.invalidate();
                }
            }
        });
        if (null != view) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("firststart", false);
            editor.apply();
        }
    }

    public void onChannelChosen(View view) {
        EditText channelField = findViewById(R.id.channelField);
        channel = channelField.getText().toString();
        if (BuildConfig.DEBUG) {
            Log.v("Channel get", channel);
        }
        channel = channel.replace(" ", "");
        if (BuildConfig.DEBUG) {
            Log.v("Channel without spaces", channel);
        }
        channel = channel.toLowerCase();
        if (BuildConfig.DEBUG) {
            Log.d("Channel set", channel);
        }
        if (!channel.equals("")) {
            if (!changingChannel) {
                socketservice.setChannel(channel);
                new ChangeViewChecker(false).start();
            } else {
                socketservice.newChannel(channel);
                new ChangeViewChecker(false).start();
                changingChannel = false;
            }
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("Channel", channel);
            editor.apply();
        } else {
            findViewById(R.id.textviewchannelnamerror).setVisibility(View.VISIBLE);
        }
    }

    public void onButtonClick(View view) {
        if (BuildConfig.DEBUG) {
            Log.v("Main",
                    "Button" + view.getId() +
                            " with command " + view.getContentDescription().toString() +
                            " short pressed");
        }
        if (!view.getContentDescription().toString()
                .substring(0, view.getContentDescription().toString().lastIndexOf("/"))
                .equals("")) {
            String temp = view.getContentDescription().toString();
            socketservice.send(temp.substring(0, temp.lastIndexOf("/")),
                    temp.substring(temp.lastIndexOf("/") + 1).equals("1"));
        } else {
            if (BuildConfig.DEBUG) {
                Log.d("Main", "Button unmapped");
            }
            Toast.makeText(this, R.string.unmapped, Toast.LENGTH_SHORT).show();
        }
    }

    public Boolean onButtonLongClick(View view) {
        if (BuildConfig.DEBUG) {
            Log.v("Main",
                    "Button" + view.getId() +
                            " with command " + view.getContentDescription().toString() +
                            " long pressed");
        }
        findViewById(R.id.buttons).setVisibility(View.GONE);
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
        return true;
    }

    public void onButtonSaveChanges(View view) {
        if (BuildConfig.DEBUG) {
            Log.d("ChangingButton", "Saving changes for button" + buttonChangingId);
        }
        findViewById(R.id.buttonsmodifier).setVisibility(View.GONE);
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
            if (newSpam.isChecked()) {
                newContent = newCommand.getText() + "/1";
            } else {
                newContent = newCommand.getText() + "/0";
            }
            buttonToChange.setContentDescription(newContent);
            editor.putString("Command" + buttonChangingId, newContent.toString());
            editor.apply();
        }

    }

    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceconnection);
    }

    private void resetRecentsChannels() {
        final TextView channelField = findViewById(R.id.channelField);
        for (int i = 5; i >= 0; i--) {
            if (sharedPreferences.getString("RecentChannel" + i, null) != null) {
                Button button = new Button(this);
                final String name = sharedPreferences.getString("RecentChannel" + i, null);
                button.setText(name);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        channelField.setText(name);
                    }
                });
                if (BuildConfig.DEBUG) {
                    Log.v("Reset Recents Channels", "Added " + name + " channel");
                }
                LinearLayout container = findViewById(R.id.lastchannelscontainer);
                container.addView(button);
            }
            if (BuildConfig.DEBUG) {
                Log.v("loaded", "" + i + sharedPreferences.getString("RecentChannel" + (i), null));
            }
        }
    }

    class ChangeViewChecker extends Thread {
        boolean oneTime = false;

        ChangeViewChecker(boolean oneTime) {
            if (oneTime) {
                this.oneTime = true;
            }
        }

        @Override
        public void run() {
            if (channel != null && !channel.equals("")) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                int j;
                for (j = 0; j < 5; j++) {
                    if (sharedPreferences.getString("RecentChannel" + (j + 1), null) != null) {
                        if (!sharedPreferences.getString("RecentChannel" + 5, "").equals(channel)) {
                            if (BuildConfig.DEBUG) {
                                Log.v("saves", "" + j + sharedPreferences.getString("RecentChannel" + (j + 1), null));
                            }
                            editor.putString("RecentChannel" + j, sharedPreferences.getString("RecentChannel" + (j + 1), null));
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Log.v("a", "" + j + sharedPreferences.getString("RecentChannel" + (j), null));
                    }
                }
                if (BuildConfig.DEBUG) {
                    Log.v("last", "" + j);
                }
                editor.putString("RecentChannel" + j, channel);
                editor.apply();
            }
            if (!oneTime) {
                while (!checkLogged()) {
                    try {
                        sleep(100);
                    } catch (Exception e) {
                        if (BuildConfig.DEBUG) {
                            Log.i("Waiting process", "interrupted", e);
                        }
                    }
                }
            }
            if (checkLogged()) {
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
                                if (BuildConfig.DEBUG) {
                                    Log.v("Main",
                                            "Both listeners has been set, " +
                                                    "and button is now " + currentButton.getText() +
                                                    " with command " + currentButton.getContentDescription());
                                }
                            } catch (NullPointerException e) {
                                end = true;
                                if (BuildConfig.DEBUG) {
                                    Log.d("ButtonView", "All listeners has been set, " +
                                            "and all buttons are restored");
                                }
                            }
                        }
                        Toolbar toolbar = findViewById(R.id.toolbar_main);
                        setSupportActionBar(toolbar);
                        setTitle(socketservice.channel);
                    }
                });
            }
        }
    }
}

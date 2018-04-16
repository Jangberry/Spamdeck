package fr.jangberry.spamdeck;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    String channel;
    String token;
    protected static final String clientID = "6ndb4f2gou52g7zdmk32e89m4gk4iq";
    private static final String savedLayout_Location = "fr.jangberry.spamdeck.layout";
    private SharedPreferences savedLayout;
    private static final String apiScopes = "chat_login";
    private SocketService socketservice;
    int buttonChangingId;

    Boolean checkLogged() {
        return socketservice.logged;
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
                                    "Command" + currentButton.getId(), ""));
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
                }
            });
        }
    }

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startService(new Intent(this, SocketService.class));
        bindService(new Intent(this, SocketService.class),
                serviceconnection,
                Context.BIND_AUTO_CREATE);
        savedLayout = this.getSharedPreferences(savedLayout_Location, MODE_PRIVATE);
        setContentView(R.layout.activity_main_login);
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
                    socketservice.socketConnect(token);
                    new ChangeViewChecker().start();
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
    }

    public void onChannelChosen(View view) {
        /*if(channel.equals("custom")){*/
        EditText channelField = findViewById(R.id.channelField);
        channel = channelField.getText().toString();
        //}
        if (!channel.equals("")){
            socketservice.setChannel(channel);
            findViewById(R.id.channelloading).setVisibility(View.VISIBLE);
            findViewById(R.id.channeltexture).setVisibility(View.VISIBLE);
        } else {

        }
    }

    public void onButtonClick(View view) {
        Log.v("Main",
                "Button" + view.getId() +
                        " with command " + view.getContentDescription().toString() +
                        " short pressed");
        if (!view.getContentDescription().toString().equals("")) {
            socketservice.send(view.getContentDescription().toString());
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
        TextView name = findViewById(R.id.text_newcommandname);
        TextView old = findViewById(view.getId());
        TextView message = findViewById(R.id.text_newcommandmessage);
        name.setText(old.getText());
        message.setText(old.getContentDescription());
        buttonChangingId = view.getId();
        findViewById(R.id.buttons).setVisibility(View.GONE);
        return true;
    }

    public void onButtonSaveChanges(View view) {
        Log.v("ChangingButton", "Saving changes for button" + buttonChangingId);
        findViewById(R.id.buttons).setVisibility(View.VISIBLE);
        SharedPreferences.Editor editor = savedLayout.edit();
        TextView buttonToChange = findViewById(buttonChangingId);
        TextView newName = findViewById(R.id.text_newcommandname);
        TextView newCommand = findViewById(R.id.text_newcommandmessage);
        CharSequence newText = newName.getText();
        buttonToChange.setText(newText);
        editor.putString("Text" + buttonChangingId, newText.toString());
        CharSequence newContent = newCommand.getText();
        buttonToChange.setContentDescription(newContent);
        editor.putString("Command" + buttonChangingId, newContent.toString());
        editor.apply();
        findViewById(R.id.buttonsmodifier).setVisibility(View.GONE);
    }

    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceconnection);
    }
}

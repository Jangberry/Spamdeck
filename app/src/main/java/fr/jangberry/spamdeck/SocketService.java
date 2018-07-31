package fr.jangberry.spamdeck;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.studioidan.httpagent.HttpAgent;
import com.studioidan.httpagent.JsonCallback;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;

public class SocketService extends Service {
    private final IBinder mBinder = new LocalBinder();
    public Boolean logged = false;
    private Socket socket = new Socket();
    private BufferedReader in;
    private PrintWriter out;
    String username;
    String channel;
    private Boolean spam = false;
    private final ArrayList sendQueue = new ArrayList();

    public SocketService() {
    }

    void setChannel(String incoming) {
        channel = incoming;
    }

    private void setUsername(String incoming) {
        username = incoming;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void send(String message, Boolean spamable) {
        if (spamable && spam) {
            sendQueue.add("PRIVMSG #" + channel + " :" + message.substring(0, message.lastIndexOf(" ")) + "\r\n");
        } else {
            sendQueue.add("PRIVMSG #" + channel + " :" + message + "\r\n");
        }
        spam = !spam;
    }

    class SendQueueTreatment extends Thread {
        public void run() {
            while (socket.isConnected()) {
                try {
                    if (!sendQueue.isEmpty()) {
                        new SendThread(sendQueue.get(0).toString()).start();
                        sendQueue.remove(0);
                        sleep(1000);
                    } else {
                        sleep(50);
                    }
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void onRecv(String message) {
        //Log.v("MSG recu", message);                   //Commented because of the spam created with
        if(BuildConfig.DEBUG) {
            Log.v("MSG formatted", message.substring(1, message.indexOf("!")) + message.substring(message.indexOf(":", 1)));
        }
            /*                                  _________/  _________________/                                            __/
                                               |           |                                                             |
            Typical twitch incoming message    :nrandolph99!nrandolph99@nrandolph99.tmi.twitch.tv PRIVMSG #katjawastaken :yeah
            where:                                  /\          /\          /\                                  /\          /\
                                                Both of them are the sender's name                              ||     And this is the message (can be any length)
                                                                                                    This is channel name
            */
    }

    public void socketConnect(String token) {
        new ConnectThread(token).start();
    }

    public void onDestroy() {
        super.onDestroy();
        try {
            socket.close();
            if(BuildConfig.DEBUG) {
                Log.i("SocketService", "Socket closed");
            }
        } catch (IOException e) {
            if(BuildConfig.DEBUG) {
                Log.e("e", "IO", e);
            }
        } catch (NullPointerException e) {
            if(BuildConfig.DEBUG) {
                Log.i("SocketService", "Socket not opened");
            }
        }

    }

    public class LocalBinder extends Binder {
        SocketService getService() {
            return SocketService.this;
        }
    }

    class SendThread extends Thread {
        /*
         *   Usage
         *
         *   new SendThread(String).start();
         */
        final String message;
        SendThread(String message) {
            this.message = message;
        }
        public void run() {
            out.println(message);
            if(BuildConfig.DEBUG) {
                Log.v("SendThread", "Sent>" + message);
            }
        }
    }

    class RecvThread extends Thread {
        /*
         *  Usage:
         *
         *  Just launch the thread : there is an infinity loop that receive messages, and call
         *  the onRecv
         */
        public void run() {
            Boolean keepReceiving = true;
            while (keepReceiving) {
                try {
                    while (socket.isConnected()) {
                        String recv = null;
                        while (recv == null && socket.isConnected()) {
                            if (!keepReceiving) {
                                sleep(1000);
                            }
                            recv = in.readLine();
                        }
                        try {
                            if (!recv.equals("") && !recv.substring(0, 4).equals("PING")) {
                                onRecv(recv);
                            } else if (recv.substring(0, 4).equals("PING")) {
                                new SendThread("PONG").start();
                                if(BuildConfig.DEBUG) {
                                    Log.d("RecvThread", "PING-PONG");
                                }
                            } else {
                                if(BuildConfig.DEBUG) {
                                    Log.i("MSG", recv);
                                }
                            }
                        } catch (StringIndexOutOfBoundsException ignored) {
                        }
                    }
                } catch (java.net.SocketException e) {
                    keepReceiving = false;
                    if(BuildConfig.DEBUG) {
                        Log.e("RecvThread", "Error", e);
                    }
                } catch (java.io.IOException e) {
                    if(BuildConfig.DEBUG) {
                        Log.e("RecvThread", "Error", e);
                    }
                } catch (java.lang.InterruptedException e) {
                    if(BuildConfig.DEBUG) {
                        Log.wtf("Receving", "Thread", e);
                    }
                }
            }
        }
    }

    public void newChannel(String channel) {
        logged = false;
        new NewChannel(channel).start();
    }

    class NewChannel extends Thread {
        final String newChannel;

        NewChannel(String newChannel) {
            this.newChannel = newChannel;
        }

        public void run() {
            if (!newChannel.equals(channel)) {
                sendQueue.clear();
                out.println("PART #" + channel);
                channel = newChannel;
                out.println("JOIN #" + newChannel);
            }
            logged = true;
        }
    }

    class ConnectThread extends Thread {
        final String token;

        ConnectThread(String token) {
            this.token = token;
        }

        @Override
        public void run() {
            if (!logged && !socket.isConnected()) {
                try {
                    if (!socket.isConnected()) {
                        SocketAddress socaddrs = new InetSocketAddress(
                                "irc.chat.twitch.tv", 6667);
                        if(BuildConfig.DEBUG) {
                            Log.d("SocketService", "Trying to connect");
                        }
                        socket.connect(socaddrs, 5000);
                        if (socket.isConnected()) {
                            out = new PrintWriter(socket.getOutputStream(), true);
                            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                            if(BuildConfig.DEBUG) {
                                Log.d("SocketService", "Socket connected");
                            }
                            new RecvThread().start();
                            String url = "https://api.twitch.tv/kraken";
                            HttpAgent.get(url)
                                    .headers("client-id", MainActivity.clientID,
                                            "Authorization", "OAuth " + token,
                                            "Accept", "application/vnd.twitchtv.v5+json")
                                    .goJson(new JsonCallback() {
                                        @Override
                                        protected void onDone(boolean success, JSONObject jsonResults) {
                                            if (success) {
                                                if(BuildConfig.DEBUG) {
                                                    Log.v("HTTP", "Success :" + jsonResults.toString());
                                                }
                                                try {
                                                    setUsername(jsonResults.getJSONObject("token")
                                                            .get("user_name")
                                                            .toString());
                                                } catch (org.json.JSONException ignored) {}
                                            } else {
                                                if(BuildConfig.DEBUG) {
                                                    Log.e("HTTP", "error");
                                                }
                                            }
                                        }
                                    });
                            out.println("PASS oauth:" + token);
                            while (username == null) {
                                sleep(10);
                            }
                            out.println("NICK " + username);
                            if(BuildConfig.DEBUG) {
                                Log.v("SocketService", "Username get ! It's " +
                                        username + " and you're now logged with");
                            }
                        }
                    }
                    while (channel == null) {
                        sleep(250);
                    }
                    out.println("JOIN #" + channel);
                    logged = true;
                    if(BuildConfig.DEBUG) {
                        Log.i("SocketService",
                                "Now logged with username " + username +
                                        " to channel " + channel);
                    }
                    new SendQueueTreatment().start();

                } catch (IOException e) {
                    if(BuildConfig.DEBUG) {
                        Log.e("e", "IOErreur", e);
                    }
                } catch (Exception e) {
                    if(BuildConfig.DEBUG) {
                        Log.e("Socket service", "", e);
                    }
                }
            } else {
                if(BuildConfig.DEBUG) {
                    Log.d("SocketService", "Already logged");
                }
            }
        }
    }
}

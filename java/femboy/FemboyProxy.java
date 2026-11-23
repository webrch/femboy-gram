package org.telegram.femboy;

import android.app.Activity;
import android.content.Context;
import android.telephony.TelephonyManager;
import android.widget.CheckBox;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.utils.proxy.ProxySettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Телеграм без VPN там, где его режут: свой прокси прямо в телефоне.
 *
 * Обычный телеграм ходит к своим серверам по их адресам, и эти адреса режут.
 * Веб-версия ходит иначе — через WebSocket на *.web.telegram.org, и этот путь
 * живёт дольше. Здесь одно склеено с другим: на 127.0.0.1 поднимается
 * MTProto-прокси, приложение подключается к нему как к любому прокси с
 * секретом, а он перекладывает тот же поток в WebSocket веб-версии.
 *
 * Чужим он не нужен и не открыт: слушает только 127.0.0.1, и соединение без
 * нашего секрета закрывается на первом же заголовке.
 */
public class FemboyProxy {

    /** Имена дата-центров у веб-шлюза, по порядку номеров с первого. */
    private static final String[] PLANETS = {"pluto", "venus", "aurora", "vesta", "flora"};

    private static ServerSocket server;
    private static Thread accepting;
    private static volatile int port;
    private static volatile boolean working;

    public static synchronized boolean running() {
        return working;
    }

    public static synchronized int port() {
        return port;
    }

    /** Секрет прокси: один на установку, лежит в настройках. */
    public static synchronized byte[] secret() {
        String saved = FemboyConfig.proxySecret();
        if (saved != null && saved.length() == 32) {
            byte[] secret = new byte[16];
            for (int i = 0; i < 16; i++) {
                secret[i] = (byte) Integer.parseInt(saved.substring(i * 2, i * 2 + 2), 16);
            }
            return secret;
        }
        byte[] secret = FemboyObfuscation.newSecret();
        FemboyConfig.setProxySecret(FemboyObfuscation.hex(secret));
        return secret;
    }

    public static synchronized boolean start() {
        if (working) {
            return true;
        }
        try {
            // порт выбирает система: занятый кем-то другим нам не нужен
            server = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
            port = server.getLocalPort();
            working = true;
            accepting = new Thread(FemboyProxy::accept, "femboy-proxy");
            accepting.setDaemon(true);
            accepting.start();
            FemboyPluginHost.log("прокси", "поднят на 127.0.0.1:" + port, false);
            return true;
        } catch (Throwable t) {
            FileLog.e(t);
            working = false;
            return false;
        }
    }

    public static synchronized void stop() {
        working = false;
        try {
            if (server != null) {
                server.close();
            }
        } catch (IOException ignored) {
        }
        server = null;
        port = 0;
    }

    /** Включить или выключить: и сам прокси, и настройку прокси в телеграме. */
    public static void enable(boolean on) {
        if (on) {
            if (!start()) {
                FemboyConfig.setProxyEnabled(false);
                return;
            }
            FemboyConfig.setProxyEnabled(true);
            // для телеграма это обычный MTProto-прокси с секретом, только на 127.0.0.1
            ConnectionsManager.setProxySettings(true, ProxySettings.builder()
                    .setType(ProxySettings.Type.MTPROTO)
                    .setAddress("127.0.0.1")
                    .setPort(port())
                    .setSecret(FemboyObfuscation.hex(secret()))
                    .build());
        } else {
            FemboyConfig.setProxyEnabled(false);
            ConnectionsManager.setProxySettings(false, null);
            stop();
        }
    }

    /**
     * Предложить прокси, если телеграм никак не подключится.
     *
     * Ждём 12 секунд и смотрим ещё раз: короткое «соединение…» бывает у всех, а
     * висящее дольше — признак того, что путь режут. Спрашиваем только в
     * России и только однажды, если человек попросил больше не спрашивать.
     */
    public static void offerIfStuck(Activity activity, int account) {
        if (true) {
            return;     // прокси убран
        }
        if (activity == null || working || FemboyConfig.proxyEnabled() || FemboyConfig.proxyAsked() || !inRussia(activity)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (state != ConnectionsManager.ConnectionStateConnecting && state != ConnectionsManager.ConnectionStateWaitingForNetwork) {
                return;
            }
            if (FemboyConfig.proxyAsked() || FemboyConfig.proxyEnabled()) {
                return;
            }
            ask(activity);
        }, 12000);
    }

    private static void ask(Activity activity) {
        CheckBox dontAsk = new CheckBox(activity);
        dontAsk.setText(LocaleController.getString(R.string.FemboyProxyDontAsk));
        dontAsk.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        int pad = AndroidUtilities.dp(22);
        dontAsk.setPadding(pad, 0, pad, 0);
        new AlertDialog.Builder(activity)
                .setTitle(LocaleController.getString(R.string.FemboyProxyOfferTitle))
                .setMessage(LocaleController.getString(R.string.FemboyProxyOffer))
                .setView(dontAsk)
                .setPositiveButton(LocaleController.getString(R.string.FemboyProxyTurnOn), (dialog, which) -> {
                    FemboyConfig.setProxyAsked(dontAsk.isChecked());
                    enable(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) ->
                        FemboyConfig.setProxyAsked(dontAsk.isChecked()))
                .show();
    }

    /** Российская SIM-карта или сеть: только там предлагать стоит. */
    private static boolean inRussia(Context context) {
        try {
            TelephonyManager phone = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (phone == null) {
                return false;
            }
            return "ru".equalsIgnoreCase(phone.getSimCountryIso()) || "ru".equalsIgnoreCase(phone.getNetworkCountryIso());
        } catch (Throwable t) {
            return false;
        }
    }

    private static void accept() {
        while (working) {
            try {
                Socket client = server.accept();
                Thread link = new Thread(() -> serve(client), "femboy-proxy-link");
                link.setDaemon(true);
                link.start();
            } catch (IOException e) {
                // закрытый сервер — это stop(), а не ошибка
                if (working) {
                    FileLog.e(e);
                }
                return;
            }
        }
    }

    /** Одно соединение телеграма: заголовок, потом два потока в обе стороны. */
    private static void serve(Socket client) {
        FemboySocket ws = null;
        try {
            client.setTcpNoDelay(true);
            InputStream fromClient = client.getInputStream();
            OutputStream toClient = client.getOutputStream();
            byte[] header = new byte[FemboyObfuscation.HEAD];
            readFully(fromClient, header);
            FemboyObfuscation.Head head = FemboyObfuscation.read(header, secret());
            if (head == null || !head.valid()) {
                FemboyPluginHost.log("прокси", "чужое соединение отклонено", true);
                client.close();
                return;
            }
            int dc = head.plainDatacenter();
            if (dc < 1 || dc > PLANETS.length) {
                FemboyPluginHost.log("прокси", "неизвестный дата-центр: " + dc, true);
                client.close();
                return;
            }
            boolean test = Math.abs(head.datacenter) >= 10000;
            ws = FemboySocket.open(PLANETS[dc - 1] + ".web.telegram.org", test ? "/apiws_test" : "/apiws", 15000);
            Object[] created = FemboyObfuscation.create(head.tag);
            ws.send((byte[]) created[0], 0, FemboyObfuscation.HEAD);
            FemboyObfuscation.Stream toServer = (FemboyObfuscation.Stream) created[1];
            FemboyObfuscation.Stream fromServer = (FemboyObfuscation.Stream) created[2];

            FemboySocket server = ws;
            Thread back = new Thread(() -> {
                try {
                    byte[] data;
                    while ((data = server.receive()) != null) {
                        toClient.write(head.outgoing.run(fromServer.run(data)));
                        toClient.flush();
                    }
                } catch (Throwable ignored) {
                }
                close(client);
                server.close();
            }, "femboy-proxy-back");
            back.setDaemon(true);
            back.start();

            byte[] buffer = new byte[16384];
            int got;
            while ((got = fromClient.read(buffer)) >= 0) {
                byte[] data = toServer.run(head.incoming.run(buffer, 0, got));
                ws.send(data, 0, data.length);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            close(client);
            if (ws != null) {
                ws.close();
            }
        }
    }

    private static void readFully(InputStream in, byte[] into) throws IOException {
        int done = 0;
        while (done < into.length) {
            int got = in.read(into, done, into.length - done);
            if (got < 0) {
                throw new IOException("клиент закрылся на заголовке");
            }
            done += got;
        }
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}

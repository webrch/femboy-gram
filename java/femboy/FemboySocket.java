package org.telegram.femboy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.net.ssl.SSLSocketFactory;

/**
 * Самый маленький WebSocket, который нужен прокси: только двоичные кадры,
 * только клиентская сторона, поверх TLS.
 *
 * Библиотеку под это не тянем: протокол умещается на одном экране, а
 * зависимость в приложении, которое ставят мимо магазина, — лишний вес и
 * лишнее место, где может сломаться.
 */
public class FemboySocket {

    /** Строка из RFC 6455, которой сервер подтверждает рукопожатие. */
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final SecureRandom random = new SecureRandom();

    private FemboySocket(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    /** Открыть wss://host/path; timeout — и на соединение, и на рукопожатие. */
    public static FemboySocket open(String host, String path, int timeout) throws IOException {
        Socket socket = SSLSocketFactory.getDefault().createSocket();
        socket.connect(new InetSocketAddress(host, 443), timeout);
        socket.setSoTimeout(timeout);
        FemboySocket ws = new FemboySocket(socket);
        ws.handshake(host, path);
        // дальше соединение может молчать сколько угодно: это решает телеграм
        socket.setSoTimeout(0);
        return ws;
    }

    private void handshake(String host, String path) throws IOException {
        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = Base64.getEncoder().encodeToString(nonce);
        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + key + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n"
                + "Sec-WebSocket-Protocol: binary\r\n"
                + "Origin: https://" + host + "\r\n"
                + "\r\n";
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();
        String headers = readHeaders();
        if (!headers.startsWith("HTTP/1.1 101")) {
            throw new IOException("сервер не поднял websocket: " + headers.split("\r\n")[0]);
        }
        String accept = accept(key);
        if (!headers.toLowerCase().contains("sec-websocket-accept: " + accept.toLowerCase())) {
            throw new IOException("сервер ответил чужим ключом");
        }
    }

    public static String accept(String key) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            sha.update((key + MAGIC).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(sha.digest());
        } catch (Exception e) {
            throw new IllegalStateException("нет SHA-1", e);
        }
    }

    /** Заголовки ответа до пустой строки, не больше 8 КБ. */
    private String readHeaders() throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int last = 0;
        while (head.size() < 8192) {
            int c = in.read();
            if (c < 0) {
                throw new IOException("соединение закрылось на рукопожатии");
            }
            head.write(c);
            if (c == '\n' && last == '\n') {
                break;
            }
            if (c != '\r') {
                last = c;
            }
        }
        return new String(head.toByteArray(), StandardCharsets.US_ASCII);
    }

    public synchronized void send(byte[] data, int offset, int length) throws IOException {
        out.write(frame(data, offset, length, random));
        out.flush();
    }

    /** Двоичный кадр клиента: FIN, опкод 2, маска обязательна. */
    public static byte[] frame(byte[] data, int offset, int length, SecureRandom random) {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(length + 14);
        frame.write(0x82);
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        if (length < 126) {
            frame.write(0x80 | length);
        } else if (length < 65536) {
            frame.write(0x80 | 126);
            frame.write((length >> 8) & 0xff);
            frame.write(length & 0xff);
        } else {
            frame.write(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.write((int) (((long) length >> shift) & 0xff));
            }
        }
        frame.write(mask, 0, 4);
        for (int i = 0; i < length; i++) {
            frame.write((data[offset + i] ^ mask[i % 4]) & 0xff);
        }
        return frame.toByteArray();
    }

    /**
     * Следующий кадр с данными. На пинг отвечаем сами, понги пропускаем.
     *
     * @return данные или null, если соединение кончилось
     */
    public byte[] receive() throws IOException {
        while (true) {
            int first = in.read();
            if (first < 0) {
                return null;
            }
            int opcode = first & 0x0f;
            int second = in.read();
            if (second < 0) {
                return null;
            }
            boolean masked = (second & 0x80) != 0;
            long length = second & 0x7f;
            if (length == 126) {
                length = (read() << 8) | read();
            } else if (length == 127) {
                length = 0;
                for (int i = 0; i < 8; i++) {
                    length = (length << 8) | read();
                }
            }
            byte[] mask = new byte[4];
            if (masked) {
                readFully(mask, 4);
            }
            int size = (int) length;
            byte[] payload = new byte[size];
            readFully(payload, size);
            if (masked) {
                for (int i = 0; i < size; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }
            if (opcode == 8) {
                throw new IOException("сервер закрыл websocket");
            }
            if (opcode == 9) {
                sendControl(10, payload);
                continue;
            }
            if (opcode == 10) {
                continue;
            }
            return payload;
        }
    }

    private synchronized void sendControl(int opcode, byte[] payload) throws IOException {
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);
        frame.write(0x80 | payload.length);
        frame.write(mask, 0, 4);
        for (int i = 0; i < payload.length; i++) {
            frame.write((payload[i] ^ mask[i % 4]) & 0xff);
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    private int read() throws IOException {
        int c = in.read();
        if (c < 0) {
            throw new IOException("поток кончился посреди кадра");
        }
        return c;
    }

    private void readFully(byte[] into, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int got = in.read(into, done, length - done);
            if (got < 0) {
                throw new IOException("поток кончился посреди кадра");
            }
            done += got;
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}

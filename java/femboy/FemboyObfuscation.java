package org.telegram.femboy;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Обфускация MTProto, та самая «obfuscated2», в которую телеграм заворачивает
 * соединение с прокси и с веб-шлюзом.
 *
 * Соединение начинается с 64 случайных байт. Из них же выводятся ключи AES-CTR
 * на оба направления: прямые байты 8..56 дают поток «туда», они же задом
 * наперёд — поток «обратно». Последние 8 байт заголовка зашифрованы и
 * говорят, каким протоколом пойдут пакеты и в какой дата-центр.
 *
 * У прокси к ключу ещё подмешан секрет: SHA-256(ключ ‖ секрет). Поэтому
 * чужой, кто не знает секрета, не разберёт и заголовок, а мы отличим своё
 * соединение от чужого.
 */
public class FemboyObfuscation {

    /** Длина заголовка, с которого начинается каждое соединение. */
    public static final int HEAD = 64;

    /** Протоколы пакетов, которые телеграм пишет в хвост заголовка. */
    private static final int TAG_ABRIDGED = 0xefefefef;
    private static final int TAG_INTERMEDIATE = 0xeeeeeeee;
    private static final int TAG_PADDED = 0xdddddddd;

    /** Поток AES-CTR: шифрование и расшифровка у него одно и то же действие. */
    public static final class Stream {
        private final Cipher cipher;

        Stream(byte[] key, byte[] iv) {
            try {
                cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            } catch (Exception e) {
                throw new IllegalStateException("нет AES/CTR", e);
            }
        }

        public byte[] run(byte[] data, int offset, int length) {
            return cipher.update(data, offset, length);
        }

        public byte[] run(byte[] data) {
            return run(data, 0, data.length);
        }
    }

    /** Разобранный заголовок от клиента. */
    public static final class Head {
        /** Расшифровывает то, что клиент шлёт нам. */
        public final Stream incoming;
        /** Шифрует то, что мы шлём клиенту. */
        public final Stream outgoing;
        /** Номер дата-центра; минус — медиа, +10000 — тестовые серверы. */
        public final int datacenter;
        public final int tag;

        Head(Stream incoming, Stream outgoing, int datacenter, int tag) {
            this.incoming = incoming;
            this.outgoing = outgoing;
            this.datacenter = datacenter;
            this.tag = tag;
        }

        /** Заголовок расшифровался осмысленно — значит, секрет совпал. */
        public boolean valid() {
            return tag == TAG_ABRIDGED || tag == TAG_INTERMEDIATE || tag == TAG_PADDED;
        }

        public int plainDatacenter() {
            int dc = Math.abs(datacenter);
            return dc >= 10000 ? dc - 10000 : dc;
        }

        public boolean media() {
            return datacenter < 0;
        }
    }

    /** Ключ и вектор из 48 байт заголовка; с секретом ключ ещё и подмешивается. */
    private static byte[][] derive(byte[] material, byte[] secret) {
        byte[] key = new byte[32];
        byte[] iv = new byte[16];
        System.arraycopy(material, 0, key, 0, 32);
        System.arraycopy(material, 32, iv, 0, 16);
        if (secret != null && secret.length > 0) {
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                sha.update(key);
                sha.update(secret, 0, Math.min(16, secret.length));
                System.arraycopy(sha.digest(), 0, key, 0, 32);
            } catch (Exception e) {
                throw new IllegalStateException("нет SHA-256", e);
            }
        }
        return new byte[][]{key, iv};
    }

    /**
     * Разобрать заголовок, который прислал клиент.
     *
     * @return null, если байт меньше заголовка; иначе Head, и тогда его valid()
     * скажет, наш ли это клиент.
     */
    public static Head read(byte[] head, byte[] secret) {
        if (head == null || head.length < HEAD) {
            return null;
        }
        byte[] forward = new byte[48];
        System.arraycopy(head, 8, forward, 0, 48);
        byte[][] in = derive(forward, secret);
        byte[] backward = new byte[48];
        for (int i = 0; i < 48; i++) {
            backward[i] = head[55 - i];
        }
        byte[][] out = derive(backward, secret);
        Stream incoming = new Stream(in[0], in[1]);
        Stream outgoing = new Stream(out[0], out[1]);
        byte[] plain = incoming.run(head, 0, HEAD);
        int datacenter = (short) (((plain[61] & 0xff) << 8) | (plain[60] & 0xff));
        return new Head(incoming, outgoing, datacenter, int32(plain, 56));
    }

    /**
     * Новый заголовок для соединения с сервером, без секрета.
     *
     * Первые байты не должны походить ни на HTTP, ни на TLS, ни на голый
     * протокол телеграма — иначе сервер примет соединение за что-то другое.
     *
     * @return {заголовок, поток «туда», поток «обратно»}
     */
    public static Object[] create(int tag) {
        SecureRandom random = new SecureRandom();
        byte[] head = new byte[HEAD];
        while (true) {
            random.nextBytes(head);
            if ((head[0] & 0xff) == 0xef) {
                continue;
            }
            int first = int32(head, 0);
            int second = int32(head, 4);
            if (first == 0x44414548 || first == 0x54534f50 || first == 0x20544547 || first == 0x4954504f
                    || first == TAG_INTERMEDIATE || first == TAG_PADDED || first == 0x02010316 || second == 0) {
                continue;
            }
            break;
        }
        int mark = tag == TAG_INTERMEDIATE ? 0xee : tag == TAG_PADDED ? 0xdd : 0xef;
        head[56] = head[57] = head[58] = head[59] = (byte) mark;

        byte[] forward = new byte[48];
        System.arraycopy(head, 8, forward, 0, 48);
        byte[][] up = derive(forward, null);
        byte[] backward = new byte[48];
        for (int i = 0; i < 48; i++) {
            backward[i] = head[55 - i];
        }
        byte[][] down = derive(backward, null);
        Stream sending = new Stream(up[0], up[1]);
        Stream receiving = new Stream(down[0], down[1]);
        // хвост заголовка уходит зашифрованным, остальное как было
        byte[] encrypted = sending.run(head, 0, HEAD);
        byte[] result = new byte[HEAD];
        System.arraycopy(head, 0, result, 0, 56);
        System.arraycopy(encrypted, 56, result, 56, 8);
        return new Object[]{result, sending, receiving};
    }

    private static int int32(byte[] b, int at) {
        return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8) | ((b[at + 2] & 0xff) << 16) | ((b[at + 3] & 0xff) << 24);
    }

    public static byte[] newSecret() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    public static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xf, 16));
            out.append(Character.forDigit(b & 0xf, 16));
        }
        return out.toString();
    }
}

package org.telegram.femboy;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Отправка из плагинов: текст, фото, видео, файлы, правка. У телеграма у
 * каждого из этих методов по десятку перегрузок с двумя десятками доводов, и
 * из питона в них легко промахнуться. Здесь по одной короткой двери на дело.
 */
public final class FemboySend {

    private FemboySend() {}

    private static AccountInstance account(int account) {
        return AccountInstance.getInstance(account);
    }

    public static void text(int account, long peer, String text, ArrayList<TLRPC.MessageEntity> entities,
                            MessageObject replyTo, MessageObject replyTop, boolean notify, int scheduleDate) {
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                text, peer, replyTo, replyTop, null, true, entities, null, null, notify, scheduleDate, 0, null, false);
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(params));
    }

    public static void params(int account, SendMessagesHelper.SendMessageParams params) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(account).sendMessage(params));
    }

    public static void document(int account, long peer, String path, String caption, MessageObject replyTo, MessageObject replyTop) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingDocument(account(account), path, path, null,
                caption, null, peer, replyTo, replyTop, null, null, null, true, 0, null,
                SendMessageChatArguments.EMPTY, false));
    }

    public static void photo(int account, long peer, String path, String caption, MessageObject replyTo, MessageObject replyTop) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingPhoto(account(account), path, null, peer,
                replyTo, replyTop, null, caption, null, null, null, 0, null, true, 0, 0,
                SendMessageChatArguments.EMPTY));
    }

    public static void video(int account, long peer, String path, String caption, MessageObject replyTo, MessageObject replyTop) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.prepareSendingVideo(account(account), path, null, null, null,
                peer, replyTo, replyTop, null, null, null, 0, null, true, 0, 0, false, false, caption,
                SendMessageChatArguments.EMPTY, 0, 0));
    }

    public static void edit(MessageObject message, String text, ArrayList<TLRPC.MessageEntity> entities) {
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(message.currentAccount)
                .editMessage(message, text, true, null, entities, 0, 0));
    }

    /**
     * Отправить файл из личной папки приложения. Телеграм такие пути молча
     * отклоняет (защита от утечки своих данных), поэтому сначала копия во
     * внешний кэш под понятным именем, потом обычная отправка.
     */
    public static void privateFile(int account, long peer, java.io.File source, String name, String caption) {
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            final java.io.File copy = publicCopy(source, name);
            if (copy != null) document(account, peer, copy.getAbsolutePath(), caption, null, null);
        });
    }

    /** Копия во внешнем кэше: оттуда телеграм отправляет, а другие приложения могут открыть. Не с главного потока. */
    public static java.io.File publicCopy(java.io.File source, String name) {
        try {
            java.io.File base = org.telegram.messenger.ApplicationLoader.applicationContext.getExternalCacheDir();
            if (base == null) base = org.telegram.messenger.FileLoader.getDirectory(org.telegram.messenger.FileLoader.MEDIA_DIR_CACHE);
            final java.io.File dir = new java.io.File(base, "femboy_send");
            dir.mkdirs();
            // видео (YouTube без JS отдаёт только mp4 с картинкой): вынимаем звук в .m4a
            if (name.toLowerCase().endsWith(".mp4") && hasVideo(source)) {
                final String audioName = name.substring(0, name.length() - 4) + ".m4a";
                final java.io.File audio = new java.io.File(dir, audioName);
                if (audio.exists() && audio.length() > 0) return audio;
                if (extractAudio(source, audio)) return audio;
            }
            final java.io.File copy = new java.io.File(dir, name);
            if (!copy.exists() || copy.length() != source.length()) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(source);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(copy)) {
                    final byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
            }
            return copy;
        } catch (Throwable error) {
            android.util.Log.e("FemboySend", "не вышло скопировать файл", error);
            return null;
        }
    }

    private static boolean hasVideo(java.io.File file) {
        final android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                final String mime = extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) return true;
            }
        } catch (Throwable ignored) {
        } finally {
            extractor.release();
        }
        return false;
    }

    /** Переложить звуковую дорожку в отдельный .m4a без перекодирования. */
    private static boolean extractAudio(java.io.File source, java.io.File target) {
        final java.io.File part = new java.io.File(target.getPath() + ".part");
        final android.media.MediaExtractor extractor = new android.media.MediaExtractor();
        android.media.MediaMuxer muxer = null;
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = -1;
            android.media.MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                final android.media.MediaFormat f = extractor.getTrackFormat(i);
                final String mime = f.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) return false;
            extractor.selectTrack(track);
            muxer = new android.media.MediaMuxer(part.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            final int out = muxer.addTrack(format);
            muxer.start();
            final int size = format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                    ? format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE) : 1024 * 1024;
            final java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(Math.max(size, 256 * 1024));
            final android.media.MediaCodec.BufferInfo info = new android.media.MediaCodec.BufferInfo();
            while (true) {
                info.size = extractor.readSampleData(buffer, 0);
                if (info.size < 0) break;
                info.offset = 0;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = (extractor.getSampleFlags() & android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        ? android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                muxer.writeSampleData(out, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            muxer.release();
            muxer = null;
            return part.renameTo(target);
        } catch (Throwable error) {
            android.util.Log.e("FemboySend", "не вышло вынуть звук из видео", error);
            return false;
        } finally {
            extractor.release();
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Throwable ignored) {
                }
            }
            part.delete();
        }
    }
}

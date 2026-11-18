package com.luiz.w26;

/** Protocolo MTK 3.0 (notificacoes do M Activie): F0 F0 F0 F1 | len BE | RC4(texto + 0). */
public final class Mtk {
    private static final byte[] SYNC = {(byte) 0xF0, (byte) 0xF0, (byte) 0xF0, (byte) 0xF1};
    private static final byte[] KEY =
            ("we had everything before us, we had nothing before us, "
                    + "we were all going direct to Heaven, we were all going direct the other way\u0000")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private Mtk() {
    }

    public static byte[] rc4(byte[] data) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (KEY[i % KEY.length] & 0xFF)) & 0xFF;
            int t = s[i]; s[i] = s[j]; s[j] = t;
        }
        byte[] out = new byte[data.length];
        int i = 0; j = 0;
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) & 0xFF;
            j = (j + s[i]) & 0xFF;
            int t = s[i]; s[i] = s[j]; s[j] = t;
            out[k] = (byte) (data[k] ^ s[(s[i] + s[j]) & 0xFF]);
        }
        return out;
    }

    public static byte[] frame(byte[] plain) {
        int n = plain.length + (plain.length > 0 && plain[plain.length - 1] == 0 ? 0 : 1);
        byte[] pt = new byte[n];
        System.arraycopy(plain, 0, pt, 0, plain.length);
        byte[] out = new byte[8 + n];
        System.arraycopy(SYNC, 0, out, 0, 4);
        out[4] = (byte) (n >>> 24);
        out[5] = (byte) (n >>> 16);
        out[6] = (byte) (n >>> 8);
        out[7] = (byte) n;
        System.arraycopy(rc4(pt), 0, out, 8, n);
        return out;
    }

    public static byte[] cmd(String text) {
        return frame(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Decifra frames recebidos; devolve os textos legitimamente decifraveis. */
    public static String decode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i + 8 <= data.length) {
            if ((data[i] & 0xFF) != 0xF0 || (data[i + 1] & 0xFF) != 0xF0
                    || (data[i + 2] & 0xFF) != 0xF0 || (data[i + 3] & 0xFF) != 0xF1) {
                i++;
                continue;
            }
            int n = ((data[i + 4] & 0xFF) << 24) | ((data[i + 5] & 0xFF) << 16)
                    | ((data[i + 6] & 0xFF) << 8) | (data[i + 7] & 0xFF);
            if (n <= 0 || n > 8192 || i + 8 + n > data.length) break;
            byte[] body = new byte[n];
            System.arraycopy(data, i + 8, body, 0, n);
            String s = new String(rc4(body), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\u0000", "");
            boolean ok = !s.isEmpty();
            for (int k = 0; k < s.length(); k++) {
                char c = s.charAt(k);
                if (c != '\n' && c != '\r' && c != '\t' && (c < 0x20 || c > 0x7E)) { ok = false; break; }
            }
            if (ok) sb.append(s).append('\n');
            i += 8 + n;
        }
        return sb.toString();
    }

    public static byte[] notification(String sender, String title, String content, int msgId) {
        String xml = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>"
                + "<event_report><header>"
                + "<category>notification</category><subType>text</subType>"
                + "<msgId>" + msgId + "</msgId><action>add</action>"
                + "</header><body>"
                + "<sender>" + esc(sender) + "</sender><appId>5</appId>"
                + "<icon><![CDATA[" + icon() + "]]></icon>"
                + "<title><![CDATA[" + cdata(title) + "]]></title>"
                + "<content><![CDATA[" + cdata(content) + "]]></content>"
                + "<timestamp>" + (System.currentTimeMillis() / 1000) + "</timestamp>"
                + "</body></event_report>";
        byte[] body = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] head = cmd("DATA " + body.length);
        byte[] out = new byte[head.length + body.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        return out;
    }

    private static String iconB64;

    private static String icon() {
        if (iconB64 != null) return iconB64;
        android.graphics.Bitmap b =
                android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888);
        b.eraseColor(0xFF1565C0);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, o);
        iconB64 = android.util.Base64.encodeToString(o.toByteArray(), android.util.Base64.NO_WRAP);
        return iconB64;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String cdata(String s) {
        return s.replace("]]>", "]]]]><![CDATA[>");
    }
}

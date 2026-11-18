package com.luiz.w26;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Calendar;
import java.util.UUID;

/** Cliente BLE do relogio: TJD ($LHBT em 18E0/2E01) + MTK 3.0 (18A0/2AA1 -> 2AA0). */
public final class W26 {
    private static final String TAG = "W26";
    public static final UUID SVC_TJD = UUID.fromString("000018e0-0000-1000-8000-00805f9b34fb");
    public static final UUID CHR_TJD = UUID.fromString("00002e01-0000-1000-8000-00805f9b34fb");
    public static final UUID SVC_MTK = UUID.fromString("000018a0-0000-1000-8000-00805f9b34fb");
    public static final UUID CHR_MTK_W = UUID.fromString("00002aa1-0000-1000-8000-00805f9b34fb");
    public static final UUID CHR_MTK_R = UUID.fromString("00002aa0-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    public interface Listener {
        void onLog(String line);

        void onState(String state);
    }

    private static final W26 INSTANCE = new W26();

    public static W26 get() {
        return INSTANCE;
    }

    private static final class Item {
        final byte id;
        final byte[] data;

        Item(byte id, byte[] data) {
            this.id = id;
            this.data = data;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Item> queue = new ArrayDeque<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic tjdChr;
    private BluetoothGattCharacteristic mtkW;
    private BluetoothGattCharacteristic mtkR;
    private BluetoothSocket spp;
    private volatile boolean sppUp;
    private volatile boolean ctaSupported;
    private final Object sppLock = new Object();
    private Listener listener;
    private boolean writing;
    private boolean discovered;
    private int mtu = 23;
    private int msgId = 100;

    private W26() {
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isConnected() {
        return gatt != null && discovered;
    }

    private void log(String s) {
        Log.d(TAG, s);
        final Listener l = listener;
        if (l != null) main.post(() -> l.onLog(s));
    }

    private void state(String s) {
        final Listener l = listener;
        if (l != null) main.post(() -> l.onState(s));
    }

    @SuppressLint("MissingPermission")
    public void connect(Context ctx, String mac) {
        disconnect();
        BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) {
            log("Bluetooth desligado");
            return;
        }
        BluetoothDevice dev = ad.getRemoteDevice(mac);
        log("conectando em " + mac);
        state("conectando");
        discovered = false;
        gatt = dev.connectGatt(ctx.getApplicationContext(), false, cb, BluetoothDevice.TRANSPORT_LE);
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        discovered = false;
        queue.clear();
        writing = false;
        BluetoothGatt g = gatt;
        gatt = null;
        if (g != null) {
            try {
                g.disconnect();
                g.close();
            } catch (Exception ignored) {
            }
        }
        state("desconectado");
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("GATT conectado (status " + status + ")");
                state("lendo servicos");
                g.requestMtu(517);
                main.postDelayed(() -> {
                    if (gatt == g && !discovered) g.discoverServices();
                }, 1200);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("GATT desconectado (status " + status + ")");
                discovered = false;
                state("desconectado");
                if (gatt == g) {
                    gatt = null;
                    g.close();
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int newMtu, int status) {
            mtu = newMtu;
            log("MTU " + newMtu);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            discovered = true;
            onReady(g);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            writing = false;
            nextWrite();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            writing = false;
            log("descritor " + d.getUuid() + " -> " + status);
            nextWrite();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            onRx(c.getUuid(), value);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            onRx(c.getUuid(), c.getValue());
        }
    };

    @SuppressLint("MissingPermission")
    private void onReady(BluetoothGatt g) {
        for (BluetoothGattService s : g.getServices()) {
            if (SVC_TJD.equals(s.getUuid())) tjdChr = s.getCharacteristic(CHR_TJD);
            if (SVC_MTK.equals(s.getUuid())) {
                mtkW = s.getCharacteristic(CHR_MTK_W);
                mtkR = s.getCharacteristic(CHR_MTK_R);
            }
        }
        log("TJD=" + (tjdChr != null) + " MTK-W=" + (mtkW != null) + " MTK-R=" + (mtkR != null));
        if (tjdChr != null) {
            g.setCharacteristicNotification(tjdChr, true);
            cccd(g, tjdChr);
        }
        if (mtkR != null) {
            g.setCharacteristicNotification(mtkR, true);
            cccd(g, mtkR);
        }
        state("conectado");
        if (!sppUp) mtkInit();
        tjdInit();
    }

    @SuppressLint("MissingPermission")
    private void cccd(BluetoothGatt g, BluetoothGattCharacteristic c) {
        BluetoothGattDescriptor d = c.getDescriptor(CCCD);
        if (d == null) {
            log("sem CCCD em " + c.getUuid());
            return;
        }
        d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        g.writeDescriptor(d);
    }

    private void onRx(UUID uuid, byte[] value) {
        if (value == null || value.length == 0) return;
        if (CHR_TJD.equals(uuid)) {
            String s = new String(value, StandardCharsets.UTF_8).trim();
            log("TJD << " + s);
        } else if (CHR_MTK_R.equals(uuid)) {
            String s = Mtk.decode(value);
            if (!s.isEmpty()) log("MTK << " + s.trim());
            else log("MTK << " + hex(value));
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(b.length, 24); i++) sb.append(String.format("%02x ", b[i]));
        return sb.toString().trim();
    }

    // ---- envio (fila: BLE so aceita uma escrita por vez) ----

    private void enqueue(BluetoothGattCharacteristic c, byte[] data) {
        if (c == null) {
            log("caracteristica ausente; pacote descartado");
            return;
        }
        queue.add(new Item((byte) (c == tjdChr ? 1 : 2), data));
        if (!writing) nextWrite();
    }

    @SuppressLint("MissingPermission")
    private void nextWrite() {
        BluetoothGatt g = gatt;
        if (g == null || writing) return;
        Item item = queue.pollFirst();
        if (item == null) return;
        BluetoothGattCharacteristic c = item.id == 1 ? tjdChr : mtkW;
        if (c == null) {
            nextWrite();
            return;
        }
        c.setWriteType(item.id == 1 ? BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                : BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        c.setValue(item.data);
        writing = true;
        if (!g.writeCharacteristic(c)) {
            writing = false;
            log("falha ao escrever");
        }
        main.postDelayed(() -> {
            if (writing) {
                writing = false;
                nextWrite();
            }
        }, 120);
    }

    public void sendTjd(String cmd) {
        byte[] body = cmd.getBytes(StandardCharsets.US_ASCII);
        String frame = "$LHBT TJD_APP TJD_Dev 0 " + body.length + " " + cmd + "#";
        enqueue(tjdChr, frame.getBytes(StandardCharsets.US_ASCII));
    }

    public void sendMtk(byte[] data) {
        if (mtkW == null) return;
        int chunk = Math.max(20, mtu - 3);
        for (int i = 0; i < data.length; i += chunk) {
            enqueue(mtkW, Arrays.copyOfRange(data, i, Math.min(data.length, i + chunk)));
        }
    }

    // ---- SPP (canal MTK real: RFCOMM com UUID de porta serial) ----

    public boolean isSppUp() {
        return sppUp;
    }

    @SuppressLint("MissingPermission")
    public void connectSpp(Context ctx, String mac) {
        disconnectSpp();
        final BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) {
            log("SPP: bluetooth desligado");
            return;
        }
        new Thread(() -> {
            try {
                ad.cancelDiscovery();
                BluetoothDevice dev = ad.getRemoteDevice(mac);
                BluetoothSocket s = dev.createRfcommSocketToServiceRecord(SPP);
                log("SPP conectando em " + mac);
                s.connect();
                synchronized (sppLock) {
                    spp = s;
                    sppUp = true;
                }
                log("SPP conectado");
                sppHandshake();
                sppRead(s);
            } catch (Exception e) {
                log("SPP falhou: " + e);
                disconnectSpp();
            }
        }, "spp").start();
    }

    public void disconnectSpp() {
        BluetoothSocket s;
        synchronized (sppLock) {
            sppUp = false;
            s = spp;
            spp = null;
        }
        if (s != null) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
            log("SPP desconectado");
        }
    }

    private void sppHandshake() {
        long t = now();
        int tz = tzOffsetMs();
        sendSpp(Mtk.cmd("SYNC REQV"));
        sendSpp(Mtk.cmd("SYNC " + t + " " + tz));
        sendSpp(Mtk.cmd("EXCD mtk_msctrl msctrl_apk 0 0 4 "));
        mreeCta();
        sendSpp(Mtk.cmd("EXCD language mtk_language 1 0 5 "));
    }

    private void sendSpp(byte[] data) {
        BluetoothSocket s;
        synchronized (sppLock) {
            s = spp;
        }
        if (s == null || !sppUp) {
            log("SPP nao conectado; pacote descartado");
            return;
        }
        try {
            synchronized (sppLock) {
                java.io.OutputStream out = s.getOutputStream();
                out.write(data);
                out.flush();
            }
        } catch (Exception e) {
            log("SPP envio: " + e.getMessage());
            disconnectSpp();
        }
    }

    private void sppRead(BluetoothSocket s) {
        byte[] buf = new byte[4096];
        java.io.ByteArrayOutputStream acc = new java.io.ByteArrayOutputStream();
        try {
            java.io.InputStream in = s.getInputStream();
            while (spp == s) {
                int n = in.read(buf);
                if (n < 0) break;
                acc.write(buf, 0, n);
                byte[] all = acc.toByteArray();
                int used = parseSpp(all);
                if (used > 0) {
                    acc.reset();
                    acc.write(all, used, all.length - used);
                }
                if (acc.size() > 65536) acc.reset();
            }
        } catch (Exception e) {
            if (spp == s) log("SPP leitura: " + e.getMessage());
        } finally {
            if (spp == s) {
                log("SPP encerrado");
                disconnectSpp();
            }
        }
    }

    private int parseSpp(byte[] b) {
        int i = 0;
        StringBuilder text = new StringBuilder();
        while (i < b.length) {
            if (i + 4 <= b.length && (b[i] & 0xFF) == 0xF0 && (b[i + 1] & 0xFF) == 0xF0
                    && (b[i + 2] & 0xFF) == 0xF0 && (b[i + 3] & 0xFF) == 0xF1) {
                if (i + 8 > b.length) break;
                int n = ((b[i + 4] & 0xFF) << 24) | ((b[i + 5] & 0xFF) << 16)
                        | ((b[i + 6] & 0xFF) << 8) | (b[i + 7] & 0xFF);
                if (n <= 0 || n > 65536) {
                    i++;
                    continue;
                }
                if (i + 8 + n > b.length) break;
                byte[] f = Arrays.copyOfRange(b, i, i + 8 + n);
                String s = Mtk.decode(f).trim();
                log("MTK-SPP << " + (s.isEmpty() ? hex(f) : s));
                int jump = 8 + n;
                if (s.startsWith("MREE ")) {
                    int plen;
                    try {
                        plen = Integer.parseInt(s.substring(5).trim());
                    } catch (NumberFormatException e) {
                        plen = -1;
                    }
                    if (plen > 0 && plen <= 65536) {
                        if (i + jump + plen > b.length) break;
                        String reply = new String(b, i + jump, plen, StandardCharsets.US_ASCII).trim();
                        log("MRE resp: " + mreMeaning(reply));
                        int rcode = mreCode(reply);
                        if (rcode == 9) {
                            ctaSupported = true;
                            log("CTA confirmado: install aceita campo de permissoes");
                        } else if (rcode == -109) {
                            ctaSupported = false;
                            log("CTA nao suportado por este firmware");
                        }
                        jump += plen;
                    }
                }
                if (s.contains("notification_delete")) {
                    log("relogio apagou " + tagOf(s, "msgId"));
                } else if (s.contains("action_operate")) {
                    log("relogio acionou " + tagOf(s, "msgId") + " " + tagOf(s, "action_id"));
                }
                i += jump;
                continue;
            }
            int c = b[i] & 0xFF;
            if (c >= 0x20 && c < 0x7F) {
                text.append((char) c);
            } else {
                if (text.length() >= 2) log("SPP << " + text);
                text.setLength(0);
            }
            i++;
        }
        if (text.length() >= 2) log("SPP << " + text);
        return i;
    }

    public void sendMtkAuto(byte[] data) {
        if (sppUp) sendSpp(data);
        else sendMtk(data);
    }

    private static String tagOf(String xml, String tag) {
        int a = xml.indexOf("<" + tag + ">");
        if (a < 0) return "";
        a += tag.length() + 2;
        int b = xml.indexOf("</" + tag + ">", a);
        return b < 0 ? "" : xml.substring(a, b);
    }

    // ---- MRE: instalador de apps .vxp do proprio relogio ----

    private static final String MRE_ERRORS[] = {
            "app_path eh NULL", "limite de apps instalados", "erro na tag do nome do app",
            "sem memoria no runtime", "atalho ja instalado", "erro ao ler info do atalho",
            "app ja instalado", "erro nas tags de icone/nome", "erro ao gravar info de app interno",
            "erro ao salvar icone", "erro ao gravar info do vxp"};

    private static int mreCode(String reply) {
        String body = reply.substring("mre_installer ".length()).trim();
        if (body.isEmpty()) return Integer.MIN_VALUE;
        try {
            return Integer.parseInt(body.split(" ")[0]);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static String mreMeaning(String reply) {        String body = reply.substring("mre_installer ".length()).trim();
        if (body.isEmpty()) return reply;
        String codeStr = body.split(" ")[0];
        String rest = body.length() > codeStr.length() ? body.substring(codeStr.length()).trim() : "";
        String meaning;
        int code;
        try {
            code = Integer.parseInt(codeStr);
        } catch (NumberFormatException e) {
            return reply;
        }
        if (code == 1) meaning = "instalado com sucesso";
        else if (code == 9) meaning = "CTA suportado";
        else if (code == -1) meaning = "desinstalado";
        else if (code == -2) meaning = "status do app";
        else if (code == -6) meaning = "arquivo excluido";
        else if (code == 5) meaning = "lista de apps";
        else if (code == 7) meaning = "permissoes";
        else if (code == 8) meaning = "permissao alterada";
        else if (code >= -1011 && code <= -1001) meaning = "falha: " + MRE_ERRORS[-1000 - code];
        else if (code == -1000) meaning = "falha: erro desconhecido";
        else if (code == -109) meaning = "falha: comando invalido (sem espaco final?)";
        else meaning = "codigo " + code;
        return meaning + (rest.isEmpty() ? "" : " | " + rest.replace("<", "").replace(">", ""));
    }

    private void mree(int cmd, int part, byte[] payload) {
        String hdr = "MREE mre_installer smartwatch " + cmd + " " + part + " " + payload.length + " ";
        if (part == 0) log("MRE >> " + hdr.trim());
        byte[] f = Mtk.frame(hdr.getBytes(StandardCharsets.US_ASCII));
        byte[] out = new byte[f.length + payload.length];
        System.arraycopy(f, 0, out, 0, f.length);
        System.arraycopy(payload, 0, out, f.length, payload.length);
        sendSpp(out);
    }

    public void mreeList() {
        mree(5, 0, "5 ".getBytes(StandardCharsets.US_ASCII));
    }

    public void mreeStatus(String name) {
        mree(-2, 0, ("-2 " + name + " ").getBytes(StandardCharsets.US_ASCII));
    }

    public void mreePermissions(String name) {
        mree(7, 0, ("7 " + name).getBytes(StandardCharsets.US_ASCII));
    }

    public void mreeUninstall(String name) {
        mree(-1, 0, ("-1 " + name).getBytes(StandardCharsets.US_ASCII));
    }

    public void mreeDelete(String name) {
        mree(-6, 0, ("-6 " + name).getBytes(StandardCharsets.US_ASCII));
    }

    public void mreeCta() {
        mree(9, 0, "9 cta".getBytes(StandardCharsets.US_ASCII));
    }

    public void mreeInstall(String name, byte[] vxp) {
        mreeInstall(name, vxp, 0);
    }

    /** permMask: bitmask de permissoes (1=chamada, 2=SMS, 128=GPS, 32768=bluetooth...). */
    public void mreeInstall(String name, byte[] vxp, int permMask) {
        if (!sppUp) {
            log("MRE precisa do SPP conectado");
            return;
        }
        String head = vxp.length + " " + name;
        if (ctaSupported) head += " " + String.format("%08X", permMask);
        mree(0, 0, head.getBytes(StandardCharsets.US_ASCII));
        mree(0, 1, vxp);
        log("MRE: enviado " + name + " (" + vxp.length + " bytes"
                + (ctaSupported ? ", perm " + String.format("%08X", permMask) : ", sem CTA") + ")");
    }

    // ---- sequencias ----

    public void mtkInit() {
        sendMtk(Mtk.cmd("SYNC REQV"));
        sendMtk(Mtk.cmd("SYNC " + now() + " " + tzOffsetMs()));
        sendMtk(Mtk.cmd("EXCD mtk_msctrl msctrl_apk 0 0 4"));
        sendMtk(Mtk.cmd("MREE mre_installer smartwatch 9 0 5"));
        sendMtk(Mtk.cmd("EXCD language mtk_language 1 0 5"));
    }

    public void tjdInit() {
        sendTjd("X1,LINK");
        sendTjd("X0,GET,0");
        sendTjd("X0,GET,7");
    }

    public void syncTime() {
        Calendar c = Calendar.getInstance();
        String when = String.format("%04d%02d%02d %02d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        int hours = tzOffsetMs() / 3600000;
        sendTjd("X1,PRSET,DATETIME," + when + "," + String.format("%+04d", hours));
        sendMtk(Mtk.cmd("SYNC " + now() + " " + tzOffsetMs()));
    }

    public void findDevice() {
        sendTjd("X1,PRSET,FindDevice,1");
    }

    public void battery() {
        sendTjd("X0,GET,7");
    }

    public void notifyWatch(String sender, String title, String content) {
        if (!isConnected() && !sppUp) {
            log("ignorado (desconectado): " + title);
            return;
        }
        byte[] frame = Mtk.notification(sender, title, content, msgId++);
        if (sppUp) {
            sendSpp(frame);
            log("notificacao SPP enviada: " + title);
        } else {
            sendMtk(frame);
            log("notificacao BLE enviada: " + title);
        }
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private static int tzOffsetMs() {
        return java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis());
    }
}

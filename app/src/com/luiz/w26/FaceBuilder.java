package com.luiz.w26;

import java.nio.charset.StandardCharsets;

/**
 * Mostradores DIGITAIS pre-montados (sem ponteiros, sem PNG personalizado).
 *
 * O W26 renderiza o mostrador digital NATIVAMENTE a partir dos recursos do
 * .vm_res (num_0..9.png + clock_bg + Configtbl.bin); o codigo VXP e um stub.
 * As estatisticas (passos/HR/data) e os atalhos de toque (musica/HR) sao do
 * firmware e dependem do Configtbl.bin oficial — por isso ele e preservado
 * exatamente. O caminho oficial de "dial push" (GATT 0x30A0) que configuraria
 * slots de estatistica dinamicamente NAO existe neste firmware.
 *
 * Os fundos A/B sao as duas artes VRE3 que ja vem no template ronman.
 */
public final class FaceBuilder {
    public static final class Preset {
        public final String name, asset;
        Preset(String name, String asset) { this.name = name; this.asset = asset; }
    }

    public static final Preset[] PRESETS = {
        new Preset("Digital — fundo A", "faces/ronman_a.vxp"),
        new Preset("Digital — fundo B", "faces/ronman_b.vxp"),
    };

    /** Configtbl.bin digital oficial (176x220): mantem estatisticas + atalhos nativos. */
    private static final String DIGITAL_CT = "000029698769a5820000016080000300";

    private FaceBuilder() {}

    /** Garante o Configtbl digital oficial no template (patch in-place de 16 B). */
    public static byte[] build(byte[] base) {
        byte[] out = base.clone();
        byte[] ct = hex(DIGITAL_CT);
        System.arraycopy(ct, 0, out, configtblOffset(out), 16);
        return out;
    }

    private static int u32(byte[] b, int p) {
        return (b[p] & 255) | (b[p + 1] & 255) << 8 | (b[p + 2] & 255) << 16 | (b[p + 3] & 255) << 24;
    }

    private static int u16(byte[] b, int p) { return (b[p] & 255) | (b[p + 1] & 255) << 8; }

    private static int configtblOffset(byte[] b) {
        int shoff = u32(b, 0x20), es = u16(b, 0x2e), n = u16(b, 0x30), strndx = u16(b, 0x32);
        int stOff = u32(b, shoff + strndx * es + 16);
        int resOff = -1;
        for (int i = 0; i < n; i++) {
            int p = shoff + i * es, nameOff = u32(b, p), e = stOff + nameOff;
            while (b[e] != 0) e++;
            String name = new String(b, stOff + nameOff, e - stOff - nameOff, StandardCharsets.US_ASCII);
            if (name.equals(".vm_res")) { resOff = u32(b, p + 16); break; }
        }
        if (resOff < 0) throw new IllegalStateException(".vm_res ausente");
        int pos = resOff;
        while (b[pos] != 0) {
            int e = pos;
            while (b[e] != 0) e++;
            String name = new String(b, pos, e - pos, StandardCharsets.US_ASCII);
            int off = u32(b, e + 1);
            if (name.equalsIgnoreCase("configtbl.bin")) return off;
            pos = e + 9;
        }
        throw new IllegalStateException("configtbl ausente");
    }

    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
}
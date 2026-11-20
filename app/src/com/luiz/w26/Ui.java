package com.luiz.w26;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Base de UI programatica (sem XML): paleta escura + helpers de card/botao/campo. */
public abstract class Ui extends Activity {
    protected static final int BG = 0xFF080C12;
    protected static final int CARD = 0xFF121A24;
    protected static final int CARD2 = 0xFF0E151E;
    protected static final int STROKE = 0xFF1E2A38;
    protected static final int FIELD = 0xFF0B1119;
    protected static final int GHOST = 0xFF1A2430;
    protected static final int ACCENT = 0xFF3D7EFF;
    protected static final int GREEN = 0xFF2DD4A7;
    protected static final int WARN = 0xFFF0616D;
    protected static final int AMBER = 0xFFF5B942;
    protected static final int PURPLE = 0xFF9B6DFF;
    protected static final int TEXT = 0xFFEDF2F8;
    protected static final int MUTED = 0xFF7E8CA0;

    protected int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    protected float density() {
        return getResources().getDisplayMetrics().density;
    }

    protected LinearLayout pageContainer() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(4), dp(16), dp(18));
        return c;
    }

    protected ScrollView scroll(View content) {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    protected ImageView icon(int kind, int color, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new Icon(kind, color, density()));
        return iv;
    }

    protected LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), STROKE);
        c.setBackground(bg);
        c.setPadding(dp(17), dp(15), dp(17), dp(17));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(13);
        c.setLayoutParams(lp);
        return c;
    }

    protected View quickTile(int iconKind, String label, View.OnClickListener l) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), STROKE);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x223D7EFF), bg, null));
        t.setPadding(dp(8), dp(16), dp(8), dp(14));
        t.setOnClickListener(l);
        t.addView(icon(iconKind, ACCENT, 26), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(12);
        lab.setTextColor(TEXT);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        t.addView(lab, llp);
        return t;
    }

    protected View linkCard(int iconKind, String title, String sub, View.OnClickListener l) {
        LinearLayout p = card();
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(30), dp(30));
        ilp.rightMargin = dp(14);
        p.addView(icon(iconKind, ACCENT, 30), ilp);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(15);
        t1.setTextColor(TEXT);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(11);
        t2.setTextColor(MUTED);
        t.addView(t2);
        p.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        p.addView(icon(Icon.CHEVRON, MUTED, 20), new LinearLayout.LayoutParams(dp(20), dp(20)));
        p.setOnClickListener(l);
        return p;
    }

    protected View statTile(String label, TextView value) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD2);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), STROKE);
        t.setBackground(bg);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11);
        lab.setTextColor(MUTED);
        t.addView(lab);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(3);
        t.addView(value, vlp);
        return t;
    }

    /** Tile de estatistica com icone colorido + valor grande + unidade. */
    protected View healthTile(int iconKind, int color, String label, TextView value, String unit) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD2);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), STROKE);
        t.setBackground(bg);
        t.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams iip = new LinearLayout.LayoutParams(dp(16), dp(16));
        iip.rightMargin = dp(6);
        head.addView(icon(iconKind, color, 16), iip);
        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextSize(11);
        lab.setTextColor(MUTED);
        head.addView(lab);
        t.addView(head);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(6);
        t.addView(value, vlp);
        if (unit != null && !unit.isEmpty()) {
            TextView u = new TextView(this);
            u.setText(unit);
            u.setTextSize(10);
            u.setTextColor(MUTED);
            t.addView(u);
        }
        return t;
    }

    protected View infoRow(String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(5), 0, dp(5));
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(MUTED);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(TEXT);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        return r;
    }

    protected TextView sectionTitle(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(2), 0, dp(10));
        return t;
    }

    protected TextView statValue(String v) {
        TextView t = new TextView(this);
        t.setText(v);
        t.setTextSize(17);
        t.setTextColor(TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    protected TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(MUTED);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    protected TextView chip(String text, int bg, int fg) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(20));
        t.setBackground(g);
        t.setPadding(dp(12), dp(5), dp(12), dp(5));
        return t;
    }

    protected Button btnBase(String text, int bg, int fg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(14));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null));
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(7);
        b.setLayoutParams(lp);
        return b;
    }

    protected Button primaryBtn(String text, View.OnClickListener l) {
        return btnBase(text, ACCENT, 0xFFFFFFFF, l);
    }

    protected Button ghostBtn(String text, View.OnClickListener l) {
        return btnBase(text, GHOST, TEXT, l);
    }

    protected Button dangerBtn(String text, View.OnClickListener l) {
        return btnBase(text, 0x33F0616D, WARN, l);
    }

    protected View row(View... views) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < views.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.topMargin = dp(7);
            if (i < views.length - 1) lp.rightMargin = dp(8);
            r.addView(views[i], lp);
        }
        return r;
    }

    protected Switch makeSwitch(boolean checked) {
        Switch s = new Switch(this);
        s.setChecked(checked);
        s.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        s.setTrackTintList(ColorStateList.valueOf(ACCENT));
        return s;
    }

    protected View toggleRow(String label, Switch sw) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTextColor(TEXT);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(sw);
        r.setPadding(0, dp(5), 0, dp(5));
        return r;
    }

    protected EditText field(String text) {
        EditText e = new EditText(this);
        e.setText(text);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(FIELD);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), STROKE);
        e.setBackground(bg);
        e.setPadding(dp(13), dp(10), dp(13), dp(10));
        return e;
    }

    protected View labeled(String label, View field) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(11);
        t.setTextColor(MUTED);
        t.setPadding(0, dp(7), 0, dp(4));
        l.addView(t);
        l.addView(field);
        return l;
    }

    protected byte[] asset(String name) throws Exception {
        try (java.io.InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) > 0) o.write(b, 0, r);
            return o.toByteArray();
        }
    }
}
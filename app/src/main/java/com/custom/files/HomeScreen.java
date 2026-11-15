package com.custom.files;

import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;

/** Start screen: a grid of big tiles — storages, Downloads, categories and tools (like the original, but light). */
final class HomeScreen extends LinearLayout implements MainActivity.Screen {
    private final MainActivity a;
    private final LinearLayout grid, favBox, favSection;
    private int gen;

    /** One tile on the home grid. */
    private static final class Tile {
        int icon, color, soft;
        String title, sub = " ";
        float frac = -1;
        Runnable click;
    }

    HomeScreen(MainActivity a) {
        super(a);
        this.a = a;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.BG);

        LinearLayout bar = Ui.appBar(a);
        bar.addView(Ui.iconBtn(a, R.drawable.ic_menu, Ui.TEXT, "תפריט", v -> a.openDrawer()));
        TextView t = Ui.line(a, Flavor.NAME, 20, Ui.TEXT, Ui.MEDIUM);
        t.setPaddingRelative(Ui.dp(8), 0, 0, 0);
        bar.addView(t, Ui.lp(0, Ui.WRAP, 1));
        bar.addView(Ui.iconBtn(a, R.drawable.ic_search, Ui.TEXT, "חיפוש", v -> a.push(BrowserScreen.search(a))));
        bar.addView(Ui.iconBtn(a, R.drawable.ic_more, Ui.TEXT, "אפשרויות", v -> menu()));
        addView(bar, Ui.lp(Ui.MATCH, Ui.dp(60)));

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = Ui.vbox(a);
        Ui.pad(col, 10, 10, 10, 10);
        sv.addView(col, new ScrollView.LayoutParams(Ui.MATCH, Ui.WRAP));
        addView(sv, Ui.lp(Ui.MATCH, 0, 1));

        grid = Ui.vbox(a);
        col.addView(grid);

        favSection = Ui.vbox(a);
        TextView fh = Ui.text(a, "מועדפים", 15, Ui.TEXT, Ui.MEDIUM);
        favSection.addView(fh, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 10, 18, 10, 10));
        favBox = Ui.vbox(a);
        favBox.setBackground(Ui.round(Ui.SURFACE, 20));
        favBox.setClipToOutline(true);
        favSection.addView(favBox, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 6, 0, 6, 0));
        col.addView(favSection);

        refresh();
    }

    @Override public boolean onBack() { return false; }

    @Override
    public void onShow() {
        a.setBars(Ui.SURFACE, true);
        refresh();
    }

    // ------------------------------------------------------------ tiles

    private View tileView(Tile t) {
        LinearLayout c = Ui.vbox(a);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setBackground(Ui.ripple(Ui.SURFACE, 20));
        c.setElevation(Ui.dp(1));
        Ui.pad(c, 6, 16, 6, 12);
        FrameLayout circ = new FrameLayout(a);
        circ.setBackground(Ui.round(t.soft, 20));
        circ.addView(Ui.img(a, t.icon, t.color), new FrameLayout.LayoutParams(Ui.dp(30), Ui.dp(30), Gravity.CENTER));
        c.addView(circ, Ui.lp(Ui.dp(60), Ui.dp(60)));
        TextView l = Ui.line(a, t.title, 14.5f, Ui.TEXT, Ui.MEDIUM);
        l.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        c.addView(l, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 10, 0, 0));
        TextView s = Ui.line(a, t.sub, 11.5f, Ui.TEXT2, Ui.REGULAR);
        s.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        s.setTextDirection(View.TEXT_DIRECTION_RTL);
        c.addView(s, Ui.margins(Ui.lp(Ui.MATCH, Ui.WRAP), 0, 2, 0, 0));
        Ui.Bar bar = new Ui.Bar(a);
        if (t.frac >= 0) {
            bar.set(t.frac, t.frac > 0.9f ? Ui.DANGER : t.frac > 0.75f ? Ui.AMBER : Ui.PRIMARY);
        } else bar.setVisibility(INVISIBLE);
        c.addView(bar, Ui.margins(Ui.lp(Ui.MATCH, Ui.dp(4)), 12, 8, 12, 0));
        c.setOnClickListener(v -> t.click.run());
        return c;
    }

    private void layoutTiles(ArrayList<Tile> tiles) {
        grid.removeAllViews();
        int cols = getResources().getDisplayMetrics().widthPixels > Ui.dp(560) ? 4 : 3;
        LinearLayout row = null;
        for (int i = 0; i < tiles.size(); i++) {
            if (i % cols == 0) {
                row = Ui.hbox(a);
                row.setGravity(Gravity.TOP);
                grid.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP));
            }
            row.addView(tileView(tiles.get(i)), Ui.margins(Ui.lp(0, Ui.WRAP, 1), 5, 5, 5, 5));
        }
        int rest = tiles.size() % cols;
        if (rest != 0) for (int i = rest; i < cols; i++) row.addView(new View(a), Ui.margins(Ui.lp(0, 1, 1), 5, 5, 5, 5));
    }

    private Tile catTile(int cat, long[] st) {
        Tile t = new Tile();
        t.icon = Media.ICON[cat];
        t.color = Media.COLOR[cat];
        t.soft = Media.SOFT[cat];
        t.title = Media.TITLES[cat];
        if (st != null && st[0] >= 0) t.sub = Fx.size(st[1]) + " (" + Fx.num(st[0]) + ")";
        t.click = () -> a.push(BrowserScreen.cat(a, cat));
        return t;
    }

    private ArrayList<Tile> buildTiles(ArrayList<Storage.Vol> vols, long[] dl, long[][] cats, int trash) {
        ArrayList<Tile> tiles = new ArrayList<>();
        for (Storage.Vol v : vols) {
            Tile t = new Tile();
            t.icon = v.primary ? R.drawable.ic_phone : R.drawable.ic_sd;
            t.color = v.primary ? 0xFF475569 : 0xFF4F46E5;
            t.soft = v.primary ? 0xFFEDF1F6 : 0xFFEEF0FE;
            t.title = v.label;
            if (v.total > 0) {
                t.sub = Fx.size(v.total - v.free) + " / " + Fx.size(v.total);
                t.frac = (float) (v.total - v.free) / v.total;
            }
            t.click = () -> a.push(BrowserScreen.dir(a, v.root));
            tiles.add(t);
        }
        if (a.prefs.systemStorage) {
            Tile t = new Tile();
            t.icon = R.drawable.ic_system;
            t.color = 0xFF334155;
            t.soft = 0xFFEDF1F6;
            t.title = "אחסון מערכת";
            t.sub = "/";
            t.click = () -> a.push(BrowserScreen.dir(a, new File("/")));
            tiles.add(t);
        }
        Tile d = new Tile();
        d.icon = R.drawable.ic_download;
        d.color = 0xFF0EA5E9;
        d.soft = 0xFFE0F2FB;
        d.title = "הורדות";
        if (dl != null && dl[0] >= 0) d.sub = Fx.size(dl[1]) + " (" + Fx.num(dl[0]) + ")";
        File dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        d.click = () -> a.push(BrowserScreen.dir(a, dlDir));
        tiles.add(d);
        int[] order = {Media.IMAGES, Media.AUDIO, Media.VIDEO, Media.DOCS, Media.APPS, Media.RECENT, Media.ARCHIVES, Media.LARGE};
        for (int c : order) if (!(Flavor.NO_VIDEO && c == Media.VIDEO)) tiles.add(catTile(c, cats == null ? null : cats[c]));
        Tile tr = new Tile();
        tr.icon = R.drawable.ic_delete;
        tr.color = 0xFF64748B;
        tr.soft = 0xFFEDF1F6;
        tr.title = "סל מיחזור";
        tr.sub = trash < 0 ? " " : trash == 0 ? "ריק" : Fx.items(trash);
        tr.click = () -> a.push(BrowserScreen.trash(a));
        tiles.add(tr);
        Tile an = new Tile();
        an.icon = R.drawable.ic_pie;
        an.color = 0xFFDB2777;
        an.soft = 0xFFFCE7F3;
        an.title = "ניתוח אחסון";
        an.sub = "מה תופס מקום";
        an.click = () -> a.push(new AnalysisScreen(a, Storage.primaryRoot(a)));
        tiles.add(an);
        return tiles;
    }

    private ArrayList<Storage.Vol> lastVols;
    private long[] lastDl;
    private long[][] lastCats;

    void refresh() {
        final int g = ++gen;
        // draw immediately with what we know, then fill in the numbers
        layoutTiles(buildTiles(lastVols != null ? lastVols : Storage.list(a, false), lastDl, lastCats, -1));
        refreshFavs();
        MainActivity.BG.execute(() -> {
            ArrayList<Storage.Vol> vols = Storage.list(a, true);
            int trash = Trash.count(a);
            MainActivity.H.post(() -> {
                if (g != gen) return;
                lastVols = vols;
                layoutTiles(buildTiles(vols, lastDl, lastCats, trash));
                a.checkStorageFull(vols);
            });
            long[] dl = Media.statsUnder(a, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
            long[][] cats = new long[Media.N][];
            for (int i = 0; i < Media.N; i++) cats[i] = Media.stats(a, i);
            MainActivity.H.post(() -> {
                if (g != gen) return;
                lastDl = dl;
                lastCats = cats;
                layoutTiles(buildTiles(vols, dl, cats, trash));
            });
        });
    }

    private void refreshFavs() {
        ArrayList<String> bm = a.prefs.bookmarks();
        favSection.setVisibility(bm.isEmpty() ? GONE : VISIBLE);
        favBox.removeAllViews();
        for (int i = 0; i < bm.size(); i++) {
            String p = bm.get(i);
            File f = new File(p);
            if (i > 0) favBox.addView(Ui.divider(a), Ui.margins(Ui.lp(Ui.MATCH, Math.max(1, Ui.dp(1))), 68, 0, 0, 0));
            LinearLayout r = Ui.hbox(a);
            Ui.pad(r, 14, 10, 10, 10);
            r.setBackground(Ui.rippleRect());
            FrameLayout tile = new FrameLayout(a);
            tile.setBackground(Ui.round(0xFFFEF3DC, 12));
            tile.addView(Ui.img(a, R.drawable.ic_star, Ui.AMBER), new FrameLayout.LayoutParams(Ui.dp(22), Ui.dp(22), Gravity.CENTER));
            r.addView(tile, Ui.lp(Ui.dp(42), Ui.dp(42)));
            LinearLayout texts = Ui.vbox(a);
            texts.setPaddingRelative(Ui.dp(14), 0, Ui.dp(8), 0);
            texts.addView(Ui.line(a, f.getName().isEmpty() ? p : f.getName(), 15.5f, Ui.TEXT, Ui.REGULAR));
            TextView s = Ui.line(a, Storage.pretty(a, f), 12.5f, Ui.TEXT2, Ui.REGULAR);
            s.setTextDirection(View.TEXT_DIRECTION_RTL);
            texts.addView(s);
            r.addView(texts, Ui.lp(0, Ui.WRAP, 1));
            r.addView(Ui.img(a, R.drawable.ic_chevron, Ui.TEXT3), Ui.lp(Ui.dp(20), Ui.dp(20)));
            r.setOnClickListener(v -> {
                if (f.isDirectory()) a.push(BrowserScreen.dir(a, f));
                else a.toast("התיקיה לא קיימת");
            });
            r.setOnLongClickListener(v -> {
                Dialogs.confirm(a, "להסיר מהמועדפים?", f.getName(), "הסר", false, null, c -> {
                    a.prefs.setBookmarked(p, false);
                    refreshFavs();
                });
                return true;
            });
            favBox.addView(r);
        }
    }

    private void menu() {
        ArrayList<Dialogs.Item> m = new ArrayList<>();
        m.add(new Dialogs.Item(R.drawable.ic_eye, a.prefs.hidden ? "הסתרת קבצים נסתרים" : "הצגת קבצים נסתרים", () -> {
            a.prefs.hidden = !a.prefs.hidden;
            a.prefs.save();
            refresh();
        }));
        m.add(new Dialogs.Item(R.drawable.ic_pie, "ניתוח אחסון", () -> a.push(new AnalysisScreen(a, Storage.primaryRoot(a)))));
        m.add(new Dialogs.Item(R.drawable.ic_refresh, "רענון", this::refresh));
        m.add(new Dialogs.Item(R.drawable.ic_settings, "הגדרות", () -> a.push(new SettingsScreen(a))));
        Dialogs.sheet(a, null, null, m);
    }
}

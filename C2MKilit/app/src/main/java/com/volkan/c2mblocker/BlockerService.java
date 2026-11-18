package com.volkan.c2mblocker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Browser;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * VOCD'nin erişilebilirlik servisi. Üç işi var:
 *
 * 1. connected2.me ekrandayken (bölünmüş ekran dahil) alt menüdeki Keşfet (1.) ve Shuffle (3.)
 *    sekmelerinin üstüne dokunmayı yutan bir katman koyar; uygulama bu sekmelerden birinde
 *    açılırsa Sohbet sekmesine geçirir.
 * 2. Tarayıcının adres çubuğunda engelli listedeki bir site görünürse sayfayı kapatır ve
 *    engelleme ekranını gösterir.
 * 3. Sıkı mod açıkken ve şifre girilmemişken, ayarlarda VOCD'nin uygulama bilgisi /
 *    erişilebilirlik sayfası veya kaldırma ekranı açılırsa ana ekrana döndürür.
 *
 * Erişilebilirlik overlay'i (TYPE_ACCESSIBILITY_OVERLAY) "diğer uygulamaların üzerinde
 * göster" iznine ihtiyaç duymaz; servis açık olduğu sürece sistem onu ayakta tutar ve
 * telefon yeniden başlatıldığında kendiliğinden geri gelir.
 */
public class BlockerService extends AccessibilityService {

    public static final String PREFS = "c2m_blocker";
    public static final String KEY_TARGETS = "targets";
    public static final String KEY_LAST_PKG = "last_pkg";
    public static final String KEY_PASS_HASH = "pass_hash";
    public static final String KEY_PASS_SALT = "pass_salt";
    public static final String KEY_FAILS = "fails";
    public static final String KEY_LOCK_UNTIL = "lock_until";
    public static final String KEY_GRACE_UNTIL = "grace_until";
    public static final String KEY_STRICT = "strict";
    public static final String KEY_SITES_ON = "sites_on";
    public static final String KEY_TABS_DETECTED = "tabs_detected";
    public static final String DEFAULT_TARGETS = "com.connected2.ozzy.c2m";

    // Alt menü sırası: 0 = Keşfet, 1 = Sohbet, 2 = Shuffle, 3 = Promote, 4 = Profil
    private static final int TAB_COUNT = 5;
    private static final int[] BLOCKED_TABS = {0, 2};
    private static final int SAFE_TAB = 1;

    /** Engelli siteden sonra tarayıcının aynı sekmede açacağı adres. */
    private static final String SAFE_URL = "https://www.google.com/";

    // Sıkı modda korunan ekranların sahibi olan paketler.
    private static final String[] SETTINGS_PKGS = {
            "com.android.settings", "com.miui.securitycenter", "com.xiaomi.misettings",
            "com.android.packageinstaller", "com.google.android.packageinstaller",
            "com.miui.packageinstaller", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.samsung.android.lool",
    };
    private static final String[] SETTINGS_DANGER = {
            "Kaldır", "Uninstall", "Durmaya zorla", "Zorla durdur", "Force stop", "durdur", "Stop",
            "Devre dışı", "Disable", "Kapat", "Turn off", "Kullan", "Use ", "Temizle", "Clear",
    };
    private static final String[] LAUNCHER_DANGER = {"Kaldır", "Uninstall"};

    /** Tarayıcı paketi → adres çubuğunun view id'leri (paket önekisiz). */
    private static final Map<String, String[]> BROWSERS = new HashMap<>();

    static {
        String[] chromium = {"url_bar"};
        for (String p : new String[]{
                "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
                "org.chromium.chrome", "com.brave.browser", "com.brave.browser_beta",
                "com.microsoft.emmx", "com.vivaldi.browser", "com.kiwibrowser.browser",
                "com.google.android.apps.chrome"}) {
            BROWSERS.put(p, chromium);
        }
        String[] firefox = {"mozac_browser_toolbar_url_view", "url_bar_title"};
        BROWSERS.put("org.mozilla.firefox", firefox);
        BROWSERS.put("org.mozilla.firefox_beta", firefox);
        BROWSERS.put("org.mozilla.fenix", firefox);
        BROWSERS.put("org.mozilla.focus", new String[]{"mozac_browser_toolbar_url_view", "display_url"});
        BROWSERS.put("com.sec.android.app.sbrowser", new String[]{"location_bar_edit_text"});
        BROWSERS.put("com.sec.android.app.sbrowser.beta", new String[]{"location_bar_edit_text"});
        String[] opera = {"url_field"};
        BROWSERS.put("com.opera.browser", opera);
        BROWSERS.put("com.opera.mini.native", opera);
        BROWSERS.put("com.opera.gx", opera);
        BROWSERS.put("com.mi.globalbrowser", new String[]{"url", "search_hint"});
        BROWSERS.put("com.android.browser", new String[]{"url", "search_hint"});
        BROWSERS.put("com.yandex.browser", new String[]{"bro_omnibar_address_title_text"});
        BROWSERS.put("com.duckduckgo.mobile.android", new String[]{"omnibarTextInput"});
    }

    /** Yeni konum bu süre boyunca aynı okunursa katman oraya taşınır. */
    private static final long MOVE_SETTLE_MS = 60;
    /** Sekmeler bu süre boyunca hiç okunamazsa katman kaldırılır. */
    private static final long HIDE_SETTLE_MS = 300;
    private static final long PLACEMENT_CHECK_MS = 250;
    private static final long SETTLE_BEFORE_CHECK_MS = 200;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private final View[] overlays = new View[BLOCKED_TABS.length];
    private final Rect[] overlayRects = new Rect[BLOCKED_TABS.length];
    private final boolean[] overlayVisible = new boolean[BLOCKED_TABS.length];

    // Katman penceresinin sistem tarafından kaydırılmasına karşı düzeltme (checkPlacement).
    private final int[] offX = new int[BLOCKED_TABS.length];
    private final int[] offY = new int[BLOCKED_TABS.length];
    private final long[] placedAt = new long[BLOCKED_TABS.length];
    private final boolean[] driftSeen = new boolean[BLOCKED_TABS.length];
    private final int[] driftDx = new int[BLOCKED_TABS.length];
    private final int[] driftDy = new int[BLOCKED_TABS.length];
    private final boolean[] fixApplied = new boolean[BLOCKED_TABS.length];
    private final boolean[] fixBlocked = new boolean[BLOCKED_TABS.length];
    private boolean placementCheckPending;

    private boolean targetInFront;
    private boolean refreshPending;
    private Rect[] shownRects;
    private Rect[] pendingRects;
    private boolean hasPending;
    private long pendingSince;
    private long lastRedirectAt;
    /** Bekleyen yenilemenin çalışacağı an; daha erken isteyen olay onu öne çekebilir. */
    private long refreshDueAt;
    /** Son sıkı mod taramasından beri pencere / iletişim kutusu değişti mi. */
    private boolean windowStateChanged = true;
    private long lastGuardScanAt;
    /** Pencere kimliği → paket. İlgisiz uygulamaların ağacı her yenilemede istenmesin. */
    private final Map<Integer, String> windowPkg = new HashMap<>();
    /** Son okunan alt menü: sekme dilimleri (soldan sağa) ve güvenli sekme. Hızlı yönlendirme için. */
    private Rect[] lastSlots;
    private int lastBarTop;
    private int lastBarBottom;
    private AccessibilityNodeInfo lastSafeNode;
    private Rect lastSafeRect;
    private long lastFastRedirectAt;
    private long lastGuardAt;
    private long lastGuardToastAt;
    private String lastBlockedHost;
    private long lastSiteBlockAt;
    private static final long GENERIC_SCAN_MS = 150;
    /** Engelli site açıkken tarayıcının üstünü kaplayan tam ekran katman. */
    private View siteBlockView;
    private TextView siteBlockHostView;
    private String siteBlockHost;
    /** Katmanı tetikleyen tarayıcı ve şu an öndeki uygulama (pencere değişim olaylarından). */
    private String siteBlockPkg;
    private String foregroundPkg;
    private long siteBlockShownAt;
    /** Bu yenilemede: bir tarayıcı görüldü mü, adresi okundu mu, engelli site bulundu mu. */
    private boolean browserSeen;
    private boolean siteEvaluated;
    private boolean siteHit;
    private String lastScanPkg;
    private long lastScanAt;

    private final Runnable refreshRunnable = () -> {
        refreshPending = false;
        refresh();
    };
    private final Runnable placementCheck = this::checkPlacement;
    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (targetInFront) handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        scheduleRefresh(0);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null) {
            rememberPackage(pkg.toString());
            noteForeground(pkg.toString());
        }
        if ((type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_SELECTED)
                && isTarget(this, pkg) && isBlockedTabEvent(event)) {
            fastRedirect();
        }
        boolean stateChange = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        if (stateChange) windowStateChanged = true;
        // Her uygulamanın her olayında hızlı tarama yapmak ana ekranı ve son uygulamalar ekranını
        // yavaşlatıyordu (sorgular o uygulamanın arayüz iş parçacığında çalışır; dokunuşlar
        // kaçıyordu). Hızlı tarama yalnızca C2, tarayıcılar ve açık bir engel katmanı için.
        long delay;
        String p = pkg != null ? pkg.toString() : null;
        if (p != null && (isTarget(this, p) || BROWSERS.containsKey(p) || BrowserApps.isBrowser(this, p))) {
            delay = 16;
        } else if (targetInFront || siteBlockView != null) {
            delay = 16; // C2 öndeyken klavye / gezinme çubuğu olayları da katmanın yerini etkiler
        } else if (stateChange) {
            delay = 60; // uygulama değişti: C2'den çıkıldıysa katmanlar çabuk kalksın
        } else {
            delay = 300;
        }
        requestRefresh(delay);
    }

    /**
     * Throttle: bekleyen yenileme varsa ertelenmez (animasyon boyunca yağan olaylar onu sürekli
     * ileri itmesin), yalnızca daha erken isteyen bir olay onu öne çeker.
     */
    private void requestRefresh(long delayMs) {
        long due = SystemClock.uptimeMillis() + delayMs;
        if (refreshPending && refreshDueAt <= due) return;
        scheduleRefresh(delayMs);
    }

    private void scheduleRefresh(long delayMs) {
        handler.removeCallbacks(refreshRunnable);
        refreshPending = true;
        refreshDueAt = SystemClock.uptimeMillis() + delayMs;
        handler.postDelayed(refreshRunnable, delayMs);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        targetInFront = false;
        refreshPending = false;
        placementCheckPending = false;
        handler.removeCallbacksAndMessages(null);
        removeOverlays();
        removeSiteBlock();
        shownRects = null;
        hasPending = false;
    }

    // ---------------------------------------------------------------- ana döngü

    private void refresh() {
        boolean strict = isStrictActive(this);
        boolean sites = SiteBlocklist.isEnabled(this);
        long nowMs = SystemClock.uptimeMillis();
        // Sıkı mod ekranın tamamında metin arar (pahalı): pencere değişince ya da saniyede bir.
        boolean guardNow = strict && (windowStateChanged || nowMs - lastGuardScanAt >= 1000);
        if (guardNow) {
            windowStateChanged = false;
            lastGuardScanAt = nowMs;
        }
        browserSeen = false;
        siteEvaluated = false;
        siteHit = false;
        Rect ime = null;
        int navBarTop = Integer.MAX_VALUE;
        // Hedef uygulamanın tüm pencereleri: arama önerileri gibi açılır pencereler ana pencerenin
        // önüne geçebiliyor, alt menü ise ana pencerede kalıyor. Hepsine bakmak gerekiyor.
        List<AccessibilityNodeInfo> targetRoots = new ArrayList<>();
        List<Rect> targetWins = new ArrayList<>();

        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException e) {
            windows = Collections.emptyList();
        }

        // Bölünmüş ekranda hedef uygulama aktif pencere olmayabilir; tüm pencerelere bak.
        for (AccessibilityWindowInfo wi : windows) {
            int type = wi.getType();
            if (type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                Rect r = new Rect();
                wi.getBoundsInScreen(r);
                if (!r.isEmpty()) {
                    if (ime == null) ime = r;
                    else ime.union(r);
                }
                continue;
            }
            if (type != AccessibilityWindowInfo.TYPE_APPLICATION && type != AccessibilityWindowInfo.TYPE_SYSTEM) {
                continue;
            }
            if (type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                Rect r = new Rect();
                wi.getBoundsInScreen(r);
                if (looksLikeNavigationBar(r)) navBarTop = Math.min(navBarTop, r.top);
            }
            String knownPkg = windowPkg.get(wi.getId());
            if (knownPkg != null && !isInteresting(knownPkg, guardNow, sites)) continue;
            AccessibilityNodeInfo root = wi.getRoot();
            if (root == null) continue;
            CharSequence pkg = root.getPackageName();
            if (pkg != null) {
                if (windowPkg.size() > 64) windowPkg.clear();
                windowPkg.put(wi.getId(), pkg.toString());
            }
            if (type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                if (isTarget(this, pkg)) {
                    Rect r = new Rect();
                    wi.getBoundsInScreen(r);
                    targetRoots.add(root);
                    targetWins.add(r);
                }
                if (sites) checkSite(root, pkg);
            }
            if (guardNow) guard(root, pkg);
        }

        if (windows.isEmpty()) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return; // geçiş anı; mevcut durumu koru, heartbeat tekrar dener
            CharSequence pkg = root.getPackageName();
            if (guardNow) guard(root, pkg);
            if (sites) checkSite(root, pkg);
            if (isTarget(this, pkg)) {
                Rect r = new Rect();
                root.getBoundsInScreen(r);
                targetRoots.add(root);
                targetWins.add(r);
            }
        }

        updateSiteBlock(sites);

        if (targetRoots.isEmpty()) {
            leaveTarget();
            return;
        }
        if (!targetInFront) {
            targetInFront = true;
            handler.removeCallbacks(heartbeat);
            handler.postDelayed(heartbeat, 250);
        }

        List<AccessibilityNodeInfo> tabs = null;
        Rect targetWin = targetWins.get(0);
        for (int i = 0; i < targetRoots.size() && tabs == null; i++) {
            tabs = findBottomTabs(targetRoots.get(i), targetWins.get(i), ime);
            if (tabs != null) targetWin = targetWins.get(i);
        }
        if (tabs != null) rememberTabs(tabs, targetWin);

        // Katmanlar bu çizginin altına inmez: altında klavye ya da sistemin geri/ana ekran tuşları var.
        int floor = Math.min(targetWin.bottom, navBarTop);
        if (ime != null && ime.top > targetWin.top && ime.top < floor
                && ime.left < targetWin.right && ime.right > targetWin.left) {
            floor = ime.top;
        }

        boolean detectionWorks = prefs(this).getBoolean(KEY_TABS_DETECTED, false);
        Rect[] candidate = null;
        if (tabs != null) {
            if (!detectionWorks) prefs(this).edit().putBoolean(KEY_TABS_DETECTED, true).apply();
            detectionWorks = true;
            candidate = coverRects(tabs, targetWin, floor);
        } else if (!detectionWorks && ime == null) {
            // Sekmeler ağaçtan hiç okunamıyorsa ekran geometrisine göre tahmin et.
            // Klavye açıkken yapma, yoksa klavye tuşlarını kapatırız.
            candidate = new Rect[BLOCKED_TABS.length];
            for (int i = 0; i < BLOCKED_TABS.length; i++) candidate[i] = fallbackTabRect(targetWin, floor, BLOCKED_TABS[i]);
        }
        applyStable(candidate);
        redirectIfOnBlockedTab(targetRoots, tabs, targetWin, floor, ime != null, detectionWorks);
        if (!placementCheckPending) {
            placementCheckPending = true;
            handler.postDelayed(placementCheck, PLACEMENT_CHECK_MS);
        }
    }

    /** Bu pencerenin ağacını okumak gerekiyor mu: C2, tarayıcı ya da sıkı modun koruduğu ekran. */
    private boolean isInteresting(String pkg, boolean guardNow, boolean sites) {
        if (isTarget(this, pkg)) return true;
        if (sites && (BROWSERS.containsKey(pkg) || BrowserApps.isBrowser(this, pkg))) return true;
        return guardNow && isGuarded(pkg);
    }

    private static boolean isGuarded(String pkg) {
        return contains(SETTINGS_PKGS, pkg) || pkg.contains("launcher") || pkg.equals("com.miui.home");
    }

    private void leaveTarget() {
        targetInFront = false;
        lastSlots = null;
        lastSafeNode = null;
        lastSafeRect = null;
        handler.removeCallbacks(heartbeat);
        handler.removeCallbacks(placementCheck);
        placementCheckPending = false;
        hideOverlays();
        shownRects = null;
        hasPending = false;
    }

    /**
     * Geri tuşu, klavye, gezinme çubuğu ya da ekran animasyonları sırasında sekmelerin konumu
     * bir an için farklı okunabiliyor, ağaç yarım güncellendiğinde hiç okunamayabiliyor.
     * - Hiçbir katman yokken sekmeler bulununca beklemeden kapatılır.
     * - Taşıma, yeni konum MOVE_SETTLE_MS boyunca değişmeden okununca yapılır; o sırada eski
     *   yerinde kalır, araya boş an girmez.
     * - Sekmeler kaybolunca katman hemen kalkmaz, HIDE_SETTLE_MS boyunca hiç görünmezlerse kalkar;
     *   anlık okuma boşlukları yanıp sönmeye ve dokunulabilir aralığa yol açmasın.
     */
    private void applyStable(Rect[] candidate) {
        if (allNull(candidate)) candidate = null;
        long now = SystemClock.uptimeMillis();
        if (similar(candidate, shownRects)) {
            hasPending = false;
            return;
        }
        if (allNull(shownRects) && candidate != null) {
            show(candidate);
            hasPending = false;
            scheduleRefresh(MOVE_SETTLE_MS); // ilk konum animasyon ortasında okunmuş olabilir; doğrula
            return;
        }
        if (candidate != null) {
            Rect[] bridge = bridge(shownRects, candidate);
            if (bridge == null) {
                // Büyük sıçrama (klavye açıldı, menü yukarı çıktı): eski yerde beklemek sekmeyi
                // açıkta bırakır. Hemen taşı, sonraki okumalar konumu düzeltir.
                show(candidate);
                hasPending = false;
                scheduleRefresh(MOVE_SETTLE_MS);
                return;
            }
            // Küçük kayma: oturana kadar eski ve yeni yerin birleşimini kapla; aradaki geçişte
            // dokunulabilir boşluk kalmasın. shownRects değişmez, oturunca tam yere geçilir.
            placeOverlays(bridge);
        }
        if (!hasPending || !similar(candidate, pendingRects)) {
            pendingRects = candidate;
            pendingSince = now;
            hasPending = true;
        }
        long wait = (candidate == null ? HIDE_SETTLE_MS : MOVE_SETTLE_MS) - (now - pendingSince);
        if (wait <= 0) {
            show(candidate);
            hasPending = false;
            scheduleRefresh(MOVE_SETTLE_MS);
        } else {
            scheduleRefresh(wait);
        }
    }

    /**
     * Eski ve yeni konumun sekme sekme birleşimi. Birleşim sekmenin iki katından uzunsa ya da
     * yana kayıyorsa (başka içeriği kapatır) null döner; o zaman doğrudan yeni yere geçilir.
     */
    private Rect[] bridge(Rect[] from, Rect[] to) {
        if (from == null || to == null || from.length != to.length) return null;
        Rect[] out = new Rect[to.length];
        for (int i = 0; i < to.length; i++) {
            Rect a = from[i];
            Rect b = to[i];
            if (a == null || b == null) {
                out[i] = b != null ? new Rect(b) : (a != null ? new Rect(a) : null);
                continue;
            }
            Rect u = new Rect(a);
            u.union(b);
            int h = Math.max(a.height(), b.height());
            if (u.height() > h * 2 || u.width() > Math.max(a.width(), b.width()) + dp(16)) return null;
            out[i] = u;
        }
        return out;
    }

    private static boolean allNull(Rect[] rects) {
        if (rects == null) return true;
        for (Rect r : rects) if (r != null) return false;
        return true;
    }

    private void show(Rect[] rects) {
        if (rects == null) {
            hideOverlays();
        } else {
            placeOverlays(rects);
        }
        shownRects = rects;
    }

    private boolean similar(Rect[] a, Rect[] b) {
        if (a == null || b == null) return a == b;
        if (a.length != b.length) return false;
        int tol = dp(4);
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null || b[i] == null) {
                if (a[i] != b[i]) return false;
                continue;
            }
            if (Math.abs(a[i].left - b[i].left) > tol || Math.abs(a[i].top - b[i].top) > tol
                    || Math.abs(a[i].right - b[i].right) > tol || Math.abs(a[i].bottom - b[i].bottom) > tol) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- sekme bulma

    /**
     * Alt menüdeki 5 sekmeyi soldan sağa döndürür. Klavye açıkken menü klavyenin üstüne
     * çıkabildiği için "alt kısım", pencerenin klavyeden yukarıda kalan bölümüne göre hesaplanır.
     */
    private List<AccessibilityNodeInfo> findBottomTabs(AccessibilityNodeInfo root, Rect win, Rect ime) {
        int w = win.width();
        int areaBottom = win.bottom;
        if (ime != null && ime.top > win.top && ime.top < win.bottom
                && ime.left < win.right && ime.right > win.left) {
            areaBottom = ime.top;
        }
        int h = areaBottom - win.top;
        if (w <= 0 || h <= 0) return null;

        List<AccessibilityNodeInfo> candidates = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> stack = new ArrayDeque<>();
        stack.push(root);
        Rect b = new Rect();
        int visited = 0;
        while (!stack.isEmpty() && visited++ < 4000) {
            AccessibilityNodeInfo n = stack.pop();
            n.getBoundsInScreen(b);
            if (b.bottom < win.top + h * 0.6f) continue; // altı ekranın üst kısmında kalan dalı atla
            if (n.isVisibleToUser()
                    && b.top >= win.top + h * 0.75f
                    && b.height() >= dp(32) && b.height() <= dp(120)
                    && b.width() >= w / 8 && b.width() <= w / 3) {
                candidates.add(n);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) stack.push(c);
            }
        }

        List<AccessibilityNodeInfo> best = null;
        int bestY = Integer.MIN_VALUE;
        for (AccessibilityNodeInfo anchor : candidates) {
            int cy = bounds(anchor).centerY();
            if (cy <= bestY) continue;
            List<AccessibilityNodeInfo> row = new ArrayList<>();
            for (AccessibilityNodeInfo c : candidates) {
                if (Math.abs(bounds(c).centerY() - cy) <= dp(12)) row.add(c);
            }
            List<AccessibilityNodeInfo> cols = toColumns(row);
            if (cols.size() == TAB_COUNT
                    && bounds(cols.get(0)).left <= win.left + w / 10
                    && bounds(cols.get(TAB_COUNT - 1)).right >= win.right - w / 10) {
                best = cols;
                bestY = cy;
            }
        }
        return best;
    }

    /** Aynı sütundaki iç içe düğümleri tekilleştirir (en genişi kalır), soldan sağa sıralar. */
    private List<AccessibilityNodeInfo> toColumns(List<AccessibilityNodeInfo> row) {
        List<AccessibilityNodeInfo> sorted = new ArrayList<>(row);
        Collections.sort(sorted, (a, c) -> {
            int byWidth = bounds(c).width() - bounds(a).width();
            if (byWidth != 0) return byWidth;
            return Boolean.compare(c.isClickable(), a.isClickable());
        });
        List<AccessibilityNodeInfo> cols = new ArrayList<>();
        for (AccessibilityNodeInfo n : sorted) {
            Rect r = bounds(n);
            boolean overlaps = false;
            for (AccessibilityNodeInfo k : cols) {
                Rect o = bounds(k);
                int overlap = Math.min(r.right, o.right) - Math.max(r.left, o.left);
                if (overlap > Math.min(r.width(), o.width()) / 2) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) cols.add(n);
        }
        Collections.sort(cols, (a, c) -> bounds(a).left - bounds(c).left);
        return cols;
    }

    /**
     * Engelli sekmelerin kaplanacağı alanlar: sekmenin tüm dilimi (komşu sekmeyle arasındaki
     * boşluğun ortasına kadar) ve menü çubuğunun tüm yüksekliği. Yalnızca sekme düğümünü
     * kaplamak kenarlarda dokunulabilir şeritler bırakıyordu. floor'un altına inilmez;
     * tamamen altında kalan sekmenin (ör. klavyenin arkasında) katmanı null olur.
     */
    private Rect[] coverRects(List<AccessibilityNodeInfo> tabs, Rect win, int floor) {
        Rect[] cols = new Rect[TAB_COUNT];
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int i = 0; i < TAB_COUNT; i++) {
            cols[i] = bounds(tabs.get(i));
            top = Math.min(top, cols[i].top);
            bottom = Math.max(bottom, cols[i].bottom);
        }
        Rect bar = barBounds(tabs.get(0), cols, top, bottom);
        if (bar != null) {
            top = Math.max(Math.min(top, bar.top), top - dp(24));
            bottom = Math.min(Math.max(bottom, bar.bottom), bottom + dp(12));
        }
        top -= dp(2);
        bottom = Math.min(bottom + dp(2), floor);

        Rect[] out = new Rect[BLOCKED_TABS.length];
        for (int k = 0; k < BLOCKED_TABS.length; k++) {
            int i = BLOCKED_TABS[k];
            int left = i == 0 ? Math.min(win.left, cols[0].left) : (cols[i - 1].right + cols[i].left) / 2;
            int right = i == TAB_COUNT - 1 ? Math.max(win.right, cols[i].right) : (cols[i].right + cols[i + 1].left) / 2;
            if (bottom - top >= dp(16) && right > left) out[k] = new Rect(left, top, right, bottom);
        }
        return out;
    }

    /** Sekmeleri içeren menü çubuğu (ortak üst düğüm); sekmelerden çok daha yüksekse yok sayılır. */
    private Rect barBounds(AccessibilityNodeInfo first, Rect[] cols, int top, int bottom) {
        AccessibilityNodeInfo n = first.getParent();
        for (int depth = 0; depth < 4 && n != null; depth++, n = n.getParent()) {
            Rect r = bounds(n);
            if (r.left <= cols[0].left && r.right >= cols[TAB_COUNT - 1].right) {
                return r.height() <= (bottom - top) + dp(48) ? r : null;
            }
        }
        return null;
    }

    /** Sistemin alttaki gezinme çubuğu: geri / ana ekran tuşları ya da hareket çizgisi. */
    private boolean looksLikeNavigationBar(Rect r) {
        if (wm == null || r.isEmpty()) return false;
        Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        return r.height() <= dp(96) && r.bottom >= size.y - dp(2) && r.top > size.y / 2
                && r.width() >= size.x / 2;
    }

    private Rect fallbackTabRect(Rect win, int floor, int index) {
        int tabWidth = win.width() / TAB_COUNT;
        int left = win.left + index * tabWidth;
        return new Rect(left, floor - dp(64), left + tabWidth, floor);
    }

    // ---------------------------------------------------------------- yönlendirme

    private void redirectIfOnBlockedTab(List<AccessibilityNodeInfo> roots, List<AccessibilityNodeInfo> tabs,
                                        Rect win, int floor, boolean imeVisible, boolean detectionWorks) {
        boolean onBlocked = false;
        if (tabs != null) {
            for (int idx : BLOCKED_TABS) {
                if (hasSelected(tabs.get(idx), 3)) onBlocked = true;
            }
        }
        for (int i = 0; i < roots.size() && !onBlocked; i++) {
            if (looksLikeShuffle(roots.get(i))) onBlocked = true;
        }
        if (!onBlocked) return;

        long now = SystemClock.uptimeMillis();
        if (now - lastRedirectAt < 1200) return;
        lastRedirectAt = now;

        if (tabs != null) {
            AccessibilityNodeInfo safe = tabs.get(SAFE_TAB);
            if (!clickNodeOrAncestor(safe)) tap(bounds(safe));
        } else if (!detectionWorks && !imeVisible) {
            tap(fallbackTabRect(win, floor, SAFE_TAB));
        }
    }

    /** Alt menünün son konumunu sakla: tıklama olayı geldiğinde ağacı yeniden taramadan karar verilir. */
    private void rememberTabs(List<AccessibilityNodeInfo> tabs, Rect win) {
        Rect[] cols = new Rect[TAB_COUNT];
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        for (int i = 0; i < TAB_COUNT; i++) {
            cols[i] = bounds(tabs.get(i));
            top = Math.min(top, cols[i].top);
            bottom = Math.max(bottom, cols[i].bottom);
        }
        Rect[] slots = new Rect[TAB_COUNT];
        for (int i = 0; i < TAB_COUNT; i++) {
            int left = i == 0 ? Math.min(win.left, cols[0].left) : (cols[i - 1].right + cols[i].left) / 2;
            int right = i == TAB_COUNT - 1 ? Math.max(win.right, cols[i].right) : (cols[i].right + cols[i + 1].left) / 2;
            slots[i] = new Rect(left, top, right, bottom);
        }
        lastSlots = slots;
        lastBarTop = top - dp(24);
        lastBarBottom = bottom + dp(12);
        lastSafeNode = tabs.get(SAFE_TAB);
        lastSafeRect = cols[SAFE_TAB];
    }

    /**
     * Tıklanan / seçilen görünüm engelli bir sekmenin diliminde mi? Katmanın arasından bir dokunuş
     * sızarsa (katman yer değiştirirken) olay sekme açılmadan önce gelir.
     */
    private boolean isBlockedTabEvent(AccessibilityEvent event) {
        Rect[] slots = lastSlots;
        if (slots == null) return false;
        AccessibilityNodeInfo src = event.getSource();
        if (src == null) return false;
        Rect b = bounds(src);
        if (b.isEmpty() || b.height() > dp(140)) return false;
        int cx = b.centerX();
        int cy = b.centerY();
        if (cy < lastBarTop || cy > lastBarBottom) return false;
        for (int idx : BLOCKED_TABS) {
            if (cx >= slots[idx].left && cx < slots[idx].right) return true;
        }
        return false;
    }

    /** Engelli sekmeye dokunulduğu anda, ekran taraması beklenmeden 2. sekmeye geç. */
    private void fastRedirect() {
        long now = SystemClock.uptimeMillis();
        if (now - lastFastRedirectAt < 300) return;
        lastFastRedirectAt = now;
        AccessibilityNodeInfo safe = lastSafeNode;
        boolean done = false;
        if (safe != null && safe.refresh()) done = clickNodeOrAncestor(safe);
        if (!done && lastSafeRect != null) tap(lastSafeRect);
        scheduleRefresh(0);
    }

    private boolean looksLikeShuffle(AccessibilityNodeInfo root) {
        return hasText(root, "Shuffle") && hasText(root, "Filtrele");
    }

    private boolean hasSelected(AccessibilityNodeInfo n, int depth) {
        if (n == null) return false;
        if (n.isSelected()) return true;
        if (depth <= 0) return false;
        for (int i = 0; i < n.getChildCount(); i++) {
            if (hasSelected(n.getChild(i), depth - 1)) return true;
        }
        return false;
    }

    private boolean clickNodeOrAncestor(AccessibilityNodeInfo n) {
        for (int i = 0; i < 4 && n != null; i++, n = n.getParent()) {
            if (n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    private void tap(Rect r) {
        Path p = new Path();
        p.moveTo(r.centerX(), r.centerY());
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 50))
                .build();
        dispatchGesture(g, null, null);
    }

    // ---------------------------------------------------------------- site engelleme

    private void checkSite(AccessibilityNodeInfo root, CharSequence pkgCs) {
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        String[] ids = BROWSERS.get(pkg);
        if (ids == null && !BrowserApps.isBrowser(this, pkg)) return;
        browserSeen = true;
        StringBuilder dbg = new StringBuilder(pkg);
        if (ids != null) {
            int found = 0;
            for (String id : ids) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id);
                if (nodes == null) continue;
                for (AccessibilityNodeInfo n : nodes) {
                    found++;
                    siteEvaluated = true;
                    String host = blockedHostIn(n, dbg, id);
                    if (host != null) {
                        recordSiteDebug(dbg.append("\n→ engellendi: ").append(host));
                        blockSite(root, pkg, host);
                        return;
                    }
                }
            }
            if (found == 0) dbg.append("\nBilinen adres çubuğu (").append(String.join(", ", ids)).append(") yok");
        }
        // Bilinen kimlikler tutmadıysa (tarayıcı güncellenmiş, ya da listede olmayan bir tarayıcı)
        // adres çubuğuna benzeyen tüm alanlara bak. Ağaç taraması pahalı; tarayıcı başına seyrek yap.
        long now = SystemClock.uptimeMillis();
        if (pkg.equals(lastScanPkg) && now - lastScanAt < GENERIC_SCAN_MS) return;
        lastScanPkg = pkg;
        lastScanAt = now;
        siteEvaluated = true;
        String host = scanForBlockedHost(root, dbg);
        if (host != null) {
            recordSiteDebug(dbg.append("\n→ engellendi: ").append(host));
            blockSite(root, pkg, host);
        } else {
            recordSiteDebug(dbg.append("\n→ listede eşleşme yok"));
        }
    }

    /**
     * Görünümün metni (ya da açıklaması) listedeki bir siteyse alan adını döndürür.
     * Tanılama için ne okunduğunu dbg'ye yazar.
     */
    private String blockedHostIn(AccessibilityNodeInfo n, StringBuilder dbg, String label) {
        CharSequence[] texts = {n.getText(), n.getContentDescription()};
        boolean focused = n.isFocused();
        for (CharSequence text : texts) {
            if (text == null || text.length() == 0) continue;
            String raw = text.toString();
            String host = SiteBlocklist.hostOf(raw);
            dbg.append("\n").append(label).append(": \"")
                    .append(raw.length() > 60 ? raw.substring(0, 60) + "…" : raw).append("\"");
            if (focused) {
                dbg.append(" (yazılıyor, atlandı)");
                return null; // kullanıcı adres çubuğuna yazıyor; sayfa açılınca yakalarız
            }
            if (host == null) {
                dbg.append(" → site değil");
                continue;
            }
            if (SiteBlocklist.match(this, host) != null) return host;
            dbg.append(" → ").append(host).append(" listede yok");
        }
        return null;
    }

    private String scanForBlockedHost(AccessibilityNodeInfo root, StringBuilder dbg) {
        ArrayDeque<AccessibilityNodeInfo> stack = new ArrayDeque<>();
        stack.push(root);
        int visited = 0;
        int notes = 0;
        while (!stack.isEmpty() && visited++ < 2500) {
            AccessibilityNodeInfo n = stack.pop();
            // WebView'in içi sayfa içeriğidir; oradaki bağlantı metinleri adres çubuğu değil.
            CharSequence cls = n.getClassName();
            if (cls != null && cls.toString().endsWith("WebView")) continue;
            String viewId = n.getViewIdResourceName();
            if (n.isVisibleToUser() && BrowserApps.looksLikeAddressBar(viewId, cls)) {
                String label = viewId != null ? viewId.substring(viewId.indexOf('/') + 1) : String.valueOf(cls);
                StringBuilder local = notes < 4 ? dbg : new StringBuilder();
                int before = local.length();
                String host = blockedHostIn(n, local, label);
                if (local.length() > before) notes++;
                if (host != null) return host;
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                AccessibilityNodeInfo c = n.getChild(i);
                if (c != null) stack.push(c);
            }
        }
        if (notes == 0) dbg.append("\nAdres çubuğuna benzeyen metin bulunamadı");
        return null;
    }

    /** Ayarlar ekranındaki "Son okunan adres" kartı için. */
    public static volatile String siteDebug;
    public static volatile long siteDebugAt;

    private static void recordSiteDebug(CharSequence text) {
        siteDebug = text.toString();
        siteDebugAt = System.currentTimeMillis();
    }

    /**
     * Engelli site: tarayıcının üstüne hemen tam ekran katman koy. Katman erişilebilirlik
     * servisinin kendi penceresi olduğu için hiçbir izne takılmaz; MIUI gibi sistemler arka
     * plandan ekran açmayı sessizce engelliyor, eski yöntem (yeni ekran açmak) orada hiç
     * görünmeden başarısız oluyordu. Katman, site önde kaldığı sürece durur.
     */
    private void blockSite(AccessibilityNodeInfo root, String pkg, String host) {
        siteHit = true;
        boolean fresh = siteBlockView == null || !host.equals(siteBlockHost);
        if (fresh) siteBlockShownAt = SystemClock.uptimeMillis();
        siteBlockPkg = pkg;
        foregroundPkg = pkg;
        showSiteBlock(host);

        long now = SystemClock.uptimeMillis();
        if (host.equals(lastBlockedHost) && now - lastSiteBlockAt < 2500) return;
        lastBlockedHost = host;
        lastSiteBlockAt = now;
        if (fresh) SiteBlocklist.countBlock(this);

        // Başka bir uygulamanın içinde açılan Chrome sekmesiyse (Custom Tab) kapat düğmesine bas.
        List<AccessibilityNodeInfo> close = root.findAccessibilityNodeInfosByViewId(pkg + ":id/close_button");
        if (close != null) {
            for (AccessibilityNodeInfo c : close) {
                if (c.isVisibleToUser() && clickNodeOrAncestor(c)) return;
            }
        }
        // Aynı sekmede güvenli bir sayfaya gitmeyi dene (EXTRA_APPLICATION_ID sekmeyi yeniden
        // kullandırır). Sistem izin vermezse katman yine de siteyi kapalı tutar.
        Intent safe = new Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_URL))
                .setPackage(pkg)
                .putExtra(Browser.EXTRA_APPLICATION_ID, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(safe);
        } catch (RuntimeException ignored) {
        }
    }

    /** Yenileme sonunda: engelli site artık önde değilse katmanı kaldır. */
    private void updateSiteBlock(boolean sitesOn) {
        if (siteBlockView == null || siteHit) return;
        if (!sitesOn) {
            hideSiteBlock();
            return;
        }
        // Kullanıcı başka bir uygulamaya geçti (ana ekran, VOCD...): katmanı kaldır.
        if (foregroundPkg != null && siteBlockPkg != null && !foregroundPkg.equals(siteBlockPkg)
                && SystemClock.uptimeMillis() - siteBlockShownAt > 500) {
            hideSiteBlock();
            return;
        }
        // Katmanın altında kalan tarayıcı penceresi listede görünmeyebilir; etkin pencereden oku.
        if (!browserSeen) {
            AccessibilityNodeInfo active = getRootInActiveWindow();
            CharSequence pkg = active != null ? active.getPackageName() : null;
            if (pkg != null && pkg.toString().equals(siteBlockPkg)) {
                checkSite(active, pkg);
                if (siteHit) return;
            }
        }
        // Tarayıcının adresi okundu ve engelli değil (geri gidildi, başka siteye geçildi): kaldır.
        // Okunamadıysa (seyrek tarama sırası gelmedi, pencere görünmüyor) kararı bekle.
        if (browserSeen && siteEvaluated) hideSiteBlock();
    }

    /** Öndeki uygulamayı izle; sistem arayüzü ve klavye ön plan sayılmaz. */
    private void noteForeground(String pkg) {
        // VOCD'nin kendi katman penceresi de olay üretebilir; o ön plan değişimi değil.
        if (pkg.equals("com.android.systemui") || pkg.equals(getPackageName())) return;
        String ime = android.provider.Settings.Secure.getString(getContentResolver(),
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        if (ime != null && ime.startsWith(pkg + "/")) return;
        foregroundPkg = pkg;
        if (siteBlockView != null && siteBlockPkg != null && !pkg.equals(siteBlockPkg)) scheduleRefresh(0);
    }

    private void showSiteBlock(String host) {
        if (wm == null) return;
        siteBlockHost = host;
        try {
            if (siteBlockView == null) {
                siteBlockView = createSiteBlockView();
                wm.addView(siteBlockView, siteBlockParams());
            }
            siteBlockHostView.setText(host);
        } catch (RuntimeException e) {
            siteBlockView = null;
        }
    }

    private void hideSiteBlock() {
        removeSiteBlock();
    }

    private void removeSiteBlock() {
        if (siteBlockView != null && wm != null) {
            try {
                wm.removeView(siteBlockView);
            } catch (RuntimeException ignored) {
            }
        }
        siteBlockView = null;
        siteBlockHost = null;
        siteBlockPkg = null;
    }

    private View createSiteBlockView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFA0B1020);
        root.setClickable(true);
        root.setOnTouchListener((view, e) -> true); // alttaki sayfaya dokunuş geçmesin

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(28);
        col.setPadding(pad, pad, pad, pad);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_block);
        icon.setColorFilter(0xFFF87171);
        col.addView(icon, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView title = new TextView(this);
        title.setText("Bu site engelli");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(20);
        col.addView(title, tl);

        siteBlockHostView = new TextView(this);
        siteBlockHostView.setTextColor(0xFFCBD5E1);
        siteBlockHostView.setTextSize(16);
        siteBlockHostView.setTypeface(Typeface.MONOSPACE);
        siteBlockHostView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = dp(10);
        col.addView(siteBlockHostView, hl);

        TextView msg = new TextView(this);
        msg.setText("Bu siteyi kendin engelledin. Dürtü birkaç dakikada geçer; derin bir nefes al.");
        msg.setTextColor(0xFFE2E8F0);
        msg.setTextSize(16);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ml.topMargin = dp(16);
        col.addView(msg, ml);

        Button back = new Button(this);
        back.setText("Geri dön");
        back.setAllCaps(false);
        back.setOnClickListener(v -> performGlobalAction(GLOBAL_ACTION_BACK));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        bl.topMargin = dp(36);
        col.addView(back, bl);

        Button home = new Button(this);
        home.setText("Ana ekrana git");
        home.setAllCaps(false);
        home.setOnClickListener(v -> performGlobalAction(GLOBAL_ACTION_HOME));
        LinearLayout.LayoutParams hb = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        hb.topMargin = dp(12);
        col.addView(home, hb);

        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return root;
    }

    private WindowManager.LayoutParams siteBlockParams() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Odak almaz: geri tuşu tarayıcıya gider, tarayıcı geri gidince katman kalkar.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.windowAnimations = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            // Durum ve gezinme çubukları açık kalsın; ana ekran / geri tuşları çalışsın.
            lp.setFitInsetsTypes(WindowInsets.Type.systemBars());
        }
        return lp;
    }

    // ---------------------------------------------------------------- sıkı mod

    private void guard(AccessibilityNodeInfo root, CharSequence pkgCs) {
        if (pkgCs == null) return;
        String pkg = pkgCs.toString();
        String[] words;
        if (contains(SETTINGS_PKGS, pkg)) {
            words = SETTINGS_DANGER;
        } else if (pkg.contains("launcher") || pkg.equals("com.miui.home")) {
            words = LAUNCHER_DANGER;
        } else {
            return;
        }
        if (!hasText(root, getString(R.string.app_name))) return;
        boolean danger = hasText(root, getString(R.string.guard_marker));
        for (int i = 0; i < words.length && !danger; i++) danger = hasText(root, words[i]);
        if (!danger) return;

        long now = SystemClock.uptimeMillis();
        if (now - lastGuardAt < 800) return;
        lastGuardAt = now;
        performGlobalAction(GLOBAL_ACTION_HOME);
        if (now - lastGuardToastAt > 5000) {
            lastGuardToastAt = now;
            Toast.makeText(this, R.string.guard_toast, Toast.LENGTH_LONG).show();
        }
    }

    static boolean isStrictActive(Context ctx) {
        SharedPreferences p = prefs(ctx);
        return p.getBoolean(KEY_STRICT, true)
                && p.contains(KEY_PASS_HASH)
                && System.currentTimeMillis() > p.getLong(KEY_GRACE_UNTIL, 0);
    }

    // ---------------------------------------------------------------- overlay

    /**
     * Katman pencereleri bir kez eklenir ve sonra yalnızca yeri değiştirilir / gizlenir.
     * Her seferinde pencere ekleyip kaldırmak gecikmeye ve görünür kaymaya yol açıyordu.
     */
    private void placeOverlays(Rect[] rects) {
        if (wm == null) return;
        long now = SystemClock.uptimeMillis();
        for (int i = 0; i < rects.length; i++) {
            Rect r = rects[i];
            if (r == null || r.isEmpty()) {
                hideOverlay(i);
                continue;
            }
            try {
                if (overlays[i] == null) {
                    View v = createBlockView();
                    wm.addView(v, layoutParams(i, r, true));
                    overlays[i] = v;
                    resetDrift(i, now);
                } else if (!overlayVisible[i] || !r.equals(overlayRects[i])) {
                    overlays[i].setVisibility(View.VISIBLE);
                    wm.updateViewLayout(overlays[i], layoutParams(i, r, true));
                    resetDrift(i, now);
                }
                overlayRects[i] = new Rect(r);
                overlayVisible[i] = true;
            } catch (RuntimeException e) {
                overlays[i] = null;
                overlayRects[i] = null;
                overlayVisible[i] = false;
            }
        }
    }

    private void hideOverlays() {
        for (int i = 0; i < overlays.length; i++) hideOverlay(i);
    }

    private void hideOverlay(int i) {
        if (overlays[i] == null || !overlayVisible[i]) return;
        overlayVisible[i] = false;
        try {
            overlays[i].setVisibility(View.GONE);
            wm.updateViewLayout(overlays[i], layoutParams(i, overlayRects[i], false));
        } catch (RuntimeException e) {
            overlays[i] = null;
            overlayRects[i] = null;
        }
    }

    private void resetDrift(int i, long now) {
        placedAt[i] = now;
        driftSeen[i] = false;
        fixApplied[i] = false;
        fixBlocked[i] = false;
    }

    /**
     * Bazı Android sürümleri / ROM'lar katman penceresini istenen yerden kaydırıyor (sistem
     * çubukları, klavye). Pencerenin ekrandaki gerçek yerini okuyup farkı telafi ederiz.
     * Aynı fark iki ardışık kontrolde görülmeden düzeltmeyiz; düzeltme pencereyi hiç
     * oynatmadıysa (sistem sınırlıyorsa) geri alır, o konum için bir daha denemeyiz.
     */
    private void checkPlacement() {
        placementCheckPending = false;
        if (wm == null) return;
        long now = SystemClock.uptimeMillis();
        int[] loc = new int[2];
        for (int i = 0; i < overlays.length; i++) {
            View v = overlays[i];
            Rect want = overlayRects[i];
            if (v == null || want == null || !overlayVisible[i] || !v.isAttachedToWindow()) continue;
            if (now - placedAt[i] < SETTLE_BEFORE_CHECK_MS) continue; // yeni konumuna henüz geçmemiş olabilir
            v.getLocationOnScreen(loc);
            int dx = want.left - loc[0];
            int dy = want.top - loc[1];
            boolean off = Math.abs(dx) > 1 || Math.abs(dy) > 1;
            if (fixApplied[i]) {
                fixApplied[i] = false;
                if (off && dx == driftDx[i] && dy == driftDy[i]) {
                    offX[i] -= dx;
                    offY[i] -= dy;
                    fixBlocked[i] = true;
                    relayout(i, now);
                    continue;
                }
            }
            if (!off || fixBlocked[i]) {
                driftSeen[i] = false;
                continue;
            }
            if (!driftSeen[i] || dx != driftDx[i] || dy != driftDy[i]) {
                driftSeen[i] = true;
                driftDx[i] = dx;
                driftDy[i] = dy;
                continue;
            }
            driftSeen[i] = false;
            int nx = offX[i] + dx;
            int ny = offY[i] + dy;
            if (Math.abs(nx) > dp(64) || Math.abs(ny) > dp(64)) continue;
            offX[i] = nx;
            offY[i] = ny;
            fixApplied[i] = true;
            relayout(i, now);
        }
    }

    private void relayout(int i, long now) {
        try {
            wm.updateViewLayout(overlays[i], layoutParams(i, overlayRects[i], true));
            placedAt[i] = now;
        } catch (RuntimeException e) {
            overlays[i] = null;
            overlayRects[i] = null;
            overlayVisible[i] = false;
        }
    }

    private void removeOverlays() {
        for (int i = 0; i < overlays.length; i++) {
            if (overlays[i] != null && wm != null) {
                try {
                    wm.removeView(overlays[i]);
                } catch (RuntimeException ignored) {
                }
            }
            overlays[i] = null;
            overlayRects[i] = null;
            overlayVisible[i] = false;
        }
    }

    private View createBlockView() {
        FrameLayout box = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF20B1020);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), 0x40FFFFFF);
        box.setBackground(bg);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_block);
        int size = dp(24);
        box.addView(icon, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        box.setOnTouchListener((view, e) -> true); // dokunmayı yut
        return box;
    }

    private WindowManager.LayoutParams layoutParams(int i, Rect r, boolean touchable) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                r.width(), r.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = r.left + offX[i];
        lp.y = r.top + offY[i];
        lp.windowAnimations = 0;
        lp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+ pencereyi varsayılan olarak durum / gezinme çubuğu ve klavyeye göre
            // yerleştiriyor; çubuk ya da klavye açılıp kapandıkça katman kayıyordu.
            // x / y'yi doğrudan ekran koordinatı olarak kullan.
            lp.setFitInsetsTypes(0);
            lp.setFitInsetsSides(0);
            lp.setFitInsetsIgnoringVisibility(true);
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return lp;
    }

    // ---------------------------------------------------------------- yardımcılar

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void rememberPackage(String pkg) {
        if (pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        prefs(this).edit().putString(KEY_LAST_PKG, pkg).apply();
    }

    static Set<String> getTargets(Context ctx) {
        String raw = prefs(ctx).getString(KEY_TARGETS, DEFAULT_TARGETS);
        Set<String> out = new HashSet<>();
        for (String s : raw.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static boolean isTarget(Context ctx, CharSequence pkg) {
        if (pkg == null) return false;
        String p = pkg.toString();
        return p.contains("connected2") || getTargets(ctx).contains(p);
    }

    private static boolean hasText(AccessibilityNodeInfo root, String text) {
        List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText(text);
        return found != null && !found.isEmpty();
    }

    private static boolean contains(String[] arr, String s) {
        for (String a : arr) if (a.equals(s)) return true;
        return false;
    }

    private static Rect bounds(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return r;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

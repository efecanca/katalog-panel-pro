// ═══════════════════════════════════════════════════════════════════
// MainActivity.java — DEĞIŞTIRILECEK METODLAR
// Paket: com.fpro.app
// ═══════════════════════════════════════════════════════════════════

// ── 1. home() metodunu tamamen değiştir ───────────────────────────
// ESKİ home():
//   void home(){
//       cleanActivityLogs();
//       tab="Ana Sayfa";
//       setContentView(new DashboardCanvas(this));
//       checkStatus();
//   }
//
// YENİ home():

void home() {
    cleanActivityLogs();
    tab = "Ana Sayfa";
    setContentView(R.layout.activity_dashboard);
    setupDashboard();
    checkStatus();
}

// ── 2. Bu metodu MainActivity'ye ekle ─────────────────────────────
void setupDashboard() {
    // Header
    TextView tvHeaderUser = findViewById(R.id.tvHeaderUser);
    if (tvHeaderUser != null && loginUser != null && loginUser.length() > 0) {
        tvHeaderUser.setText(loginUser.toUpperCase(java.util.Locale.ROOT));
    }

    // API URL
    TextView tvApiUrl = findViewById(R.id.tvApiUrl);
    if (tvApiUrl != null) {
        String displayUrl = apiBase.replace("http://", "").replace("https://", "");
        tvApiUrl.setText("API: " + displayUrl);
    }

    // Stats
    TextView tvContacts = findViewById(R.id.tvStatContacts);
    if (tvContacts != null) tvContacts.setText(formatStat(contacts.size()));

    TextView tvFavLists = findViewById(R.id.tvStatFavLists);
    if (tvFavLists != null) tvFavLists.setText(formatStat(favLists.size()));

    TextView tvSentToday = findViewById(R.id.tvStatSentToday);
    if (tvSentToday != null) tvSentToday.setText(formatStat(sent.size()));

    TextView tvQueue = findViewById(R.id.tvStatQueue);
    if (tvQueue != null) tvQueue.setText(formatStat(queue.size()));

    // Connection status reference (checkStatus() will update this)
    connectionText = findViewById(R.id.tvConnectionStatus);

    // Activity logs
    populateDashboardActivities();

    // Tile click listeners — mevcut iş mantığını korur
    View tileRehber = findViewById(R.id.tileRehber);
    if (tileRehber != null) tileRehber.setOnClickListener(v -> favListsScreen());

    View tileMedya = findViewById(R.id.tileMedya);
    if (tileMedya != null) tileMedya.setOnClickListener(v -> mediaScreen());

    View tileGonderim = findViewById(R.id.tileGonderim);
    if (tileGonderim != null) tileGonderim.setOnClickListener(v -> sendScreen());

    View tileFavori = findViewById(R.id.tileFavori);
    if (tileFavori != null) tileFavori.setOnClickListener(v -> favListsScreen());

    View tileRaporlar = findViewById(R.id.tileRaporlar);
    if (tileRaporlar != null) tileRaporlar.setOnClickListener(v -> reportsScreen());

    View tileAyarlar = findViewById(R.id.tileAyarlar);
    if (tileAyarlar != null) tileAyarlar.setOnClickListener(v -> settingsScreen());

    // QR card
    View qrCard = findViewById(R.id.qrCard);
    if (qrCard != null) qrCard.setOnClickListener(v -> showMobileQrDialog());

    // User pill → settings
    View userPill = findViewById(R.id.userPill);
    if (userPill != null) userPill.setOnClickListener(v -> settingsScreen());
}

// ── 3. Bu yardımcı metodları ekle ────────────────────────────────

String formatStat(int n) {
    if (n >= 1000) {
        return String.format(java.util.Locale.US, "%,.0f", (double) n)
                     .replace(',', '.');
    }
    return String.valueOf(n);
}

void populateDashboardActivities() {
    try {
        org.json.JSONArray logs = getActivityLogs();

        int[] rowIds = {
            R.id.activityRow1,
            R.id.activityRow2,
            R.id.activityRow3
        };
        int[][] titleSubTimeIds = {
            {R.id.tvActivity1Title, R.id.tvActivity1Sub, R.id.tvActivity1Time},
            {R.id.tvActivity2Title, R.id.tvActivity2Sub, R.id.tvActivity2Time},
            {R.id.tvActivity3Title, R.id.tvActivity3Sub, R.id.tvActivity3Time}
        };

        // Varsayılan aktiviteleri gizle
        for (int rowId : rowIds) {
            View row = findViewById(rowId);
            if (row != null) row.setVisibility(View.GONE);
        }

        if (logs.length() == 0) {
            // Hiç log yoksa 1. satırı "henüz aktivite yok" olarak göster
            View row0 = findViewById(R.id.activityRow1);
            if (row0 != null) row0.setVisibility(View.VISIBLE);
            TextView t1 = findViewById(R.id.tvActivity1Title);
            TextView s1 = findViewById(R.id.tvActivity1Sub);
            TextView tm1 = findViewById(R.id.tvActivity1Time);
            if (t1 != null) t1.setText("Henüz aktivite yok");
            if (s1 != null) s1.setText("İşlem yaptıkça burada görünür");
            if (tm1 != null) tm1.setText("--:--");
            return;
        }

        int start = Math.max(0, logs.length() - 3);
        int row = 0;
        for (int i = start; i < logs.length() && row < 3; i++) {
            org.json.JSONObject o = logs.optJSONObject(i);
            if (o == null) continue;

            View rowView = findViewById(rowIds[row]);
            if (rowView != null) rowView.setVisibility(View.VISIBLE);

            TextView tvTitle = findViewById(titleSubTimeIds[row][0]);
            TextView tvSub   = findViewById(titleSubTimeIds[row][1]);
            TextView tvTime  = findViewById(titleSubTimeIds[row][2]);

            if (tvTitle != null) tvTitle.setText(o.optString("title", "Aktivite"));
            if (tvSub   != null) tvSub.setText(o.optString("sub", ""));
            if (tvTime  != null) tvTime.setText(o.optString("time", "--:--"));

            row++;
        }

    } catch (Exception ignored) {}
}

// ── 4. checkStatus() içinde bu satırı güncelle ──────────────────
// checkStatus() metodu zaten connectionText referansını kullanıyor.
// setupDashboard() içinde connectionText = findViewById(R.id.tvConnectionStatus)
// yaptığımız için checkStatus() mevcut haliyle çalışır.
// Sadece şu ek satırı checkStatus içindeki runOnUiThread bloklarına ekle:
//
//   View dot = findViewById(R.id.vStatusDot);
//   if (dot != null) {
//       dot.setBackground(getDrawable(ok
//           ? R.drawable.bg_status_dot_green
//           : R.drawable.bg_status_dot_red));
//   }
//   TextView tvSession = findViewById(R.id.tvSessionInfo);
//   if (tvSession != null) {
//       tvSession.setText(ok ? "Oturum: aktif" : "Oturum: kapalı");
//       tvSession.setTextColor(ok ? Color.rgb(33,211,102) : Color.rgb(239,68,68));
//   }

// ── 5. invalidateDashboard() — bu satırı koru, zaten çalışır ────
//   void invalidateDashboard(){
//       try{ if(getWindow()!=null && getWindow().getDecorView()!=null)
//            getWindow().getDecorView().invalidate(); }catch(Exception ignored){}
//   }
//
// Ek olarak, dashboard açıkken stat'ları da günceller:
//   void invalidateDashboard(){
//       try{
//           if(tab != null && tab.equals("Ana Sayfa")){
//               TextView tv = findViewById(R.id.tvStatContacts);
//               if(tv != null) tv.setText(formatStat(contacts.size()));
//               tv = findViewById(R.id.tvStatFavLists);
//               if(tv != null) tv.setText(formatStat(favLists.size()));
//               tv = findViewById(R.id.tvStatQueue);
//               if(tv != null) tv.setText(formatStat(queue.size()));
//               tv = findViewById(R.id.tvStatSentToday);
//               if(tv != null) tv.setText(formatStat(sent.size()));
//           }
//           if(getWindow()!=null) getWindow().getDecorView().invalidate();
//       }catch(Exception ignored){}
//   }


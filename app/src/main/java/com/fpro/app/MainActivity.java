
package com.fpro.app;
import android.provider.Settings;
import android.os.PowerManager;
import android.os.Build;
import android.content.Intent;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.util.Base64;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.provider.ContactsContract;
import android.content.*;
import android.content.pm.PackageManager;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {

    final int BG=Color.BLACK, CARD=Color.rgb(13,16,17), CARD2=Color.rgb(18,22,24);
    final int GREEN=Color.rgb(48,207,64), BLUE=Color.rgb(31,132,255), PURPLE=Color.rgb(142,86,255);
    final int RED=Color.rgb(239,68,68), YELLOW=Color.rgb(255,210,45), MUTED=Color.rgb(155,165,170);
    final int BORDER=Color.rgb(45,55,58);
    final String API="http://178.105.143.110:3001";
    String apiBase="http://178.105.143.110:3001";
    String loginUser="", apiToken="";
    static final int REQ_MEDIA=501;
    static final int REQ_ALBUM_BASE=600; // 600,601,602... her albüm için
    // Albüm sistemi: her albüm = {photos: [], caption: ""}
    ArrayList<ArrayList<String>> albums=new ArrayList<>(); // foto listeleri
    ArrayList<String> albumCaptions=new ArrayList<>(); // her albümün mesajı
    ArrayList<String> albumNames=new ArrayList<>(); // her albümün adı
    boolean albumSendMode=true; // true=albüm modu, false=tek medya modu
    int pickingAlbumIdx=-1; // hangi albüm için galeri açıldı

    LinearLayout root;
    String tab="Ana Sayfa", activeList="";
    ArrayList<C> contacts=new ArrayList<>(), filtered=new ArrayList<>();
    LinkedHashSet<String> selected=new LinkedHashSet<>(), editingPhones=new LinkedHashSet<>();
    ArrayList<String> media=new ArrayList<>(), reports=new ArrayList<>(), favLists=new ArrayList<>(), sent=new ArrayList<>(), queue=new ArrayList<>();
    LinkedHashSet<String> selectedFavLists=new LinkedHashSet<>();
    HashMap<String,String> favStatusCache=new HashMap<>();
    TextView connectionText, countText, sendButton, statusText, queueText, sentText, progressText, currentPersonText, etaText;
    volatile boolean waConnected=false;
    volatile String waStatus="● Durum kontrol ediliyor";
    ProgressBar sendProgress;
    CircularProgressView circularView;
    EditText searchBox, msgBox, personDelayBox, mediaDelayBox, delayMinBox, delayMaxBox;
    boolean manualMode=false; // false=OTO, true=Manuel
    ListView listView;
    ContactAdapter adapter;
    boolean listEditMode=false;
    volatile boolean sending=false, stop=false;
    // ── V2 Sunucu Kuyruğu ───────────────────────────────────────────
    boolean useServerQueueV2 = true;   // true=V2 varsayılan, false=eski motor (fallback)
    String  v2JobId          = null;   // sunucudan dönen jobId
    android.os.Handler v2PollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    Runnable v2PollRunnable  = null;
    // ────────────────────────────────────────────────────────────────
    long scheduledSendAt=0;
    Handler scheduleHandler=new Handler(Looper.getMainLooper());

    static class C { String n,p,note=""; C(String n,String p){this.n=n;this.p=p;} }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        loadSchedule();
        perms(false);
        if(!isLoggedIn()){ loginScreen(); return; }
        wipeRuntimeForUserSwitch();
        load();
        seedDefaults();
        loadContacts();
        syncFromServerSilent();
        // cloudPullFavLists kaldirildi - sadece acilista cekiliyor
        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
            FavSyncWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "fav_sync", ExistingPeriodicWorkPolicy.KEEP, syncWork); // Tüm kullanıcılar
        // cloudPullContacts kapatildi: rehber sadece cihazda kalir
        home();
    }

    void seedDefaults(){
        if(loginUser!=null && loginUser.equalsIgnoreCase("admin") && favLists.isEmpty()){
            favLists.add("VIP Müşteriler");
            favLists.add("Toptancılar");
            favLists.add("Mağazalar");
            favLists.add("Yeni Müşteriler");
            activeList="VIP Müşteriler";
            selectedFavLists.add(activeList);
            save();
        }
    }

    void perms(boolean force){
        if(Build.VERSION.SDK_INT>=33){
            ArrayList<String> ps=new ArrayList<>();
            if(force || checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_CONTACTS);
            if(force || checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_MEDIA_IMAGES);
            if(force || checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_MEDIA_VIDEO);
            if(!ps.isEmpty()) requestPermissions(ps.toArray(new String[0]),99);
        } else if(Build.VERSION.SDK_INT>=23){
            ArrayList<String> ps=new ArrayList<>();
            if(force || checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_CONTACTS);
            if(force || checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if(!ps.isEmpty()) requestPermissions(ps.toArray(new String[0]),99);
        }
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r!=99) loadContacts();
    }

    void load(){
        SharedPreferences sp=appPrefs();
        selected.clear(); media.clear(); reports.clear(); favLists.clear(); selectedFavLists.clear();
        addSplit(selected, sp.getString("selected",""), ",");
        addSplit(media, sp.getString("media",""), "\\|");
        addSplit(reports, sp.getString("reports",""), "\\|");
        addSplit(favLists, sp.getString("favLists",""), "\\|");
        // Albüm verilerini yükle
        try{
            String albumData=sp.getString("albumData","");
            if(!albumData.isEmpty()){
                albums.clear(); albumCaptions.clear(); albumNames.clear();
                org.json.JSONArray arr=new org.json.JSONArray(albumData);
                for(int i=0;i<arr.length();i++){
                    org.json.JSONObject a=arr.getJSONObject(i);
                    ArrayList<String> photos=new ArrayList<>();
                    org.json.JSONArray pArr=a.optJSONArray("photos");
                    if(pArr!=null) for(int j=0;j<pArr.length();j++) photos.add(pArr.getString(j));
                    albums.add(photos);
                    albumCaptions.add(a.optString("caption",""));
                    albumNames.add(a.optString("name",""));
                }
            }
        }catch(Exception ignored){}
        addSplit(selectedFavLists, sp.getString("selectedFavLists",""), "\\|");
        activeList=sp.getString("activeList", activeList); if(favLists.isEmpty()) activeList="";
        if(selectedFavLists.isEmpty() && activeList!=null && activeList.length()>0) selectedFavLists.add(activeList);
    }

    void save(){
        appPrefs().edit()
                .putString("selected",join(selected,","))
                .putString("media",join(media,"|"))
                .putString("reports",join(reports,"|"))
                .putString("favLists",join(favLists,"|"))
                .putString("activeList",activeList)
                .putString("selectedFavLists",join(selectedFavLists,"|"))
                .putBoolean("albumSendMode",albumSendMode)
                .apply();
        // Albüm verilerini kaydet
        try{
            org.json.JSONArray albumsJson=new org.json.JSONArray();
            for(int i=0;i<albums.size();i++){
                org.json.JSONObject a=new org.json.JSONObject();
                a.put("photos",new org.json.JSONArray(albums.get(i)));
                a.put("caption",i<albumCaptions.size()?albumCaptions.get(i):"");
                a.put("name",i<albumNames.size()?albumNames.get(i):"");
                albumsJson.put(a);
            }
            appPrefs().edit().putString("albumData",albumsJson.toString()).apply();
        }catch(Exception ignored){}
    }

    void addSplit(Collection<String> c,String s,String sep){
        if(s==null||s.length()==0)return;
        for(String x:s.split(sep)) if(x.length()>0)c.add(x);
    }

    String normPhone(String p){
        if(p==null) return "";
        p=p.replaceAll("[^0-9]",""); if(p.startsWith("90") && p.length()==12) p=p.substring(2); if(p.startsWith("0") && p.length()==11) p=p.substring(1);
        if(p.startsWith("00")) p="+"+p.substring(2);
        return p;
    }

String listKey(String name){ return "list_"+name.replaceAll("[^A-Za-z0-9ğüşöçıİĞÜŞÖÇ_-]","_"); }


    String waPhone(String p){
        if(p==null) return "";
        p=p.replaceAll("[^0-9+]","");
        if(p.startsWith("00")) p=p.substring(2);
        if(p.startsWith("+")) p=p.substring(1);

        // TR GSM: 5xxxxxxxxx -> 905xxxxxxxxx
        if(p.length()==10 && p.startsWith("5")){
            p="90"+p;
        }

        // TR GSM: 05xxxxxxxxx -> 905xxxxxxxxx
        if(p.length()==11 && p.startsWith("05")){
            p="9"+p;
        }

        return p;
    }

    boolean phoneInSet(Collection<String> set, String phone){
        String n=normPhone(phone);
        for(String p:set){
            if(normPhone(p).equals(n)) return true;
        }
        return false;
    }

    void removePhoneFromSet(Collection<String> set, String phone){
        String n=normPhone(phone);
        Iterator<String> it=set.iterator();
        while(it.hasNext()){
            String p=it.next();
            if(normPhone(p).equals(n)){
                it.remove();
                return;
            }
        }
    }

    LinkedHashSet<String> getListPhones(String name){
        LinkedHashSet<String> s=new LinkedHashSet<>();
        String raw=appPrefs().getString(listKey(name),"");
        if(raw==null || raw.trim().length()==0) return s;

        // Yeni kayıt formatı virgül. Eski | formatı varsa sadece onu oku.
        if(raw.contains(",")){
            addSplit(s, raw, ",");
        }else{
            addSplit(s, raw, "\\|");
        }
        return s;
    }
    void saveListPhones(String name, Collection<String> phones){
    appPrefs().edit().putString(listKey(name),join(phones,",")).commit();
    try{
        if(apiToken!=null && apiToken.length()>=5){
            new Thread(()->cloudPushFavContacts()).start();
        }
    }catch(Exception ignored){}
}

    String join(Collection<String> c,String sep){
        StringBuilder b=new StringBuilder();
        for(String x:c){ if(x==null||x.length()==0)continue; if(b.length()>0)b.append(sep); b.append(x); }
        return b.toString();
    }

    TextView t(String s,int size,boolean bold,int color){
        TextView v=new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setPadding(dp(2),dp(2),dp(2),dp(2));
        v.setIncludeFontPadding(false);
        return v;
    }

    GradientDrawable bg(int color,int r){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(r)); g.setStroke(dp(1),BORDER);
        return g;
    }
    GradientDrawable grad(int a,int b,int r){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{a,b});
        g.setCornerRadius(dp(r)); g.setStroke(dp(1),BORDER);
        return g;
    }
    int darker(int c){ return Color.rgb(Math.max(0,Color.red(c)-38),Math.max(0,Color.green(c)-38),Math.max(0,Color.blue(c)-38)); }

    TextView btn(String s,int color){
        TextView b=t(s,14,true,Color.WHITE); b.setGravity(Gravity.CENTER);
        b.setBackground(grad(color,darker(color),14)); b.setPadding(dp(12),dp(11),dp(12),dp(11));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(7),0,dp(4)); b.setLayoutParams(lp);
        return b;
    }
    TextView smallBtn(String s,int color){
        TextView b=t(s,12,true,Color.WHITE); b.setGravity(Gravity.CENTER);
        b.setBackground(grad(color,darker(color),10)); b.setPadding(dp(8),dp(8),dp(8),dp(8));
        return b;
    }
    LinearLayout card(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(14),dp(14),dp(14),dp(14)); l.setBackground(bg(CARD,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(8),0,dp(8)); l.setLayoutParams(lp); return l;
    }
    LinearLayout rowCard(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setPadding(dp(12),dp(12),dp(12),dp(12)); l.setBackground(bg(CARD2,14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(6),0,dp(6)); l.setLayoutParams(lp); return l;
    }
    EditText input(String val,String hint){
        EditText e=new EditText(this); e.setText(val); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(MUTED); e.setTextSize(14);
        e.setBackground(bg(Color.rgb(8,11,12),12)); e.setPadding(dp(12),dp(10),dp(12),dp(10)); return e;
    }

    
void base(String title, boolean showMenu){
        tab=title;
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(10));
        root.setBackgroundColor(Color.rgb(0,5,9));
        ScrollView sv=new ScrollView(this);
        sv.setFillViewport(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(root);
        setContentView(sv);
    }


    
    void baseFixed(String title){
        tab=title;
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(10));
        root.setBackgroundColor(Color.rgb(0,5,9));
        setContentView(root);
    }

FrameLayout.LayoutParams bottomParams(){ FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2); p.gravity=Gravity.BOTTOM; return p; }

    LinearLayout menu(){
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(6),dp(10),dp(6),dp(10));
        bar.setBackground(grad(Color.rgb(4,10,10),Color.rgb(0,0,0),0));

        String[] a={"Ana Sayfa","Kişiler","Medya","Gönderim","Raporlar","Ayarlar"};

        for(String x:a){
            boolean active=tab.equals(x);
            LinearLayout item=new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(2),dp(5),dp(2),dp(5));

            TextView ic=t(icon(x), active?28:25, true, active?GREEN:Color.rgb(210,218,220));
            ic.setGravity(Gravity.CENTER);

            TextView label=t(x, active?13:12, true, active?GREEN:Color.rgb(230,235,235));
            label.setGravity(Gravity.CENTER);
            label.setPadding(0,dp(2),0,0);

            if(active){
                GradientDrawable glow=grad(Color.rgb(8,35,22),Color.rgb(2,12,8),18);
                glow.setStroke(dp(1),Color.rgb(38,125,70));
                item.setBackground(glow);
            }

            item.addView(ic,new LinearLayout.LayoutParams(-1,dp(34)));
            item.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));

            item.setOnClickListener(q->{
                if(x.equals("Ana Sayfa"))home();
                if(x.equals("Kişiler"))favListsScreen();
                if(x.equals("Medya"))mediaScreen();
                if(x.equals("Gönderim"))sendScreen();
                if(x.equals("Raporlar"))reportsScreen();
                if(x.equals("Ayarlar"))settingsScreen();
            });

            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(72),1);
            lp.setMargins(dp(2),0,dp(2),0);
            bar.addView(item,lp);
        }

        return bar;
    }

    String icon(String s){
        if(s.equals("Ana Sayfa"))return "⌂";
        if(s.equals("Kişiler"))return "👥";
        if(s.equals("Medya"))return "▧";
        if(s.equals("Gönderim"))return "➤";
        if(s.equals("Ayarlar"))return "⚙";
        return "▤";
    }

    
    
    String userScope(){
        if(loginUser==null || loginUser.trim().length()==0) return "default";
        return loginUser.replaceAll("[^a-zA-Z0-9_-]","_");
    }

    String userScopeKey(){
        String u=(loginUser==null||loginUser.trim().length()==0)?"guest":loginUser.trim().toLowerCase(java.util.Locale.ROOT);
        return u.replaceAll("[^a-z0-9_\\-]","_");
    }

SharedPreferences appPrefs(){
        return getSharedPreferences("fpro_data_"+userScopeKey(),MODE_PRIVATE);
    }
    SharedPreferences loginPrefs(){
        return getSharedPreferences("fpro_login_global",MODE_PRIVATE);
    }



    void clearRuntimeData(){
        selected.clear();
        media.clear();
        reports.clear();
        favLists.clear();
        sent.clear();
        queue.clear();
        contacts.clear();
        editingPhones.clear();
        selectedFavLists.clear();
    }



    String safeScopeName(String v){
        if(v==null || v.trim().length()==0) return "guest";
        return v.replaceAll("[^a-zA-Z0-9_-]","_");
    }

    String activeUserScope(){
        return userScopeKey();
    }

    SharedPreferences userPrefs(){
        return getSharedPreferences("fpro_user_"+activeUserScope(), MODE_PRIVATE);
    }

    void wipeRuntimeForUserSwitch(){
        try{ selected.clear(); }catch(Exception ignored){}
        try{ media.clear(); }catch(Exception ignored){}
        try{ reports.clear(); }catch(Exception ignored){}
        try{ favLists.clear(); }catch(Exception ignored){}
        try{ sent.clear(); }catch(Exception ignored){}
        try{ queue.clear(); }catch(Exception ignored){}
        try{ contacts.clear(); }catch(Exception ignored){}
        try{ editingPhones.clear(); }catch(Exception ignored){}
        try{ selectedFavLists.clear(); }catch(Exception ignored){}
    }


boolean isLoggedIn(){
        SharedPreferences p=getSharedPreferences("fpro_login",MODE_PRIVATE);
        apiBase=p.getString("apiBase",apiBase);
        loginUser=p.getString("loginUser","");
        apiToken=p.getString("apiToken","");
        // Manuel mod tercihini yükle
        manualMode=appPrefs().getBoolean("manualMode",false);
        albumSendMode=appPrefs().getBoolean("albumSendMode",true);
        return apiToken!=null && apiToken.length()>5
            && loginUser!=null && loginUser.length()>0
            && apiBase!=null && apiBase.startsWith("http");
    }

    void saveLogin(String user,String token,String base){
        loginUser=user;
        apiToken=token;
        apiBase=base;
        getSharedPreferences("fpro_login",MODE_PRIVATE).edit()
            .putString("loginUser",user)
            .putString("apiToken",token)
            .putString("apiBase",base)
            .apply();
    }

    void logoutLogin(){
        getSharedPreferences("fpro_login",MODE_PRIVATE).edit().clear().apply();
        loginUser="";
        apiToken="";
        wipeRuntimeForUserSwitch();
        loginScreen();
    }

    
void subscriptionScreen(String mesaj){
    root.removeAllViews();
    LinearLayout c=new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setGravity(android.view.Gravity.CENTER);
    c.setPadding(dp(32),dp(80),dp(32),dp(32));
    c.addView(t("🔒",64,false,Color.WHITE));
    c.addView(t("Abonelik Süresi Doldu",20,true,RED));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
    lp.setMargins(0,dp(12),0,dp(24));
    TextView msg=t(mesaj,13,false,MUTED);
    msg.setGravity(android.view.Gravity.CENTER);
    c.addView(msg,lp);
    TextView btn=btn("📞 Aboneliği Yenile İçin İletişime Geçin",GREEN);
    btn.setOnClickListener(v->{
        android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_DIAL);
        i.setData(android.net.Uri.parse("tel:+905416960617"));
        startActivity(i);
    });
    c.addView(btn);
    TextView cikis=smallBtn("Çıkış Yap",RED);
    cikis.setOnClickListener(v->{ appPrefs().edit().clear().apply(); loginScreen(); });
    LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(-2,-2);
    lp2.setMargins(0,dp(16),0,0);
    lp2.gravity=android.view.Gravity.CENTER;
    c.addView(cikis,lp2);
    root.addView(c);
}


void expiredSession(String mesaj){
    try{ getSharedPreferences("fpro_login",MODE_PRIVATE).edit().clear().apply(); }catch(Exception ignored){}
    try{ loginUser=""; apiToken=""; clearRuntimeData(); }catch(Exception ignored){}
    runOnUiThread(()->{
        loginScreen();
        new AlertDialog.Builder(this)
            .setTitle("Abonelik Süresi Doldu")
            .setMessage(mesaj+"\n\nDevam etmek için yönetici ile iletişime geçin.")
            .setPositiveButton("WhatsApp ile iletişim",(d,w)->{
                try{
                    Intent i=new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse("https://wa.me/905416960617?text=Merhaba%20aboneli%C4%9Fim%20sona%20erdi%2C%20destek%20istiyorum."));
                    startActivity(i);
                }catch(Exception e){ toast("WhatsApp açılamadı"); }
            })
            .setNegativeButton("Kapat",null)
            .show();
    });
}

void loginScreen(){
        tab="Login";
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(26),dp(18),dp(18));
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout hero=card();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.addView(t("KATALOG",28,true,Color.WHITE));
        hero.addView(t("PANEL PRO",30,true,GREEN));
        hero.addView(t("Cloud WhatsApp Suite",15,false,MUTED));

        LinearLayout status=rowCard();
        status.addView(t("🛡",24,true,GREEN),new LinearLayout.LayoutParams(dp(44),-2));
        LinearLayout stx=new LinearLayout(this); stx.setOrientation(LinearLayout.VERTICAL);
        stx.addView(t("Sunucu Durumu",14,true,Color.WHITE));
        stx.addView(t("Online",13,true,GREEN));
        status.addView(stx,new LinearLayout.LayoutParams(0,-2,1));
        status.addView(t("API Aktif",13,true,GREEN));
        hero.addView(status);

        EditText user=input(loginUser.length()>0?loginUser:"","Kullanıcı adı");
        EditText pass=input("","Şifre");
        pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        hero.addView(user);
        hero.addView(pass);

        TextView remember=t("☑  Beni Hatırla",14,true,Color.WHITE);
        hero.addView(remember);

        TextView login=btn("↪  GİRİŞ YAP",GREEN);
        login.setOnClickListener(v->{
            String base=apiBase;
            String u=user.getText().toString().trim();
            String pw=pass.getText().toString();
            if(base.endsWith("/")) base=base.substring(0,base.length()-1);
            final String fBase=base;
            new Thread(()->{
                try{
                    JSONObject body=new JSONObject();
                    body.put("username",u);
                    body.put("password",pw);
                    JSONObject r=new JSONObject(httpPost(fBase+"/api/login",body.toString()));
                    if(!r.optBoolean("ok",false)) throw new Exception(r.optString("error","Giriş başarısız"));
                    String token=r.optString("token","");
                    saveLogin(u,token,fBase);
                    wipeRuntimeForUserSwitch();
                    load();
                    seedDefaults();
                    loadContacts();
                    cloudPullFavLists();
                    syncFromServerSilent();
                    // cloudPullFavLists kaldirildi - sadece acilista cekiliyor
                    JSONObject sub=new JSONObject(httpGet(fBase+"/api/check-subscription?token="+token));
                if(sub.optBoolean("active",false)){
                    // cloudPullFavLists kaldirildi - sadece acilista cekiliyor
                    int kalan=sub.optInt("kalan_gun",9999);
                if(kalan<=0){ expiredSession("Abonelik süreniz doldu!"); return; }
                    if(kalan<=30 && kalan<9999){ runOnUiThread(()->toast("⚠ Aboneliğiniz "+kalan+" gün sonra sona eriyor")); }
                    runOnUiThread(()->home());
                } else {
                    String err=sub.optString("error","Abonelik suresi doldu");
                    expiredSession(err);
                }
                }catch(Exception e){
                    runOnUiThread(()->toast("Giriş hatası: "+e.getMessage()));
                }
            }).start();
        });
        hero.addView(login);

        LinearLayout safe=rowCard();
        safe.addView(t("🔐 Güvenli Bağlantı",12,true,GREEN),new LinearLayout.LayoutParams(0,-2,1));
        safe.addView(t("☁ Cloud Sync",12,true,GREEN),new LinearLayout.LayoutParams(0,-2,1));
        safe.addView(t("🛡 Veri Güvende",12,true,GREEN),new LinearLayout.LayoutParams(0,-2,1));
        hero.addView(safe);

        root.addView(hero);
        TextView foot=t("FPRO PANEL v93  •  Güvenli  •  Hızlı  •  Profesyonel",12,false,MUTED);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);
    }


    JSONArray contactsToJson(){
        JSONArray arr=new JSONArray();
        try{
            for(C c:contacts){
                JSONObject o=new JSONObject();
                o.put("name",c.n);
                o.put("phone",c.p);
                arr.put(o);
            }
        }catch(Exception ignored){}
        return arr;
    }

    JSONArray favListsToJson(){
        JSONArray arr=new JSONArray();
        try{
            for(String name:favLists){
                JSONObject o=new JSONObject();
                o.put("name",name);
                JSONArray phones=new JSONArray();
                for(String p:getListPhones(name)) phones.put(p);
                o.put("phones",phones);
                arr.put(o);
            }
        }catch(Exception ignored){}
        return arr;
    }

    void checkUpdate(){
        // Gunde bir kez kontrol et
        long lastCheck=appPrefs().getLong("lastUpdateCheck",0);
        if(System.currentTimeMillis()-lastCheck < 86400000) return;
        appPrefs().edit().putLong("lastUpdateCheck",System.currentTimeMillis()).apply();
        // Bilinmeyen kaynak izni kontrol et
        if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O){
            if(!getPackageManager().canRequestPackageInstalls()){
                runOnUiThread(()->{
                    new android.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Kurulum İzni Gerekli")
                        .setMessage("Otomatik güncelleme için bilinmeyen kaynaklardan kurulum iznine ihtiyaç var.")
                        .setPositiveButton("İzin Ver",(d,w)->{
                            android.content.Intent i=new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            i.setData(android.net.Uri.parse("package:"+getPackageName()));
                            startActivity(i);
                        })
                        .setNegativeButton("Sonra",(d,w)->d.dismiss())
                        .show();
                });
                return;
            }
        }
        new Thread(()->{
            try{
                JSONObject r=new JSONObject(httpGet(apiBase+"/version.json"));
                int latestCode=r.optInt("versionCode",0);
                int currentCode=94;
                boolean force=r.optBoolean("forceUpdate",false);
                String latestName=r.optString("versionName","");
                String changelog=r.optString("changelog","");
                final String apkUrl=r.optString("apkUrl","");
                if(latestCode>currentCode){
                    runOnUiThread(()->{
                        android.app.AlertDialog.Builder b=new android.app.AlertDialog.Builder(MainActivity.this);
                        b.setTitle(" Güncelleme Mevcut - v"+latestName);
                        b.setMessage(changelog+"\n\nMevcut: v9.4 -> Yeni: v"+latestName);
                        b.setPositiveButton("İndir ve Güncelle",(d,w)->{
                            downloadAndInstallApk(apkUrl);
                        });
                        if(!force) b.setNegativeButton("Sonra",(d,w)->d.dismiss());
                        b.setCancelable(!force);
                        b.show();
                    });
                }
            }catch(Exception e){}
        }).start();
    }
    void downloadAndInstallApk(String url){
        toast("APK indiriliyor...");
        new Thread(()->{
            try{
                java.net.URL u=new java.net.URL(url);
                java.net.HttpURLConnection c=(java.net.HttpURLConnection)u.openConnection();
                c.setConnectTimeout(15000);
                java.io.InputStream in=c.getInputStream();
                java.io.File f=new java.io.File(getExternalFilesDir(null),"update.apk");
                java.io.FileOutputStream out=new java.io.FileOutputStream(f);
                byte[] buf=new byte[4096]; int n;
                while((n=in.read(buf))>0) out.write(buf,0,n);
                out.close(); in.close();
                runOnUiThread(()->{
                    try{
                        android.content.Intent i=new android.content.Intent(android.content.Intent.ACTION_VIEW);
                        android.net.Uri uri=androidx.core.content.FileProvider.getUriForFile(MainActivity.this,getPackageName()+".provider",f);
                        i.setDataAndType(uri,"application/vnd.android.package-archive");
                        i.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }catch(Exception e){ toast("Kurulum hatası: "+e.getMessage()); }
                });
            }catch(Exception e){ runOnUiThread(()->toast("İndirme hatası: "+e.getMessage())); }
        }).start();
    }
    void applyFavListsFromJson(JSONArray arr){
        if(arr==null) return;
        try{
            favLists.clear();
            for(int i=0;i<arr.length();i++){
                JSONObject o=arr.optJSONObject(i);
                if(o==null) continue;
                String name=o.optString("name","");
                if(name.length()==0) continue;
                favLists.add(name);
                JSONArray phones=o.optJSONArray("phones");
                LinkedHashSet<String> set=new LinkedHashSet<>();
                if(phones!=null){
                    for(int k=0;k<phones.length();k++){
                            String p=phones.optString(k,"");
                            if(p!=null){
                                p=p.trim();
                                if(p.length()>0){
                                    if(p.contains(",")){
                                        for(String x:p.split(",")){
                                            x=x.trim();
                                            if(x.length()>0) set.add(x);
                                        }
                                    }else if(p.contains("|")){
                                        for(String x:p.split("\\|")){
                                            x=x.trim();
                                            if(x.length()>0) set.add(x);
                                        }
                                    }else{
                                        set.add(p);
                                    }
                                }
                            }
                        }
                }
                appPrefs().edit().putString(listKey(name),join(set,",")).apply();
            }
            save();
        }catch(Exception ignored){}
    }

    void cloudPushFavContacts(){
        if(apiToken==null || apiToken.length()<5) return;
        try{
            JSONObject fbody=new JSONObject();
            fbody.put("favLists",favListsToJson());
            fbody.put("token",apiToken);
            httpPost(apiBase+"/api/favlists?token="+apiToken,fbody.toString());
        }catch(Exception ignored){}
    }

    void cloudPushFavLists(){
        if(apiToken==null || apiToken.length()<5) return;
        new Thread(()->{
            try{
                org.json.JSONObject fbody=new org.json.JSONObject();
                fbody.put("favLists",favListsToJson());
                fbody.put("token",apiToken);
                httpPost(apiBase+"/api/favlists?token="+apiToken,fbody.toString());
            }catch(Exception ignored){}
        }).start();
    }

    void cloudReconnect(){
        if(apiBase==null || apiToken==null) return;
        new Thread(()->{
            try{
                java.net.URL url=new java.net.URL(apiBase+"/reconnect?token="+apiToken);
                java.net.HttpURLConnection c=(java.net.HttpURLConnection)url.openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(5000);
                c.getResponseCode();
                c.disconnect();
            }catch(Exception ignored){}
        }).start();
    }

    void cloudPullFavLists(){
        if(apiToken==null || apiToken.length()<5) return;
        try{
            JSONObject r=new JSONObject(httpGet(apiBase+"/api/favlists?token="+apiToken));
            if(r.optBoolean("ok",false)){
                JSONArray arr=r.optJSONArray("favLists");
                if(arr!=null && arr.length()>0) applyFavListsFromJson(arr);
            }
        }catch(Exception ignored){}
    }

    // Rehberi sunucudan çek
    void cloudPullContacts(){
        if(apiToken==null || apiToken.length()<5) return;
        try{
            JSONObject r=new JSONObject(httpGet(apiBase+"/api/contacts?token="+apiToken));
            if(r.optBoolean("ok",false)){
                JSONArray arr=r.optJSONArray("contacts");
                if(arr!=null && arr.length()>0){
                    contacts.clear();
                    for(int i=0;i<arr.length();i++){
                        JSONObject co=arr.optJSONObject(i);
                        if(co!=null){
                            C c=new C(co.optString("n",""),co.optString("p",""));
                            c.note=co.optString("note","");
                            contacts.add(c);
                        }
                    }
                }
            }
        }catch(Exception ignored){}
    }

    void cloudSyncNow(){
        new Thread(()->{
            cloudPushFavContacts();
            runOnUiThread(()->toast("Fav ve rehber sunucuya senkronize edildi"));
        }).start();
    }

void syncFromServerSilent(){
        if(apiToken==null || apiToken.length()==0) return;
        try{
            JSONObject r=new JSONObject(httpGet(apiBase+"/api/data?token="+apiToken));
            if(!r.optBoolean("ok",false)) return;
            JSONObject data=r.optJSONObject("data");
            if(data==null) return;

            // Şimdilik güvenli temel sync: media/reports/settings alanları sunucuda tutulabilir.
            // Android local format çok değişken olduğu için fav listeler mevcut yapıyı bozmadan korunur.
            if(data.has("reports")){
                reports.clear();
                JSONArray arr=data.optJSONArray("reports");
                if(arr!=null){
                    for(int i=0;i<arr.length();i++) reports.add(arr.optString(i,arr.optJSONObject(i)!=null?arr.optJSONObject(i).toString():""));
                }
            }
        }catch(Exception ignored){}
    }

    void syncToServerSilent(){
        if(apiToken==null || apiToken.length()==0) return;
        try{
            JSONObject data=new JSONObject();
            data.put("reports", new JSONArray(reports));
            data.put("mediaLocalCount", media.size());
            data.put("favListCount", favLists.size());
            data.put("updatedAt", System.currentTimeMillis());
            JSONObject wrap=new JSONObject();
            wrap.put("data",data);
            httpPost(apiBase+"/api/data?token="+apiToken,wrap.toString());
        }catch(Exception ignored){}
    }




    LinearLayout v62Panel(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14),dp(14),dp(14),dp(14));
        p.setBackground(grad(Color.rgb(7,20,30),Color.rgb(2,8,12),22));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(8),0,dp(10));
        p.setLayoutParams(lp);
        return p;
    }

    TextView v62Chip(String text,int color){
        TextView v=t(text,12,true,color);
        v.setPadding(dp(10),dp(7),dp(10),dp(7));
        v.setGravity(Gravity.CENTER);
        v.setBackground(grad(Color.argb(80,20,28,32),Color.argb(120,5,10,14),18));
        return v;
    }

    LinearLayout v62Metric(String icon,String number,String label,int color){
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8),dp(10),dp(8),dp(10));
        TextView ic=t(icon,24,true,color); ic.setGravity(Gravity.CENTER);
        TextView num=t(number,21,true,Color.WHITE); num.setGravity(Gravity.CENTER);
        TextView lab=t(label,11,false,MUTED); lab.setGravity(Gravity.CENTER);
        b.addView(ic); b.addView(num); b.addView(lab);
        return b;
    }

    LinearLayout v62Tile(String icon,String title,String sub,int color,View.OnClickListener l){
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),dp(12),dp(10),dp(10));
        b.setBackground(grad(Color.argb(125,10,22,28),Color.argb(175,3,8,14),24));
        TextView ic=t(icon,34,true,color); ic.setGravity(Gravity.CENTER);
        TextView tt=t(title,15,true,Color.WHITE); tt.setGravity(Gravity.CENTER);
        TextView ss=t(sub,11,false,MUTED); ss.setGravity(Gravity.CENTER);
        TextView go=t("›",22,true,color); go.setGravity(Gravity.CENTER);
        b.addView(ic);
        b.addView(tt);
        b.addView(ss);
        b.addView(go);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout v62Activity(String icon,String title,String sub,String time,int color){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0,dp(8),0,dp(8));
        TextView ic=t(icon,24,true,color);
        ic.setGravity(Gravity.CENTER);
        r.addView(ic,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout texts=new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(t(title,14,true,Color.WHITE));
        texts.addView(t(sub,12,false,MUTED));
        r.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        TextView tm=t(time,13,true,color);
        tm.setGravity(Gravity.RIGHT);
        r.addView(tm,new LinearLayout.LayoutParams(dp(84),-2));
        r.addView(t("✓",22,true,color),new LinearLayout.LayoutParams(dp(34),-2));
        return r;
    }




    int glass1(){ return Color.rgb(7,18,27); }
    int glass2(){ return Color.rgb(2,7,12); }

    LinearLayout v63Card(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14),dp(14),dp(14),dp(14));
        p.setBackground(grad(glass1(),glass2(),24));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(8),0,dp(10));
        p.setLayoutParams(lp);
        return p;
    }

    TextView v63Text(String txt,int sp,boolean bold,int color){
        TextView v=t(txt,sp,bold,color);
        v.setIncludeFontPadding(true);
        return v;
    }

    LinearLayout v63Metric(String icon,String num,String label,int color){
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6),dp(10),dp(6),dp(10));
        TextView i=v63Text(icon,25,true,color); i.setGravity(Gravity.CENTER);
        TextView n=v63Text(num,21,true,Color.WHITE); n.setGravity(Gravity.CENTER);
        TextView l=v63Text(label,11,false,MUTED); l.setGravity(Gravity.CENTER);
        b.addView(i); b.addView(n); b.addView(l);
        return b;
    }

    LinearLayout v63Tile(String icon,String title,String sub,int color,View.OnClickListener click){
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),dp(10),dp(10),dp(10));
        b.setBackground(grad(Color.rgb(6,17,25),Color.rgb(3,8,14),22));
        TextView i=v63Text(icon,34,true,color); i.setGravity(Gravity.CENTER);
        TextView tt=v63Text(title,15,true,Color.WHITE); tt.setGravity(Gravity.CENTER);
        TextView ss=v63Text(sub,11,false,MUTED); ss.setGravity(Gravity.CENTER);
        TextView arrow=v63Text("›",23,true,color); arrow.setGravity(Gravity.CENTER);
        b.addView(i);
        b.addView(tt);
        b.addView(ss);
        b.addView(arrow);
        b.setOnClickListener(click);
        return b;
    }

    LinearLayout v63Activity(String icon,String title,String sub,String time,int color){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(7),0,dp(7));
        TextView ic=v63Text(icon,24,true,color); ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout texts=new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(v63Text(title,14,true,Color.WHITE));
        texts.addView(v63Text(sub,12,false,MUTED));
        row.addView(texts,new LinearLayout.LayoutParams(0,-2,1));
        TextView tm=v63Text(time,13,true,color); tm.setGravity(Gravity.RIGHT);
        row.addView(tm,new LinearLayout.LayoutParams(dp(70),-2));
        row.addView(v63Text("✓",21,true,color),new LinearLayout.LayoutParams(dp(30),-2));
        return row;
    }




    int neonGreen(){ return Color.rgb(38,220,85); }
    int neonBlue(){ return Color.rgb(30,145,255); }
    int neonPurple(){ return Color.rgb(150,75,255); }
    int neonYellow(){ return Color.rgb(255,210,35); }
    int panelA(){ return Color.rgb(7,19,27); }
    int panelB(){ return Color.rgb(2,7,12); }

    GradientDrawable v64Bg(int strokeColor,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(8,20,28),Color.rgb(2,7,12)});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),Color.argb(150,Color.red(strokeColor),Color.green(strokeColor),Color.blue(strokeColor)));
        return g;
    }

    LinearLayout v64Card(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(16),dp(15),dp(16),dp(15));
        p.setBackground(v64Bg(Color.rgb(38,64,78),24));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(8),0,dp(10));
        p.setLayoutParams(lp);
        return p;
    }

    TextView v64Label(String txt,int sp,boolean bold,int color){
        TextView v=t(txt,sp,bold,color);
        v.setTypeface(Typeface.SANS_SERIF);
        return v;
    }

    LinearLayout v64Metric(String icon,String number,String label,int color){
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4),dp(8),dp(4),dp(8));
        b.addView(v64Label(icon,24,true,color));
        TextView n=v64Label(number,20,true,Color.WHITE); n.setGravity(Gravity.CENTER);
        TextView l=v64Label(label,11,false,MUTED); l.setGravity(Gravity.CENTER);
        b.addView(n);
        b.addView(l);
        return b;
    }

    LinearLayout v64Tile(String icon,String title,String sub,int color,View.OnClickListener click){
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10),dp(12),dp(10),dp(10));
        b.setBackground(v64Bg(color,22));
        TextView ic=v64Label(icon,33,true,color); ic.setGravity(Gravity.CENTER);
        TextView tt=v64Label(title,15,true,Color.WHITE); tt.setGravity(Gravity.CENTER);
        TextView ss=v64Label(sub,11,false,MUTED); ss.setGravity(Gravity.CENTER);
        TextView arrow=v64Label("›",22,true,color); arrow.setGravity(Gravity.CENTER);
        b.addView(ic);
        b.addView(tt);
        b.addView(ss);
        b.addView(arrow);
        b.setOnClickListener(click);
        return b;
    }

    LinearLayout v64Activity(String icon,String title,String sub,String time,int color){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(7),0,dp(7));
        TextView ic=v64Label(icon,22,true,color); ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(v64Label(title,13,true,Color.WHITE));
        tx.addView(v64Label(sub,11,false,MUTED));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView tm=v64Label(time,12,true,color); tm.setGravity(Gravity.RIGHT);
        row.addView(tm,new LinearLayout.LayoutParams(dp(58),-2));
        return row;
    }



    int v65Green(){ return Color.rgb(33,220,88); }
    int v65Blue(){ return Color.rgb(35,145,255); }
    int v65Purple(){ return Color.rgb(145,72,255); }
    int v65Yellow(){ return Color.rgb(255,215,30); }
    int v65PanelA(){ return Color.rgb(7,20,28); }
    int v65PanelB(){ return Color.rgb(2,8,13); }

    GradientDrawable v65Bg(int stroke,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{v65PanelA(),v65PanelB()});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),Color.argb(130,Color.red(stroke),Color.green(stroke),Color.blue(stroke)));
        return g;
    }

    LinearLayout v65Card(int stroke,int radius,int pad){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        p.setBackground(v65Bg(stroke,radius));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(4),0,dp(6));
        p.setLayoutParams(lp);
        return p;
    }

    TextView v65Text(String text,int sp,boolean bold,int color){
        TextView v=t(text,sp,bold,color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    TextView v65Circle(String text,int size,int color){
        TextView v=v65Text(text,size,true,color);
        v.setGravity(Gravity.CENTER);
        v.setBackground(v65Bg(color,60));
        return v;
    }

    LinearLayout v65Metric(String icon,String num,String label,int color){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2),dp(5),dp(2),dp(5));
        TextView i=v65Text(icon,20,true,color); i.setGravity(Gravity.CENTER);
        TextView n=v65Text(num,19,true,Color.WHITE); n.setGravity(Gravity.CENTER);
        TextView l=v65Text(label,10,false,MUTED); l.setGravity(Gravity.CENTER);
        box.addView(i);
        box.addView(n);
        box.addView(l);
        return box;
    }

    LinearLayout v65Tile(String icon,String title,String sub,int color,View.OnClickListener click){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8),dp(7),dp(8),dp(6));
        box.setBackground(v65Bg(color,20));
        TextView i=v65Text(icon,28,true,color); i.setGravity(Gravity.CENTER);
        TextView tt=v65Text(title,13,true,Color.WHITE); tt.setGravity(Gravity.CENTER);
        TextView ss=v65Text(sub,10,false,MUTED); ss.setGravity(Gravity.CENTER);
        TextView go=v65Text("›",18,true,color); go.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(34)));
        box.addView(tt,new LinearLayout.LayoutParams(-1,dp(20)));
        box.addView(ss,new LinearLayout.LayoutParams(-1,dp(30)));
        box.addView(go,new LinearLayout.LayoutParams(-1,dp(18)));
        box.setOnClickListener(click);
        return box;
    }

    LinearLayout v65Activity(String icon,String title,String sub,String time,int color){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(3),0,dp(3));
        TextView ic=v65Text(icon,18,true,color); ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(v65Text(title,11,true,Color.WHITE));
        tx.addView(v65Text(sub,9,false,MUTED));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView tm=v65Text(time,10,true,color); tm.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(tm,new LinearLayout.LayoutParams(dp(50),-1));
        return row;
    }



    int v66Green(){ return Color.rgb(32,230,95); }
    int v66Blue(){ return Color.rgb(30,145,255); }
    int v66Purple(){ return Color.rgb(150,75,255); }
    int v66Yellow(){ return Color.rgb(255,215,35); }
    int v66Cyan(){ return Color.rgb(0,210,210); }
    int v66PanelA(){ return Color.rgb(8,22,31); }
    int v66PanelB(){ return Color.rgb(2,8,13); }

    GradientDrawable v66Bg(int stroke,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{v66PanelA(),v66PanelB()});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),Color.argb(145,Color.red(stroke),Color.green(stroke),Color.blue(stroke)));
        return g;
    }

    LinearLayout v66Card(int stroke,int radius,int pad){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        p.setBackground(v66Bg(stroke,radius));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(4),0,dp(6));
        p.setLayoutParams(lp);
        return p;
    }

    TextView v66Text(String text,int sp,boolean bold,int color){
        TextView v=t(text,sp,bold,color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    TextView v66Round(String text,int sp,int color){
        TextView v=v66Text(text,sp,true,color);
        v.setGravity(Gravity.CENTER);
        v.setBackground(v66Bg(color,72));
        return v;
    }

    LinearLayout v66Metric(String icon,String num,String label,int color){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(1),dp(4),dp(1),dp(4));
        TextView i=v66Text(icon,18,true,color); i.setGravity(Gravity.CENTER);
        TextView n=v66Text(num,18,true,Color.WHITE); n.setGravity(Gravity.CENTER);
        TextView l=v66Text(label,9,false,MUTED); l.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(22)));
        box.addView(n,new LinearLayout.LayoutParams(-1,dp(22)));
        box.addView(l,new LinearLayout.LayoutParams(-1,dp(16)));
        return box;
    }

    LinearLayout v66Tile(String icon,String title,String sub,int color,View.OnClickListener click){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6),dp(6),dp(6),dp(5));
        box.setBackground(v66Bg(color,20));
        TextView i=v66Text(icon,28,true,color); i.setGravity(Gravity.CENTER);
        TextView tt=v66Text(title,13,true,Color.WHITE); tt.setGravity(Gravity.CENTER);
        TextView ss=v66Text(sub,9,false,MUTED); ss.setGravity(Gravity.CENTER);
        TextView go=v66Text("›",16,true,color); go.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(34)));
        box.addView(tt,new LinearLayout.LayoutParams(-1,dp(18)));
        box.addView(ss,new LinearLayout.LayoutParams(-1,dp(26)));
        box.addView(go,new LinearLayout.LayoutParams(-1,dp(16)));
        box.setOnClickListener(click);
        return box;
    }

    LinearLayout v66Activity(String icon,String title,String sub,String time,int color){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(2),0,dp(2));
        TextView ic=v66Text(icon,17,true,color); ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(v66Text(title,10,true,Color.WHITE));
        tx.addView(v66Text(sub,8,false,MUTED));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView tm=v66Text(time,9,true,color); tm.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(tm,new LinearLayout.LayoutParams(dp(46),-1));
        return row;
    }

    TextView v66IconBadge(String text,int color){
        TextView v=v66Text(text,24,true,color);
        v.setGravity(Gravity.CENTER);
        v.setBackground(v66Bg(color,44));
        return v;
    }



    int v67Green(){ return Color.rgb(34,220,95); }
    int v67Blue(){ return Color.rgb(30,145,255); }
    int v67Purple(){ return Color.rgb(150,75,255); }
    int v67Yellow(){ return Color.rgb(255,215,35); }
    int v67Cyan(){ return Color.rgb(0,210,210); }
    int v67PanelA(){ return Color.rgb(7,20,29); }
    int v67PanelB(){ return Color.rgb(2,8,13); }

    GradientDrawable v67Bg(int stroke,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{v67PanelA(),v67PanelB()});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1),Color.argb(145,Color.red(stroke),Color.green(stroke),Color.blue(stroke)));
        return g;
    }

    LinearLayout v67Card(int stroke,int radius,int pad){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(pad),dp(pad),dp(pad),dp(pad));
        p.setBackground(v67Bg(stroke,radius));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(5),0,dp(7));
        p.setLayoutParams(lp);
        return p;
    }

    TextView v67Text(String text,int sp,boolean bold,int color){
        TextView v=t(text,sp,bold,color);
        v.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    TextView v67Round(String text,int sp,int color){
        TextView v=v67Text(text,sp,true,color);
        v.setGravity(Gravity.CENTER);
        v.setBackground(v67Bg(color,72));
        return v;
    }

    LinearLayout v67Metric(String icon,String num,String label,int color){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2),dp(5),dp(2),dp(5));
        TextView i=v67Text(icon,20,true,color); i.setGravity(Gravity.CENTER);
        TextView n=v67Text(num,20,true,Color.WHITE); n.setGravity(Gravity.CENTER);
        TextView l=v67Text(label,10,false,MUTED); l.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(22)));
        box.addView(n,new LinearLayout.LayoutParams(-1,dp(24)));
        box.addView(l,new LinearLayout.LayoutParams(-1,dp(16)));
        return box;
    }

    LinearLayout v67Tile(String icon,String title,String sub,int color,View.OnClickListener click){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6),dp(7),dp(6),dp(5));
        box.setBackground(v67Bg(color,20));
        TextView i=v67Text(icon,30,true,color); i.setGravity(Gravity.CENTER);
        TextView tt=v67Text(title,14,true,Color.WHITE); tt.setGravity(Gravity.CENTER);
        TextView ss=v67Text(sub,10,false,MUTED); ss.setGravity(Gravity.CENTER);
        TextView go=v67Text("›",17,true,color); go.setGravity(Gravity.CENTER);
        box.addView(i,new LinearLayout.LayoutParams(-1,dp(34)));
        box.addView(tt,new LinearLayout.LayoutParams(-1,dp(20)));
        box.addView(ss,new LinearLayout.LayoutParams(-1,dp(28)));
        box.addView(go,new LinearLayout.LayoutParams(-1,dp(16)));
        box.setOnClickListener(click);
        return box;
    }

    LinearLayout v67Activity(String icon,String title,String sub,String time,int color){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(3),0,dp(3));
        TextView ic=v67Text(icon,18,true,color); ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(dp(34),dp(34)));
        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(v67Text(title,11,true,Color.WHITE));
        tx.addView(v67Text(sub,9,false,MUTED));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1));
        TextView tm=v67Text(time,10,true,color); tm.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(tm,new LinearLayout.LayoutParams(dp(48),-1));
        return row;
    }


    void cleanActivityLogs(){
        try{
            org.json.JSONArray old=new org.json.JSONArray(appPrefs().getString("activity_logs","[]"));
            org.json.JSONArray arr=new org.json.JSONArray();
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            for(int i=old.length()-1;i>=0;i--){
                org.json.JSONObject x=old.optJSONObject(i);
                if(x==null) continue;
                String key=x.optString("title","")+"|"+x.optString("sub","");
                if(seen.contains(key)) continue;
                seen.add(key);
                arr.put(x);
                if(arr.length()>=5) break;
            }
            org.json.JSONArray rev=new org.json.JSONArray();
            for(int i=arr.length()-1;i>=0;i--) rev.put(arr.optJSONObject(i));
            appPrefs().edit().putString("activity_logs",rev.toString()).apply();
        }catch(Exception ignored){}
    }

void home(){
        cleanActivityLogs();
        tab="Ana Sayfa";
        setContentView(R.layout.activity_dashboard);
        setupDashboard();
        checkStatus();
        checkUpdate();
    }

    void setupDashboard(){
        TextView tvUser=findViewById(R.id.tvHeaderUsername);
        if(tvUser!=null){
            String u=(loginUser==null||loginUser.trim().length()==0)?"TEST":loginUser.toUpperCase(java.util.Locale.ROOT);
            tvUser.setText(u);
        }
        TextView tvApi=findViewById(R.id.tvApiUrl);
        if(tvApi!=null) tvApi.setText("admin".equals(loginUser) ? "API: "+apiBase : "API: ***");
        connectionText=findViewById(R.id.tvWaStatus);
        refreshDashStats();
        refreshDashLogs();
        View v;
        v=findViewById(R.id.tileRehber);    if(v!=null)v.setOnClickListener(q->contactsScreen());
        v=findViewById(R.id.tileMedya);     if(v!=null)v.setOnClickListener(q->mediaScreen());
        v=findViewById(R.id.tileGonderim); if(v!=null)v.setOnClickListener(q->sendScreen());
        v=findViewById(R.id.tileFavori);    if(v!=null)v.setOnClickListener(q->favListsScreen());
        v=findViewById(R.id.tileRaporlar); if(v!=null)v.setOnClickListener(q->reportsScreen());
        v=findViewById(R.id.tileAyarlar);  if(v!=null)v.setOnClickListener(q->settingsScreen());
        v=findViewById(R.id.qrCard);        if(v!=null)v.setOnClickListener(q->showMobileQrDialog());
        v=findViewById(R.id.userPill);      if(v!=null)v.setOnClickListener(q->settingsScreen());
    }

    void refreshDashStats(){
        TextView tv;
        tv=findViewById(R.id.tvStatContacts); if(tv!=null)tv.setText(String.valueOf(contacts.size()));
        tv=findViewById(R.id.tvStatFav);      if(tv!=null)tv.setText(String.valueOf(favLists.size()));
        tv=findViewById(R.id.tvStatSent);     if(tv!=null)tv.setText(String.valueOf(sent.size()));
        tv=findViewById(R.id.tvStatQueue);    if(tv!=null)tv.setText(String.valueOf(queue.size()));
    }

    void refreshDashLogs(){
        try{
            org.json.JSONArray logs=getActivityLogs();
            int[][] ids={
                {R.id.actRow1,R.id.tvAct1Title,R.id.tvAct1Sub,R.id.tvAct1Time},
                {R.id.actRow2,R.id.tvAct2Title,R.id.tvAct2Sub,R.id.tvAct2Time},
                {R.id.actRow3,R.id.tvAct3Title,R.id.tvAct3Sub,R.id.tvAct3Time}
            };
            for(int[] row:ids){View rv=findViewById(row[0]);if(rv!=null)rv.setVisibility(View.GONE);}
            if(logs.length()==0){
                View rv=findViewById(R.id.actRow1);if(rv!=null)rv.setVisibility(View.VISIBLE);
                TextView tt=findViewById(R.id.tvAct1Title);if(tt!=null)tt.setText("Henüz aktivite yok");
                TextView ts=findViewById(R.id.tvAct1Sub);if(ts!=null)ts.setText("İşlem yaptıkça burada görünür");
                TextView tm=findViewById(R.id.tvAct1Time);if(tm!=null)tm.setText("--:--");
                return;
            }
            int start=Math.max(0,logs.length()-3);
            int row=0;
            for(int i=start;i<logs.length()&&row<3;i++){
                org.json.JSONObject o=logs.optJSONObject(i);
                if(o==null)continue;
                View rv=findViewById(ids[row][0]);if(rv!=null)rv.setVisibility(View.VISIBLE);
                TextView tt=findViewById(ids[row][1]);if(tt!=null)tt.setText(o.optString("title","Aktivite"));
                TextView ts=findViewById(ids[row][2]);if(ts!=null)ts.setText(o.optString("sub",""));
                TextView tm=findViewById(ids[row][3]);if(tm!=null)tm.setText(o.optString("time","--:--"));
                row++;
            }
        }catch(Exception ignored){}
    }


    LinearLayout statBox(String icon,String title,String num,String sub){
        LinearLayout b=card(); b.setPadding(dp(10),dp(10),dp(10),dp(10));
        b.addView(t(icon+"  "+title,13,true,Color.WHITE)); b.addView(t(num,24,true,Color.WHITE)); b.addView(t(sub,11,false,MUTED)); return b;
    }
    void navRow(String ic,String title,String sub,View.OnClickListener l){
        LinearLayout r=rowCard(); r.addView(t(ic,28,true,GREEN),new LinearLayout.LayoutParams(dp(45),-2)); LinearLayout tx=new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.addView(t(title,16,true,Color.WHITE)); tx.addView(t(sub,12,false,MUTED)); r.addView(tx,new LinearLayout.LayoutParams(0,-2,1)); r.addView(t("›",28,true,MUTED)); r.setOnClickListener(l); root.addView(r);
    }

    void checkStatus(){
        if(apiToken==null || apiToken.length()<5){
            waConnected=false;
            waStatus="● WhatsApp bağlantısı yok";
                // Gönderim varsa otomatik yeniden bağlan
                if(sending){ new Handler(Looper.getMainLooper()).postDelayed(()->{ try{ cloudReconnect(); }catch(Exception ignored){} }, 3000); }
            if(connectionText!=null) connectionText.setText(waStatus);
            invalidateDashboard();
            return;
        }

        waConnected=false;
        waStatus="● Durum kontrol ediliyor";
        if(connectionText!=null) connectionText.setText(waStatus);
        invalidateDashboard();

        new Thread(()->{
            try{
                JSONObject j=new JSONObject(httpGet(apiBase+"/mobile/status?token="+apiToken));
                boolean ok=j.optBoolean("connected",false);
                runOnUiThread(()->{
                    waConnected=ok;
                    waStatus=ok?"● Bağlantı aktif":"● WhatsApp bağlantısı yok";
                    if(connectionText!=null) connectionText.setText(waStatus);
                    invalidateDashboard();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    waConnected=false;
                    waStatus="● WhatsApp bağlantısı yok";
                    if(connectionText!=null) connectionText.setText(waStatus);
                    invalidateDashboard();
                });
            }
        }).start();
    }

    void contactsScreen(){
        baseFixed("Rehber");
        loadContacts();

        LinearLayout top=card();
        top.addView(t("Rehber",22,true,Color.WHITE));
        countText=t(contacts.size()+" kisi",14,false,GREEN);
        top.addView(countText);

        // Arama kutusu
        searchBox=input("","Kisi ara...");
        searchBox.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ filterContacts(); }
            public void afterTextChanged(android.text.Editable s){}
        });
        top.addView(searchBox);

        // Rehber yenile
        TextView sync=btn("REHBERi YENiLE",BLUE);
        sync.setOnClickListener(v->{
            syncPhoneContactsToUser();
            filterContacts();
            toast("Rehber yenilendi: "+contacts.size()+" kisi");
        });
        top.addView(sync);
        root.addView(top);

        // Kisi listesi - tek secim - tüm kişileri göster
        filtered.clear();
        filtered.addAll(contacts);
        if(filtered.isEmpty()) loadContacts();
        if(filtered.isEmpty()) filtered.addAll(contacts);
        listView=new ListView(this);
        listView.setDividerHeight(1);
        listView.setBackgroundColor(BG);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setScrollingCacheEnabled(false);

        // ContactAdapter kullan - tüm kişiler görünsün
        adapter=new ContactAdapter(true){
            public View getView(int pos, View conv, ViewGroup parent){
                if(pos >= filtered.size()) return new View(MainActivity.this);
                C c=filtered.get(pos);
                LinearLayout row=rowCard();
                TextView name=t(c.n==null||c.n.isEmpty()?c.p:c.n,15,true,Color.WHITE);
                TextView phone=t(c.p,12,false,MUTED);
                row.addView(name);
                row.addView(phone);
                row.setOnClickListener(v->{
                    selected.clear();
                    selected.add(c.p);
                    selectedFavLists.clear();
                    save();
                    toast((c.n==null||c.n.isEmpty()?c.p:c.n)+" secildi");
                    sendScreen();
                });
                return row;
            }
        };
        listView.setAdapter(adapter);
        root.addView(listView,new LinearLayout.LayoutParams(-1,0,1));
    }

    void favListsScreen(){
        base("Fav Listeler",false);

        LinearLayout sync=card();
        sync.setPadding(dp(14),dp(12),dp(14),dp(12));
        sync.addView(t("Rehber Durumu",18,true,Color.WHITE));
        sync.addView(t("Telefondan okunan kişi: "+contacts.size(),12,false,MUTED));
        TextView refresh=btn("TELEFON REHBERİNİ SENKRONİZE ET",BLUE);
        refresh.setTextSize(11);
        refresh.setOnClickListener(v->{ syncPhoneContactsToUser(); toast("Rehber yenilendi"); favListsScreen(); });
        sync.addView(refresh,new LinearLayout.LayoutParams(-1,dp(44)));
        root.addView(sync);

        for(String l:favLists){
            LinkedHashSet<String> phones=getListPhones(l);

            LinearLayout c=card();
            c.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView star=t("★",30,true,YELLOW);
            star.setGravity(Gravity.CENTER);
            row.addView(star,new LinearLayout.LayoutParams(dp(44),dp(66)));

            LinearLayout info=new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.addView(t(l,17,true,Color.WHITE));
            info.addView(t(phones.size()+" kişi",11,false,MUTED));
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));

            LinearLayout actions=new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            TextView edit=smallBtn("DÜZENLE",BLUE);
            edit.setTextSize(9);
            edit.setOnClickListener(v->{ activeList=l; editingPhones.clear(); editingPhones.addAll(getListPhones(l)); listEditScreen(); });
            actions.addView(edit,new LinearLayout.LayoutParams(dp(68),dp(36)));

            TextView send=smallBtn("GÖNDER",GREEN);
            send.setTextSize(9);
            send.setOnClickListener(v->{ activeList=l; selectedFavLists.clear(); selectedFavLists.add(l); save(); sendScreen(); });
            actions.addView(send,new LinearLayout.LayoutParams(dp(64),dp(36)));

            TextView del=smallBtn("SİL",RED);
            del.setTextSize(9);
            del.setOnClickListener(v->{ favLists.remove(l); appPrefs().edit().remove("list_"+l).apply(); save(); cloudPushFavLists(); favListsScreen(); });
            actions.addView(del,new LinearLayout.LayoutParams(dp(44),dp(36)));

            row.addView(actions,new LinearLayout.LayoutParams(-2,-2));
            c.addView(row);
            root.addView(c);
        }

        TextView add=btn("+ YENİ LİSTE OLUŞTUR",GREEN);
        add.setTextSize(12);
        add.setOnClickListener(v->newListDialog());
        root.addView(add,new LinearLayout.LayoutParams(-1,dp(46)));
    }

    void favListCard(String name){
        int count=getListPhones(name).size();
        LinearLayout c=rowCard();
        TextView star=t("★",35,true,YELLOW); star.setGravity(Gravity.CENTER); c.addView(star,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout mid=new LinearLayout(this); mid.setOrientation(LinearLayout.VERTICAL); mid.addView(t(name,17,true,Color.WHITE)); mid.addView(t(count+" kişi",13,false,MUTED)); mid.addView(t("Son gönderim: kayıt yok",11,false,MUTED));
        c.addView(mid,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL);
        TextView edit=smallBtn("DÜZENLE",GREEN); edit.setOnClickListener(v->{activeList=name; save(); listEditScreen();}); actions.addView(edit);
        TextView send=smallBtn("GÖNDER",GREEN); send.setOnClickListener(v->{activeList=name; selected.clear(); selected.addAll(getListPhones(name)); save(); sendScreen();}); actions.addView(send);
        TextView del=smallBtn("SİL",RED); del.setOnClickListener(v->confirmDeleteList(name)); actions.addView(del);
        c.addView(actions,new LinearLayout.LayoutParams(dp(105),-2));
        
        
        root.addView(c);
    }

    void confirmDeleteList(String name){
        if(favLists.size()<=1){ toast("En az 1 liste kalmalı"); return; }
        new AlertDialog.Builder(this)
            .setTitle("Liste silinsin mi?")
            .setMessage(name+" listesini kaldırmak istiyor musun?")
            .setPositiveButton("Sil",(d,w)->{
                favLists.remove(name);
                appPrefs().edit().remove(listKey(name)).apply();
                if(activeList.equals(name)) activeList=favLists.get(0);
                save();
                favListsScreen();
            })
            .setNegativeButton("İptal",null)
            .show();
    }

    void buildFavStatusCache(){
        favStatusCache.clear();
        for(String l:favLists){
            LinkedHashSet<String> phones=getListPhones(l);
            for(String p:phones){
                String np=normPhone(p);
                if(np.length()==0) continue;
                String old=favStatusCache.get(np);
                if(old==null || old.length()==0) favStatusCache.put(np,l);
                else if(!((", "+old+", ").contains(", "+l+", "))) favStatusCache.put(np,old+", "+l);
            }
        }
    }

    boolean favStatusHasList(String status, String listName){
        if(status==null || listName==null || listName.length()==0) return false;
        String[] parts=status.split(",");
        for(String part:parts){
            if(part.trim().equals(listName)) return true;
        }
        return false;
    }

    void listEditScreen(){
        listEditMode=true;
        editingPhones.clear(); editingPhones.addAll(getListPhones(activeList));
        base("Liste Düzenle",true);

        LinearLayout top=card(); top.addView(t("⭐ "+activeList,22,true,Color.WHITE)); countText=t(editingPhones.size()+" kişi",15,true,GREEN); top.addView(countText);
        LinearLayout tabs=new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        TextView kişiler=smallBtn("KİŞİLER",GREEN); tabs.addView(kişiler,new LinearLayout.LayoutParams(0,-2,1));
        TextView ekle=smallBtn("EKLE",BLUE); ekle.setOnClickListener(v->addToListScreen()); tabs.addView(ekle,new LinearLayout.LayoutParams(0,-2,1));
        TextView ist=smallBtn("İSTATİSTİK",PURPLE); ist.setOnClickListener(v->statsScreen()); tabs.addView(ist,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(tabs);
        TextView addContact=btn("+ YENİ KİŞİ EKLE",BLUE); addContact.setOnClickListener(v->manualAddContactDialog()); top.addView(addContact);
        TextView saveList=btn("Listeyi Kaydet",GREEN); saveList.setOnClickListener(v->{saveListPhones(activeList,editingPhones); selected.clear(); selected.addAll(editingPhones); save(); toast("Liste kaydedildi"); favListsScreen();}); top.addView(saveList);
        TextView delList=btn("BU LİSTEYİ KALDIR",RED); delList.setOnClickListener(v->confirmDeleteList(activeList)); top.addView(delList);
        root.addView(top);

        buildEditableList(false);
    }

    void addToListScreen(){
        listEditMode=true;
        editingPhones.clear();
        editingPhones.addAll(getListPhones(activeList));

        // Arama yapmadan tüm rehber görünsün.
        loadContacts();

        baseFixed("Ekle");
        LinearLayout top=card();
        top.addView(t("Rehberden Ekle",22,true,Color.WHITE));
        countText=t("Listeye eklenecek: 0",14,true,GREEN);
        top.addView(countText);

        searchBox=input("","Kişi ara...");
        top.addView(searchBox);

        TextView sync=btn("REHBERİ YENİLE",BLUE);
        sync.setOnClickListener(v->{
            syncPhoneContactsToUser();
            filterContacts();
            toast("Rehber yenilendi: "+contacts.size()+" kişi");
        });
        top.addView(sync);

        TextView addManual=btn("+ YENİ KİŞİ EKLE VE REHBERE KAYDET",BLUE);
        addManual.setOnClickListener(v->manualAddContactDialog());
        top.addView(addManual);

        TextView saveList=btn("LİSTEYE EKLE",GREEN);
        saveList.setOnClickListener(v->{
            saveListPhones(activeList,editingPhones);
            selected.clear();
            selected.addAll(editingPhones);
            save();
            toast("Liste güncellendi");
            listEditScreen();
        });
        top.addView(saveList);

        root.addView(top);

        searchBox.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ filterContacts(); }
            public void afterTextChanged(android.text.Editable e){}
        });

        buildEditableList(true);
    }

    void buildEditableList(boolean allContacts){
        if(allContacts && contacts.isEmpty()) loadContacts();
        filtered.clear();
        if(allContacts){
            filtered.addAll(contacts);
        } else {
            // Listedeki numaraları normalize ederek eşleştir
            java.util.HashSet<String> normalizedPhones=new java.util.HashSet<>();
            for(String p:editingPhones){
                normalizedPhones.add(p.replaceAll("[^0-9]",""));
            }
            for(C c:contacts){
                String normalized=c.p==null?"":c.p.replaceAll("[^0-9]","");
                if(normalizedPhones.contains(normalized)) filtered.add(c);
            }
            // Rehberde olmayan ama listede olan numaraları da göster
            java.util.HashSet<String> foundPhones=new java.util.HashSet<>();
            for(C c:filtered) foundPhones.add(c.p.replaceAll("[^0-9]",""));
            for(String p:editingPhones){
                String normalized=p.replaceAll("[^0-9]","");
                if(!foundPhones.contains(normalized)){
                    C ghost=new C(p,p); // isim yoksa numara göster
                    filtered.add(ghost);
                }
            }
        }

        if(allContacts && contacts.isEmpty()){
            LinearLayout empty=card();
            empty.addView(t("Rehber bos gorunuyor",18,true,Color.WHITE));
            empty.addView(t("REHBERi YENiLE butonuna bas.",13,false,MUTED));
            root.addView(empty);
        }

        listView=new ListView(this);
        listView.setDividerHeight(1);
        listView.setBackgroundColor(BG);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setScrollingCacheEnabled(false);
        listView.setSmoothScrollbarEnabled(true);
        adapter=new ContactAdapter(allContacts);
        listView.setAdapter(adapter);
        root.addView(listView,new LinearLayout.LayoutParams(-1,0,1));
    }


    void filterContacts(){
        String q="";
        if(searchBox!=null) q=searchBox.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for(C c:contacts){
            String name=c.n==null?"":c.n.toLowerCase(Locale.ROOT);
            String phone=c.p==null?"":c.p;
            if(q.length()==0 || name.contains(q) || phone.contains(q)){
                filtered.add(c);
            }
        }
        if(adapter!=null) adapter.notifyDataSetChanged();
        updateCount();
    }

    void updateCount(){
        if(countText!=null){
            if(tab.equals("Ekle")) countText.setText("Listeye eklenecek: 0");
            else countText.setText(editingPhones.size()+" kişi");
        }
    }

    class ContactAdapter extends BaseAdapter {
        boolean all;
        ContactAdapter(boolean all){this.all=all;}

        public int getCount(){return filtered.size();}
        public Object getItem(int i){return filtered.get(i);}
        public long getItemId(int i){return i;}

        public View getView(int pos, View convert, ViewGroup parent){
            LinearLayout row;
            TextView name,b;

            if(convert==null){
                row=rowCard();
                name=t("",14,true,Color.WHITE);
                name.setTag("name");
                row.addView(name,new LinearLayout.LayoutParams(0,-2,1));

                b=smallBtn("Ekle",BLUE);
                b.setTag("button");
                row.addView(b,new LinearLayout.LayoutParams(dp(112),-2));
            } else {
                row=(LinearLayout)convert;
            }

            name=(TextView)row.findViewWithTag("name");
            b=(TextView)row.findViewWithTag("button");

            C c=filtered.get(pos);
            String phone=normPhone(c.p);
            boolean is=phoneInSet(editingPhones,c.p);

            String line=c.n+"\n"+c.p;
            if(is){ line+="\n✅ Seçili"; }

            name.setText(line);

            if(is){
                b.setText("Seçili ✓");
                b.setBackground(grad(GREEN,darker(GREEN),10));
            }else{
                b.setText("Ekle");
                b.setBackground(grad(BLUE,darker(BLUE),10));
            }

        View.OnClickListener l=v->{
            boolean isNow=phoneInSet(editingPhones,c.p);
            if(isNow){
                removePhoneFromSet(editingPhones,c.p);
            } else {
                editingPhones.add(c.p);
            }
            saveListPhones(activeList,editingPhones);
            save();
            if(adapter!=null) adapter.notifyDataSetChanged();
            updateCount();
        };
            b.setOnClickListener(l);
            row.setOnClickListener(l);

            return row;
        }
    }

    void newListDialog(){
        final EditText e=input("","Liste adı");
        new AlertDialog.Builder(this).setTitle("Yeni Liste").setView(e).setPositiveButton("Oluştur",(d,w)->{
            String n=e.getText().toString().trim(); if(n.length()==0)n="Yeni Liste";
            if(!favLists.contains(n)) favLists.add(n); activeList=n; save(); cloudPushFavLists(); listEditScreen();
        }).setNegativeButton("İptal",null).show();
    }

    void renameListDialog(){
        final EditText e=input(activeList,"Yeni liste adı");
        new AlertDialog.Builder(this).setTitle("Liste adını değiştir").setView(e).setPositiveButton("Kaydet",(d,w)->{
            String n=e.getText().toString().trim(); if(n.length()==0)return;
            LinkedHashSet<String> phones=getListPhones(activeList);
            favLists.remove(activeList); if(!favLists.contains(n)) favLists.add(n);
            appPrefs().edit().remove(listKey(activeList)).apply();
            activeList=n; saveListPhones(activeList,phones); save(); listEditScreen();
        }).setNegativeButton("İptal",null).show();
    }

    void statsScreen(){
        base("İstatistik",false);
        LinearLayout c=card(); c.addView(t(activeList,22,true,Color.WHITE)); c.addView(t(getListPhones(activeList).size()+" kişi",15,true,GREEN)); c.addView(t("Gönderilen: "+sent.size()+"\nBekleyen: "+queue.size()+"\nHatalı: 0",16,false,Color.WHITE)); root.addView(c);
    }

    void mediaScreen(){
        base("Medya & Albumler",false);
        root.setPadding(dp(14),dp(8),dp(14),dp(80)); // bottom padding for sticky bar

        int totalPhotos=0; for(ArrayList<String> al:albums) totalPhotos+=al.size();

        // ── SAYFA BAŞLIĞI ──────────────────────────────────────────────────
        LinearLayout titleBlock=new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(2),dp(10),dp(2),dp(4));
        if(!albumSendMode){
            titleBlock.addView(t("Albümler",26,true,Color.WHITE));
            titleBlock.addView(t(albums.size()+" albüm · "+totalPhotos+" fotoğraf",13,false,0xFF48505E));
        } else {
            titleBlock.addView(t("Gönderim",26,true,Color.WHITE));
            titleBlock.addView(t("Albüm seç, liste seç, gönder",13,false,0xFF48505E));
        }
        root.addView(titleBlock);

        // ── ALBÜM KARTLARI ─────────────────────────────────────────────────
        int[] colors={0xFF5B5BD6,0xFF0891B2,0xFF059669,0xFFD97706,0xFF7C3AED,0xFFDC2626};
        int screenW=getResources().getDisplayMetrics().widthPixels;
        int cellSize=(screenW - dp(28) - dp(6)) / 3;

        for(int idx=0;idx<albums.size();idx++){
            final int aIdx=idx;
            ArrayList<String> photos=albums.get(idx);
            String caption=idx<albumCaptions.size()?albumCaptions.get(idx):"";
            String albumName=idx<albumNames.size()&&!albumNames.get(idx).isEmpty()
                ?albumNames.get(idx):"Albüm "+(idx+1);
            int color=colors[idx%colors.length];
            int colorDim=(color&0x00FFFFFF)|0x1A000000;

            // Kart
            LinearLayout aCard=new LinearLayout(this);
            aCard.setOrientation(LinearLayout.VERTICAL);
            aCard.setClipToOutline(true);
            android.graphics.drawable.GradientDrawable cardBg=new android.graphics.drawable.GradientDrawable();
            cardBg.setColor(0xFF13151A); cardBg.setCornerRadius(dp(20)); cardBg.setStroke(dp(1),0xFF1E2028);
            aCard.setBackground(cardBg);
            LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
            cardLp.setMargins(0,dp(4),0,dp(4)); aCard.setLayoutParams(cardLp);

            // Üst şerit
            android.view.View stripe=new android.view.View(this);
            android.graphics.drawable.GradientDrawable stripeBg=new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color,darker(color)});
            stripeBg.setCornerRadii(new float[]{dp(20),dp(20),dp(20),dp(20),0,0,0,0});
            stripe.setBackground(stripeBg);
            aCard.addView(stripe,new LinearLayout.LayoutParams(-1,dp(3)));

            // ── BAŞLIK SATIRI (tıklanabilir — medyayı açar/kapar) ──
            LinearLayout aHead=new LinearLayout(this);
            aHead.setOrientation(LinearLayout.HORIZONTAL);
            aHead.setGravity(android.view.Gravity.CENTER_VERTICAL);
            aHead.setPadding(dp(14),dp(14),dp(14),dp(14));

            // Numara badge
            TextView numBadge=new TextView(this);
            numBadge.setText(String.valueOf(idx+1));
            numBadge.setTextSize(13); numBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            numBadge.setTextColor(Color.WHITE); numBadge.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable nbBg=new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{color,darker(color)});
            nbBg.setCornerRadius(dp(10)); numBadge.setBackground(nbBg);
            LinearLayout.LayoutParams nbLp=new LinearLayout.LayoutParams(dp(34),dp(34));
            nbLp.setMargins(0,0,dp(12),0); numBadge.setLayoutParams(nbLp);
            aHead.addView(numBadge);

            // İsim + alt bilgi
            LinearLayout midCol=new LinearLayout(this);
            midCol.setOrientation(LinearLayout.VERTICAL);
            midCol.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
            TextView nameTv=t(albumName,15,true,0xFFF1F1F1);
            midCol.addView(nameTv);
            String subTxt=photos.isEmpty()?"Fotoğraf yok":photos.size()+" fotoğraf";
            TextView subTv=t(subTxt,11,false,0xFF3D4455);
            LinearLayout.LayoutParams stLp=new LinearLayout.LayoutParams(-1,-2);
            stLp.setMargins(0,dp(2),0,0); subTv.setLayoutParams(stLp);
            midCol.addView(subTv);
            aHead.addView(midCol);

            // Foto pill
            TextView fotoPill=t("🖼 "+photos.size(),10,true,Color.WHITE);
            fotoPill.setPadding(dp(8),dp(3),dp(8),dp(3));
            android.graphics.drawable.GradientDrawable pillBg=new android.graphics.drawable.GradientDrawable();
            pillBg.setColor(colorDim); pillBg.setCornerRadius(dp(8));
            fotoPill.setBackground(pillBg);
            LinearLayout.LayoutParams pillLp=new LinearLayout.LayoutParams(-2,-2);
            pillLp.setMargins(0,0,dp(8),0); fotoPill.setLayoutParams(pillLp);
            aHead.addView(fotoPill);

            // Chevron
            final TextView chevron=t("⌄",13,false,0xFF3D4455);
            chevron.setPadding(0,0,dp(8),0);
            aHead.addView(chevron);

            // Sil butonu
            TextView delBtn=new TextView(this);
            delBtn.setText("🗑"); delBtn.setTextSize(13); delBtn.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable delBg=new android.graphics.drawable.GradientDrawable();
            delBg.setColor(0xFF1C1820); delBg.setCornerRadius(dp(8)); delBg.setStroke(dp(1),0xFF2A1F1F);
            delBtn.setBackground(delBg); delBtn.setPadding(dp(9),dp(7),dp(9),dp(7));
            delBtn.setOnClickListener(v->{
                new android.app.AlertDialog.Builder(this)
                    .setTitle("Albümü Sil")
                    .setMessage(albumName+" silinsin mi?")
                    .setPositiveButton("Sil",(d,w)->{
                        albums.remove(aIdx);
                        if(aIdx<albumCaptions.size()) albumCaptions.remove(aIdx);
                        if(aIdx<albumNames.size()) albumNames.remove(aIdx);
                        save(); mediaScreen();
                    }).setNegativeButton("İptal",null).show();
            });
            aHead.addView(delBtn);

            // ── BODY (collapse) ──
            final LinearLayout aBody=new LinearLayout(this);
            aBody.setOrientation(LinearLayout.VERTICAL);
            aBody.setVisibility(android.view.View.GONE);

            // Başlığa tıklayınca aç/kapat
            aHead.setOnClickListener(v->{
                boolean nowVisible=aBody.getVisibility()==android.view.View.VISIBLE;
                aBody.setVisibility(nowVisible?android.view.View.GONE:android.view.View.VISIBLE);
                chevron.setText(nowVisible?"⌄":"⌃");
            });
            aCard.addView(aHead);

            // Ayraç
            android.view.View div1=new android.view.View(this);
            div1.setBackgroundColor(0xFF191C22);
            aCard.addView(div1,new LinearLayout.LayoutParams(-1,dp(1)));

            // ── YÖNETİM MODU BODY ──
            if(!albumSendMode){
                // Foto strip (3-col grid) veya empty state
                if(photos.isEmpty()){
                    LinearLayout emptyState=new LinearLayout(this);
                    emptyState.setOrientation(LinearLayout.VERTICAL);
                    emptyState.setGravity(android.view.Gravity.CENTER);
                    emptyState.setPadding(dp(16),dp(22),dp(16),dp(22));
                    emptyState.setBackgroundColor(0xFF0D0E11);
                    emptyState.setOnClickListener(v->{ pickingAlbumIdx=aIdx; galleryPickerForAlbum(); });
                    TextView ei=t("🖼",26,false,Color.WHITE); ei.setGravity(android.view.Gravity.CENTER); ei.setAlpha(.2f);
                    emptyState.addView(ei);
                    TextView el=t("Fotoğraf eklemek için dokun",12,true,0xFF2E3340);
                    el.setGravity(android.view.Gravity.CENTER);
                    LinearLayout.LayoutParams elLp=new LinearLayout.LayoutParams(-1,-2); elLp.setMargins(0,dp(6),0,0); el.setLayoutParams(elLp);
                    emptyState.addView(el);
                    aBody.addView(emptyState);
                } else {
                    android.widget.GridLayout grid=new android.widget.GridLayout(this);
                    grid.setColumnCount(3); grid.setBackgroundColor(0xFF0D0E11);
                    grid.setPadding(dp(2),dp(2),dp(2),dp(2));
                    for(int pi=0;pi<photos.size();pi++){
                        final int pIdx=pi;
                        android.widget.FrameLayout cell=new android.widget.FrameLayout(this);
                        android.widget.GridLayout.LayoutParams glp=new android.widget.GridLayout.LayoutParams();
                        glp.width=cellSize; glp.height=cellSize; glp.setMargins(dp(2),dp(2),dp(2),dp(2));
                        cell.setLayoutParams(glp);
                        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        android.graphics.drawable.GradientDrawable imgBg=new android.graphics.drawable.GradientDrawable();
                        imgBg.setColor(0xFF1E2533); imgBg.setCornerRadius(dp(4)); img.setBackground(imgBg);
                        try{ img.setImageURI(Uri.parse(photos.get(pi))); }catch(Throwable ignored){}
                        cell.addView(img,new android.widget.FrameLayout.LayoutParams(-1,-1));
                        android.view.View ov=new android.view.View(this);
                        android.graphics.drawable.GradientDrawable ovBg=new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x88000000,0x00000000});
                        ov.setBackground(ovBg); cell.addView(ov,new android.widget.FrameLayout.LayoutParams(-1,-1));
                        TextView xBtn=new TextView(this); xBtn.setText("✕"); xBtn.setTextSize(9); xBtn.setTextColor(Color.WHITE);
                        xBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); xBtn.setGravity(android.view.Gravity.CENTER);
                        android.graphics.drawable.GradientDrawable xBg=new android.graphics.drawable.GradientDrawable();
                        xBg.setColor(0xBB000000); xBg.setCornerRadius(dp(4)); xBtn.setBackground(xBg); xBtn.setPadding(dp(5),dp(2),dp(5),dp(2));
                        android.widget.FrameLayout.LayoutParams xLp=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.TOP|android.view.Gravity.END);
                        xLp.setMargins(0,dp(4),dp(4),0); xBtn.setLayoutParams(xLp);
                        xBtn.setOnClickListener(v->{ photos.remove(pIdx); save(); mediaScreen(); });
                        cell.addView(xBtn);
                        grid.addView(cell);
                    }
                    // + ekle hücresi
                    android.widget.FrameLayout addCell=new android.widget.FrameLayout(this);
                    android.widget.GridLayout.LayoutParams addGlp=new android.widget.GridLayout.LayoutParams();
                    addGlp.width=cellSize; addGlp.height=cellSize; addGlp.setMargins(dp(2),dp(2),dp(2),dp(2));
                    addCell.setLayoutParams(addGlp);
                    LinearLayout addInner=new LinearLayout(this); addInner.setOrientation(LinearLayout.VERTICAL); addInner.setGravity(android.view.Gravity.CENTER);
                    android.graphics.drawable.GradientDrawable aiBg=new android.graphics.drawable.GradientDrawable();
                    aiBg.setColor(0xFF0D0E11); aiBg.setCornerRadius(dp(4)); aiBg.setStroke(dp(2),0xFF252830); addInner.setBackground(aiBg);
                    addInner.setOnClickListener(v->{ pickingAlbumIdx=aIdx; galleryPickerForAlbum(); });
                    addInner.addView(t("＋",22,false,0xFF2E3340));
                    addInner.addView(t("Ekle",9,true,0xFF2E3340));
                    addCell.addView(addInner,new android.widget.FrameLayout.LayoutParams(-1,-1));
                    grid.addView(addCell);
                    aBody.addView(grid);
                }

                // Ayraç
                android.view.View div2=new android.view.View(this); div2.setBackgroundColor(0xFF191C22);
                aBody.addView(div2,new LinearLayout.LayoutParams(-1,dp(1)));

                // Albüm adı input
                LinearLayout nameWrap=new LinearLayout(this); nameWrap.setOrientation(LinearLayout.VERTICAL);
                nameWrap.setPadding(dp(14),dp(10),dp(14),dp(4));
                TextView nameLbl=t("ALBÜM ADI",10,true,0xFF3D4455); nameLbl.setLetterSpacing(0.06f);
                LinearLayout.LayoutParams nlLp=new LinearLayout.LayoutParams(-1,-2); nlLp.setMargins(0,0,0,dp(6)); nameLbl.setLayoutParams(nlLp);
                nameWrap.addView(nameLbl);
                final EditText nameInput=new EditText(this);
                nameInput.setText(albumName.equals("Albüm "+(idx+1))?"":albumName);
                nameInput.setHint("Albüm "+(idx+1));
                nameInput.setTextColor(Color.WHITE); nameInput.setHintTextColor(0xFF2E3340);
                nameInput.setTextSize(13); nameInput.setSingleLine(true);
                android.graphics.drawable.GradientDrawable niBg=new android.graphics.drawable.GradientDrawable();
                niBg.setColor(0xFF0D0E11); niBg.setCornerRadius(dp(10)); niBg.setStroke(dp(1),0xFF1E2028); nameInput.setBackground(niBg);
                nameInput.setPadding(dp(12),dp(10),dp(12),dp(10));
                nameInput.setOnFocusChangeListener((v,f)->{
                    if(!f){
                        while(albumNames.size()<=aIdx) albumNames.add("");
                        String nm=nameInput.getText().toString().trim();
                        albumNames.set(aIdx,nm); save();
                    }
                });
                nameWrap.addView(nameInput); aBody.addView(nameWrap);

                // Mesaj input
                LinearLayout capWrap=new LinearLayout(this); capWrap.setOrientation(LinearLayout.VERTICAL);
                capWrap.setPadding(dp(14),dp(6),dp(14),dp(14));
                LinearLayout capLblRow=new LinearLayout(this); capLblRow.setOrientation(LinearLayout.HORIZONTAL); capLblRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                capLblRow.setPadding(0,0,0,dp(6));
                capLblRow.addView(t("✉",12,false,color));
                TextView capLbl=t("  MESAJ",10,true,0xFF3D4455); capLbl.setLetterSpacing(0.06f);
                capLbl.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); capLblRow.addView(capLbl);
                TextView tagBadge=t("{isim}",9,true,color); tagBadge.setPadding(dp(6),dp(2),dp(6),dp(2));
                android.graphics.drawable.GradientDrawable tagBg=new android.graphics.drawable.GradientDrawable();
                tagBg.setColor(colorDim); tagBg.setCornerRadius(dp(5)); tagBadge.setBackground(tagBg); capLblRow.addView(tagBadge);
                capWrap.addView(capLblRow);
                LinearLayout capBox=new LinearLayout(this); capBox.setOrientation(LinearLayout.VERTICAL);
                android.graphics.drawable.GradientDrawable cbBg=new android.graphics.drawable.GradientDrawable();
                cbBg.setColor(0xFF0D0E11); cbBg.setCornerRadius(dp(10)); cbBg.setStroke(dp(1),0xFF1E2028); capBox.setBackground(cbBg);
                capBox.setPadding(dp(12),dp(10),dp(12),dp(10));
                final EditText capInput=new EditText(this); capInput.setText(caption);
                capInput.setHint("Ürün adı, fiyat, detay..."); capInput.setTextColor(Color.WHITE); capInput.setHintTextColor(0xFF2E3340);
                capInput.setTextSize(13); capInput.setBackground(null); capInput.setMinLines(1); capInput.setMaxLines(3);
                capInput.setOnFocusChangeListener((v,f)->{
                    if(!f){ while(albumCaptions.size()<=aIdx) albumCaptions.add(""); albumCaptions.set(aIdx,capInput.getText().toString()); save(); }
                });
                capBox.addView(capInput); capWrap.addView(capBox); aBody.addView(capWrap);

            } else {
                // ── GÖNDERİM MODU BODY ──
                // Mini foto şeridi (yatay, sil yok)
                if(!photos.isEmpty()){
                    android.widget.HorizontalScrollView hsv=new android.widget.HorizontalScrollView(this);
                    hsv.setHorizontalScrollBarEnabled(false);
                    hsv.setBackgroundColor(0xFF0D0E11);
                    LinearLayout thumbRow=new LinearLayout(this); thumbRow.setOrientation(LinearLayout.HORIZONTAL);
                    thumbRow.setPadding(dp(10),dp(8),dp(10),dp(8)); thumbRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    for(String uri:photos){
                        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        android.graphics.drawable.GradientDrawable tBg=new android.graphics.drawable.GradientDrawable();
                        tBg.setColor(0xFF1E2533); tBg.setCornerRadius(dp(8)); img.setBackground(tBg);
                        img.setClipToOutline(true);
                        try{ img.setImageURI(Uri.parse(uri)); }catch(Throwable ignored){}
                        LinearLayout.LayoutParams tLp=new LinearLayout.LayoutParams(dp(52),dp(52)); tLp.setMargins(0,0,dp(4),0); img.setLayoutParams(tLp);
                        thumbRow.addView(img);
                    }
                    hsv.addView(thumbRow); aBody.addView(hsv);
                    android.view.View div3=new android.view.View(this); div3.setBackgroundColor(0xFF191C22);
                    aBody.addView(div3,new LinearLayout.LayoutParams(-1,dp(1)));
                }

                // Liste seçici + Gönder butonu
                LinearLayout sendRow=new LinearLayout(this);
                sendRow.setOrientation(LinearLayout.HORIZONTAL);
                sendRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                sendRow.setPadding(dp(14),dp(12),dp(14),dp(12));

                // Liste seçici
                LinearLayout listSel=new LinearLayout(this); listSel.setOrientation(LinearLayout.HORIZONTAL);
                listSel.setGravity(android.view.Gravity.CENTER_VERTICAL);
                android.graphics.drawable.GradientDrawable lsBg=new android.graphics.drawable.GradientDrawable();
                lsBg.setColor(0xFF0D0E11); lsBg.setCornerRadius(dp(10)); lsBg.setStroke(dp(1),0xFF1E2028); listSel.setBackground(lsBg);
                listSel.setPadding(dp(10),dp(9),dp(10),dp(9));
                listSel.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
                TextView listIcon=t("👥",13,false,Color.WHITE); listIcon.setPadding(0,0,dp(6),0); listSel.addView(listIcon);
                // Aktif favlist adı
                String activeFavName=activeList.isEmpty()?(favLists.isEmpty()?"Liste seç":favLists.get(0)):activeList;
                int favCount=0;
                for(C c:contacts){ if(listsOfPhone(c.p).contains(activeFavName)) favCount++; }
                final String favDisplay=favLists.isEmpty()?"Liste seç":activeFavName+" · "+favCount+" kişi";
                TextView listVal=t(favDisplay,12,true,favLists.isEmpty()?0xFF3D4455:0xFF7C7FDB);
                listVal.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); listSel.addView(listVal);
                TextView listArrow=t("▾",12,false,0xFF3D4455); listArrow.setPadding(dp(4),0,0,0); listSel.addView(listArrow);

                // Favlist seçim popup
                listSel.setOnClickListener(v->{
                    if(favLists.isEmpty()){ toast("Önce favori liste oluşturun"); return; }
                    String[] items=favLists.toArray(new String[0]);
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Liste Seç")
                        .setItems(items,(d,w)->{ activeList=favLists.get(w); save(); mediaScreen(); })
                        .show();
                });
                sendRow.addView(listSel);

                // Gönder butonu
                boolean canSend=!photos.isEmpty()&&!favLists.isEmpty();
                TextView sendBtn=new TextView(this);
                sendBtn.setText(canSend?"🚀 Gönder":"🚀");
                sendBtn.setTextSize(12); sendBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                sendBtn.setTextColor(canSend?Color.WHITE:0xFF2E3340);
                sendBtn.setGravity(android.view.Gravity.CENTER);
                android.graphics.drawable.GradientDrawable sbBg=new android.graphics.drawable.GradientDrawable();
                sbBg.setColor(canSend?0xFF5B5BD6:0xFF13151A); sbBg.setCornerRadius(dp(10));
                if(!canSend) sbBg.setStroke(dp(1),0xFF1E2028);
                sendBtn.setBackground(sbBg); sendBtn.setPadding(dp(16),dp(9),dp(16),dp(9));
                LinearLayout.LayoutParams sbLp=new LinearLayout.LayoutParams(-2,-2); sbLp.setMargins(dp(8),0,0,0); sendBtn.setLayoutParams(sbLp);
                if(canSend){
                    sendBtn.setOnClickListener(v->queueAlbum(aIdx));
                }
                sendRow.addView(sendBtn);
                aBody.addView(sendRow);

                // Eğer bu albüm kuyrukta/gönderimdeyse progress göster
                checkAlbumQueueStatus(aIdx,aBody,color);
            }

            aCard.addView(aBody);
            root.addView(aCard);
        }

        // ── YENİ ALBÜM (sadece yönetim modunda) ──
        if(!albumSendMode){
            LinearLayout addCard=new LinearLayout(this);
            addCard.setOrientation(LinearLayout.HORIZONTAL);
            addCard.setGravity(android.view.Gravity.CENTER);
            addCard.setPadding(dp(16),dp(17),dp(16),dp(17));
            android.graphics.drawable.GradientDrawable acBg=new android.graphics.drawable.GradientDrawable();
            acBg.setColor(0x00000000); acBg.setCornerRadius(dp(20)); acBg.setStroke(dp(2),0xFF1E2028);
            addCard.setBackground(acBg);
            LinearLayout.LayoutParams acLp=new LinearLayout.LayoutParams(-1,-2); acLp.setMargins(0,dp(4),0,dp(4)); addCard.setLayoutParams(acLp);
            LinearLayout addIcon=new LinearLayout(this); addIcon.setGravity(android.view.Gravity.CENTER);
            android.graphics.drawable.GradientDrawable aiBg2=new android.graphics.drawable.GradientDrawable();
            aiBg2.setColor(0x1A5B5BD6); aiBg2.setCornerRadius(dp(8)); aiBg2.setStroke(dp(1),0x335B5BD6); addIcon.setBackground(aiBg2);
            LinearLayout.LayoutParams aiLp=new LinearLayout.LayoutParams(dp(28),dp(28)); aiLp.setMargins(0,0,dp(10),0); addIcon.setLayoutParams(aiLp);
            addIcon.addView(t("＋",14,true,0xFF5B5BD6));
            addCard.addView(addIcon);
            addCard.addView(t("Yeni Albüm",13,true,0xFF5B5BD6));
            addCard.setOnClickListener(v->{
                albums.add(new ArrayList<>()); albumCaptions.add(""); albumNames.add(""); save(); mediaScreen();
            });
            root.addView(addCard);
        }

        // ── STICKY BOTTOM BAR ──────────────────────────────────────────────
        // FrameLayout kök içinde sticky simüle etmek için root scroll sonuna ekle
        LinearLayout bottomBar=new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(14),dp(16),dp(14),dp(20));
        android.graphics.drawable.GradientDrawable bbBg=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x000A0B0D,0xFF0A0B0D,0xFF0A0B0D});
        bottomBar.setBackground(bbBg);

        LinearLayout modeBtn=new LinearLayout(this);
        modeBtn.setOrientation(LinearLayout.HORIZONTAL);
        modeBtn.setGravity(android.view.Gravity.CENTER);
        modeBtn.setPadding(dp(16),dp(15),dp(16),dp(15));
        android.graphics.drawable.GradientDrawable mbBg=new android.graphics.drawable.GradientDrawable();
        if(!albumSendMode){
            mbBg.setColor(0xFF5B5BD6); mbBg.setCornerRadius(dp(16));
            modeBtn.setBackground(mbBg);
            modeBtn.addView(t("🚀  Gönderime Geç",14,true,Color.WHITE));
            modeBtn.setOnClickListener(v->{ albumSendMode=true; mediaScreen(); });
        } else {
            mbBg.setColor(0xFF13151A); mbBg.setCornerRadius(dp(16)); mbBg.setStroke(dp(1),0xFF2A2D36);
            modeBtn.setBackground(mbBg);
            modeBtn.addView(t("✏️  Albümlere Dön",14,true,0xFF8892A4));
            modeBtn.setOnClickListener(v->{ albumSendMode=false; mediaScreen(); });
        }
        bottomBar.addView(modeBtn,new LinearLayout.LayoutParams(-1,-2));
        root.addView(bottomBar);

        // Legacy migration
        if(!media.isEmpty()&&albums.isEmpty()){
            LinearLayout lc=card(); lc.addView(t("Eski Medya ("+media.size()+" foto)",14,true,YELLOW));
            lc.addView(t("Bu fotoğraflar Albüm 1'e taşınacak",12,false,MUTED));
            TextView mg=btn("Albüm 1'e Taşı",GREEN);
            mg.setOnClickListener(v->{ albums.add(new ArrayList<>(media)); albumCaptions.add(""); albumNames.add(""); media.clear(); save(); mediaScreen(); });
            lc.addView(mg); root.addView(lc);
        }
    }

    void checkAlbumQueueStatus(int aIdx, LinearLayout container, int color){
        // Sunucudan kuyruk durumunu sorgula ve progress göster
        new Thread(()->{
            try{
                java.net.URL url=new java.net.URL(apiBase+"/api/queue-status?token="+apiToken);
                java.net.HttpURLConnection con=(java.net.HttpURLConnection)url.openConnection();
                con.setConnectTimeout(2000); con.setReadTimeout(2000);
                String resp=new String(con.getInputStream().readAllBytes()); con.disconnect();
                org.json.JSONObject j=new org.json.JSONObject(resp);
                boolean active=j.optBoolean("active",false);
                int sent2=j.optInt("sent",0), total=j.optInt("total",0);
                int jobAlbum=j.optInt("albumIdx",-1);
                if(!active||jobAlbum!=aIdx) return;
                runOnUiThread(()->{
                    // Progress bar ekle
                    android.view.View progDiv=new android.view.View(this);
                    progDiv.setBackgroundColor(0xFF191C22);
                    container.addView(progDiv,new LinearLayout.LayoutParams(-1,dp(1)));

                    LinearLayout progWrap=new LinearLayout(this);
                    progWrap.setOrientation(LinearLayout.VERTICAL);
                    progWrap.setPadding(dp(14),dp(10),dp(14),dp(12));
                    android.widget.ProgressBar pb=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                    pb.setMax(total>0?total:1); pb.setProgress(sent2);
                    LinearLayout.LayoutParams pbLp=new LinearLayout.LayoutParams(-1,dp(4)); pbLp.setMargins(0,0,0,dp(8)); pb.setLayoutParams(pbLp);
                    progWrap.addView(pb);
                    LinearLayout progRow=new LinearLayout(this);
                    progRow.setOrientation(LinearLayout.HORIZONTAL);
                    progRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    TextView progTxt=t(sent2+" / "+total+" gönderildi",11,true,0xFF3FB950);
                    progTxt.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1)); progRow.addView(progTxt);
                    TextView stopBtn=t("■ Durdur",10,true,0xFFEF4444);
                    stopBtn.setPadding(dp(8),dp(3),dp(8),dp(3));
                    android.graphics.drawable.GradientDrawable stBg=new android.graphics.drawable.GradientDrawable();
                    stBg.setColor(0x14EF4444); stBg.setCornerRadius(dp(6)); stBg.setStroke(dp(1),0x33EF4444); stopBtn.setBackground(stBg);
                    stopBtn.setOnClickListener(v->stopQueueAlbum());
                    progRow.addView(stopBtn); progWrap.addView(progRow);
                    container.addView(progWrap);
                });
            }catch(Exception ignored){}
        }).start();
    }

    void queueAlbum(int aIdx){
        if(activeList.isEmpty()){ toast("Önce bir liste seçin"); return; }
        ArrayList<String> photos=albums.get(aIdx);
        if(photos.isEmpty()){ toast("Bu albümde fotoğraf yok"); return; }
        String caption=aIdx<albumCaptions.size()?albumCaptions.get(aIdx):"";
        // Kuyruğa ekle
        new Thread(()->{
            try{
                org.json.JSONObject body=new org.json.JSONObject();
                body.put("token",apiToken);
                body.put("albumIdx",aIdx);
                body.put("list",activeList);
                body.put("caption",caption);
                org.json.JSONArray photosArr=new org.json.JSONArray();
                for(String p:photos) photosArr.put(p);
                body.put("photos",photosArr);
                java.net.URL url=new java.net.URL(apiBase+"/api/queue-album");
                java.net.HttpURLConnection con=(java.net.HttpURLConnection)url.openConnection();
                con.setRequestMethod("POST"); con.setDoOutput(true);
                con.setRequestProperty("Content-Type","application/json");
                con.setConnectTimeout(4000);
                byte[] out=body.toString().getBytes("UTF-8");
                con.getOutputStream().write(out); con.getOutputStream().close();
                int code=con.getResponseCode(); con.disconnect();
                runOnUiThread(()->{ toast(code==200?"Gönderim başladı!":"Hata: "+code); mediaScreen(); });
            }catch(Exception e){ runOnUiThread(()->toast("Bağlantı hatası")); }
        }).start();
    }

    void stopQueueAlbum(){
        new Thread(()->{
            try{
                java.net.URL url=new java.net.URL(apiBase+"/api/queue-stop?token="+apiToken);
                java.net.HttpURLConnection con=(java.net.HttpURLConnection)url.openConnection();
                con.setConnectTimeout(3000); con.getResponseCode(); con.disconnect();
                runOnUiThread(()->{ toast("Durduruldu"); mediaScreen(); });
            }catch(Exception e){ runOnUiThread(()->toast("Hata")); }
        }).start();
    }

    void cell(GridLayout grid,String uri){
        LinearLayout cell=new LinearLayout(this); cell.setOrientation(LinearLayout.VERTICAL); cell.setPadding(dp(7),dp(7),dp(7),dp(7)); cell.setBackground(bg(CARD2,16));
        ImageView img=new ImageView(this); img.setScaleType(ImageView.ScaleType.CENTER_CROP); try{img.setImageURI(Uri.parse(uri));}catch(Exception e){}
        cell.addView(img,new LinearLayout.LayoutParams(-1,dp(150)));
        TextView rem=t("Kaldır",13,true,RED); rem.setGravity(Gravity.CENTER); rem.setOnClickListener(v->{media.remove(uri);save();mediaScreen();}); cell.addView(rem);
        GridLayout.LayoutParams gp=new GridLayout.LayoutParams(); gp.width=(getResources().getDisplayMetrics().widthPixels-dp(44))/2; gp.setMargins(dp(4),dp(4),dp(4),dp(4)); grid.addView(cell,gp);
    }
    void galleryPicker(){ perms(false); Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(Intent.createChooser(i,"Galeriden coklu gorsel sec"),REQ_MEDIA); }
    void galleryPickerForAlbum(){ perms(false); Intent i=new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(Intent.createChooser(i,"Album "+(pickingAlbumIdx+1)+" icin gorsel sec"),REQ_ALBUM_BASE+pickingAlbumIdx); }
    void filePicker(){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE); i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*","application/pdf"}); startActivityForResult(Intent.createChooser(i,"PDF / Dosya seç"),REQ_MEDIA); }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null) return;

        // Albüm icin secim
        if(req>=REQ_ALBUM_BASE){
            int aIdx=req-REQ_ALBUM_BASE;
            while(albums.size()<=aIdx) albums.add(new ArrayList<>());
            ArrayList<String> photos=albums.get(aIdx);
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount();i++){
                    Uri u=data.getClipData().getItemAt(i).getUri();
                    persist(u);
                    if(!photos.contains(u.toString())) photos.add(u.toString());
                }
            } else if(data.getData()!=null){
                Uri u=data.getData(); persist(u);
                if(!photos.contains(u.toString())) photos.add(u.toString());
            }
            save(); mediaScreen();
            return;
        }

        // Normal medya secimi (eski uyumluluk)
        if(req==REQ_MEDIA){
            if(data.getClipData()!=null){
                for(int i=0;i<data.getClipData().getItemCount();i++){
                    Uri u=data.getClipData().getItemAt(i).getUri();
                    persist(u);
                    if(!media.contains(u.toString())) media.add(u.toString());
                }
            } else if(data.getData()!=null){
                Uri u=data.getData(); persist(u);
                if(!media.contains(u.toString())) media.add(u.toString());
            }
            save(); mediaScreen();
        }
    }
    void persist(Uri u){ try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){} }


    LinkedHashSet<String> getSelectedSendPhones(){
        LinkedHashSet<String> all=new LinkedHashSet<>();
        if(selectedFavLists.isEmpty() && activeList!=null) selectedFavLists.add(activeList);
        for(String l:selectedFavLists) all.addAll(getListPhones(l));
        return all;
    }

    String selectedFavTitle(){
        if(selectedFavLists.isEmpty()) return activeList;
        if(selectedFavLists.size()==1) return selectedFavLists.iterator().next();
        return selectedFavLists.size()+" fav liste seçili";
    }

    
    void scheduleSendDialog(){
        final LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(8),dp(12),dp(8));

        final EditText hour=input("", "Saat: 09");
        final EditText minute=input("", "Dakika: 30");
        box.addView(t("Seçili fav listeler saat gelince otomatik gönderilir.",13,false,MUTED));
        box.addView(hour);
        box.addView(minute);

        new AlertDialog.Builder(this)
            .setTitle("Zamanlı Gönderim")
            .setView(box)
            .setPositiveButton("Planla",(d,w)->{
                try{
                    int h=Integer.parseInt(hour.getText().toString().trim());
                    int m=Integer.parseInt(minute.getText().toString().trim());
                    Calendar c=Calendar.getInstance();
                    c.set(Calendar.HOUR_OF_DAY,h);
                    c.set(Calendar.MINUTE,m);
                    c.set(Calendar.SECOND,0);
                    c.set(Calendar.MILLISECOND,0);
                    if(c.getTimeInMillis()<System.currentTimeMillis()) c.add(Calendar.DAY_OF_MONTH,1);
                    scheduledSendAt=c.getTimeInMillis();
                    saveSchedule();
                    startScheduleWatcher();
                    toast("Planlandı: "+String.format("%02d:%02d",h,m));
                    sendScreen();
                }catch(Exception e){ toast("Saat/dakika hatalı"); }
            })
            .setNegativeButton("İptal",null)
            .show();
    }

    void saveSchedule(){
        appPrefs().edit().putLong("scheduledSendAt",scheduledSendAt).apply();
    }


    void loadSchedule(){
        scheduledSendAt=appPrefs().getLong("scheduledSendAt",0);
        if(scheduledSendAt>System.currentTimeMillis()) startScheduleWatcher();
    }


    String scheduleText(){
        if(scheduledSendAt<=0) return "Zamanlı gönderim yok";
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("dd.MM HH:mm",java.util.Locale.getDefault());
        return "Planlandı: "+f.format(new java.util.Date(scheduledSendAt));
    }

    void cancelSchedule(){
        scheduledSendAt=0;
        saveSchedule();
        toast("Zamanlı gönderim iptal edildi");
        sendScreen();
    }

    void startScheduleWatcher(){
        scheduleHandler.removeCallbacksAndMessages(null);
        long delay=Math.max(1000, scheduledSendAt-System.currentTimeMillis());
        scheduleHandler.postDelayed(()->{
            if(scheduledSendAt>0 && System.currentTimeMillis()>=scheduledSendAt){
                scheduledSendAt=0;
                saveSchedule();
                toast("Zamanlı gönderim başladı");
                startSend();
            }
        },delay);
    }



    


    


    


    


    


    


    void addScheduleCard(){
        LinearLayout sc=card();
        sc.addView(t("⏰ Zamanlı Gönderim",20,true,Color.WHITE));
        sc.addView(t(scheduleText(),14,false,MUTED));
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView plan=btn("ZAMAN PLANLA",BLUE);
        plan.setOnClickListener(v->scheduleSendDialog());
        row.addView(plan,new LinearLayout.LayoutParams(0,dp(48),1));
        TextView cancel=btn("İPTAL",YELLOW);
        cancel.setOnClickListener(v->cancelSchedule());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1);
        lp.setMargins(dp(8),0,0,0);
        row.addView(cancel,lp);
        sc.addView(row);
        root.addView(sc);
    }


void sendScreen(){
        base("Gönderim Kontrol",false);
        LinkedHashSet<String> listPhones=getSelectedSendPhones();

        LinearLayout c=card();
        c.addView(t("Gönderilecek Liste",14,false,MUTED));
        c.addView(t("⭐ "+selectedFavTitle()+" ("+listPhones.size()+" kişi)",18,true,Color.WHITE));
        TextView choose=btn("Fav Listeleri Seç",BLUE);
        choose.setOnClickListener(v->chooseListDialog());
        c.addView(choose);
        root.addView(c);


        LinearLayout counts=new LinearLayout(this);
        counts.setOrientation(LinearLayout.HORIZONTAL);
        counts.addView(statBox("👥","Kişi",String.valueOf(listPhones.size()),"kişi"),new LinearLayout.LayoutParams(0,-2,1));
        counts.addView(statBox("▧","Medya",String.valueOf(media.size()),"dosya"),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(counts);

        LinearLayout form=card();
        // Mesaj kutusu - sadece albüm yoksa göster
        if(albums.isEmpty()){
            form.addView(t("Mesaj (isteğe bağlı)",14,false,MUTED));
            msgBox=input("","Mesaj yazmazsan yazi gonderilmez");
            msgBox.setMinLines(3);
            form.addView(msgBox);
        } else {
            // Albüm mesajları medya ekranında ayarlanıyor
            msgBox=input("",""); // boş - kullanılmaz
            msgBox.setVisibility(android.view.View.GONE);
            form.addView(msgBox);
            // Albüm özeti göster
            LinearLayout albumInfo=new LinearLayout(this);
            albumInfo.setOrientation(LinearLayout.VERTICAL);
            albumInfo.setBackgroundColor(0xFF052e16);
            android.graphics.drawable.GradientDrawable aiBg=new android.graphics.drawable.GradientDrawable();
            aiBg.setColor(0xFF052e16); aiBg.setCornerRadius(dp(10)); aiBg.setStroke(dp(1),0xFF16a34a);
            albumInfo.setBackground(aiBg);
            albumInfo.setPadding(dp(12),dp(10),dp(12),dp(10));
            int totalPh=0; for(ArrayList<String> al:albums) totalPh+=al.size();
            albumInfo.addView(t(albums.size()+" albüm • "+totalPh+" fotoğraf hazır",14,true,0xFF4ade80));
            for(int ai=0;ai<albums.size();ai++){
                String cap=ai<albumCaptions.size()?albumCaptions.get(ai):"";
                String preview=cap.length()>30?cap.substring(0,30)+"...":cap;
                albumInfo.addView(t("Albüm "+(ai+1)+": "+preview,12,false,0xFF6b7280));
            }
            TextView editAlbums=new TextView(this);
            editAlbums.setText("Albümleri Düzenle ->");
            editAlbums.setTextColor(0xFF22d3ee); editAlbums.setTextSize(12);
            editAlbums.setPadding(0,dp(6),0,0);
            editAlbums.setOnClickListener(v->mediaScreen());
            albumInfo.addView(editAlbums);
            form.addView(albumInfo);
        }

        // Albüm sayısı bilgisi
        if(!albums.isEmpty()){
            int totalP=0; for(ArrayList<String> al:albums) totalP+=al.size();
            form.addView(t(albums.size()+" albüm • "+totalP+" foto • Her kişiye sırayla gönderilir",12,false,0xFF4ade80));
        }

        // OTO MOD - sabit akilli profil
        delayMinBox=input("8",""); delayMinBox.setVisibility(android.view.View.GONE);
        delayMaxBox=input("15",""); delayMaxBox.setVisibility(android.view.View.GONE);
        mediaDelayBox=input("5",""); mediaDelayBox.setVisibility(android.view.View.GONE);
        personDelayBox=input("8",""); personDelayBox.setVisibility(android.view.View.GONE);
        form.addView(delayMinBox); form.addView(delayMaxBox);
        form.addView(mediaDelayBox); form.addView(personDelayBox);


        statusText=t("Hazır",14,true,YELLOW);
        form.addView(statusText);
        sendButton=btn("➤ GÖNDERİMİ BAŞLAT",GREEN);
        sendButton.setOnClickListener(v->{ if(sending) stopSend(); else startSend();});
        form.addView(sendButton);
        root.addView(form);

        LinearLayout live=card();
        live.addView(t("Canlı İlerleme",20,true,Color.WHITE));
        progressText=t("0% gönderildi",18,true,GREEN);
        currentPersonText=t("Şu an: bekleniyor",14,false,Color.WHITE);
        etaText=t("Kalan süre: hesaplanmadı",13,false,MUTED);
        sendProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        sendProgress.setMax(100);
        sendProgress.setProgress(0);
        live.addView(progressText);
        live.addView(sendProgress,new LinearLayout.LayoutParams(-1,dp(16)));
        live.addView(currentPersonText);
        live.addView(etaText);
        // Kuyruk durumunu göster
        String queueInit=queue.isEmpty()?"Kuyrukta: Yok":"Kuyrukta: "+queue.size()+" kisi bekliyor";
        String sentInit=sent.isEmpty()?"Gonderilen: Henüz yok":"Gonderilen: "+sent.size()+" kisi";
        queueText=t(queueInit,13,false,Color.WHITE);
        sentText=t(sentInit,13,false,GREEN);
        live.addView(queueText);
        live.addView(sentText);

        // Kuyruk varsa otomatik devam et
        if(!queue.isEmpty() && !sending){
            runOnUiThread(()->{
                statusText.setText("Kuyrukta "+queue.size()+" kisi var, devam ediliyor...");
                new android.os.Handler().postDelayed(()->startSend(), 1500);
            });
        }
        root.addView(live);
    }



    void chooseListDialog(){
        final String[] arr=favLists.toArray(new String[0]);
        final boolean[] checked=new boolean[arr.length];

        for(int i=0;i<arr.length;i++){
            checked[i]=selectedFavLists.contains(arr[i]);
        }

        new AlertDialog.Builder(this)
            .setTitle("Fav listelerini seç")
            .setMultiChoiceItems(arr,checked,(d,which,isChecked)->{
                checked[which]=isChecked;
            })
            .setPositiveButton("Uygula",(d,w)->{
                selectedFavLists.clear();
                for(int i=0;i<arr.length;i++){
                    if(checked[i]) selectedFavLists.add(arr[i]);
                }
                if(selectedFavLists.isEmpty()){
                    toast("En az 1 fav liste seçilmeli");
                    return;
                }
                activeList=selectedFavLists.iterator().next();
                selected.clear();
                selected.addAll(getSelectedSendPhones());
                save();
                sendScreen();
            })
            .setNegativeButton("İptal",null)
            .show();
    }




    ArrayList<String> randomizedMediaList(){
        ArrayList<String> list=new ArrayList<>();
        list.addAll(media);
        Collections.shuffle(list,new Random(System.nanoTime()));
        return list;
    }

    String randomCaptionStyle(String msg){
        if(msg==null) return "";
        String m=msg.trim();
        if(m.length()==0) return "";
        Random rnd=new Random(System.nanoTime());
        // Tam emoji listesi: ⭐💫🎉⚡💮🌸🍁🍂🌼🌴🍀☘️🍃🌱🌿❄️🐞🦋🧣🟠🟡🟢🔵🟣🟤🧕
        String[] emojis={
            "\u2B50",           // ⭐
            "\uD83D\uDCAB",     // 💫
            "\uD83C\uDF89",     // 🎉
            "\u26A1",           // ⚡
            "\uD83D\uDCAE",     // 💮
            "\uD83C\uDF38",     // 🌸
            "\uD83C\uDF41",     // 🍁
            "\uD83C\uDF42",     // 🍂
            "\uD83C\uDF3C",     // 🌼
            "\uD83C\uDF34",     // 🌴
            "\uD83C\uDF40",     // 🍀
            "\u2618\uFE0F",     // ☘️
            "\uD83C\uDF43",     // 🍃
            "\uD83C\uDF31",     // 🌱
            "\uD83C\uDF3F",     // 🌿
            "\u2744\uFE0F",     // ❄️
            "\uD83D\uDC1E",     // 🐞
            "\uD83E\uDD8B",     // 🦋
            "\uD83E\uDDE3",     // 🧣
            "\uD83D\uDFE0",     // 🟠
            "\uD83D\uDFE1",     // 🟡
            "\uD83D\uDFE2",     // 🟢
            "\uD83D\uDD35",     // 🔵
            "\uD83D\uDFE3",     // 🟣
            "\uD83D\uDFE4",     // 🟤
            "\uD83E\uDDD5"      // 🧕
        };
        // Başa rastgele 1 emoji
        String emoji=emojis[rnd.nextInt(emojis.length)];
        // Font: 0=kalın, 1=italik, 2=düz — yazıya dokunma
        int style=rnd.nextInt(3);
        String styled;
        if(style==0) styled="*"+m+"*";
        else if(style==1) styled="_"+m+"_";
        else styled=m;
        return emoji+" "+styled;
    }



    void startSend(){
        LinkedHashSet<String> sendSet=getSelectedSendPhones();
        ArrayList<C> targets=new ArrayList<>();
        for(String p:sendSet){
            C found=null;
            for(C c:contacts){
                if(normPhone(c.p).equals(normPhone(p))){
                    found=c;
                    break;
                }
            }
            if(found!=null) targets.add(found);
            else targets.add(new C(p,p));
        }
        if(targets.isEmpty()){toast("Önce listeye kişi ekle");return;}
        if(albums.isEmpty() && media.isEmpty()&&msgBox.getText().toString().trim().length()==0){toast("Albüm veya medya sec");return;}
        sending=true; stop=false; sent.clear(); queue.clear(); for(C c:targets)queue.add(c.n+" - "+c.p);
        sendButton.setText("GÖNDERİMİ DURDUR"); sendButton.setBackground(grad(RED,darker(RED),14));
        String msg=msgBox.getText().toString().trim();
        // WakeLock - ekran kapansa bile gonderim devam eder
        android.os.PowerManager pm2=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        final android.os.PowerManager.WakeLock wl2=pm2.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,"KatalogPanel:Send");
        wl2.acquire(4*60*60*1000L);

        new Thread(()->{
            int ok=0, fail=0, consecutive=0;
            final int total=targets.size();
            final long startMs=System.currentTimeMillis();
            Random rng=new Random();
            int turIdx=0; // Tur sayaci

            runOnUiThread(()->updateProgressUI(0,total,"basliyor",startMs));

            for(C c:targets){
                if(stop) break;

                // ── AKILLI PROFIL: Her 20 kiside mola ──────────────
                if(ok>0 && ok%20==0){
                    turIdx++;
                    // Tur bazli mola: 3dk, 4dk, 5dk, sonra 3-6dk random
                    int molaDk;
                    if(turIdx==1) molaDk=2;
                    else if(turIdx==2) molaDk=3;
                    else if(turIdx==3) molaDk=4;
                    else molaDk=2+rng.nextInt(3); // 2-4dk random
                    final int molaDkFinal=molaDk;
                    final int okFinal=ok;
                    runOnUiThread(()->statusText.setText(
                        "Mola: "+molaDkFinal+"dk bekleniyor... ("+okFinal+"/"+total+")"));
                    try{ Thread.sleep(molaDk*60*1000L); }catch(Exception ignored){}
                }

                // ── 3 art arda hata -> 90sn koruma molasi ──────────
                if(consecutive>=3){
                    runOnUiThread(()->statusText.setText("Koruma: 90sn bekleniyor..."));
                    try{ Thread.sleep(90000L+rng.nextInt(30000)); }catch(Exception ignored){}
                    consecutive=0;
                }

                int beforeDone=ok;
                runOnUiThread(()->{
                    statusText.setText("Gonderiliyor: "+c.n);
                    updateProgressUI(beforeDone,total,c.n,startMs);
                    refreshQueue();
                });

                try{
                    if(!albums.isEmpty()){
                        // YENİ: Albüm sistemi - her albümü sırayla gönder
                        for(int aIdx=0;aIdx<albums.size();aIdx++){
                            if(stop) break;
                            ArrayList<String> photos=albums.get(aIdx);
                            if(photos.isEmpty()) continue;
                            String albumCaption=aIdx<albumCaptions.size()?albumCaptions.get(aIdx):msg;
                            String finalCaption=albumCaption.replace("{isim}",c.n);
                            finalCaption=antiSpamText(randomCaptionStyle(finalCaption),rng);
                            uploadAlbum(c.p, photos, finalCaption);
                            // Albümler arasi 3-6sn bekle
                            if(aIdx<albums.size()-1){
                                Thread.sleep((2+rng.nextInt(3))*1000L);
                            }
                        }
                    } else {
                        // ESKİ: Tek medya listesi
                        ArrayList<String> mediaOrder=randomizedMediaList();
                        if(!mediaOrder.isEmpty()){
                            String caption=msg.length()>0?
                                antiSpamText(randomCaptionStyle(msg.replace("{isim}",c.n)),rng):"";
                            uploadAlbum(c.p, mediaOrder, caption);
                        } else if(false && msg.length()>0){
                            JSONObject body=new JSONObject();
                            body.put("phone",c.p);
                            body.put("message",antiSpamText(
                                randomCaptionStyle(msg.replace("{isim}",c.n)),rng));
                            body.put("token",apiToken);
                            httpPost(apiBase+"/send?token="+apiToken,body.toString());
                        }
                    }

                    ok++; consecutive=0;
                    queue.remove(c.n+" - "+c.p);
                    sent.add(c.n+" - "+c.p);
                    reports.add(0,"Gonderildi: "+c.n+" - "+c.p);
                    save();
                    int doneNow=ok;
                    runOnUiThread(()->{
                        updateProgressUI(doneNow,total,c.n,startMs);
                        refreshQueue();
                    });

                    // ── Kisi arasi akilli bekleme ───────────────────
                    // Tur 1: 8sn, Tur 2: 10sn, Tur 3: 12sn, sonra 8-15sn random
                    int baseDelay;
                    if(turIdx==0) baseDelay=6;
                    else if(turIdx==1) baseDelay=7;
                    else if(turIdx==2) baseDelay=8;
                    else baseDelay=6+rng.nextInt(5); // 6-10sn
                    // Gaussian varyasyon +-2sn
                    baseDelay+=(int)(rng.nextGaussian()*2);
                    baseDelay=Math.max(6,Math.min(baseDelay,20));
                    // Her 7 kiside +2-4sn ekstra
                    if(ok%7==0) baseDelay+=2+rng.nextInt(3);
                    Thread.sleep(baseDelay*1000L);

                }catch(Exception e){
                    fail++; consecutive++;
                    reports.add(0,"Hata: "+c.p+" / "+e.getMessage());
                    save();
                    try{ Thread.sleep((10+rng.nextInt(10))*1000L); }catch(Exception ignored){}
                }
            }

            try{ if(wl2!=null&&wl2.isHeld()) wl2.release(); }catch(Exception ignored){}
            sending=false; stop=false;
            int fOk=ok, fFail=fail;
            runOnUiThread(()->{
                updateProgressUI(fOk,targets.size(),"tamamlandi",startMs);
                statusText.setText("Gonderildi Basarili: "+fOk+" Hatali: "+fFail);
                sendButton.setText("GONDERIMi YENIDEN BASLAT");
                sendButton.setBackground(grad(GREEN,darker(GREEN),14));
                refreshQueue();
            });
        }).start();
    }


    void stopSend(){ stop=true; statusText.setText("Durduruluyor..."); }

    String antiSpamText(String original, Random rng){
        if(original==null||original.length()==0) return original;
        StringBuilder sb=new StringBuilder(original);
        String[] invisible={"\u200B","\u200C","\u200D"};
        sb.append(invisible[rng.nextInt(invisible.length)]);
        return sb.toString();
    }

    void updateProgressUI(int done, int total, String currentName, long startMs){
        int percent = total<=0 ? 0 : (int)Math.round((done*100.0)/total);
        if(sendProgress!=null) sendProgress.setProgress(percent);
        if(progressText!=null) progressText.setText(percent+"% gönderildi");
        if(currentPersonText!=null) currentPersonText.setText("Şu an: "+(currentName==null||currentName.length()==0?"bekleniyor":currentName));
        if(etaText!=null){
            long elapsed = System.currentTimeMillis()-startMs;
            if(done>0 && total>done){
                long avg = elapsed/done;
                long left = avg*(total-done);
                etaText.setText("Kalan süre: yaklaşık "+formatDuration(left));
            } else if(total>0 && done>=total){
                etaText.setText("Tamamlandı ✅");
            } else {
                etaText.setText("Kalan süre: hesaplanıyor...");
            }
        }
    }

    String formatDuration(long ms){
        long sec=Math.max(0,ms/1000);
        long min=sec/60;
        long rem=sec%60;
        if(min>=60){
            long h=min/60;
            long m=min%60;
            return h+"s "+m+"dk";
        }
        return min+"dk "+rem+"sn";
    }


    void refreshQueue(){ if(queueText!=null)queueText.setText("Kuyrukta:\n"+(queue.isEmpty()?"Yok":join(queue,"\n"))); if(sentText!=null)sentText.setText("Gönderilen:\n"+(sent.isEmpty()?"Henüz yok":join(sent,"\n"))); }

    // Tüm medyaları albüm olarak tek seferde gönder
    void uploadAlbum(String phone, ArrayList<String> uris, String caption) throws Exception {
        if(uris.isEmpty()) return;

        String boundary="----KatalogAlbum"+System.currentTimeMillis();
        HttpURLConnection conn=(HttpURLConnection)new URL(apiBase+"/send-album?token="+apiToken).openConnection();
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(180000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);

        java.io.OutputStream os=conn.getOutputStream();
        java.io.PrintStream ps=new java.io.PrintStream(os,true,"UTF-8");

        // phone
        ps.print("--"+boundary+"\r\n");
        ps.print("Content-Disposition: form-data; name=\"phone\"\r\n\r\n");
        ps.print(phone+"\r\n");

        // caption
        ps.print("--"+boundary+"\r\n");
        ps.print("Content-Disposition: form-data; name=\"caption\"\r\n\r\n");
        ps.print(caption+"\r\n");

        // Her medya dosyasını ekle
        for(int i=0;i<uris.size();i++){
            Uri uri=Uri.parse(uris.get(i));
            android.content.ContentResolver cr=getContentResolver();
            String mime=cr.getType(uri);
            if(mime==null) mime="image/jpeg";
            String ext=mime.contains("video")?"mp4":"jpg";

            ps.print("--"+boundary+"\r\n");
            ps.print("Content-Disposition: form-data; name=\"files\"; filename=\"media"+i+"."+ext+"\"\r\n");
            ps.print("Content-Type: "+mime+"\r\n\r\n");

            java.io.InputStream is=cr.openInputStream(uri);
            if(is!=null){
                byte[] buf=new byte[4096]; int n;
                while((n=is.read(buf))>-1) os.write(buf,0,n);
                is.close();
            }
            ps.print("\r\n");
        }

        ps.print("--"+boundary+"--\r\n");
        ps.flush();

        int code=conn.getResponseCode();
        if(code>=400){
            // Albüm başarısız - tek tek gönder
            for(String u:uris){
                upload(phone, "", Uri.parse(u));
            }
            if(!caption.isEmpty()) sendTextMessage(phone, caption);
        }
    }

    void sendTextMessage(String phone, String text) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(apiBase+"/send?token="+apiToken).openConnection();
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
        conn.setDoOutput(true); conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type","application/json");
        org.json.JSONObject obj=new org.json.JSONObject();
        obj.put("phone",phone); obj.put("message",text);
        conn.getOutputStream().write(obj.toString().getBytes("UTF-8"));
        conn.getResponseCode();
    }

    void upload(String phone,String caption,Uri fileUri)throws Exception{
        String boundary="----KP"+System.currentTimeMillis();
        HttpURLConnection conn=(HttpURLConnection)new URL(apiBase+"/send-media?token="+apiToken).openConnection(); conn.setConnectTimeout(15000); conn.setReadTimeout(120000); conn.setDoOutput(true); conn.setRequestMethod("POST"); conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        OutputStream out=conn.getOutputStream(); form(out,boundary,"phone",waPhone(phone)); form(out,boundary,"caption",caption);
        String mime=getContentResolver().getType(fileUri); if(mime==null)mime="image/jpeg"; String type="image"; if(mime.startsWith("video"))type="video"; if(mime.contains("pdf")||mime.contains("document"))type="document"; form(out,boundary,"type",type);
        String fn=type.equals("video")?"video.mp4":type.equals("document")?"document.pdf":"image.jpg";
        out.write(("--"+boundary+"\r\n").getBytes()); out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""+fn+"\"\r\n").getBytes()); out.write(("Content-Type: "+mime+"\r\n\r\n").getBytes());
        InputStream in=getContentResolver().openInputStream(fileUri); byte[] buf=new byte[8192]; int len; while((len=in.read(buf))!=-1)out.write(buf,0,len); in.close();
        out.write(("\r\n--"+boundary+"--\r\n").getBytes()); out.flush(); out.close(); int code=conn.getResponseCode(); if(code>=400)throw new Exception(readStream(conn.getErrorStream())); else readStream(conn.getInputStream());
    }
    void form(OutputStream out,String boundary,String name,String value)throws Exception{ out.write(("--"+boundary+"\r\n").getBytes()); out.write(("Content-Disposition: form-data; name=\""+name+"\"\r\n\r\n").getBytes()); out.write((value+"\r\n").getBytes("UTF-8")); }

    void reportsScreen(){
        base("Raporlar",false);

        // Kuyruk durumu - en üstte göster
        if(!queue.isEmpty()){
            LinearLayout qCard=card();
            qCard.addView(t("Bekleyen Kuyruk",16,true,YELLOW));
            qCard.addView(t(queue.size()+" kisi gonderim bekliyor",13,false,MUTED));
            for(int i=0;i<Math.min(5,queue.size());i++){
                qCard.addView(t("• "+new ArrayList<>(queue).get(i),13,false,Color.WHITE));
            }
            if(queue.size()>5) qCard.addView(t("... ve "+(queue.size()-5)+" kisi daha",12,false,MUTED));
            TextView resumeBtn=btn("GONDERIME DEVAM ET ("+queue.size()+" kisi)",GREEN);
            resumeBtn.setOnClickListener(v->sendScreen());
            qCard.addView(resumeBtn);
            root.addView(qCard);
        }

        // Raporlar
        LinearLayout c=card();
        c.addView(t("Gonderim Raporlari",22,true,Color.WHITE));
        c.addView(t("Kayit: "+reports.size(),15,false,MUTED));
        TextView clear=btn("Raporlari Temizle",RED);
        clear.setOnClickListener(v->{reports.clear();save();reportsScreen();});
        c.addView(clear);
        root.addView(c);

        for(String r:reports){
            LinearLayout x=card();
            x.addView(t(r,13,false,r.startsWith("Hata")?RED:Color.WHITE));
            root.addView(x);
        }
    }


    TextView compactConnectionPill(){
        TextView pill=t("● Kontrol",13,true,YELLOW);
        pill.setPadding(dp(10),dp(5),dp(10),dp(5));
        GradientDrawable bg=grad(Color.rgb(10,24,18),Color.rgb(3,9,7),18);
        bg.setStroke(dp(1),Color.rgb(34,90,55));
        pill.setBackground(bg);
        pill.setOnClickListener(v->settingsScreen());
        connectionText=pill;
        checkStatus();
        return pill;
    }


    void connectionRepairDialog(){
        new AlertDialog.Builder(this)
            .setTitle("Bağlantı Sorununu Onar")
            .setMessage("VPS kontrol komutları:\nssh root@178.105.143.110\ncd ~/wa-server\npm2 restart all\ncurl http://127.0.0.1:3001/status")
            .setPositiveButton("Kontrol Et",(d,w)->checkStatus())
            .setNegativeButton("Kapat",null)
            .show();
    }


    void showQrDialog(){
        new Thread(()->{
            try{
                JSONObject j=new JSONObject(httpGet(API+"/qr"));
                String qr=j.optString("qr","");
                runOnUiThread(()->{
                    new AlertDialog.Builder(this)
                        .setTitle("QR Bağlantı")
                        .setMessage(qr.length()>0 ? "QR verisi hazır. VPS terminalinde QR daha net görünür.\n\n"+qr.substring(0,Math.min(qr.length(),350))+"..." : "QR hazır değil. Session bağlı olabilir veya QR henüz oluşmadı.")
                        .setPositiveButton("Durumu Kontrol Et",(d,w)->checkStatus())
                        .setNegativeButton("Kapat",null)
                        .show();
                });
            }catch(Exception e){ runOnUiThread(()->toast("QR alınamadı: "+e.getMessage())); }
        }).start();
    }

    void requestPairingCodeDialog(){
        final EditText phone=input("905416960617","Telefon numarası 90 ile");
        new AlertDialog.Builder(this)
            .setTitle("Telefon Kodu Al")
            .setView(phone)
            .setPositiveButton("Kod Al",(d,w)->{
                String p=normalize(phone.getText().toString());
                new Thread(()->{
                    try{
                        JSONObject body=new JSONObject();
                        body.put("phone",p);
                        JSONObject r=new JSONObject(httpPost(API+"/pairing-code",body.toString()));
                        String code=r.optString("code","");
                        String err=r.optString("error","");
                        if(code.length()>0){
                            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("pairing_code",code));
                        }
                        runOnUiThread(()->{
                            AlertDialog.Builder b=new AlertDialog.Builder(this)
                                .setTitle("Eşleştirme Kodu")
                                .setMessage(code.length()>0 ? code : "Kod alınamadı: "+err);

                                if(code.length()>0){
                                    b.setNeutralButton("KOPYALA",(dialogCopy,whichCopy)->{
                                        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                                        cm.setPrimaryClip(ClipData.newPlainText("pairing_code",code));
                                        toast("Kod kopyalandı ✅");
                                    });
                                }

                                b.setPositiveButton("Tamam",null)
                                 .show();
                        });
                    }catch(Exception e){ runOnUiThread(()->toast("Kod alınamadı: "+e.getMessage())); }
                }).start();
            })
            .setNegativeButton("İptal",null)
            .show();
    }

    
    
    void showMobileQrDialog(){
        if(apiToken==null || apiToken.length()<5){
            toast("Önce giriş yap");
            return;
        }

        final AlertDialog[] dlg=new AlertDialog[1];
        final LinearLayout[] box=new LinearLayout[1];
        final ImageView[] qrImg=new ImageView[1];
        final TextView[] statusTxt=new TextView[1];

        runOnUiThread(()->{
            box[0]=new LinearLayout(this);
            box[0].setOrientation(LinearLayout.VERTICAL);
            box[0].setPadding(dp(12),dp(12),dp(12),dp(12));

            statusTxt[0]=t("QR hazırlanıyor...",18,true,Color.BLACK);
            box[0].addView(statusTxt[0]);

            qrImg[0]=new ImageView(this);
            qrImg[0].setAdjustViewBounds(true);
            qrImg[0].setPadding(dp(8),dp(8),dp(8),dp(8));
            box[0].addView(qrImg[0],new LinearLayout.LayoutParams(dp(280),dp(280)));

            TextView hint=t("Otomatik yenileniyor...",14,false,Color.DKGRAY);
            box[0].addView(hint);

            dlg[0]=new AlertDialog.Builder(this)
                .setTitle("WhatsApp QR Bağlantı")
                .setView(box[0])
                .setNegativeButton("Kapat",null)
                .create();

            dlg[0].show();
        });

        new Thread(()->{
            int retry=0;

            while(retry<15){
                try{
                    JSONObject r=new JSONObject(httpGet(apiBase+"/mobile/qr?token="+apiToken));

                    boolean connected=r.optBoolean("connected",false);
                    String dataUrl=r.optString("qrDataUrl","");

                    runOnUiThread(()->{
                        try{
                            if(connected){
                                statusTxt[0].setText("WhatsApp bağlı ✅");
                                toast("WhatsApp bağlandı");
                                return;
                            }

                            if(dataUrl!=null && dataUrl.startsWith("data:image")){
                                String b64=dataUrl.substring(dataUrl.indexOf(",")+1);

                                byte[] bytes=android.util.Base64.decode(
                                    b64,
                                    android.util.Base64.DEFAULT
                                );

                                Bitmap bmp=BitmapFactory.decodeByteArray(
                                    bytes,
                                    0,
                                    bytes.length
                                );

                                qrImg[0].setImageBitmap(bmp);
                                statusTxt[0].setText("QR kodu okut");
                            }else{
                                statusTxt[0].setText(
                                    "QR hazırlanıyor..."
                                );
                            }

                        }catch(Exception e){
                            statusTxt[0].setText("QR render hata");
                        }
                    });

                    if(connected) break;

                    Thread.sleep(2000);

                }catch(Exception e){
                    final String err=e.getMessage();

                    runOnUiThread(()->{
                        if(statusTxt[0]!=null){
                            statusTxt[0].setText("Bağlantı hata");
                        }
                    });

                    try{ Thread.sleep(2000); }catch(Exception ignored){}
                }

                retry++;
            }

            runOnUiThread(()->{
                if(dlg[0]!=null && dlg[0].isShowing()){
                    if(qrImg[0].getDrawable()==null){
                        statusTxt[0].setText(
                            "QR alınamadı. Session sıfırlayıp tekrar deneyin."
                        );
                    }
                }
            });

        }).start();
    }


    void resetMobileSession(){
        if(apiToken==null || apiToken.length()<5){
            toast("Önce giriş yap");
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Session Sıfırla")
            .setMessage("Bu kullanıcı için WhatsApp bağlantısı sıfırlanacak ve yeni QR üretilecek. Emin misin?")
            .setPositiveButton("Sıfırla",(d,w)->{
                new Thread(()->{
                    try{
                        httpPost(apiBase+"/mobile/reset-session?token="+apiToken,"{}");
                        runOnUiThread(()->{
                            toast("Session sıfırlandı");
                            showMobileQrDialog();
                        });
                    }catch(Exception e){
                        runOnUiThread(()->toast("Sıfırlama hata: "+e.getMessage()));
                    }
                }).start();
            })
            .setNegativeButton("İptal",null)
            .show();
    }



    void ensureRuntimePermissions(){
        try{
            createNotificationChannel();

            if(Build.VERSION.SDK_INT>=33){
                if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},9071);
                }
            }

            try{
                PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
                if(pm!=null && Build.VERSION.SDK_INT>=23 && !pm.isIgnoringBatteryOptimizations(getPackageName())){
                    Intent i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:"+getPackageName()));
                    startActivity(i);
                }
            }catch(Exception ignored){}

        }catch(Exception ignored){}
    }

    void createNotificationChannel(){
        try{
            if(Build.VERSION.SDK_INT>=26){
                NotificationChannel ch=new NotificationChannel(
                    "fpro_send_channel",
                    "Katalog Panel Gönderim",
                    NotificationManager.IMPORTANCE_DEFAULT
                );
                ch.setDescription("Gönderim durumu ve arka plan bildirimleri");
                NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
                if(nm!=null) nm.createNotificationChannel(ch);
            }
        }catch(Exception ignored){}
    }

    void openBackgroundPermissionSettings(){
        try{
            Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:"+getPackageName()));
            startActivity(i);
        }catch(Exception e){
            toast("Ayarlar açılamadı");
        }
    }



void settingsScreen(){
        base("Ayarlar",false);

        // ── USER HERO ──────────────────────────────────────────────────────
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(18),dp(18),dp(18),dp(16));
        android.graphics.drawable.GradientDrawable heroBg=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BR_TL,
            new int[]{0xFF1A1F2E,0xFF0D1117,0xFF12101A});
        heroBg.setCornerRadius(dp(20)); heroBg.setStroke(dp(1),0xFF2D3748);
        hero.setBackground(heroBg);
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);
        heroLp.setMargins(0,dp(4),0,dp(10)); hero.setLayoutParams(heroLp);

        // Avatar + isim satırı
        LinearLayout userRow=new LinearLayout(this);
        userRow.setOrientation(LinearLayout.HORIZONTAL);
        userRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        userRow.setPadding(0,0,0,dp(14));

        LinearLayout avatar=new LinearLayout(this);
        avatar.setGravity(android.view.Gravity.CENTER);
        android.graphics.drawable.GradientDrawable avBg=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xFF4F46E5,0xFF7C3AED});
        avBg.setCornerRadius(dp(14)); avBg.setStroke(dp(1),0x664F46E5);
        avatar.setBackground(avBg);
        LinearLayout.LayoutParams avLp=new LinearLayout.LayoutParams(dp(52),dp(52));
        avLp.setMargins(0,0,dp(14),0); avatar.setLayoutParams(avLp);
        avatar.addView(t("👤",22,false,Color.WHITE));
        userRow.addView(avatar);

        LinearLayout userInfo=new LinearLayout(this);
        userInfo.setOrientation(LinearLayout.VERTICAL);
        userInfo.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        userInfo.addView(t(loginUser!=null?loginUser:"kullanici",18,true,Color.WHITE));
        // Admin badge
        boolean isAdmin=loginUser!=null&&loginUser.equalsIgnoreCase("admin");
        TextView roleBadge=t(isAdmin?"⚡ Admin":"👤 Kullanıcı",11,true,isAdmin?0xFF818CF8:0xFF58A6FF);
        roleBadge.setPadding(dp(8),dp(3),dp(8),dp(3));
        android.graphics.drawable.GradientDrawable rbBg=new android.graphics.drawable.GradientDrawable();
        rbBg.setColor(isAdmin?0x1A818CF8:0x1A58A6FF); rbBg.setCornerRadius(dp(6));
        rbBg.setStroke(dp(1),isAdmin?0x334F46E5:0x331F6FEB);
        roleBadge.setBackground(rbBg);
        LinearLayout.LayoutParams rbLp=new LinearLayout.LayoutParams(-2,-2);
        rbLp.setMargins(0,dp(5),0,0); roleBadge.setLayoutParams(rbLp);
        userInfo.addView(roleBadge);
        userRow.addView(userInfo);
        hero.addView(userRow);

        // Abonelik barı — async doldurulur
        LinearLayout subBar=new LinearLayout(this);
        subBar.setOrientation(LinearLayout.HORIZONTAL);
        subBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        subBar.setPadding(dp(12),dp(10),dp(12),dp(10));
        android.graphics.drawable.GradientDrawable subBg=new android.graphics.drawable.GradientDrawable();
        subBg.setColor(0xFF0D1117); subBg.setCornerRadius(dp(12)); subBg.setStroke(dp(1),0xFF2D3748);
        subBar.setBackground(subBg);

        TextView subIcon=t("🛡",18,false,Color.WHITE);
        LinearLayout.LayoutParams siLp=new LinearLayout.LayoutParams(-2,-2);
        siLp.setMargins(0,0,dp(10),0); subIcon.setLayoutParams(siLp);
        subBar.addView(subIcon);

        LinearLayout subInfo=new LinearLayout(this);
        subInfo.setOrientation(LinearLayout.VERTICAL);
        subInfo.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        subInfo.addView(t("Abonelik durumu",11,false,0xFF8892A4));
        // Progress bar placeholder
        android.widget.ProgressBar subProgress=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        subProgress.setMax(100); subProgress.setProgress(50);
        subProgress.setIndeterminate(true);
        LinearLayout.LayoutParams spLp=new LinearLayout.LayoutParams(-1,dp(5));
        spLp.setMargins(0,dp(5),0,0); subProgress.setLayoutParams(spLp);
        subInfo.addView(subProgress);
        subBar.addView(subInfo);

        final TextView subDays=t("...",12,true,0xFF3FB950);
        LinearLayout.LayoutParams sdLp=new LinearLayout.LayoutParams(-2,-2);
        sdLp.setMargins(dp(10),0,0,0); subDays.setLayoutParams(sdLp);
        subBar.addView(subDays);
        hero.addView(subBar);
        root.addView(hero);

        // Async abonelik sorgusu
        new Thread(()->{
            try{
                java.net.URL url=new java.net.URL(apiBase+"/api/check-subscription?token="+apiToken);
                java.net.HttpURLConnection con=(java.net.HttpURLConnection)url.openConnection();
                con.setConnectTimeout(3000);
                String resp=new String(con.getInputStream().readAllBytes());
                con.disconnect();
                org.json.JSONObject j=new org.json.JSONObject(resp);
                int kalan=j.optInt("kalan_gun",9999);
                String daysTxt=kalan>=9999?"Sınırsız":kalan+" gün kaldı";
                int dayColor=kalan<=7?0xFFEF4444:kalan<=30?0xFFEAB308:0xFF3FB950;
                int progressVal=kalan>=9999?100:Math.min(100,(int)(kalan*100/365f));
                runOnUiThread(()->{
                    subDays.setText(daysTxt); subDays.setTextColor(dayColor);
                    subProgress.setIndeterminate(false); subProgress.setProgress(progressVal);
                    android.graphics.drawable.ClipDrawable clip=(android.graphics.drawable.ClipDrawable)subProgress.getProgressDrawable();
                });
            }catch(Exception ignored){
                runOnUiThread(()->{ subDays.setText("—"); subProgress.setIndeterminate(false); });
            }
        }).start();

        // ── BAĞLANTI GRUBU ─────────────────────────────────────────────────
        root.addView(sectionLabel("Bağlantı"));
        LinearLayout connGroup=settingsGroup();

        connGroup.addView(settingsRow("📱","QR ile WhatsApp Bağla","Oturumu başlat veya yenile",0xFF4F46E5,
            v->showMobileQrDialog(), false));
        connGroup.addView(groupDivider());
        connGroup.addView(settingsRow("🔄","Oturumu Sıfırla","WhatsApp bağlantısını temizle",0xFFEAB308,
            v->resetMobileSession(), false));
        connGroup.addView(groupDivider());
        connGroup.addView(settingsRow("☁️","Cloud Sync","Favori listelerini sunucuyla senkronize et",0xFF3FB950,
            v->cloudSyncNow(), false));
        root.addView(connGroup);

        // ── SİSTEM GRUBU ───────────────────────────────────────────────────
        root.addView(sectionLabel("Sistem"));
        LinearLayout sysGroup=settingsGroup();
        sysGroup.addView(settingsRow("🔔","Bildirim İzinleri","Arka plan & bildirim erişimi",0xFF1F6FEB,
            v->ensureRuntimePermissions(), false));
        sysGroup.addView(groupDivider());
        sysGroup.addView(settingsRow("🔋","Pil Optimizasyonu","Arka planda çalışmaya izin ver",0xFF0891B2,
            v->openBackgroundPermissionSettings(), false));
        root.addView(sysGroup);

        // ── ADMİN GRUBU ────────────────────────────────────────────────────
        if(isAdmin){
            root.addView(sectionLabel("Admin"));
            LinearLayout adminGroup=settingsGroup();
            adminGroup.addView(settingsRow("👥","Kullanıcı Yönetimi","Ekle, sil, aktif/pasif yap",0xFF1F6FEB,
                v->showUserManagementDialog(), false));
            adminGroup.addView(groupDivider());
            adminGroup.addView(settingsRow("🔑","Şifre Değiştir","Hesap parolasını güncelle",0xFF7C3AED,
                v->showChangePasswordDialog(loginUser), false));
            root.addView(adminGroup);
        }

        // ── ÇIKIŞ ──────────────────────────────────────────────────────────
        LinearLayout logoutBtn=new LinearLayout(this);
        logoutBtn.setOrientation(LinearLayout.HORIZONTAL);
        logoutBtn.setGravity(android.view.Gravity.CENTER);
        logoutBtn.setPadding(dp(16),dp(15),dp(16),dp(15));
        android.graphics.drawable.GradientDrawable loBg=new android.graphics.drawable.GradientDrawable();
        loBg.setColor(0x14EF4444); loBg.setCornerRadius(dp(18)); loBg.setStroke(dp(1),0x40EF4444);
        logoutBtn.setBackground(loBg);
        LinearLayout.LayoutParams loLp=new LinearLayout.LayoutParams(-1,-2);
        loLp.setMargins(0,dp(4),0,dp(8)); logoutBtn.setLayoutParams(loLp);
        logoutBtn.addView(t("🚪  Çıkış Yap",15,true,0xFFEF4444));
        logoutBtn.setOnClickListener(v->logoutLogin());
        root.addView(logoutBtn);
    }




    void showUserManagementDialog(){
        if(!loginUser.equalsIgnoreCase("admin")) return;
        new Thread(()->{
            try{
                String resp=httpGet(apiBase+"/admin/users?token="+apiToken);
                if(resp==null||resp.trim().startsWith("<")){
                    runOnUiThread(()->toast("Sunucu hatasi")); return;
                }
                org.json.JSONObject r=new org.json.JSONObject(resp);
                if(!r.optBoolean("ok",false)){
                    runOnUiThread(()->toast("Hata: "+r.optString("error",""))); return;
                }
                org.json.JSONArray users=r.optJSONArray("users");
                if(users==null) users=new org.json.JSONArray();
                final org.json.JSONArray fu=users;
                runOnUiThread(()->{
                    LinearLayout box=new LinearLayout(this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setPadding(dp(8),dp(8),dp(8),dp(8));
                    for(int i=0;i<fu.length();i++){
                        try{
                            org.json.JSONObject u=fu.getJSONObject(i);
                            String uname=u.optString("username","");
                            boolean active=u.optBoolean("active",true);
                            LinearLayout row=new LinearLayout(this);
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                            row.setPadding(0,dp(6),0,dp(6));
                            TextView uLbl=t((active?"✅ ":"❌ ")+uname,14,true,active?GREEN:RED);
                            uLbl.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
                            row.addView(uLbl);
                            TextView pwBtn=smallBtn("SIFRE",BLUE);
                            pwBtn.setOnClickListener(v->showChangePasswordDialog(uname));
                            row.addView(pwBtn);
                            if(!uname.equalsIgnoreCase("admin")){
                                TextView togBtn=smallBtn(active?"KAPAT":"AC",active?RED:GREEN);
                                togBtn.setOnClickListener(v->toggleUser(uname,!active));
                                row.addView(togBtn);
                                TextView delBtn=smallBtn("SIL",RED);
                                delBtn.setOnClickListener(v->confirmDeleteUser(uname));
                                row.addView(delBtn);
                            }
                            box.addView(row);
                            View div=new View(this);
                            div.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));
                            div.setBackgroundColor(0x22FFFFFF);
                            box.addView(div);
                        }catch(Exception ignored){}
                    }
                    TextView addBtn=btn("+ Yeni Kullanici Ekle",GREEN);
                    addBtn.setOnClickListener(v->showAddUserDialog());
                    box.addView(addBtn);
                    new AlertDialog.Builder(this)
                        .setTitle("Kullanici Yonetimi")
                        .setView(box)
                        .setNegativeButton("Kapat",null)
                        .show();
                });
            }catch(Exception e){ runOnUiThread(()->toast("Hata: "+e.getMessage())); }
        }).start();
    }

    void showAddUserDialog(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(8),dp(16),dp(8));
        final EditText etUser=input("","Kullanici adi");
        final EditText etPass=input("","Sifre (min 6)");
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(t("Kullanici Adi",12,false,MUTED)); box.addView(etUser);
        box.addView(t("Sifre",12,false,MUTED)); box.addView(etPass);
        new AlertDialog.Builder(this).setTitle("Yeni Kullanici").setView(box)
            .setPositiveButton("Ekle",(d,w)->{
                String u=etUser.getText().toString().trim();
                String p=etPass.getText().toString();
                if(u.length()<2){toast("En az 2 karakter");return;}
                if(p.length()<6){toast("En az 6 karakter");return;}
                new Thread(()->{
                    try{
                        org.json.JSONObject body=new org.json.JSONObject();
                        body.put("username",u); body.put("password",p);
                        String resp=httpPost(apiBase+"/admin/add-user?token="+apiToken,body.toString());
                        org.json.JSONObject r=new org.json.JSONObject(resp);
                        runOnUiThread(()->{ if(r.optBoolean("ok",false)){toast("Eklendi: "+u); showUserManagementDialog();} else toast("Hata: "+r.optString("error","")); });
                    }catch(Exception e){runOnUiThread(()->toast("Hata: "+e.getMessage()));}
                }).start();
            }).setNegativeButton("Iptal",null).show();
    }

    void showChangePasswordDialog(String targetUser){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(8),dp(16),dp(8));
        final EditText etNew=input("","Yeni sifre");
        etNew.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText etNew2=input("","Tekrar gir");
        etNew2.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(t(targetUser+" - yeni sifre",13,false,MUTED));
        box.addView(etNew); box.addView(etNew2);
        new AlertDialog.Builder(this).setTitle("Sifre Degistir").setView(box)
            .setPositiveButton("Kaydet",(d,w)->{
                String p1=etNew.getText().toString();
                String p2=etNew2.getText().toString();
                if(p1.length()<6){toast("Min 6 karakter");return;}
                if(!p1.equals(p2)){toast("Sifreler eslesmyor");return;}
                new Thread(()->{
                    try{
                        org.json.JSONObject body=new org.json.JSONObject();
                        body.put("username",targetUser); body.put("newPassword",p1);
                        String resp=httpPost(apiBase+"/admin/change-password?token="+apiToken,body.toString());
                        org.json.JSONObject r=new org.json.JSONObject(resp);
                        runOnUiThread(()->{ if(r.optBoolean("ok",false)) toast("Sifre guncellendi"); else toast("Hata: "+r.optString("error","")); });
                    }catch(Exception e){runOnUiThread(()->toast("Hata: "+e.getMessage()));}
                }).start();
            }).setNegativeButton("Iptal",null).show();
    }

    void confirmDeleteUser(String username){
        new AlertDialog.Builder(this).setTitle("Kullanici Sil")
            .setMessage(username+" silinsin mi?")
            .setPositiveButton("Sil",(d,w)->deleteUser(username))
            .setNegativeButton("Iptal",null).show();
    }

    void deleteUser(String username){
        new Thread(()->{
            try{
                org.json.JSONObject body=new org.json.JSONObject();
                body.put("username",username);
                String resp=httpPost(apiBase+"/admin/delete-user?token="+apiToken,body.toString());
                org.json.JSONObject r=new org.json.JSONObject(resp);
                runOnUiThread(()->{ if(r.optBoolean("ok",false)){toast(username+" silindi"); showUserManagementDialog();} else toast("Hata: "+r.optString("error","")); });
            }catch(Exception e){runOnUiThread(()->toast("Hata: "+e.getMessage()));}
        }).start();
    }

    void toggleUser(String username, boolean activate){
        new Thread(()->{
            try{
                org.json.JSONObject body=new org.json.JSONObject();
                body.put("username",username); body.put("active",activate);
                String resp=httpPost(apiBase+"/admin/toggle-user?token="+apiToken,body.toString());
                org.json.JSONObject r=new org.json.JSONObject(resp);
                runOnUiThread(()->{ if(r.optBoolean("ok",false)){toast(username+(activate?" aktif":" pasif")); showUserManagementDialog();} else toast("Hata: "+r.optString("error","")); });
            }catch(Exception e){runOnUiThread(()->toast("Hata: "+e.getMessage()));}
        }).start();
    }

    void manualAddContactDialog(){
        final LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(8),dp(12),dp(8));
        final EditText name=input("","Ad Soyad / Firma");
        final EditText phone=input("","Telefon: 905xxxxxxxxx");
        final EditText note=input("","Not / Etiket");
        box.addView(name); box.addView(phone); box.addView(note);
        new AlertDialog.Builder(this)
            .setTitle("Yeni Kişi Ekle")
            .setView(box)
            .setPositiveButton("Kaydet",(d,w)->{
                String n=name.getText().toString().trim();
                String p=normalize(phone.getText().toString());
                if(n.length()==0)n="Yeni Kişi";
                if(p.length()<10){ toast("Telefon numarası hatalı"); return; }
                saveContactToPhone(n,p,note.getText().toString().trim());
                editingPhones.add(p);
                saveListPhones(activeList,editingPhones);
                loadContacts();
                toast("Kişi rehbere ve aktif listeye eklendi");
                listEditScreen();
            })
            .setNegativeButton("İptal",null)
            .show();
    }

    void saveContactToPhone(String name,String phone,String note){
        try{
            ArrayList<android.content.ContentProviderOperation> ops=new ArrayList<>();
            int raw=ops.size();
            ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.RawContacts.CONTENT_URI)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_TYPE,null)
                .withValue(android.provider.ContactsContract.RawContacts.ACCOUNT_NAME,null)
                .build());
            ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID,raw)
                .withValue(android.provider.ContactsContract.Data.MIMETYPE,android.provider.ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(android.provider.ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,name)
                .build());
            ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID,raw)
                .withValue(android.provider.ContactsContract.Data.MIMETYPE,android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,phone)
                .withValue(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE,android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build());
            if(note!=null && note.length()>0){
                ops.add(android.content.ContentProviderOperation.newInsert(android.provider.ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(android.provider.ContactsContract.Data.RAW_CONTACT_ID,raw)
                    .withValue(android.provider.ContactsContract.Data.MIMETYPE,android.provider.ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(android.provider.ContactsContract.CommonDataKinds.Note.NOTE,note)
                    .build());
            }
            getContentResolver().applyBatch(android.provider.ContactsContract.AUTHORITY,ops);
        }catch(Exception e){ toast("Rehbere yazılamadı: "+e.getMessage()); }
    }

    String listsOfPhone(String phone){
        ArrayList<String> names=new ArrayList<>();
        String np=normPhone(phone);
        for(String l:favLists){
            LinkedHashSet<String> phones=getListPhones(l);
            if(phones.contains(phone) || phones.contains(np)){
                names.add(l);
            }
        }
        return join(names,", ");
    }

    void addOrMoveDialog(C c){
        String phone=normPhone(c.p);
        String exists=listsOfPhone(phone);

        if(phoneInSet(editingPhones,c.p)){
            removePhoneFromSet(editingPhones,c.p);
            saveListPhones(activeList,editingPhones);
            save();
            if(adapter!=null) adapter.notifyDataSetChanged();
            updateCount();
            try{ new Thread(()->cloudPushFavContacts()).start(); }catch(Exception ignored){}
            return;
        }

        if(exists.length()==0){
            editingPhones.add(c.p);
            saveListPhones(activeList,editingPhones);
            if(adapter!=null) adapter.notifyDataSetChanged();
            updateCount();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Kişi zaten favoride")
            .setMessage(c.n+" şu listede: "+exists)
            .setPositiveButton("Bu listeye de ekle",(d,w)->{
                editingPhones.add(c.p);
                saveListPhones(activeList,editingPhones);
                if(adapter!=null) adapter.notifyDataSetChanged();
                updateCount();
            })
            .setNeutralButton("Diğerlerinden taşı",(d,w)->{
                for(String l:favLists){
                    LinkedHashSet<String> set=getListPhones(l);
                    if(!l.equals(activeList)){
                        removePhoneFromSet(set,c.p);
                        saveListPhones(l,set);
                    }
                }
                editingPhones.add(c.p);
                saveListPhones(activeList,editingPhones);
                if(adapter!=null) adapter.notifyDataSetChanged();
                updateCount();
            })
            .setNegativeButton("İptal",null)
            .show();
    }


    void loadContacts(){
        // Kullanıcı izolasyonu: telefon rehberi otomatik global yüklenmez.
        // Her kullanıcı kendi senkronize ettiği rehberi appPrefs içinde görür.
        contacts.clear();
        String saved=appPrefs().getString("contacts_local","");
        if(saved!=null && saved.length()>0){
            try{
                JSONArray arr=new JSONArray(saved);
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.optJSONObject(i);
                    if(o!=null){
                        String n=o.optString("name","");
                        String p=o.optString("phone","");
                        if(p.length()>0) contacts.add(new C(n,p));
                    }
                }
            }catch(Exception ignored){}
        }
    }

    void syncPhoneContactsToUser(){
        contacts.clear();
        try{
            if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.READ_CONTACTS)!=PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},99);
                return;
            }

            LinkedHashMap<String,String> map=new LinkedHashMap<>();

            Uri uri=ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection=new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
            };

            Cursor cur=getContentResolver().query(
                    uri,
                    projection,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC"
            );

            if(cur!=null){
                int nameIdx=cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIdx=cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while(cur.moveToNext()){
                    String n=nameIdx>=0?cur.getString(nameIdx):"";
                    String p=phoneIdx>=0?cur.getString(phoneIdx):"";
                    p=normPhone(p);

                    if(p.length()>5){
                        if(n==null || n.trim().length()==0) n=p;
                        if(!map.containsKey(p)){
                            map.put(p,n.trim());
                        }
                    }
                }
                cur.close();
            }

            contacts.clear();
            for(String p:map.keySet()){
                contacts.add(new C(map.get(p),p));
            }

            JSONArray arr=new JSONArray();
            for(C c:contacts){
                JSONObject o=new JSONObject();
                o.put("name",c.n);
                o.put("phone",c.p);
                arr.put(o);
            }

            appPrefs().edit().putString("contacts_local",arr.toString()).apply();
            addActivityLog("Rehber senkronize edildi",contacts.size()+" kişi yüklendi");
            if(contacts.isEmpty()){
                toast("Rehber boş: izinleri ve cihaz rehberini kontrol et");
            }
        }catch(Exception e){
            toast("Rehber okunamadı: "+e.getMessage());
        }
    }

    String normalize(String p){ if(p==null)return""; p=p.replaceAll("[^0-9]",""); if(p.startsWith("00"))p=p.substring(2); if(p.startsWith("0"))p="90"+p.substring(1); if(p.length()==10)p="90"+p; return p; }
    String httpGet(String u)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(3000); c.setReadTimeout(5000); return readStream(c.getInputStream()); }
    String httpPost(String u,String json)throws Exception{ HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection(); c.setConnectTimeout(5000); c.setReadTimeout(30000); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json"); OutputStream os=c.getOutputStream(); os.write(json.getBytes("UTF-8")); os.close(); return readStream(c.getResponseCode()>=400?c.getErrorStream():c.getInputStream()); }
    String readStream(InputStream is)throws Exception{ if(is==null)return""; BufferedReader br=new BufferedReader(new InputStreamReader(is)); StringBuilder sb=new StringBuilder(); String l; while((l=br.readLine())!=null)sb.append(l); return sb.toString(); }
    int parseInt(String s,int d){ try{return Integer.parseInt(s.trim());}catch(Exception e){return d;} }
    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density); }
    @Override
    public void onBackPressed(){
        try{
            if(tab==null || tab.equals("Ana Sayfa") || tab.equals("Login")){
                moveTaskToBack(true);
            }else{
                home();
            }
        }catch(Exception e){
            home();
        }
    }


    
    void addActivityLog(String title,String sub){
        try{
            org.json.JSONArray old=new org.json.JSONArray(appPrefs().getString("activity_logs","[]"));
            org.json.JSONArray arr=new org.json.JSONArray();

            // Eski loglarda aynı title/sub tekrarlarını tekilleştir.
            java.util.HashSet<String> seen=new java.util.HashSet<>();
            for(int i=0;i<old.length();i++){
                org.json.JSONObject x=old.optJSONObject(i);
                if(x==null) continue;
                String key=x.optString("title","")+"|"+x.optString("sub","");
                if(seen.contains(key)) continue;
                seen.add(key);
                arr.put(x);
            }

            String newKey=title+"|"+sub;
            if(seen.contains(newKey)){
                appPrefs().edit().putString("activity_logs",arr.toString()).apply();
                return;
            }

            org.json.JSONObject o=new org.json.JSONObject();
            o.put("title",title);
            o.put("sub",sub);
            o.put("time",new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date()));
            arr.put(o);

            while(arr.length()>5){
                org.json.JSONArray n=new org.json.JSONArray();
                for(int i=1;i<arr.length();i++) n.put(arr.get(i));
                arr=n;
            }

            appPrefs().edit().putString("activity_logs",arr.toString()).apply();
        }catch(Exception ignored){}
    }

    org.json.JSONArray getActivityLogs(){
        try{
            return new org.json.JSONArray(appPrefs().getString("activity_logs","[]"));
        }catch(Exception e){
            return new org.json.JSONArray();
        }
    }

class DashboardCanvas extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float sx,sy;

        DashboardCanvas(Context c){
            super(c);
            setBackgroundColor(Color.rgb(0,5,9));
        }

        int X(float v){return (int)(v*sx);}
        int Y(float v){return (int)(v*sy);}
        int green(){return Color.rgb(37,211,102);}
        int red(){return Color.rgb(239,68,68);}
        int blue(){return Color.rgb(35,145,255);}
        int purple(){return Color.rgb(150,75,255);}
        int yellow(){return Color.rgb(255,215,35);}
        int cyan(){return Color.rgb(0,210,210);}
        int muted(){return Color.rgb(155,165,170);}
        int card(){return Color.rgb(5,17,24);}
        int stroke(){return Color.rgb(34,48,58);}

        void round(Canvas c,float l,float t,float r,float b,float rad,int color,int st){
            p.reset(); p.setAntiAlias(true);
            RectF rf=new RectF(X(l),Y(t),X(r),Y(b));
            LinearGradient lg=new LinearGradient(X(l),Y(t),X(r),Y(b),color,Color.rgb(2,8,13),Shader.TileMode.CLAMP);
            p.setShader(lg); p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(rf,X(rad),X(rad),p);
            p.setShader(null);
            if(st!=0){
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(X(1.5f)); p.setColor(st);
                c.drawRoundRect(rf,X(rad),X(rad),p);
            }
        }

        void glow(Canvas c,float cx,float cy,float rr,int color){
            p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(34,Color.red(color),Color.green(color),Color.blue(color)));
            c.drawCircle(X(cx),Y(cy),X(rr),p);
        }

        void text(Canvas c,String txt,float x,float y,float sp,int color,boolean bold){
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setTextSize(X(sp));
            p.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            c.drawText(txt,X(x),Y(y),p);
        }

        void center(Canvas c,String txt,float x,float y,float w,float sp,int color,boolean bold){
            p.reset(); p.setAntiAlias(true); p.setColor(color); p.setTextSize(X(sp));
            p.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(txt,X(x+w/2),Y(y),p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void circle(Canvas c,float cx,float cy,float rr,int color,boolean fill){
            p.reset(); p.setAntiAlias(true); p.setColor(color);
            p.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE); p.setStrokeWidth(X(3));
            c.drawCircle(X(cx),Y(cy),X(rr),p);
        }

        void asset(Canvas c,int res,float x,float y,float w,float h){
            try{
                Bitmap bm=BitmapFactory.decodeResource(getResources(),res);
                if(bm!=null){
                    RectF dst=new RectF(X(x),Y(y),X(x+w),Y(y+h));
                    Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG|Paint.DITHER_FLAG);
                    c.drawBitmap(bm,null,dst,bp);
                }
            }catch(Exception ignored){}
        }

        void metric(Canvas c,String ic,String num,String lab,float x,int color){
            center(c,ic,x,530,150,30,color,true);
            center(c,num,x,575,150,34,Color.WHITE,true);
            center(c,lab,x,612,150,19,muted(),false);
        }

        void tile(Canvas c,String ic,String title,String sub,float l,float t,int color){
            glow(c,l+140,t+80,58,color);
            round(c,l,t,l+286,t+246,24,Color.rgb(5,17,25),Color.argb(150,Color.red(color),Color.green(color),Color.blue(color)));
            center(c,ic,l,t+62,286,42,color,true);
            center(c,title,l+16,t+118,254,28,Color.WHITE,true);
            center(c,sub,l+20,t+155,246,19,muted(),false);
            circle(c,l+143,t+205,17,color,false);
            center(c,"›",l+127,t+213,32,24,color,true);
        }

        void activity(Canvas c,String ic,String title,String sub,String time,float y,int color){
            circle(c,75,y-8,22,color,false);
            center(c,ic,59,y,32,22,color,true);
            text(c,title,120,y-14,21,Color.WHITE,true);
            text(c,sub,120,y+12,17,muted(),false);
            text(c,time,822,y,20,color,true);
            circle(c,905,y-8,22,color,false);
            center(c,"✓",889,y,32,21,color,true);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            sx=getWidth()/1000f;
            sy=getHeight()/1800f;

            int statusColor=waConnected?green():red();

            // Header
            asset(c,R.drawable.ic_whatsapp_asset,45,54,60,60);
            text(c,"KATALOG",122,92,22,Color.WHITE,true);
            text(c,"PANEL PRO",122,122,18,green(),true);

            round(c,705,48,960,142,26,Color.rgb(8,20,28),stroke());
            circle(c,758,96,32,green(),false);
            center(c,"👤",742,106,32,26,blue(),true);
            text(c,(loginUser==null||loginUser.length()==0?"TEST":loginUser),815,88,24,Color.WHITE,true);
            text(c,"Aktif Kullanıcı",815,117,18,muted(),false);
            text(c,"⌄",930,102,25,muted(),true);

            // WhatsApp card
            round(c,35,175,965,438,28,card(),stroke());
            glow(c,155,305,92,green());
            asset(c,R.drawable.ic_whatsapp_asset,88,234,132,132);
            circle(c,220,370,24,statusColor,true);
            center(c,waConnected?"✓":"×",204,378,32,22,Color.WHITE,true);

            text(c,"WhatsApp Bağlantı",290,255,32,Color.WHITE,true);
            text(c,waConnected?"● Bağlantı aktif":"● Bağlantı yok",290,315,28,statusColor,false);
            text(c,apiToken!=null && apiToken.length()>5 ? "API: "+apiBase.replace("http://","").replace("https://","") : "API: gizli",290,370,20,muted(),false);
            text(c,waConnected?"Oturum süresi: aktif":"Oturum kapalı",290,412,21,statusColor,false);

            // QR card
            round(c,760,228,922,382,22,Color.rgb(7,20,28),stroke());
            asset(c,R.drawable.ic_qr_asset,803,244,78,78);
            center(c,"QR Yenile",770,360,142,20,Color.WHITE,true);

            // Metrics
            round(c,35,465,965,642,24,card(),stroke());
            metric(c,"👥",String.valueOf(contacts.size()),"Toplam Kişi",80,blue());
            metric(c,"★",String.valueOf(favLists.size()),"Favori Liste",305,yellow());
            metric(c,"➤","0","Bugünkü Gönderim",535,blue());
            metric(c,"◷",String.valueOf(queue.size()),"Kuyrukta",760,purple());

            // Tiles
            tile(c,"👥","REHBER","Rehberini yönet",35,668,green());
            tile(c,"▧","MEDYA","Medya dosyaları",357,668,blue());
            tile(c,"➤","GÖNDERİM","Toplu gönderim",679,668,purple());
            tile(c,"★","FAVORİ LİSTELER","Listeleri yönet",35,938,yellow());
            tile(c,"▥","RAPORLAR","Raporları görüntüle",357,938,cyan());
            tile(c,"⚙","AYARLAR","Sistem ayarları",679,938,Color.rgb(125,145,255));

            // Activities
            round(c,35,1220,965,1490,24,card(),stroke());
            text(c,"⌁  SON AKTİVİTELER",70,1280,25,Color.WHITE,true);
            text(c,"Tümünü Gör ›",780,1280,20,muted(),false);

            try{
                org.json.JSONArray logs=MainActivity.this.getActivityLogs();
                if(logs.length()==0){
                    activity(c,"•","Henüz aktivite yok","İşlem yaptıkça burada görünür","--:--",1365,muted());
                }else{
                    int start=Math.max(0,logs.length()-3);
                    int row=0;
                    int[] colors=new int[]{green(),purple(),blue()};
                    String[] icons=new String[]{"👥","➤","☁"};
                    for(int i=start;i<logs.length();i++){
                        org.json.JSONObject o=logs.optJSONObject(i);
                        if(o!=null){
                            activity(c,icons[row%3],o.optString("title","Aktivite"),o.optString("sub",""),o.optString("time","--:--"),1365+(row*58),colors[row%3]);
                            row++;
                        }
                    }
                }
            }catch(Exception e){
                activity(c,"•","Aktivite okunamadı","","--:--",1365,muted());
            }

            // Footer
            round(c,35,1525,965,1620,18,Color.rgb(5,17,24),stroke());
            center(c,"🛡",60,1582,50,22,muted(),true);
            text(c,"FPRO PANEL v93",120,1570,18,muted(),false);
            text(c,"Güvenli · Hızlı · Profesyonel",120,1602,16,muted(),false);
            text(c,new java.text.SimpleDateFormat("dd.MM",java.util.Locale.getDefault()).format(new java.util.Date()),845,1570,16,muted(),false);
            text(c,new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date()),845,1602,16,muted(),false);
        }

        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX()/sx;
            float y=e.getY()/sy;

            if(x>=760 && x<=922 && y>=228 && y<=382){ showMobileQrDialog(); return true; }
            if(x>=705 && x<=960 && y>=48 && y<=142){ settingsScreen(); return true; }

            if(y>=668 && y<=914){
                if(x>=35 && x<=321){ favListsScreen(); return true; }
                if(x>=357 && x<=643){ mediaScreen(); return true; }
                if(x>=679 && x<=965){ sendScreen(); return true; }
            }
            if(y>=938 && y<=1184){
                if(x>=35 && x<=321){ favListsScreen(); return true; }
                if(x>=357 && x<=643){ reportsScreen(); return true; }
                if(x>=679 && x<=965){ settingsScreen(); return true; }
            }
            return true;
        }
    }


    // ── Circular Progress View ──────────────────────────────────────
    class CircularProgressView extends android.view.View {
        private final android.graphics.Paint trackPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint glowPaint  = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint arcPaint   = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint tickPaint  = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint textPaint  = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint subPaint   = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float progress = 0f;

        CircularProgressView(android.content.Context ctx){
            super(ctx);
            trackPaint.setStyle(android.graphics.Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(18));
            trackPaint.setColor(Color.rgb(28,28,28));
            trackPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);

            glowPaint.setStyle(android.graphics.Paint.Style.STROKE);
            glowPaint.setStrokeWidth(dp(28));
            glowPaint.setColor(Color.argb(40,34,197,94));
            glowPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(dp(10), android.graphics.BlurMaskFilter.Blur.NORMAL));

            arcPaint.setStyle(android.graphics.Paint.Style.STROKE);
            arcPaint.setStrokeWidth(dp(18));
            arcPaint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            arcPaint.setColor(Color.rgb(34,197,94));

            tickPaint.setStyle(android.graphics.Paint.Style.STROKE);
            tickPaint.setStrokeWidth(1.2f);
            tickPaint.setColor(Color.rgb(38,38,38));

            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            textPaint.setColor(Color.rgb(238,238,238));
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

            subPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            subPaint.setColor(Color.rgb(72,72,72));
        }

        void setProgress(float p){ progress=Math.max(0,Math.min(100,p)); invalidate(); }

        @Override
        protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(), h=getHeight(), cx=w/2f, cy=h/2f;
            float pad=dp(22);
            float r=(Math.min(w,h)/2f)-pad;
            android.graphics.RectF oval=new android.graphics.RectF(cx-r,cy-r,cx+r,cy+r);

            // Tick marks
            for(int i=0;i<30;i++){
                float angle=(float)(i*12.0);
                float rad=(float)Math.toRadians(angle-90);
                boolean major=i%5==0;
                float r1=r+dp(major?4:2), r2=r+dp(major?9:6);
                canvas.drawLine(
                    cx+(float)Math.cos(rad)*r1, cy+(float)Math.sin(rad)*r1,
                    cx+(float)Math.cos(rad)*r2, cy+(float)Math.sin(rad)*r2,
                    tickPaint);
            }

            // Track
            canvas.drawArc(oval,-90,360,false,trackPaint);

            // Glow + arc
            float sweep=progress/100f*360f;
            if(sweep>0){
                canvas.drawArc(oval,-90,sweep,false,glowPaint);
                android.graphics.LinearGradient shader=new android.graphics.LinearGradient(
                    oval.left,oval.top,oval.right,oval.bottom,
                    Color.rgb(22,163,74),Color.rgb(74,222,128),
                    android.graphics.Shader.TileMode.CLAMP);
                arcPaint.setShader(shader);
                canvas.drawArc(oval,-90,sweep,false,arcPaint);
            }

            // % text
            float ts=dp(34);
            textPaint.setTextSize(ts);
            canvas.drawText((int)progress+"%",cx,cy+ts*0.35f,textPaint);

            // sub label
            subPaint.setTextSize(dp(10));
            canvas.drawText("tamamlandı",cx,cy+ts*0.35f+dp(11),subPaint);
        }

        @Override
        protected void onMeasure(int w,int h){
            int s=dp(170); setMeasuredDimension(s,s);
        }
    }
    // ───────────────────────────────────────────────────────────────

    void invalidateDashboard(){
        try{
            if(getWindow()!=null && getWindow().getDecorView()!=null)
                getWindow().getDecorView().invalidate();
            if(tab!=null && tab.equals("Ana Sayfa")){
                runOnUiThread(()->{
                    if(connectionText!=null){
                        connectionText.setText(waConnected?"Baglanti aktif":"WhatsApp baglantisi yok");
                        connectionText.setTextColor(waConnected
                            ?android.graphics.Color.rgb(33,211,102)
                            :android.graphics.Color.rgb(239,68,68));
                    }
                    View dot=findViewById(R.id.vStatusDot);
                    if(dot!=null) dot.setBackground(getResources().getDrawable(
                        waConnected?R.drawable.bg_dot_green:R.drawable.bg_dot_red,null));
                    TextView badge=findViewById(R.id.tvBagli);
                    if(badge!=null){
                        badge.setText(waConnected?"BAGLI":"BAGLI DEGIL");
                        badge.setBackgroundResource(waConnected
                            ?R.drawable.bg_badge_connected
                            :R.drawable.bg_badge_disconnected);
                    }
                    refreshDashStats();
                });
            }
        }catch(Exception ignored){}
    }



}

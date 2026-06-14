
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
    final String API="http:
                // Android kişi arası delay kaldırıldı; zamanlama sunucuda.


                }catch(Exception e){
                    fail++; consecutive++;
                    reports.add(0,"Hata: "+c.p+" / "+e.getMessage());
                    save();
                // Hata sonrası Android bekleme kaldırıldı.

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
                // UI state: tamamlandı
                onSendComplete();
            });
        }).start();
    }


        void onSendComplete(){
        // Ring: 100% yeşil, ok ikon, stop gizle
        if(sendRingProg!=null){ sendRingProg[0]=100; if(sendRingView!=null) sendRingView.invalidate(); }
        if(sendArrowImg!=null) sendArrowImg.setVisibility(android.view.View.VISIBLE);
        if(sendRingPctTv!=null) sendRingPctTv.setVisibility(android.view.View.GONE);
        if(sendRingLbl!=null){ sendRingLbl.setGravity(android.view.Gravity.CENTER); sendRingLbl.setText("TAMAMLANDI"); sendRingLbl.setGravity(android.view.Gravity.CENTER); }
        if(sentText!=null) sentText.setText(getSelectedSendPhones().size()+" kişi");
        if(queueText!=null) queueText.setText("Boş");
        if(sendRingInnerBg!=null) sendRingInnerBg.setColor(0xFF16A34A);
        if(sendStopFrame!=null) sendStopFrame.setVisibility(android.view.View.GONE);
        if(sendStRow!=null){ sendStRow.setVisibility(android.view.View.VISIBLE); }
        if(sendProgress!=null) sendProgress.setProgress(100);
        if(progressText!=null) progressText.setText("100% gönderildi");
        if(currentPersonText!=null) currentPersonText.setText("Durum: Tamamlandı ✅");
        if(etaText!=null) etaText.setText("Tamamlandı ✅");
        refreshQueue();
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
        if(currentPersonText!=null){
            if(currentName!=null && currentName.equals("tamamlandi")){
                currentPersonText.setText("Durum: Tamamlandı ✅");
            } else {
                if(currentName!=null && currentName.equals("tamamlandi"))
            currentPersonText.setText("Durum: Tamamlandı ✅");
        else
            currentPersonText.setText("Şu an: "+(currentName==null||currentName.length()==0?"bekleniyor":currentName));
            }
        }
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


    void refreshQueue(){
        int total=getSelectedSendPhones().size();
        int prog=sendProgress!=null?sendProgress.getProgress():0;

        int sentCount=sent.size();
        if(prog>=100 && total>0) sentCount=total;

        int qCount=Math.max(0,total-sentCount);

        if(sentText!=null){
            sentText.setText(sentCount<=0?"Henüz gönderilmedi":sentCount+" kişi");
        }
        if(queueText!=null){
            queueText.setText(qCount<=0?"Boş":qCount+" kişi bekliyor");
        }
    }

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



    LinearLayout settingsGroup(){
        LinearLayout g=new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF141921); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1),0xFF2D3748);
        g.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(4)); g.setLayoutParams(lp);
        g.setClipToOutline(true);
        return g;
    }

    LinearLayout settingsRow(String icon,String title,String sub,int iconColor,android.view.View.OnClickListener onClick,boolean danger){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(14),dp(14),dp(14));
        row.setOnClickListener(onClick);

        // İkon kutusu
        LinearLayout iconBox=new LinearLayout(this);
        iconBox.setGravity(android.view.Gravity.CENTER);
        int iconBg=(iconColor & 0x00FFFFFF)|0x22000000;
        int iconBorder=(iconColor & 0x00FFFFFF)|0x44000000;
        android.graphics.drawable.GradientDrawable ibBg=new android.graphics.drawable.GradientDrawable();
        ibBg.setColor(iconBg); ibBg.setCornerRadius(dp(11)); ibBg.setStroke(dp(1),iconBorder);
        iconBox.setBackground(ibBg);
        LinearLayout.LayoutParams ibLp=new LinearLayout.LayoutParams(dp(38),dp(38));
        ibLp.setMargins(0,0,dp(14),0); iconBox.setLayoutParams(ibLp);
        iconBox.addView(t(icon,18,false,Color.WHITE));
        row.addView(iconBox);

        // Metin
        LinearLayout textCol=new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        int titleColor=danger?0xFFEF4444:0xFFE2E8F0;
        textCol.addView(t(title,14,true,titleColor));
        if(sub!=null&&!sub.isEmpty()){
            TextView subTv=t(sub,11,false,0xFF5A6478);
            LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);
            slp.setMargins(0,dp(2),0,0); subTv.setLayoutParams(slp);
            textCol.addView(subTv);
        }
        row.addView(textCol);

        // Ok
        TextView arrow=t("›",20,false,0xFF3A4455);
        arrow.setPadding(dp(4),0,0,0);
        row.addView(arrow);
        return row;
    }

    android.view.View groupDivider(){
        android.view.View d=new android.view.View(this);
        d.setBackgroundColor(0xFF1E2533);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));
        lp.setMargins(dp(66),0,0,0); d.setLayoutParams(lp);
        return d;
    }

    TextView sectionLabel(String text){
        TextView v=t(text.toUpperCase(),11,true,0xFF5A6478);
        v.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(dp(4),dp(6),0,dp(4)); v.setLayoutParams(lp);
        return v;
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
// DISABLED_CRASH_FIX                     android.graphics.drawable.ClipDrawable clip=(android.graphics.drawable.ClipDrawable)subProgress.getProgressDrawable();
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

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
V2 Queue gecisi: startSend() artik dosyalari tek seferde yukluyor,
sunucudaki kuyrugu polling ile takip ediyor.
"""
import re, shutil

TARGET = "./app/src/main/java/com/fpro/app/MainActivity.java"
src = open(TARGET,"r",encoding="utf-8").read()
shutil.copy(TARGET, TARGET+".bak_v2queue")
print("Backup:", TARGET+".bak_v2queue")

ok = 0

# ── 1) currentJobId alani ekle ───────────────────────────────────────────────
OLD1 = "    volatile boolean sending=false, stop=false;"
NEW1 = "    volatile boolean sending=false, stop=false;\n    volatile String currentJobId=\"\";"
if OLD1 in src:
    src = src.replace(OLD1, NEW1, 1)
    ok += 1; print("OK 1: currentJobId alani eklendi")
else:
    print("MISS 1")

# ── 2) startSend() metodunun TAMAMINI degistir ───────────────────────────────
# startSend() basindan, bir sonraki metod tanimina kadar (void stopSend() oncesi
# baska metodlar da olabilir, o yuzden ayni indent seviyesindeki "    void "
# pattern'ini arayip ilk eslesmeyi bulacagiz)
start_idx = src.index("    void startSend(){")
# Bir sonraki "    void " (ayni indent, 4 bosluk) ara
search_from = start_idx + len("    void startSend(){")
next_method_match = re.search(r'\n    (void|String|boolean|int|LinkedHashSet)\s', src[search_from:])
if next_method_match:
    end_idx = search_from + next_method_match.start() + 1  # +1 ile \n dahil
    old_startSend = src[start_idx:end_idx]
else:
    old_startSend = None
    print("MISS 2: startSend() sonu bulunamadi")

NEW_STARTSEND = '''    void startSend(){
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

        sending=true; stop=false; sent.clear(); queue.clear();
        for(C c:targets) queue.add(c.n+" - "+c.p);

        String msg=msgBox.getText().toString().trim();

        final android.os.PowerManager pm2=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        final android.os.PowerManager.WakeLock wl2=pm2.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,"KatalogPanel:Send");
        wl2.acquire(4*60*60*1000L);

        new Thread(()->{
            final int total=targets.size();
            final long startMs=System.currentTimeMillis();
            Random rng=new Random();

            runOnUiThread(()->{
                statusText.setText("Yükleniyor: dosyalar sunucuya gönderiliyor...");
                updateProgressUI(0,total,"yukleniyor",startMs);
            });

            try{
                ArrayList<String> phonesList=new ArrayList<>();
                for(C c:targets) phonesList.add(c.p);

                ArrayList<String> allFileUris=new ArrayList<>();
                org.json.JSONArray albumCounts=new org.json.JSONArray();
                ArrayList<String> albumCaptionBases=new ArrayList<>();

                if(!albums.isEmpty()){
                    for(int ai=0;ai<albums.size();ai++){
                        ArrayList<String> photos=albums.get(ai);
                        if(photos.isEmpty()) continue;
                        albumCounts.put(photos.size());
                        allFileUris.addAll(photos);
                        albumCaptionBases.add(ai<albumCaptions.size()?albumCaptions.get(ai):msg);
                    }
                } else if(!media.isEmpty()){
                    ArrayList<String> mediaOrder=randomizedMediaList();
                    if(!mediaOrder.isEmpty()){
                        albumCounts.put(mediaOrder.size());
                        allFileUris.addAll(mediaOrder);
                        albumCaptionBases.add(msg);
                    }
                }

                if(allFileUris.isEmpty()){
                    runOnUiThread(()->toast("Gönderilecek dosya bulunamadı"));
                    sendFinishedUI(wl2);
                    return;
                }

                // Her kişi x her albüm için kişiselleştirilmiş caption (eskiden gönderim
                // anında hesaplanıyordu, şimdi tek seferde request'e gömülüyor)
                org.json.JSONArray captionsMatrix=new org.json.JSONArray();
                for(C c:targets){
                    org.json.JSONArray row=new org.json.JSONArray();
                    for(String base:albumCaptionBases){
                        String finalCaption=base.replace("{isim}",c.n);
                        if(!finalCaption.isEmpty()) finalCaption=antiSpamText(randomCaptionStyle(finalCaption),rng);
                        row.put(finalCaption);
                    }
                    captionsMatrix.put(row);
                }

                String respStr=uploadQueueV2(phonesList, allFileUris, albumCounts, captionsMatrix);
                org.json.JSONObject resp=new org.json.JSONObject(respStr);
                if(!resp.optBoolean("ok",false)){
                    throw new Exception(resp.optString("error","Queue başlatılamadı"));
                }
                final String jobId=resp.optString("jobId","");
                currentJobId=jobId;

                // ── Polling döngüsü ──────────────────────────────────────
                int lastCurrent=0;
                while(true){
                    String statusStr;
                    try{
                        statusStr=httpGetString(apiBase+"/api/queue-status-v2?token="+apiToken+"&jobId="+jobId);
                    }catch(Exception e){
                        Thread.sleep(2000);
                        continue;
                    }

                    org.json.JSONObject st=new org.json.JSONObject(statusStr);
                    final int current=st.optInt("current",0);
                    final int totalSrv=st.optInt("total",total);
                    final boolean running=st.optBoolean("running",false);
                    final long pausedUntil=st.optLong("pausedUntil",0);

                    if(current>lastCurrent){
                        for(int k=lastCurrent;k<current && k<targets.size();k++){
                            C c=targets.get(k);
                            queue.remove(c.n+" - "+c.p);
                            sent.add(c.n+" - "+c.p);
                        }
                        lastCurrent=current;
                    }

                    final String nameNow=current<targets.size()?targets.get(current).n:"tamamlaniyor";
                    runOnUiThread(()->{
                        if(pausedUntil>System.currentTimeMillis()){
                            long remain=(pausedUntil-System.currentTimeMillis())/1000;
                            statusText.setText("Mola: "+remain+"sn bekleniyor... ("+current+"/"+totalSrv+")");
                        } else {
                            statusText.setText("Gonderiliyor: "+nameNow+" ("+current+"/"+totalSrv+")");
                        }
                        updateProgressUI(current,totalSrv,nameNow,startMs);
                        refreshQueue();
                    });

                    if(!running) break;
                    Thread.sleep(2500);
                }

                runOnUiThread(()->toast("Gönderim tamamlandı"));

            }catch(Exception e){
                final String errMsg=e.getMessage();
                runOnUiThread(()->toast("Hata: "+errMsg));
            }

            sendFinishedUI(wl2);
        }).start();
    }

    void sendFinishedUI(android.os.PowerManager.WakeLock wl2){
        sending=false;
        currentJobId="";
        try{ if(wl2.isHeld()) wl2.release(); }catch(Exception ignored){}
        runOnUiThread(()->{
            refreshQueue();
        });
    }
'''

if old_startSend:
    src = src.replace(old_startSend, NEW_STARTSEND, 1)
    ok += 1; print("OK 2: startSend() V2 polling ile degistirildi")

# ── 3) stopSend() metodunu sunucuya da durdurma istegi gondersin ─────────────
OLD3 = '    void stopSend(){ stop=true; statusText.setText("Durduruluyor..."); }'
NEW3 = '''    void stopSend(){
        stop=true; statusText.setText("Durduruluyor...");
        final String jobIdToStop=currentJobId;
        if(jobIdToStop!=null && !jobIdToStop.isEmpty()){
            new Thread(()->{ try{ httpPostStopV2(jobIdToStop); }catch(Exception ignored){} }).start();
        }
    }'''
if OLD3 in src:
    src = src.replace(OLD3, NEW3, 1)
    ok += 1; print("OK 3: stopSend() sunucu durdurma istegi gonderiyor")
else:
    print("MISS 3")

# ── 4) Yardimci metodlar ekle (uploadAlbum() oncesine) ───────────────────────
HELPER_METHODS = '''    String uploadQueueV2(ArrayList<String> phonesList, ArrayList<String> fileUris,
                          org.json.JSONArray albumCounts, org.json.JSONArray captionsMatrix) throws Exception {
        String boundary="----KatalogQueueV2"+System.currentTimeMillis();
        HttpURLConnection conn=(HttpURLConnection)new URL(apiBase+"/api/queue-album-v2?token="+apiToken).openConnection();
        conn.setConnectTimeout(60000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);

        java.io.OutputStream os=conn.getOutputStream();
        java.io.PrintStream ps=new java.io.PrintStream(os,true,"UTF-8");

        ps.print("--"+boundary+"\\r\\n");
        ps.print("Content-Disposition: form-data; name=\\"phones\\"\\r\\n\\r\\n");
        ps.print(android.text.TextUtils.join(",", phonesList)+"\\r\\n");

        ps.print("--"+boundary+"\\r\\n");
        ps.print("Content-Disposition: form-data; name=\\"albumCounts\\"\\r\\n\\r\\n");
        ps.print(albumCounts.toString()+"\\r\\n");

        ps.print("--"+boundary+"\\r\\n");
        ps.print("Content-Disposition: form-data; name=\\"captionsMatrix\\"\\r\\n\\r\\n");
        ps.print(captionsMatrix.toString()+"\\r\\n");

        ps.print("--"+boundary+"\\r\\n");
        ps.print("Content-Disposition: form-data; name=\\"delay\\"\\r\\n\\r\\n");
        ps.print("10\\r\\n");

        for(int i=0;i<fileUris.size();i++){
            Uri uri=Uri.parse(fileUris.get(i));
            android.content.ContentResolver cr=getContentResolver();
            String mime=cr.getType(uri);
            if(mime==null) mime="image/jpeg";
            String ext;
            if(mime.contains("pdf")) ext="pdf";
            else if(mime.contains("video")) ext="mp4";
            else ext="jpg";

            ps.print("--"+boundary+"\\r\\n");
            ps.print("Content-Disposition: form-data; name=\\"files\\"; filename=\\"media"+i+"."+ext+"\\"\\r\\n");
            ps.print("Content-Type: "+mime+"\\r\\n\\r\\n");
            ps.flush();

            java.io.InputStream is=cr.openInputStream(uri);
            if(is!=null){
                byte[] buf=new byte[8192]; int n;
                while((n=is.read(buf))>-1) os.write(buf,0,n);
                is.close();
            }
            ps.print("\\r\\n");
        }

        ps.print("--"+boundary+"--\\r\\n");
        ps.flush();

        int code=conn.getResponseCode();
        java.io.InputStream respStream = code>=400 ? conn.getErrorStream() : conn.getInputStream();
        String respBody=readStreamToString(respStream);
        if(code>=400){
            throw new Exception("Queue V2 upload hatasi ("+code+"): "+respBody);
        }
        return respBody;
    }

    String httpGetString(String urlStr) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
        conn.setRequestMethod("GET");
        int code=conn.getResponseCode();
        java.io.InputStream is = code>=400?conn.getErrorStream():conn.getInputStream();
        return readStreamToString(is);
    }

    void httpPostStopV2(String jobId) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(
            apiBase+"/api/queue-stop-v2?token="+apiToken+"&jobId="+jobId).openConnection();
        conn.setConnectTimeout(10000); conn.setReadTimeout(15000);
        conn.setDoOutput(true); conn.setRequestMethod("POST");
        conn.getOutputStream().write(new byte[0]);
        conn.getResponseCode();
    }

    String readStreamToString(java.io.InputStream is) throws Exception {
        if(is==null) return "";
        java.io.ByteArrayOutputStream baos=new java.io.ByteArrayOutputStream();
        byte[] buf=new byte[4096]; int n;
        while((n=is.read(buf))>-1) baos.write(buf,0,n);
        return baos.toString("UTF-8");
    }

    void uploadAlbum(String phone, ArrayList<String> uris, String caption) throws Exception {'''

OLD4_ANCHOR = "    void uploadAlbum(String phone, ArrayList<String> uris, String caption) throws Exception {"
HELPER_ALREADY_DEFINED = "String uploadQueueV2(ArrayList<String> phonesList" in src
if HELPER_ALREADY_DEFINED:
    print("OK 4: Yardimci metodlar zaten tanimli (atlandi)")
elif OLD4_ANCHOR in src:
    src = src.replace(OLD4_ANCHOR, HELPER_METHODS, 1)
    ok += 1; print("OK 4: Yardimci metodlar eklendi")
else:
    print("MISS 4: uploadAlbum() anchor bulunamadi")

open(TARGET,"w",encoding="utf-8").write(src)
print(f"\\nTAMAM {ok}/4 — {len(src):,} karakter")
print("Build: git add . && git commit -m 'V2 queue gecisi' && git push")

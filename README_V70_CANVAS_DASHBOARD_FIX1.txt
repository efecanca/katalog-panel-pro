Katalog Panel PRO v70 Canvas Dashboard Fix1

Düzeltme:
- MainActivity.java Canvas text() compile hatası düzeltildi.
- Hatalı çağrı:
  text(c,815,44,24,Color.WHITE,true);
- Doğru çağrı:
  text(c,(loginUser==null || loginUser.length()==0 ? "TEST" : loginUser),815,44,24,Color.WHITE,true);

Not:
v69 Canvas Dashboard yapısı korunmuştur.

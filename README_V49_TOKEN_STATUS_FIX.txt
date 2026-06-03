Katalog Panel PRO v49 Token Status Fix

Düzeltme:
- Bağlantı kontrolü artık token ile yapılır.
- /status yerine /status?token=TOKEN kullanılır.
- QR bağlı olduğu halde uygulamada "bağlı değil" görünmesi düzeltildi.

Sunucu test:
curl "http://127.0.0.1:3001/status?token=admin-token-change"

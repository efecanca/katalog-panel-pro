/*
VPS_WA_CONTACTS_ADDON.js

Bu endpoint yalnızca bağlı WhatsApp hesabının Baileys session'ında görünen WhatsApp sohbetlerini/kişilerini listeler.
Telefon rehberinin tamamını vermez. Telefon rehberi Android cihazdan okunmalıdır.

sender.js içinde app ve sock tanımlandıktan sonra eklenebilir:

app.get("/wa-contacts", async (req, res) => {
  try {
    const chats = await sock.groupFetchAllParticipating().catch(() => ({}));
    // Kişisel sohbet listesi Baileys store tutuluyorsa store.chats kullanılabilir.
    // Basit örnek: açık store yoksa boş liste döndür.
    res.json({ ok:true, note:"Baileys store bağlıysa kişisel chatler buradan döndürülür.", contacts: [] });
  } catch(e) {
    res.status(500).json({ ok:false, error:e.message });
  }
});

Not:
- WhatsApp kişileri sunucudan tam rehber gibi alınamaz.
- En sağlıklı kaynak Android ContactsContract'tır.
- Sunucudan ancak WhatsApp session'da görünen chat/contact bilgileri alınabilir.
*/

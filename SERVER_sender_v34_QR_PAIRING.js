const express = require("express");
const multer = require("multer");
const fs = require("fs");
const path = require("path");
const P = require("pino");
const qrcode = require("qrcode-terminal");
const { default: makeWASocket, useMultiFileAuthState, DisconnectReason, fetchLatestBaileysVersion } = require("@whiskeysockets/baileys");

const app = express();
const upload = multer({ dest: "uploads/" });

app.use(express.json({ limit: "100mb" }));
app.use(express.urlencoded({ extended: true }));

let sock;
let connected = false;
let lastQR = "";
let lastQRDataUrl = "";

// ── Telefon numarasını uluslararası formata çevir ─────────────────
function normalizePhone(phone) {
    if (!phone) return "";
    // Sadece rakamlar
    let p = String(phone).replace(/\D/g, "");
    // 00 ile başlıyorsa kaldır
    if (p.startsWith("00")) p = p.substring(2);
    // 0 ile başlıyorsa Türkiye kodu ekle
    if (p.startsWith("0") && p.length === 11) p = "90" + p.substring(1);
    // 10 haneli ise Türkiye kodu ekle (05xx → 905xx)
    if (p.length === 10) p = "90" + p;
    return p;
}

// ── Token doğrulama ───────────────────────────────────────────────
const USERS = {}; // { token: { username, apiBase } }

function getTokenFromReq(req) {
    return req.query.token || req.headers["x-token"] || req.body?.token || "";
}

// ── WhatsApp bağlantısı ───────────────────────────────────────────
async function start() {
    const { state, saveCreds } = await useMultiFileAuthState("./auth");

    let version;
    try {
        const result = await fetchLatestBaileysVersion();
        version = result.version;
    } catch (e) {
        version = [2, 3000, 1015901307];
    }

    sock = makeWASocket({
        version,
        auth: state,
        logger: P({ level: "silent" }),
        printQRInTerminal: true,
        browser: ["KatalogPanelPRO", "Chrome", "1.0"],
        generateHighQualityLinkPreview: false,
        syncFullHistory: false,
    });

    sock.ev.on("creds.update", saveCreds);

    sock.ev.on("connection.update", async ({ connection, qr, lastDisconnect }) => {
        if (qr) {
            lastQR = qr;
            connected = false;
            console.log("QR oluştu");
            qrcode.generate(qr, { small: true });
            // QR'ı base64 data URL olarak sakla (mobil için)
            try {
                const QRCode = require("qrcode");
                lastQRDataUrl = await QRCode.toDataURL(qr);
            } catch (e) {
                lastQRDataUrl = "";
            }
        }
        if (connection === "open") {
            connected = true;
            lastQR = "";
            lastQRDataUrl = "";
            console.log("WhatsApp bağlı ✅");
        }
        if (connection === "close") {
            connected = false;
            const code = lastDisconnect?.error?.output?.statusCode;
            console.log("Bağlantı kapandı:", code);
            if (code !== DisconnectReason.loggedOut) {
                setTimeout(start, 3000);
            }
        }
    });
}

// ══════════════════════════════════════════════════════════════════
//  ENDPOINTS
// ══════════════════════════════════════════════════════════════════

// ── Sağlık kontrolü ──────────────────────────────────────────────
app.get("/status", (req, res) => {
    res.json({ connected, hasQR: !!lastQR, ok: true });
});

app.get("/qr", (req, res) => {
    res.json({ connected, qr: lastQR, hasQR: !!lastQR });
});

// ── Mobil uygulama için status ────────────────────────────────────
app.get("/mobile/status", (req, res) => {
    res.json({ connected, ok: true });
});

// ── Mobil uygulama için QR (base64 data URL) ─────────────────────
app.get("/mobile/qr", async (req, res) => {
    if (connected) {
        return res.json({ connected: true, ok: true });
    }
    if (!lastQR) {
        return res.json({ connected: false, qrDataUrl: "", ok: true });
    }
    try {
        if (!lastQRDataUrl) {
            const QRCode = require("qrcode");
            lastQRDataUrl = await QRCode.toDataURL(lastQR);
        }
        res.json({ connected: false, qrDataUrl: lastQRDataUrl, ok: true });
    } catch (e) {
        res.json({ connected: false, qrDataUrl: "", ok: false, error: e.message });
    }
});

// ── Session sıfırla ───────────────────────────────────────────────
app.post("/mobile/reset-session", async (req, res) => {
    try {
        connected = false;
        lastQR = "";
        lastQRDataUrl = "";
        if (sock) {
            try { await sock.logout(); } catch (e) {}
            try { sock.ev.removeAllListeners(); } catch (e) {}
        }
        // Auth klasörünü sil
        const authDir = "./auth";
        if (fs.existsSync(authDir)) {
            fs.readdirSync(authDir).forEach(f => {
                try { fs.unlinkSync(path.join(authDir, f)); } catch (e) {}
            });
        }
        setTimeout(start, 1000);
        res.json({ ok: true, message: "Session sıfırlandı, yeni QR bekleniyor" });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

// ── Pairing code ──────────────────────────────────────────────────
app.post("/pairing-code", async (req, res) => {
    try {
        const phone = normalizePhone(req.body.phone);
        if (!phone) return res.status(400).json({ ok: false, error: "phone gerekli" });
        if (!sock) return res.status(503).json({ ok: false, error: "socket hazır değil" });
        if (connected) return res.json({ ok: false, error: "Zaten bağlı" });
        const code = await sock.requestPairingCode(phone);
        res.json({ ok: true, code });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

// ── MESAJ GÖNDER ──────────────────────────────────────────────────
app.post("/send", async (req, res) => {
    try {
        const phone = normalizePhone(req.body.phone);
        const message = req.body.message || "";

        if (!phone) {
            return res.status(400).json({ ok: false, error: "Telefon numarası gerekli" });
        }
        if (!message) {
            return res.status(400).json({ ok: false, error: "Mesaj boş olamaz" });
        }
        if (!connected || !sock) {
            return res.status(503).json({ ok: false, error: "WhatsApp bağlı değil" });
        }

        const jid = phone + "@s.whatsapp.net";
        console.log(`Gönderiliyor → ${jid}: ${message.substring(0, 30)}...`);

        await sock.sendMessage(jid, { text: message });

        console.log(`Gönderildi ✅ → ${jid}`);
        res.json({ ok: true, phone: jid });

    } catch (e) {
        console.error("Gönderim hatası:", e.message);
        res.status(500).json({ ok: false, error: e.message });
    }
});

// ── MEDYA GÖNDER ─────────────────────────────────────────────────
app.post("/send-media", upload.single("file"), async (req, res) => {
    let filePath = req.file ? req.file.path : null;
    try {
        const phone = normalizePhone(req.body.phone);
        const caption = req.body.caption || "";
        const type = req.body.type || "image";

        if (!phone) {
            return res.status(400).json({ ok: false, error: "Telefon numarası gerekli" });
        }
        if (!connected || !sock) {
            return res.status(503).json({ ok: false, error: "WhatsApp bağlı değil" });
        }
        if (!req.file) {
            return res.status(400).json({ ok: false, error: "Dosya gerekli" });
        }

        const jid = phone + "@s.whatsapp.net";
        const buffer = fs.readFileSync(filePath);

        let payload;
        if (type === "video") {
            payload = { video: buffer, caption };
        } else if (type === "document") {
            payload = {
                document: buffer,
                caption,
                fileName: req.file.originalname || "document.pdf",
                mimetype: req.file.mimetype || "application/pdf"
            };
        } else {
            payload = { image: buffer, caption };
        }

        console.log(`Medya gönderiliyor → ${jid} [${type}]`);
        await sock.sendMessage(jid, payload);
        console.log(`Medya gönderildi ✅ → ${jid}`);

        res.json({ ok: true, phone: jid });

    } catch (e) {
        console.error("Medya gönderim hatası:", e.message);
        res.status(500).json({ ok: false, error: e.message });
    } finally {
        if (filePath && fs.existsSync(filePath)) {
            try { fs.unlinkSync(filePath); } catch (e) {}
        }
    }
});

// ══════════════════════════════════════════════════════════════════
// KULLANICI LİSTESİ — Buraya kullanıcı ekle/çıkar
// Şifreleri mutlaka değiştir!
// ══════════════════════════════════════════════════════════════════
const ALLOWED_USERS = {
    "admin":  process.env.ADMIN_PASSWORD  || "Admin@2024!",
    "user1":  process.env.USER1_PASSWORD  || "User1@2024!",
    "user2":  process.env.USER2_PASSWORD  || "User2@2024!",
};

// Brute-force koruması
const loginAttempts = {};
const MAX_ATTEMPTS = 5;
const LOCKOUT_MS = 5 * 60 * 1000;

// ── Login endpoint ────────────────────────────────────────────────
app.post("/api/login", (req, res) => {
    const ip = req.ip || "unknown";
    const { username, password } = req.body;

    // Brute-force kontrolü
    const now = Date.now();
    if (!loginAttempts[ip]) loginAttempts[ip] = { count: 0, first: now };
    const att = loginAttempts[ip];
    if (now - att.first > LOCKOUT_MS) { att.count = 0; att.first = now; }
    if (att.count >= MAX_ATTEMPTS) {
        const min = Math.ceil((LOCKOUT_MS - (now - att.first)) / 60000);
        return res.status(429).json({ ok: false, error: `Cok fazla deneme. ${min} dk bekleyin.` });
    }

    if (!username || !password) {
        return res.status(400).json({ ok: false, error: "Kullanici adi ve sifre gerekli" });
    }

    // Kullanıcı adı + şifre kontrolü
    // Hem ALLOWED_USERS hem de users_data.json'dan kontrol et
    const users = loadUsers();
    const uKey = username.toLowerCase();
    const userData = users[uKey];
    // Pasif kullanıcı giremez
    if (userData && userData.active === false) {
        att.count++;
        return res.status(401).json({ ok: false, error: "Bu hesap devre disi birakildi." });
    }
    const correctPw = (userData && userData.password) || ALLOWED_USERS[uKey];
    if (!correctPw || password !== correctPw) {
        att.count++;
        const kalan = MAX_ATTEMPTS - att.count;
        console.warn(`Basarisiz giris: ${username} @ ${ip} (${att.count}/${MAX_ATTEMPTS})`);
        return res.status(401).json({
            ok: false,
            error: kalan > 0
                ? `Kullanici adi veya sifre hatali. ${kalan} hak kaldi.`
                : "Hesap kilitlendi. 5 dakika bekleyin."
        });
    }

    // Başarılı giriş — sayacı sıfırla
    att.count = 0;
    const token = Buffer.from(`${username}:${Date.now()}:${Math.random()}`).toString("base64");
    USERS[token] = { username, createdAt: Date.now() };
    console.log(`Giris basarili: ${username} @ ${ip}`);
    res.json({ ok: true, token, username });
});

// ══════════════════════════════════════════════════════════════════
//  ADMİN KULLANICI YÖNETİMİ ENDPOİNTLERİ
// ══════════════════════════════════════════════════════════════════

// Kullanıcıları dosyaya kaydet/yükle
const USERS_FILE = "./users_data.json";

function loadUsers() {
    try {
        if (fs.existsSync(USERS_FILE)) {
            return JSON.parse(fs.readFileSync(USERS_FILE, "utf8"));
        }
    } catch(e) {}
    // Varsayılan: ALLOWED_USERS'dan oluştur
    const users = {};
    for (const [uname, pw] of Object.entries(ALLOWED_USERS)) {
        users[uname] = { password: pw, active: true, createdAt: Date.now() };
    }
    saveUsers(users);
    return users;
}

function saveUsers(users) {
    fs.writeFileSync(USERS_FILE, JSON.stringify(users, null, 2));
}

// Token'ın admin olup olmadığını kontrol et
function isAdmin(token) {
    const u = USERS[token];
    return u && u.username && u.username.toLowerCase() === "admin";
}

// Kullanıcı listesi
app.get("/admin/users", (req, res) => {
    const token = req.query.token || "";
    if (!isAdmin(token)) return res.status(403).json({ ok: false, error: "Yetkisiz" });
    const users = loadUsers();
    const list = Object.entries(users).map(([username, data]) => ({
        username,
        active: data.active !== false,
        createdAt: data.createdAt
    }));
    res.json({ ok: true, users: list });
});

// Yeni kullanıcı ekle
app.post("/admin/add-user", (req, res) => {
    const token = req.query.token || "";
    if (!isAdmin(token)) return res.status(403).json({ ok: false, error: "Yetkisiz" });
    const { username, password } = req.body || {};
    if (!username || !password) return res.status(400).json({ ok: false, error: "username ve password gerekli" });
    if (username.length < 2) return res.status(400).json({ ok: false, error: "Kullanıcı adı çok kısa" });
    if (password.length < 6) return res.status(400).json({ ok: false, error: "Şifre en az 6 karakter olmalı" });
    const users = loadUsers();
    if (users[username.toLowerCase()]) return res.status(400).json({ ok: false, error: "Bu kullanıcı zaten var" });
    users[username.toLowerCase()] = { password, active: true, createdAt: Date.now() };
    saveUsers(users);
    // ALLOWED_USERS'ı da güncelle (runtime)
    ALLOWED_USERS[username.toLowerCase()] = password;
    console.log(`Yeni kullanıcı eklendi: ${username}`);
    res.json({ ok: true });
});

// Şifre değiştir
app.post("/admin/change-password", (req, res) => {
    const token = req.query.token || "";
    if (!isAdmin(token)) return res.status(403).json({ ok: false, error: "Yetkisiz" });
    const { username, newPassword } = req.body || {};
    if (!username || !newPassword) return res.status(400).json({ ok: false, error: "username ve newPassword gerekli" });
    if (newPassword.length < 6) return res.status(400).json({ ok: false, error: "Şifre en az 6 karakter olmalı" });
    const users = loadUsers();
    const key = username.toLowerCase();
    if (!users[key]) return res.status(404).json({ ok: false, error: "Kullanıcı bulunamadı" });
    users[key].password = newPassword;
    saveUsers(users);
    ALLOWED_USERS[key] = newPassword;
    console.log(`Şifre güncellendi: ${username}`);
    res.json({ ok: true });
});

// Kullanıcı aktif/pasif
app.post("/admin/toggle-user", (req, res) => {
    const token = req.query.token || "";
    if (!isAdmin(token)) return res.status(403).json({ ok: false, error: "Yetkisiz" });
    const { username, active } = req.body || {};
    if (!username) return res.status(400).json({ ok: false, error: "username gerekli" });
    if (username.toLowerCase() === "admin") return res.status(400).json({ ok: false, error: "Admin devre dışı bırakılamaz" });
    const users = loadUsers();
    const key = username.toLowerCase();
    if (!users[key]) return res.status(404).json({ ok: false, error: "Kullanıcı bulunamadı" });
    users[key].active = active !== false;
    saveUsers(users);
    // Pasif kullanıcıyı ALLOWED_USERS'dan çıkar
    if (!users[key].active) {
        delete ALLOWED_USERS[key];
    } else {
        ALLOWED_USERS[key] = users[key].password;
    }
    console.log(`Kullanıcı ${username}: ${active ? "aktif" : "pasif"}`);
    res.json({ ok: true });
});

// ── Data sync endpoints ───────────────────────────────────────────
const DATA_DIR = "./user_data";
if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR);

function userFile(token) {
    const safe = token.replace(/[^a-zA-Z0-9_-]/g, "_");
    return path.join(DATA_DIR, `${safe}.json`);
}

app.get("/api/data", (req, res) => {
    try {
        const token = getTokenFromReq(req);
        if (!token) return res.status(401).json({ ok: false, error: "Token gerekli" });
        const file = userFile(token);
        if (!fs.existsSync(file)) return res.json({ ok: true, data: {} });
        const data = JSON.parse(fs.readFileSync(file, "utf8"));
        res.json({ ok: true, data });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

app.post("/api/data", (req, res) => {
    try {
        const token = getTokenFromReq(req);
        if (!token) return res.status(401).json({ ok: false, error: "Token gerekli" });
        const file = userFile(token);
        fs.writeFileSync(file, JSON.stringify(req.body.data || {}, null, 2));
        res.json({ ok: true });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

app.post("/api/contacts", (req, res) => {
    try {
        const token = getTokenFromReq(req);
        if (!token) return res.status(401).json({ ok: false, error: "Token gerekli" });
        const file = userFile(token + "_contacts");
        fs.writeFileSync(file, JSON.stringify(req.body.contacts || [], null, 2));
        res.json({ ok: true });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

app.get("/api/favlists", (req, res) => {
    try {
        const token = getTokenFromReq(req);
        if (!token) return res.status(401).json({ ok: false, error: "Token gerekli" });
        const file = userFile(token + "_favlists");
        if (!fs.existsSync(file)) return res.json({ ok: true, favLists: [] });
        const favLists = JSON.parse(fs.readFileSync(file, "utf8"));
        res.json({ ok: true, favLists });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

app.post("/api/favlists", (req, res) => {
    try {
        const token = getTokenFromReq(req);
        if (!token) return res.status(401).json({ ok: false, error: "Token gerekli" });
        const file = userFile(token + "_favlists");
        fs.writeFileSync(file, JSON.stringify(req.body.favLists || [], null, 2));
        res.json({ ok: true });
    } catch (e) {
        res.status(500).json({ ok: false, error: e.message });
    }
});

// ── Server başlat ─────────────────────────────────────────────────
app.listen(3001, "0.0.0.0", () => {
    console.log("═══════════════════════════════════════");
    console.log("  Katalog Panel PRO — API Sunucu");
    console.log("  Port: 3001");
    console.log("═══════════════════════════════════════");
});

start();


// ══════════════════════════════════════════════════════════════════
//  SUNUCU KEEPALIVE — Bağlantı kopmasını önler
//  WhatsApp ~5 dakika sessizlikte bağlantıyı kesiyor
//  Her 20 saniyede bir sunucu kendine ping atıyor
// ══════════════════════════════════════════════════════════════════
setInterval(async () => {
    if (!sock || !connected) return;
    try {
        // WhatsApp'a boş presence gönder — "online" sinyali
        await sock.sendPresenceUpdate('available');
        // Alternatif: kendi JID'ine presence güncelle
    } catch(e) {
        // Hata olursa reconnect
        console.log("Keepalive hatası, yeniden bağlanıyor...", e.message);
        connected = false;
        setTimeout(start, 2000);
    }
}, 20000); // 20 saniye

// Bağlantı kopunca otomatik yeniden bağlan
process.on('uncaughtException', (e) => {
    console.error('Uncaught exception:', e.message);
    if (!connected) setTimeout(start, 3000);
});

process.on('unhandledRejection', (reason) => {
    console.error('Unhandled rejection:', reason);
});


// ══════════════════════════════════════════════════════════════════
//  SUNUCU BAN KORUMALARI
// ══════════════════════════════════════════════════════════════════

// Gönderim rate limiter — dakikada max 4 mesaj
const sendTimes = [];
function rateLimitCheck() {
    const now = Date.now();
    // Son 60 saniyedeki gönderimler
    const recent = sendTimes.filter(t => now - t < 60000);
    sendTimes.length = 0;
    sendTimes.push(...recent);
    if (recent.length >= 4) {
        return false; // Rate limit aşıldı
    }
    sendTimes.push(now);
    return true;
}

// /send endpoint'ini rate limiter ile güçlendir
app.post("/send-protected", async (req, res) => {
    // Rate limit kontrolü
    if (!rateLimitCheck()) {
        return res.status(429).json({
            ok: false,
            error: "Rate limit: Çok hızlı gönderim. Lütfen bekleyin.",
            retryAfter: 15
        });
    }

    try {
        const phone = normalizePhone(req.body.phone);
        const message = req.body.message || "";

        if (!phone) return res.status(400).json({ ok: false, error: "phone gerekli" });
        if (!message) return res.status(400).json({ ok: false, error: "message gerekli" });
        if (!connected || !sock) return res.status(503).json({ ok: false, error: "WhatsApp bağlı değil" });

        const jid = phone + "@s.whatsapp.net";

        // Gönderim öncesi kısa rastgele bekleme (sunucu tarafında da)
        const serverDelay = 500 + Math.floor(Math.random() * 1500); // 0.5-2sn
        await new Promise(r => setTimeout(r, serverDelay));

        await sock.sendMessage(jid, { text: message });
        console.log("✅ Gönderildi →", jid);
        res.json({ ok: true });

    } catch(e) {
        console.error("❌ Gönderim hatası:", e.message);
        // WhatsApp ban sinyalleri
        if (e.message && (
            e.message.includes("rate-overlimit") ||
            e.message.includes("not-authorized") ||
            e.message.includes("forbidden")
        )) {
            console.error("🚨 BAN SİNYALİ:", e.message);
            res.status(503).json({ ok: false, error: "WhatsApp ban uyarısı: " + e.message });
        } else {
            res.status(500).json({ ok: false, error: e.message });
        }
    }
});

// Bağlantı durumu monitoring — ban log
sock?.ev?.on?.("messages.upsert", ({ messages }) => {
    messages.forEach(m => {
        // Kendi gönderdiğimiz mesajlar
        if (m.key.fromMe) {
            // Ban uyarısı mesajları WhatsApp'tan gelir
            const text = m.message?.conversation || "";
            if (text.includes("banned") || text.includes("spam")) {
                console.error("🚨 WhatsApp BAN MESAJI:", text);
            }
        }
    });
});

cd ~/wa-server
pm2 delete all
pkill node
npm install express multer pino qrcode-terminal @whiskeysockets/baileys -y
cp sender.js "sender.backup.$(date +%F-%H%M%S).js" 2>/dev/null || true
cp SERVER_sender_v34_QR_PAIRING.js sender.js
pm2 start sender.js --name katalog-wa
pm2 save
curl http://127.0.0.1:3001/status

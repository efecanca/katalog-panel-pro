Katalog Panel PRO v39 SP Fix

Düzeltme:
- favListsScreen içinde tanımsız 'sp' değişkeni compile hatası veriyordu.
- SharedPreferences doğrudan çağrıldı:
  getSharedPreferences("fpro",MODE_PRIVATE)

Compile hatası giderildi.

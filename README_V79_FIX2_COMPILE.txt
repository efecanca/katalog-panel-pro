FPRO Panel v79 Fix2 Compile

Düzeltmeler:
- getActivityLogs() eksik metod hatası giderildi.
- DashboardCanvas içinden MainActivity.this.getActivityLogs() çağrısı yapıldı.
- addActivityLog() metodunun MainActivity içinde olduğundan emin olundu.
- invalidateDashboard() yardımcı metodu eklendi.
- Önceki compile safety düzeltmeleri korundu.

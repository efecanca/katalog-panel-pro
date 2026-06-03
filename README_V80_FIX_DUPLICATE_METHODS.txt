FPRO Panel v80 Fix Duplicate Methods

Düzeltmeler:
- addActivityLog() çift tanım hatası temizlendi.
- getActivityLogs() çift/eksik tanım kontrol edildi.
- invalidateDashboard() çift/eksik tanım kontrol edildi.
- DashboardCanvas içinden MainActivity.this.getActivityLogs() çağrısı korunur.
- Önceki compile safety düzeltmeleri korundu.

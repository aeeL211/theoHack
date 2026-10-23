# TheoTown Signature & Billing Spoofer (Loader.java)

> **⚠️ Disclaimer:** Proyek ini dibuat **hanya untuk tujuan pembelajaran dan riset keamanan pribadi**. Penulis tidak bertanggung jawab atas penyalahgunaan, pelanggaran ToS, atau kerugian pihak lain. Gunakan pada salinan APK milik Anda sendiri.

## 🎯 Tujuan

- **Riset Signature Spoofing** di Android: memahami bagaimana `PackageInfo.CREATOR` dan `IPackageManager` dapat diproksi untuk memalsukan signature aplikasi.
- **Riset Billing Bypass**: mempelajari cara kerja `com.badlogic.gdx.pay.PurchaseManager` dan bagaimana interaksinya dengan `InAppHandler` di game berbasis libGDX.
- **Eksperimen Dynamic Proxy**: menerapkan `java.lang.reflect.Proxy` untuk mencegat pemanggilan metode sistem tanpa modifikasi native.

## ⚙️ Cara Kerja

1. **Signature Spoofing**  
   `killPM()` mengganti `PackageInfo.CREATOR` dengan `SpoofedPackageInfoCreator`. Setiap `PackageInfo` yang dibuat dari Binder (termasuk saat `getPackageInfo` dipanggil) akan dimodifikasi: array `signatures` dan `signingInfo` untuk package target diganti dengan sertifikat yang di-hardcode.

2. **Installer Spoofing**  
   `initProxy()` memasang `java.lang.reflect.Proxy` pada `ActivityThread.sPackageManager` dan `PackageManager.mPM`. Metode `getInstallSourceInfo` dan `getInstallerPackageName` dipalsukan agar mengembalikan `"com.android.vending"`.

3. **Billing Bypass**  
   `hookPurchaseManager()` melakukan polling hingga field `TheoTown.purchaseManager` terisi, lalu menggantinya dengan `PurchaseManagerProxy`. Proxy ini:
   - `installed()` → `true`
   - `install()` & `purchaseRestore()` → *no-op*
   - `purchase(sku)` → memanggil `triggerFakeHandlePurchase(sku)` untuk membuat `Transaction` palsu dan mengirimkannya ke `InAppHandler.handlePurchase`.

## 🚀 Cara Pakai

> **Nama file DEX output** didefinisikan **satu kali** di `.github/workflows/build-dex.yml` pada `env.DEX_NAME`. Secara default bernilai `classes7.dex`. Ubah di situ kalau perlu — di bawah ini kita sebut sebagai **"file DEX"**.

1. Jalankan workflow **Build DEX** di tab *Actions* (atau push ke `main`).
2. Unduh artifact **`dex-output`** — di dalamnya ada file DEX hasil build.
3. Decode APK target dengan `apktool`.
4. Salin **file DEX** ke root direktori APK hasil decode (sejajar dengan `classes.dex`, `classes2.dex`, dst.).
5. Ubah `AndroidManifest.xml`: set `android:name="aeeL.Loader"` pada tag `<application>`.
6. Rebuild, sign, dan install.

## 📦 Kompatibilitas

- **Android 7.0 (API 24) – Android 16 (API 36)**
- ✅ **Berhasil diuji pada TheoTown v1.12.95a di Android 11.**
- Dependensi: [`HiddenApiBypass` 6.2](https://github.com/LSPosed/AndroidHiddenApiBypass) — versi dipusatkan di `env.HIDDENAPI_VERSION` pada workflow.

## 🧠 Struktur Kode

| Komponen | Fungsi |
|---|---|
| `Loader` (Application) | Entry point; menjalankan `killPM()` dan `hookPurchaseManager()` di `static {}`. |
| `SpoofedPackageInfoCreator` | Mengganti `CREATOR` agar signature package target dipalsukan. |
| `PurchaseManagerProxy` | Proxy `PurchaseManager` untuk bypass billing. |
| `triggerFakeHandlePurchase` | Membuat `Transaction` palsu dan memanggil `handlePurchase`. |
| `initProxy` | Memasang proxy pada `IPackageManager`. |

## ⚠️ Keterbatasan

- **Bukan solusi universal**: hanya package target yang di-hardcode (`info.flowersoft.theotown.theotown`).
- **Server-side check**: pembelian yang diverifikasi di server tetap akan gagal.
# TheoTown Signature & Billing Spoofer

> **⚠️ Disclaimer:** Proyek ini dibuat **hanya untuk tujuan pembelajaran dan riset keamanan pribadi**. Penulis tidak bertanggung jawab atas penyalahgunaan, pelanggaran ToS, atau kerugian pihak lain. Gunakan pada salinan APK milik Anda sendiri.

## 🎯 Tujuan

- **Riset Signature Spoofing** di Android: memahami bagaimana `PackageInfo.CREATOR` dan `IPackageManager` dapat diproksi untuk memalsukan signature aplikasi.
- **Riset Billing Bypass**: mempelajari cara kerja `com.badlogic.gdx.pay.PurchaseManager` dan bagaimana interaksinya dengan `InAppHandler` di game berbasis libGDX.
- **Eksperimen Dynamic Proxy**: menerapkan `java.lang.reflect.Proxy` untuk mencegat pemanggilan metode sistem tanpa modifikasi native.
- **Kompatibilitas API 24–36**: menangani perbedaan `PackageInfo.CREATOR`, `SigningInfo`, dan `InstallSourceInfo` antar versi Android.

## ⚙️ Cara Kerja

1. **Signature Spoofing**  
   `killPM()` mengganti `PackageInfo.CREATOR` dengan `SpoofedPackageInfoCreator`. Setiap `PackageInfo` yang dibuat dari Binder (termasuk saat `getPackageInfo` dipanggil) akan dimodifikasi untuk package target:
   - seluruh elemen array `signatures` diganti dengan sertifikat di `CERTIFICATE`;
   - pada API 28+, `SigningInfo` (APK signers dan certificate history) ikut diganti melalui `SigningInfoAccessor`;
   - cache lokal `PackageManager` serta cache `Parcel` dibersihkan agar `PackageInfo` lama tidak dipakai.

2. **Installer Spoofing**  
   `initProxy()` memasang `java.lang.reflect.Proxy` pada `ActivityThread.sPackageManager` dan `PackageManager.mPM`. Hanya package target yang dipalsukan:
   - API 24–29: `getInstallerPackageName()` mengembalikan `"com.android.vending"`.
   - API 30+: `getInstallSourceInfo()` mengembalikan objek `InstallSourceInfo` dengan initiating/installing package `"com.android.vending"`.
   - Konstruktor `InstallSourceInfo` dipilih sesuai versi API (30–32, 33, 34+), dengan fallback melalui `CREATOR` + `Parcel`.
   - API 33+ `packageSource` diisi `PACKAGE_SOURCE_STORE`.

3. **Billing Bypass**  
   `hookPurchaseManager()` melakukan polling hingga field `TheoTown.purchaseManager` terisi, lalu menggantinya dengan `PurchaseManagerProxy`. Proxy ini:
   - `installed()` → `true`
   - `install()` & `purchaseRestore()` → *no-op*
   - `purchase(sku)` → memanggil `triggerFakeHandlePurchase(sku)` untuk membuat `Transaction` palsu, mengisi `identifier`/`orderId`, lalu memanggil `InAppHandler.handlePurchase`.
   - Setelah `handlePurchase` berhasil, `showPurchaseSuccessToast()` menampilkan toast **Pembelian berhasil:** `<sku>` di main thread.

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
- ✅ **Kompilasi diverifikasi terhadap `android.jar` API 36.**
- ⚠️ Pengujian runtime di seluruh rentang API 24–36 belum dilakukan; build berhasil diuji pada TheoTown v1.12.95a di Android 11 (API 30).
- Dependensi: [`HiddenApiBypass` 6.2](https://github.com/LSPosed/AndroidHiddenApiBypass) — versi dipusatkan di `env.HIDDENAPI_VERSION` pada workflow.

### Matriks API

| API | Signature spoof | Install source spoof |
|---|---|---|
| 24–27 | `PackageInfo.CREATOR` → `signatures` | `getInstallerPackageName()` |
| 28–29 | `signatures` + `SigningInfo` | `getInstallerPackageName()` |
| 30–32 | `signatures` + `SigningInfo` | `getInstallSourceInfo()` (parcel / konstruktor 4 parameter) |
| 33 | `signatures` + `SigningInfo` | `getInstallSourceInfo()` (5 parameter / parcel, `PACKAGE_SOURCE_STORE`) |
| 34–36 | `signatures` + `SigningInfo` | `getInstallSourceInfo()` (6 parameter / parcel, `PACKAGE_SOURCE_STORE`) |

## 🧠 Struktur Kode

| Komponen | Fungsi |
|---|---|
| `Loader` (Application) | Entry point; menjalankan `killPM()` dan `hookPurchaseManager()` di `static {}`. |
| `SpoofedPackageInfoCreator` | Mengganti `CREATOR` agar signature package target dipalsukan, termasuk seluruh signer. |
| `SigningInfoAccessor` | Mengakses `SigningInfo` API 28+ secara terisolasi agar API 24–27 tetap aman. |
| `createSpoofedInstallSourceInfo` | Membuat `InstallSourceInfo` Play Store sesuai versi API. |
| `createInstallSourceInfoFromParcel` | Fallback pembuatan `InstallSourceInfo` melalui `CREATOR` dan `Parcel`. |
| `PurchaseManagerProxy` | Proxy `PurchaseManager` untuk bypass billing. |
| `triggerFakeHandlePurchase` | Membuat `Transaction` palsu dan memanggil `handlePurchase`. |
| `showPurchaseSuccessToast` | Menampilkan toast pembelian berhasil di main thread. |
| `initProxy` | Memasang proxy pada `IPackageManager`. |

## ⚠️ Keterbatasan

- **Bukan solusi universal**: hanya package target yang di-hardcode (`info.flowersoft.theotown.theotown`).
- **Cakupan signature spoof terbatas**: `PackageInfo.signatures`/`SigningInfo` dipalsukan, tetapi pengecekan tingkat platform seperti `PackageManager.checkSignatures()` dan verifikasi di server tidak ikut diubah.
- **Install source hanya pada API `PackageManager`**: status installer di sistem, PackageInstaller, atau Play Store tidak berubah.
- **Membutuhkan Hidden API bypass** yang kompatibel dengan versi Android yang dipakai; jika bypass gagal, sebagian hook dapat dinonaktifkan secara diam-diam.
- **Server-side check**: pembelian yang diverifikasi di server tetap akan gagal.
package aeeL;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * Titik masuk aplikasi yang menyiapkan proxy paket dan pembelian.
 *
 * <p>Loader memasang proxy {@code PackageManager} lintas proses untuk mengganti metadata
 * tanda tangan dan sumber pemasangan paket target, lalu memasang proxy {@code PurchaseManager}
 * untuk menangani alur pembelian.
 */
public class Loader extends Application implements InvocationHandler {

  private static final String TAG = "Loader";
  private static final String TARGET_PACKAGE = "info.flowersoft.theotown.theotown";
  private static final String PLAY_STORE_PACKAGE = "com.android.vending";

  /** API 30: {@code PackageManager.getInstallSourceInfo} dan {@code InstallSourceInfo}. */
  private static final int API_INSTALL_SOURCE = 30;

  /** API 33: {@code InstallSourceInfo} menambahkan {@code packageSource}. */
  private static final int API_PACKAGE_SOURCE = 33;

  /** API 34: {@code InstallSourceInfo} menambahkan {@code updateOwnerPackageName}. */
  private static final int API_UPDATE_OWNER = 34;

  /** Sertifikat penandatangan paket target dalam encoding Base64. */
  private static final String CERTIFICATE =
      "MIIDUTCCAjmgAwIBAgIENUHk9DANBgkqhkiG9w0BAQsFADBZMRAwDgYDVQQIEwdHZXJtYW55MRIwEAYD"
          + "VQQHEwlLYXJsc3J1aGUxEzARBgNVBAoTCmZsb3dlcnNvZnQxHDAaBgNVBAMTE0ZhYmlhbiBNaWx0ZW5i"
          + "ZXJnZXIwHhcNMTUwNDExMDkxODUxWhcNNDAwNDA0MDkxODUxWjBZMRAwDgYDVQQIEwdHZXJtYW55MRIw"
          + "EAYDVQQHEwlLYXJsc3J1aGUxEzARBgNVBAoTCmZsb3dlcnNvZnQxHDAaBgNVBAMTE0ZhYmlhbiBNaWx0"
          + "ZW5iZXJnZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCOuhCDyQn3pqzQSiskDPpsw/il"
          + "eOPTNgteZ1eHZz3LhO/PseTw0gBZphdFf0K8WCBNqnYH+ixwTowOfq7GjqSP+XXPruZnFZMjNYACAv+K"
          + "hHVJozDYRvnFRFD6GZtRafUkKvpYe6AljOu0CywlwYKzznnE/XQlVi1rv6QOpyOX/q9VoHtpMgXMeh4k"
          + "nRbo/uJmMGHJDaRScSTzPkrdZ32FWfOJ6R82fMDsitpjeykOFlHJvnXQ98WSRkgCG+b+Czy65bd4mR9H"
          + "r7IJLuwSN67L8tpnJpDJYGJ+Xsd25fHH9Z9vyzKpI5wejnoiFzQPVBz8n0zqJydAt5Jr1ea3JXXHAgMB"
          + "AAGjITAfMB0GA1UdDgQWBBTdpQl2ZhnVMvM6YyfulK/QlCrpszANBgkqhkiG9w0BAQsFAAOCAQEAJZoF"
          + "fvwA672eOjN9NDCTf/GHBgUBMWdPbG5/VwmW+YA3q4U6Tf2DZtmS17pZBzSRyYinpU0zoS7IaR6nTTn4"
          + "xTACiIpYVUnMOpFhN3K9K87rNC/3e5wa8zA+Zf3HK+Lyh9tpMKKZnxdpS34z1nlp0Czv3UXNW3gqVAq/"
          + "Qw14+WGaIRnnP/a42PLh7/+8lItBmH/p2KGGz2crcMUse4CPxmNGxCw5nXeWCuBPlhqwn2GNnKc+qp1o"
          + "WvSDDWpf2b3CjtbFsgaW1boDnT+WyZfi29R2F3iG5596MXfHh5ojlVKiHjtoia0NcMc2SDxxvfqAnDec"
          + "2TF1kDftLXfvi5vx5g==";

  /** Instance {@code IPackageManager} asli yang menjadi tujuan delegasi proxy. */
  private Object baseObject;

  /** Konteks aplikasi untuk menampilkan notifikasi kepada pengguna. */
  private static volatile Context appContext;

  static {
    killPM(TARGET_PACKAGE, CERTIFICATE);
    hookPurchaseManager();
  }

  public Loader() {
    super();
  }

  /**
   * Mencari field deklaratif pada kelas dan seluruh superclass-nya.
   *
   * @param clazz kelas awal pencarian
   * @param name nama field yang dicari
   * @return field yang dapat diakses
   * @throws NoSuchFieldException jika field tidak ditemukan pada hierarki kelas
   */
  public static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    NoSuchFieldException first = null;
    while (true) {
      try {
        Field field = current.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (NoSuchFieldException e) {
        if (first == null) {
          first = e;
        }
        current = current.getSuperclass();
        if (current == null || current == Object.class) {
          throw first;
        }
      }
    }
  }

  /**
   * Memasang proxy pada {@code ActivityThread.sPackageManager} dan
   * {@code ApplicationPackageManager.mPM}.
   *
   * @param context konteks yang digunakan untuk memperoleh {@link PackageManager}
   */
  private void initProxy(Context context) {
    try {
      Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
      Method currentActivityThread =
          activityThreadClass.getDeclaredMethod("currentActivityThread");
      Object activityThread = currentActivityThread.invoke(null);

      Field packageManagerField = findField(activityThreadClass, "sPackageManager");
      this.baseObject = packageManagerField.get(activityThread);

      Class<?> ipmClass = Class.forName("android.content.pm.IPackageManager");
      Object proxy =
          Proxy.newProxyInstance(ipmClass.getClassLoader(), new Class<?>[] {ipmClass}, this);
      packageManagerField.set(activityThread, proxy);

      PackageManager packageManager = context.getPackageManager();
      Field mpmField = findField(packageManager.getClass(), "mPM");
      mpmField.set(packageManager, proxy);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void attachBaseContext(Context base) {
    appContext = base;
    initProxy(base);
    super.attachBaseContext(base);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String name = method.getName();

    if ("getInstallSourceInfo".equals(name) && isTargetPackageCall(args)) {
      Object spoofed = createSpoofedInstallSourceInfo();
      if (spoofed != null) {
        return spoofed;
      }
      // Jika konstruktor InstallSourceInfo tidak dapat dipanggil pada versi Android ini,
      // teruskan panggilan asli agar pemanggil tetap mendapatkan objek yang valid.
    } else if ("getInstallerPackageName".equals(name) && isTargetPackageCall(args)) {
      return PLAY_STORE_PACKAGE;
    }

    return method.invoke(this.baseObject, args);
  }

  /**
   * Memeriksa apakah parameter pertama panggilan Binder adalah paket target.
   *
   * @param args argumen panggilan Binder yang dapat {@code null}
   * @return {@code true} jika paket target menjadi sasaran panggilan
   */
  private static boolean isTargetPackageCall(Object[] args) {
    return args != null && args.length > 0 && TARGET_PACKAGE.equals(args[0]);
  }

  /**
   * Membuat {@code InstallSourceInfo} dengan sumber pemasangan Play Store.
   *
   * <p>Konstruktor {@code InstallSourceInfo} berubah pada API 33 dan 34. Konstruktor lama
   * tetap dicoba sebagai fallback agar API 30 sampai 36 tetap kompatibel.
   *
   * @return objek {@code InstallSourceInfo} atau {@code null} jika pembuatan gagal
   */
  private static Object createSpoofedInstallSourceInfo() {
    if (Build.VERSION.SDK_INT < API_INSTALL_SOURCE) {
      return null;
    }

    try {
      Class<?> installSourceInfoClass = Class.forName("android.content.pm.InstallSourceInfo");
      Class<?> signingInfoClass = Class.forName("android.content.pm.SigningInfo");

      if (Build.VERSION.SDK_INT >= API_UPDATE_OWNER) {
        Object info =
            tryCreateInstallSourceInfo(
                installSourceInfoClass, signingInfoClass, true, true);
        if (info != null) {
          return info;
        }
      }
      if (Build.VERSION.SDK_INT >= API_PACKAGE_SOURCE) {
        Object info =
            tryCreateInstallSourceInfo(
                installSourceInfoClass, signingInfoClass, false, true);
        if (info != null) {
          return info;
        }
      }
      // Fallback parcel tidak memerlukan konstruktor hidden dan tetap menyertakan
      // package source pada API 33+.
      Object info = createInstallSourceInfoFromParcel(installSourceInfoClass);
      if (info != null) {
        return info;
      }
      return tryCreateInstallSourceInfo(
          installSourceInfoClass, signingInfoClass, false, false);
    } catch (Throwable t) {
      Log.e(TAG, "createSpoofedInstallSourceInfo failed", t);
      return null;
    }
  }

  /**
   * Memanggil konstruktor {@code InstallSourceInfo} sesuai bentuk yang tersedia.
   *
   * @param installSourceInfoClass kelas {@code InstallSourceInfo}
   * @param signingInfoClass kelas {@code SigningInfo}
   * @param withUpdateOwner apakah konstruktor memiliki parameter update owner (API 34+)
   * @param withPackageSource apakah konstruktor memiliki parameter package source (API 33+)
   * @return objek hasil konstruksi atau {@code null} jika konstruktor tidak tersedia
   */
  private static Object tryCreateInstallSourceInfo(
      Class<?> installSourceInfoClass,
      Class<?> signingInfoClass,
      boolean withUpdateOwner,
      boolean withPackageSource) {
    try {
      Class<?>[] parameterTypes;
      Object[] arguments;
      if (withUpdateOwner) {
        parameterTypes =
            new Class<?>[] {
              String.class, signingInfoClass, String.class, String.class, String.class, int.class
            };
        arguments =
            new Object[] {
              PLAY_STORE_PACKAGE, null, null, PLAY_STORE_PACKAGE, null, getStorePackageSource()
            };
      } else if (withPackageSource) {
        parameterTypes =
            new Class<?>[] {
              String.class, signingInfoClass, String.class, String.class, int.class
            };
        arguments =
            new Object[] {
              PLAY_STORE_PACKAGE, null, null, PLAY_STORE_PACKAGE, getStorePackageSource()
            };
      } else {
        parameterTypes =
            new Class<?>[] {String.class, signingInfoClass, String.class, String.class};
        arguments = new Object[] {PLAY_STORE_PACKAGE, null, null, PLAY_STORE_PACKAGE};
      }

      Constructor<?> constructor =
          installSourceInfoClass.getDeclaredConstructor(parameterTypes);
      constructor.setAccessible(true);
      return constructor.newInstance(arguments);
    } catch (Throwable t) {
      return null;
    }
  }

  /**
   * Membuat {@code InstallSourceInfo} melalui {@code CREATOR} sebagai fallback.
   *
   * <p>Pendekatan ini tidak memerlukan konstruktor hidden. Format parcel mengikuti versi
   * Android: API 30-32 tanpa package source, API 33 dengan package source, dan API 34+
   * dengan update owner sebelum package source.
   *
   * @param installSourceInfoClass kelas {@code InstallSourceInfo}
   * @return objek {@code InstallSourceInfo} atau {@code null} jika fallback gagal
   */
  private static Object createInstallSourceInfoFromParcel(Class<?> installSourceInfoClass) {
    Parcel parcel = Parcel.obtain();
    try {
      parcel.writeString(PLAY_STORE_PACKAGE);
      parcel.writeParcelable(null, 0);
      parcel.writeString(null);
      parcel.writeString(PLAY_STORE_PACKAGE);
      if (Build.VERSION.SDK_INT >= API_UPDATE_OWNER) {
        // writeString8(null) dan writeString(null) sama-sama menulis panjang -1.
        parcel.writeString(null);
        parcel.writeInt(getStorePackageSource());
      } else if (Build.VERSION.SDK_INT >= API_PACKAGE_SOURCE) {
        parcel.writeInt(getStorePackageSource());
      }
      parcel.setDataPosition(0);

      @SuppressWarnings("unchecked")
      Parcelable.Creator<Object> creator =
          (Parcelable.Creator<Object>) installSourceInfoClass.getField("CREATOR").get(null);
      return creator.createFromParcel(parcel);
    } catch (Throwable t) {
      Log.w(TAG, "InstallSourceInfo parcel fallback failed", t);
      return null;
    } finally {
      parcel.recycle();
    }
  }

  /**
   * Mengambil nilai {@code PackageInstaller.PACKAGE_SOURCE_STORE} secara reflektif.
   *
   * <p>Konstanta tersebut baru tersedia sejak API 33. Nilai cadangan {@code 2} digunakan jika
   * konstanta tidak dapat dibaca.
   *
   * @return nilai package source untuk paket yang dipasang dari toko aplikasi
   */
  private static int getStorePackageSource() {
    try {
      Class<?> packageInstallerClass = Class.forName("android.content.pm.PackageInstaller");
      return packageInstallerClass.getField("PACKAGE_SOURCE_STORE").getInt(null);
    } catch (Throwable ignored) {
      return 2;
    }
  }

  /**
   * Mengganti {@link PackageInfo#CREATOR} dan membersihkan cache parcel agar metadata paket
   * target dapat disesuaikan saat di-unmarshall.
   *
   * @param packageName nama paket target
   * @param signatureBase64 sertifikat pengganti dalam encoding Base64
   */
  private static void killPM(String packageName, String signatureBase64) {
    Signature signature = new Signature(Base64.decode(signatureBase64, Base64.NO_WRAP));

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      try {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/os/Parcel;", "Landroid/content/pm", "Landroid/app");
      } catch (Throwable t) {
        Log.w(TAG, "Hidden API exemption failed", t);
      }
    }

    Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;
    Parcelable.Creator<PackageInfo> replacement =
        new SpoofedPackageInfoCreator(originalCreator, packageName, signature);

    try {
      Field creatorField = findField(PackageInfo.class, "CREATOR");
      creatorField.set(null, replacement);
    } catch (Throwable t) {
      // Jangan menghentikan proses aplikasi jika CREATOR tidak dapat diganti.
      Log.e(TAG, "Unable to replace PackageInfo.CREATOR", t);
    }

    // PackageManager memiliki cache PackageInfo lokal sejak API 30. Nonaktifkan agar
    // PackageInfo berikutnya selalu di-unmarshall melalui CREATOR pengganti.
    try {
      Class<?> packageManagerClass = Class.forName("android.content.pm.PackageManager");
      Method disableCache = packageManagerClass.getDeclaredMethod("disablePackageInfoCache");
      disableCache.setAccessible(true);
      disableCache.invoke(null);
    } catch (Throwable ignored) {
      // Cache lokal tidak tersedia pada API 24-29.
    }
    try {
      Object cache = findField(PackageManager.class, "sPackageInfoCache").get(null);
      cache.getClass().getMethod("clear").invoke(cache);
    } catch (Throwable ignored) {
      // Nama dan tipe cache PackageInfo berbeda antar versi Android.
    }
    clearStaticMap(PackageManager.class, "sPackageInfoCache");

    // mCreators bersifat per-Parcel pada API 24-33, sedangkan sPairedCreators bersifat
    // statis pada API 34+. Keduanya dibersihkan jika tersedia sebagai map statis.
    clearStaticMap(Parcel.class, "mCreators");
    clearStaticMap(Parcel.class, "sPairedCreators");
  }

  /**
   * Menghapus isi field map statis jika field tersebut tersedia.
   *
   * @param owner kelas pemilik field
   * @param fieldName nama field map
   */
  private static void clearStaticMap(Class<?> owner, String fieldName) {
    try {
      Object value = findField(owner, fieldName).get(null);
      if (value instanceof Map<?, ?>) {
        ((Map<?, ?>) value).clear();
      }
    } catch (Throwable ignored) {
      // Field dapat berupa instance field, tidak tersedia, atau terlindungi hidden API.
    }
  }

  /**
   * Mengganti {@code TheoTown.purchaseManager} dengan proxy {@code PurchaseManager}.
   *
   * <p>Field tersebut baru diisi oleh {@code TheoTown.create()} setelah instance
   * {@link Application} dibuat, sehingga hook melakukan polling pada thread latar sampai nilai
   * non-null tersedia.
   */
  private static void hookPurchaseManager() {
    Thread thread =
        new Thread(
            () -> {
              for (int attempt = 0; attempt < 300; attempt++) {
                try {
                  Class<?> theoTownClass =
                      Class.forName("info.flowersoft.theotown.crossplatform.TheoTown");
                  Field field = findField(theoTownClass, "purchaseManager");
                  Object current = field.get(null);

                  if (current != null
                      && Proxy.isProxyClass(current.getClass())
                      && Proxy.getInvocationHandler(current) instanceof PurchaseManagerProxy) {
                    return;
                  }

                  if (current != null) {
                    Class<?> purchaseManagerClass =
                        Class.forName("com.badlogic.gdx.pay.PurchaseManager");
                    Object proxy =
                        Proxy.newProxyInstance(
                            current.getClass().getClassLoader(),
                            new Class<?>[] {purchaseManagerClass},
                            new PurchaseManagerProxy(current));
                    field.set(null, proxy);
                    Log.i(TAG, "purchaseManager hooked: " + current.getClass().getName());
                    return;
                  }
                } catch (Throwable e) {
                  if (attempt == 0) {
                    Log.e(TAG, "hookPurchaseManager attempt 0 failed", e);
                  }
                }
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  return;
                }
              }
              Log.e(TAG, "purchaseManager hook timed out");
            },
            "PurchaseManagerHookThread");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Membuat {@code Transaction} sementara dan meneruskannya ke
   * {@code InAppHandler.handlePurchase} agar alur pembelian dianggap berhasil.
   *
   * @param sku identifier produk yang dibeli
   */
  private static void triggerFakeHandlePurchase(String sku) {
    try {
      Class<?> inAppHandlerClass = Class.forName("info.flowersoft.theotown.InAppHandler");
      Object instance = inAppHandlerClass.getMethod("getInstance").invoke(null);

      Class<?> transactionClass = Class.forName("com.badlogic.gdx.pay.Transaction");
      Object transaction = transactionClass.getDeclaredConstructor().newInstance();

      Field identifierField = findField(transactionClass, "identifier");
      Field orderIdField = findField(transactionClass, "orderId");
      identifierField.set(transaction, sku);
      orderIdField.set(transaction, "fake-purchase-" + System.currentTimeMillis());

      Method handlePurchase = inAppHandlerClass.getMethod("handlePurchase", transactionClass);
      handlePurchase.invoke(instance, transaction);
      showPurchaseSuccessToast(sku);

      Log.i(TAG, "Fake handlePurchase succeeded for sku=" + sku);
    } catch (Throwable t) {
      Log.e(TAG, "triggerFakeHandlePurchase failed for sku=" + sku, t);
      try {
        Class<?> inAppHandlerClass = Class.forName("info.flowersoft.theotown.InAppHandler");
        Object instance = inAppHandlerClass.getMethod("getInstance").invoke(null);
        Method errorMethod = inAppHandlerClass.getMethod("handlePurchaseError", Throwable.class);
        errorMethod.invoke(instance, t);
      } catch (Throwable nested) {
        Log.e(TAG, "handlePurchaseError also failed", nested);
      }
    }
  }

  /**
   * Menampilkan toast bahwa pembelian berhasil.
   *
   * <p>Toast dijadwalkan pada thread utama agar aman dipanggil dari thread mana pun.
   *
   * @param sku identifier produk yang dibeli
   */
  private static void showPurchaseSuccessToast(String sku) {
    Context context = appContext;
    if (context == null) {
      Log.w(TAG, "Toast pembelian dilewati: appContext belum tersedia");
      return;
    }

    Runnable showToast =
        () ->
            Toast.makeText(context, "Pembelian berhasil: " + sku, Toast.LENGTH_SHORT).show();
    if (Looper.myLooper() == Looper.getMainLooper()) {
      showToast.run();
    } else {
      new Handler(Looper.getMainLooper()).post(showToast);
    }
  }

  /**
   * Proxy {@code PurchaseManager} yang mengalihkan operasi pembelian ke
   * {@link #triggerFakeHandlePurchase(String)}.
   *
   * <ul>
   *   <li>{@code installed} selalu mengembalikan {@code true};
   *   <li>{@code install} dan {@code purchaseRestore} tidak melakukan apa pun;
   *   <li>{@code purchase} memicu alur pembelian lokal;
   *   <li>metode lain didelegasikan ke instance asli.
   * </ul>
   */
  private static final class PurchaseManagerProxy implements InvocationHandler {
    private final Object original;

    PurchaseManagerProxy(Object original) {
      this.original = original;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();

      if ("installed".equals(name)) {
        return true;
      }

      if ("purchase".equals(name)) {
        if (args != null && args.length >= 1 && args[0] instanceof String) {
          Log.i(TAG, "PurchaseManager.purchase(" + args[0] + ") intercepted");
          triggerFakeHandlePurchase((String) args[0]);
        }
        return null;
      }

      if ("install".equals(name)) {
        Log.i(TAG, "PurchaseManager.install() no-op");
        return null;
      }

      if ("purchaseRestore".equals(name)) {
        Log.i(TAG, "PurchaseManager.purchaseRestore() no-op");
        return null;
      }

      if ("getInformation".equals(name)) {
        try {
          return method.invoke(original, args);
        } catch (Throwable t) {
          Class<?> informationClass = Class.forName("com.badlogic.gdx.pay.Information");
          return informationClass.getField("UNAVAILABLE").get(null);
        }
      }

      try {
        return method.invoke(original, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }

  /**
   * Lapisan akses untuk {@link SigningInfo} yang hanya dipanggil pada API 28 ke atas.
   *
   * <p>Kelas ini hanya dipanggil dari cabang {@code SDK_INT >= P}, sehingga referensi
   * {@code SigningInfo} tidak dieksekusi pada API 24–27.
   */
  public static final class SigningInfoAccessor {
    private SigningInfoAccessor() {
    }

    /** Mengembalikan {@code signingInfo} dari paket sebagai {@code Object} (API 28+). */
    public static Object getSigningInfo(PackageInfo info) {
      return info.signingInfo;
    }

    /** Mengembalikan signer APK dari objek {@code SigningInfo} (API 28+). */
    public static Signature[] getApkContentsSigners(Object signingInfo) {
      return ((SigningInfo) signingInfo).getApkContentsSigners();
    }

    /** Mengembalikan riwayat sertifikat dari objek {@code SigningInfo} (API 28+). */
    public static Signature[] getSigningCertificateHistory(Object signingInfo) {
      return ((SigningInfo) signingInfo).getSigningCertificateHistory();
    }
  }

  /**
   * {@link Parcelable.Creator} yang mengganti tanda tangan paket target setelah proses
   * unmarshalling.
   */
  public static final class SpoofedPackageInfoCreator
      implements Parcelable.Creator<PackageInfo> {

    /** Creator asli yang mendelegasikan proses unmarshalling. */
    public final Parcelable.Creator<PackageInfo> originalCreator;

    /** Nama paket yang metadata tanda tangannya diganti. */
    public final String targetPackageName;

    /** Tanda tangan pengganti untuk paket target. */
    public final Signature spoofedSignature;

    public SpoofedPackageInfoCreator(
        Parcelable.Creator<PackageInfo> originalCreator,
        String targetPackageName,
        Signature spoofedSignature) {
      this.originalCreator = originalCreator;
      this.targetPackageName = targetPackageName;
      this.spoofedSignature = spoofedSignature;
    }

    @Override
    @SuppressWarnings("deprecation")
    public PackageInfo createFromParcel(Parcel source) {
      PackageInfo info = this.originalCreator.createFromParcel(source);
      if (info == null || !this.targetPackageName.equals(info.packageName)) {
        return info;
      }

      Signature signature = this.spoofedSignature;
      if (info.signatures == null || info.signatures.length == 0) {
        // Pastikan pemeriksaan berbasis PackageInfo.signatures tetap mendapatkan sertifikat
        // pengganti, termasuk pada API 28+ yang dapat mengosongkan field ini.
        info.signatures = new Signature[] {signature};
      } else {
        replaceSignatures(info.signatures, signature);
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
          Object signingInfo = SigningInfoAccessor.getSigningInfo(info);
          if (signingInfo != null) {
            replaceSignatures(SigningInfoAccessor.getApkContentsSigners(signingInfo), signature);
            replaceSignatures(
                SigningInfoAccessor.getSigningCertificateHistory(signingInfo), signature);
          }
        } catch (Throwable t) {
          Log.w(TAG, "SigningInfo spoof failed", t);
        }
      }
      return info;
    }

    /**
     * Mengganti seluruh sertifikat pada array dengan sertifikat pengganti.
     *
     * <p>Mengganti semua elemen penting untuk paket dengan lebih dari satu signer, bukan hanya
     * signer pertama.
     *
     * @param signatures array sertifikat yang akan diubah
     * @param signature sertifikat pengganti
     */
    private static void replaceSignatures(Signature[] signatures, Signature signature) {
      if (signatures == null) {
        return;
      }
      for (int i = 0; i < signatures.length; i++) {
        signatures[i] = signature;
      }
    }

    @Override
    public PackageInfo[] newArray(int size) {
      return this.originalCreator.newArray(size);
    }
  }
}

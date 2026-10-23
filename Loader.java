package aeeL;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

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
    initProxy(base);
    super.attachBaseContext(base);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    String name = method.getName();

    // TODO: [AGENT] Pastikan args tidak null dan memiliki panjang yang cukup sebelum indeks 0.
    if ("getInstallSourceInfo".equals(name)
        && TARGET_PACKAGE.equals(String.valueOf(args[0]))) {
      try {
        Class<?> installSourceInfoClass = Class.forName("android.content.pm.InstallSourceInfo");
        Class<?> signingInfoClass = Class.forName("android.content.pm.SigningInfo");
        Constructor<?> constructor =
            installSourceInfoClass.getConstructor(
                String.class, signingInfoClass, String.class, String.class);
        return constructor.newInstance(
            "com.android.vending", null, null, "com.android.vending");
      } catch (Throwable t) {
        return null;
      }
    }
    if ("getInstallerPackageName".equals(name)) {
      return "com.android.vending";
    }
    return method.invoke(this.baseObject, args);
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

    Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;
    Parcelable.Creator<PackageInfo> replacement =
        new SpoofedPackageInfoCreator(originalCreator, packageName, signature);

    try {
      Field creatorField = findField(PackageInfo.class, "CREATOR");
      creatorField.set(null, replacement);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      HiddenApiBypass.addHiddenApiExemptions(
          "Landroid/os/Parcel;", "Landroid/content/pm", "Landroid/app");
    }

    try {
      Object cache = findField(PackageManager.class, "sPackageInfoCache").get(null);
      cache.getClass().getMethod("clear").invoke(cache);
    } catch (Throwable ignored) {
      // Cache bersifat opsional dan tidak tersedia pada semua versi Android.
    }
    try {
      ((Map<?, ?>) findField(Parcel.class, "mCreators").get(null)).clear();
    } catch (Throwable ignored) {
      // Field internal dapat berubah atau tidak tersedia.
    }
    try {
      ((Map<?, ?>) findField(Parcel.class, "sPairedCreators").get(null)).clear();
    } catch (Throwable ignored) {
      // Field internal dapat berubah atau tidak tersedia.
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

    /** Mengembalikan {@code signingInfo} dari paket (API 28+). */
    public static SigningInfo getSigningInfo(PackageInfo info) {
      return info.signingInfo;
    }

    /** Mengembalikan signer APK dari {@code SigningInfo} (API 28+). */
    public static Signature[] getApkContentsSigners(SigningInfo info) {
      return info.getApkContentsSigners();
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
      // TODO: [AGENT] info.packageName dapat null untuk parcel yang tidak valid; bandingkan dari
      //  sisi konstanta untuk menghindari NPE.
      if (info.packageName.equals(this.targetPackageName)) {
        Signature[] signatures = info.signatures;
        Signature signature = this.spoofedSignature;
        if (signatures != null && signatures.length > 0) {
          signatures[0] = signature;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          SigningInfo signingInfo = SigningInfoAccessor.getSigningInfo(info);
          if (signingInfo != null) {
            Signature[] apkSignatures = SigningInfoAccessor.getApkContentsSigners(signingInfo);
            if (apkSignatures != null && apkSignatures.length > 0) {
              apkSignatures[0] = signature;
            }
          }
        }
      }
      return info;
    }

    @Override
    public PackageInfo[] newArray(int size) {
      return this.originalCreator.newArray(size);
    }
  }
}

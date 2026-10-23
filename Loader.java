package aeeL;

import android.app.Application;
import android.content.Context;
import android.content.pm.InstallSourceInfo;
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
 * Loader — signature spoofing + installer spoofing + fake billing
 * (via proxy PurchaseManager).
 */
public class Loader extends Application implements InvocationHandler {

  private static final String TAG = "Loader";
  private static final String TARGET_PACKAGE = "info.flowersoft.theotown.theotown";
  private static final String CERTIFICATE = "MIIDUTCCAjmgAwIBAgIENUHk9DANBgkqhkiG9w0BAQsFADBZMRAwDgYDVQQIEwdHZXJtYW55MRIwEAYDVQQHEwlLYXJsc3J1aGUxEzARBgNVBAoTCmZsb3dlcnNvZnQxHDAaBgNVBAMTE0ZhYmlhbiBNaWx0ZW5iZXJnZXIwHhcNMTUwNDExMDkxODUxWhcNNDAwNDA0MDkxODUxWjBZMRAwDgYDVQQIEwdHZXJtYW55MRIwEAYDVQQHEwlLYXJsc3J1aGUxEzARBgNVBAoTCmZsb3dlcnNvZnQxHDAaBgNVBAMTE0ZhYmlhbiBNaWx0ZW5iZXJnZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCOuhCDyQn3pqzQSiskDPpsw/ileOPTNgteZ1eHZz3LhO/PseTw0gBZphdFf0K8WCBNqnYH+ixwTowOfq7GjqSP+XXPruZnFZMjNYACAv+KhHVJozDYRvnFRFD6GZtRafUkKvpYe6AljOu0CywlwYKzznnE/XQlVi1rv6QOpyOX/q9VoHtpMgXMeh4knRbo/uJmMGHJDaRScSTzPkrdZ32FWfOJ6R82fMDsitpjeykOFlHJvnXQ98WSRkgCG+b+Czy65bd4mR9Hr7IJLuwSN67L8tpnJpDJYGJ+Xsd25fHH9Z9vyzKpI5wejnoiFzQPVBz8n0zqJydAt5Jr1ea3JXXHAgMBAAGjITAfMB0GA1UdDgQWBBTdpQl2ZhnVMvM6YyfulK/QlCrpszANBgkqhkiG9w0BAQsFAAOCAQEAJZoFfvwA672eOjN9NDCTf/GHBgUBMWdPbG5/VwmW+YA3q4U6Tf2DZtmS17pZBzSRyYinpU0zoS7IaR6nTTn4xTACiIpYVUnMOpFhN3K9K87rNC/3e5wa8zA+Zf3HK+Lyh9tpMKKZnxdpS34z1nlp0Czv3UXNW3gqVAq/Qw14+WGaIRnnP/a42PLh7/+8lItBmH/p2KGGz2crcMUse4CPxmNGxCw5nXeWCuBPlhqwn2GNnKc+qp1oWvSDDWpf2b3CjtbFsgaW1boDnT+WyZfi29R2F3iG5596MXfHh5ojlVKiHjtoia0NcMc2SDxxvfqAnDec2TF1kDftLXfvi5vx5g==";

  private Object baseObject;

  static {
    killPM(TARGET_PACKAGE, CERTIFICATE);
    hookPurchaseManager();
  }

  public Loader() {
    super();
  }

  public static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    try {
      Field field = clazz.getDeclaredField(name);
      field.setAccessible(true);
      return field;
    } catch (NoSuchFieldException first) {
      while (true) {
        clazz = clazz.getSuperclass();
        if (clazz == null || clazz == Object.class) throw first;
        try {
          Field field = clazz.getDeclaredField(name);
          field.setAccessible(true);
          return field;
        } catch (NoSuchFieldException ignored) {
        }
      }
    }
  }

  private void initProxy(Context context) {
    try {
      Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
      Method currentActivityThread = activityThreadClass.getDeclaredMethod("currentActivityThread");
      Object activityThread = currentActivityThread.invoke(null);

      Field sPackageManager = activityThreadClass.getDeclaredField("sPackageManager");
      sPackageManager.setAccessible(true);
      this.baseObject = sPackageManager.get(activityThread);

      Class<?> ipmClass = Class.forName("android.content.pm.IPackageManager");
      Object proxy = Proxy.newProxyInstance(
          ipmClass.getClassLoader(), new Class<?>[]{ipmClass}, this);
      sPackageManager.set(activityThread, proxy);

      PackageManager packageManager = context.getPackageManager();
      Field mPM = packageManager.getClass().getDeclaredField("mPM");
      mPM.setAccessible(true);
      mPM.set(packageManager, proxy);
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
    if ("getInstallSourceInfo".equals(name)
        && TARGET_PACKAGE.equals(String.valueOf(args[0]))) {
      try {
        Class<?> isiClass = Class.forName("android.content.pm.InstallSourceInfo");
        Class<?> siClass = Class.forName("android.content.pm.SigningInfo");
        Constructor<?> ctor = isiClass.getConstructor(
            String.class, siClass, String.class, String.class);
        return ctor.newInstance("com.android.vending", null, null, "com.android.vending");
      } catch (Throwable t) {
        return null;
      }
    }
    if ("getInstallerPackageName".equals(name)) {
      return "com.android.vending";
    }
    return method.invoke(this.baseObject, args);
  }

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

    if (Build.VERSION.SDK_INT >= 28) {
      HiddenApiBypass.addHiddenApiExemptions(
          "Landroid/os/Parcel;",
          "Landroid/content/pm",
          "Landroid/app");
    }

    try {
      Object cache = findField(PackageManager.class, "sPackageInfoCache").get(null);
      cache.getClass().getMethod("clear").invoke(cache);
    } catch (Throwable ignored) {
    }
    try {
      ((Map<?, ?>) findField(Parcel.class, "mCreators").get(null)).clear();
    } catch (Throwable ignored) {
    }
    try {
      ((Map<?, ?>) findField(Parcel.class, "sPairedCreators").get(null)).clear();
    } catch (Throwable ignored) {
    }
  }

  /**
   * Ganti TheoTown.purchaseManager dengan Proxy PurchaseManager.
   *
   * Field diset secara lazy oleh TheoTown.create() setelah Application
   * dibuat, jadi kita polling di background thread sampai field non-null.
   */
  private static void hookPurchaseManager() {
    Thread t = new Thread(() -> {
      for (int attempt = 0; attempt < 300; attempt++) {
        try {
          Class<?> theoTownClass = Class.forName("info.flowersoft.theotown.crossplatform.TheoTown");
          Field field = findField(theoTownClass, "purchaseManager");
          Object current = field.get(null);

          if (current != null
              && Proxy.isProxyClass(current.getClass())
              && Proxy.getInvocationHandler(current) instanceof PurchaseManagerProxy) {
            return;
          }

          if (current != null) {
            Class<?> pmClass = Class.forName("com.badlogic.gdx.pay.PurchaseManager");
            Object proxy = Proxy.newProxyInstance(
                current.getClass().getClassLoader(),
                new Class<?>[]{pmClass},
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
    }, "PurchaseManagerHookThread");
    t.setDaemon(true);
    t.start();
  }

  /**
   * Bikin Transaction palsu dan panggil InAppHandler.handlePurchase,
   * sehingga game berpikir pembelian berhasil dan setter UI dipanggil.
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
        Method errMethod = inAppHandlerClass.getMethod("handlePurchaseError", Throwable.class);
        errMethod.invoke(instance, t);
      } catch (Throwable t2) {
        Log.e(TAG, "handlePurchaseError also failed", t2);
      }
    }
  }

  /**
   * Proxy PurchaseManager: bypass billing.
   *  - installed()   -> true (agar requestPurchase lanjut)
   *  - install(...)  -> no-op (tidak ada billing sungguhan)
   *  - purchase(sku) -> trigger handlePurchase(fakeTx)
   *  - purchaseRestore() -> no-op
   *  - getInformation() -> delegate ke original (atau UNAVAILABLE)
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
          Class<?> infoClass = Class.forName("com.badlogic.gdx.pay.Information");
          return infoClass.getField("UNAVAILABLE").get(null);
        }
      }

      try {
        return method.invoke(original, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }
  }

  public static final class SigningInfoAccessor {
    private SigningInfoAccessor() {
    }

    public static SigningInfo getSigningInfo(PackageInfo info) {
      return info.signingInfo;
    }

    public static Signature[] getApkContentsSigners(SigningInfo info) {
      return info.getApkContentsSigners();
    }
  }

  public static final class SpoofedPackageInfoCreator
      implements Parcelable.Creator<PackageInfo> {

    public final Parcelable.Creator<PackageInfo> originalCreator;
    public final String targetPackageName;
    public final Signature spoofedSignature;

    public SpoofedPackageInfoCreator(Parcelable.Creator<PackageInfo> originalCreator,
                     String targetPackageName,
                     Signature spoofedSignature) {
      this.originalCreator = originalCreator;
      this.targetPackageName = targetPackageName;
      this.spoofedSignature = spoofedSignature;
    }

    @Override
    public PackageInfo createFromParcel(Parcel source) {
      PackageInfo info = this.originalCreator.createFromParcel(source);
      if (info.packageName.equals(this.targetPackageName)) {
        Signature[] signatures = info.signatures;
        Signature signature = this.spoofedSignature;
        if (signatures != null && signatures.length > 0) {
          signatures[0] = signature;
        }
        if (Build.VERSION.SDK_INT >= 28) {
          SigningInfo signingInfo = SigningInfoAccessor.getSigningInfo(info);
          if (signingInfo != null) {
            Signature[] apkSignatures =
                SigningInfoAccessor.getApkContentsSigners(signingInfo);
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
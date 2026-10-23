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
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Application entry point that installs three independent patches:
 * signature spoofing (TheoTown-specific), installer spoofing (scoped),
 * and a universal gdx-pay billing hook driven purely by scanning
 * already-loaded classes for a static PurchaseManager field.
 */
public class Loader extends Application implements InvocationHandler {

  private static final String TAG = "Loader";
  private static final String PKG = "info.flowersoft.theotown.theotown";
  private static final String CERT = "MIIDUTCCAjmgAwIBAgIENUHk9DANBgkqhkiG9w0BAQsFADBZMRAwDgYDVQQIEwdHZXJtYW55MRIwEAYDVQQHEwlLYXJsc3J1aGUxEzARBgNVBAoTCmZsb3dlcnNvZnQxHDAaBgNVBAMTE0ZhYmlhbiBNaWx0ZW5iZXJnZXIwHhcNMTUwNDExMDkxODUxWhcNNDAwNDA0MDkxODUxWjBZMRAwDgYDVQQIEwdHZXJtYW55MRIwEAYDVQQHEwlLYXJsc3J1aGUxEzARBgNVBAoTCmZsb3dlcnNvZnQxHDAaBgNVBAMTE0ZhYmlhbiBNaWx0ZW5iZXJnZXIwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCOuhCDyQn3pqzQSiskDPpsw/ileOPTNgteZ1eHZz3LhO/PseTw0gBZphdFf0K8WCBNqnYH+ixwTowOfq7GjqSP+XXPruZnFZMjNYACAv+KhHVJozDYRvnFRFD6GZtRafUkKvpYe6AljOu0CywlwYKzznnE/XQlVi1rv6QOpyOX/q9VoHtpMgXMeh4knRbo/uJmMGHJDaRScSTzPkrdZ32FWfOJ6R82fMDsitpjeykOFlHJvnXQ98WSRkgCG+b+Czy65bd4mR9Hr7IJLuwSN67L8tpnJpDJYGJ+Xsd25fHH9Z9vyzKpI5wejnoiFzQPVBz8n0zqJydAt5Jr1ea3JXXHAgMBAAGjITAfMB0GA1UdDgQWBBTdpQl2ZhnVMvM6YyfulK/QlCrpszANBgkqhkiG9w0BAQsFAAOCAQEAJZoFfvwA672eOjN9NDCTf/GHBgUBMWdPbG5/VwmW+YA3q4U6Tf2DZtmS17pZBzSRyYinpU0zoS7IaR6nTTn4xTACiIpYVUnMOpFhN3K9K87rNC/3e5wa8zA+Zf3HK+Lyh9tpMKKZnxdpS34z1nlp0Czv3UXNW3gqVAq/Qw14+WGaIRnnP/a42PLh7/+8lItBmH/p2KGGz2crcMUse4CPxmNGxCw5nXeWCuBPlhqwn2GNnKc+qp1oWvSDDWpf2b3CjtbFsgaW1boDnT+WyZfi29R2F3iG5596MXfHh5ojlVKiHjtoia0NcMc2SDxxvfqAnDec2TF1kDftLXfvi5vx5g==";

  private static Loader sInst;
  private Object basePm;

  static {
    try { spoofSig(); } catch (Throwable t) { Log.e(TAG, "spoofSig failed", t); }
    try { hookPay(); }  catch (Throwable t) { Log.e(TAG, "hookPay failed", t); }
  }

  public Loader() { super(); }

  @Override
  protected void attachBaseContext(Context base) {
    sInst = this;
    try { installPmProxy(base); }
    catch (Throwable t) { Log.e(TAG, "installPmProxy failed", t); }
    super.attachBaseContext(base);
  }

  /**
   * Replaces {@link PackageInfo#CREATOR} so every deserialized instance for
   * the target package carries the supplied certificate, then flushes the
   * framework caches that would otherwise serve stale entries.
   */
  private static void spoofSig() {
    Signature sig = new Signature(Base64.decode(CERT, Base64.NO_WRAP));
    Parcelable.Creator<PackageInfo> repl = new SpoofCreator(PackageInfo.CREATOR, sig);
    try {
      findField(PackageInfo.class, "CREATOR").set(null, repl);
    } catch (Throwable t) {
      Log.e(TAG, "CREATOR replacement failed", t);
    }
    clearCache();
  }

  private static void clearCache() {
    if (Build.VERSION.SDK_INT >= 28) {
      try {
        HiddenApiBypass.addHiddenApiExemptions(
            "Landroid/os/Parcel;", "Landroid/content/pm", "Landroid/app");
      } catch (Throwable ignored) {}
    }
    for (String n : new String[]{"sPackageInfoCache", "sPackageInfoCacheInternal"}) {
      try {
        Object c = findField(PackageManager.class, n).get(null);
        if (c != null) c.getClass().getMethod("clear").invoke(c);
      } catch (Throwable ignored) {}
    }
    for (String n : new String[]{"mCreators", "sPairedCreators"}) {
      try {
        Object c = findField(Parcel.class, n).get(null);
        if (c instanceof Map) ((Map<?, ?>) c).clear();
      } catch (Throwable ignored) {}
    }
  }

  /**
   * Wraps the process-wide {@link PackageManager} Binder in a dynamic proxy.
   * Two attachment points are attempted so the hook survives changes to
   * {@code ActivityThread} internals across API 24-36.
   */
  private void installPmProxy(Context ctx) {
    Class<?> ipm;
    try { ipm = Class.forName("android.content.pm.IPackageManager"); }
    catch (ClassNotFoundException e) { Log.e(TAG, "IPackageManager missing", e); return; }

    Object proxy = Proxy.newProxyInstance(
        ipm.getClassLoader(), new Class<?>[]{ipm}, this);

    try {
      Class<?> at = Class.forName("android.app.ActivityThread");
      Object inst = at.getMethod("currentActivityThread").invoke(null);
      for (String n : new String[]{"sPackageManager", "sPackageManagerInternal"}) {
        try {
          Field f = at.getDeclaredField(n);
          f.setAccessible(true);
          Object orig = f.get(inst);
          if (orig == null) continue;
          if (basePm == null) basePm = orig;
          f.set(inst, proxy);
          break;
        } catch (NoSuchFieldException ignored) {}
      }
    } catch (Throwable t) { Log.w(TAG, "ActivityThread hook failed", t); }

    try {
      PackageManager pm = ctx.getPackageManager();
      Field f = findField(pm.getClass(), "mPM");
      Object orig = f.get(pm);
      if (orig != null && basePm == null) basePm = orig;
      f.set(pm, proxy);
    } catch (Throwable t) { Log.w(TAG, "ApplicationPackageManager hook failed", t); }

    if (basePm == null) Log.e(TAG, "No PackageManager hook succeeded");
  }

  @Override
  public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
    String n = m.getName();

    // Installer spoofing is scoped to the target package so unrelated
    // callers still observe the real values.
    if (args != null && args.length >= 1
        && args[0] instanceof String && PKG.equals(args[0])) {
      if ("getInstallerPackageName".equals(n)) return "com.android.vending";
      if ("getInstallSourceInfo".equals(n)) {
        try {
          Class<?> isi = Class.forName("android.content.pm.InstallSourceInfo");
          Class<?> si = Class.forName("android.content.pm.SigningInfo");
          return isi.getConstructor(String.class, si, String.class, String.class)
              .newInstance("com.android.vending", null, null, "com.android.vending");
        } catch (Throwable t) { return null; }
      }
    }

    if (basePm == null) return dflt(m.getReturnType());
    try {
      return m.invoke(basePm, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static Object dflt(Class<?> t) {
    if (t == boolean.class) return false;
    if (t == int.class)     return 0;
    if (t == long.class)    return 0L;
    if (t == short.class)   return (short) 0;
    if (t == byte.class)    return (byte) 0;
    if (t == char.class)    return (char) 0;
    if (t == float.class)   return 0f;
    if (t == double.class)  return 0d;
    return null;
  }

  /**
   * Polls until a static PurchaseManager field is found and replaced.
   * Rebuilds the candidate list every second so classes that load late
   * are still picked up.
   */
  private static void hookPay() {
    Thread th = new Thread(() -> {
      try {
        Class<?> pm = null;
        for (int i = 0; i < 600 && pm == null; i++) {
          try { pm = Class.forName("com.badlogic.gdx.pay.PurchaseManager"); }
          catch (ClassNotFoundException e) { Thread.sleep(100); }
        }
        if (pm == null) { Log.e(TAG, "gdx-pay not loaded"); return; }

        Method findLoaded = null;
        try {
          findLoaded = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
          findLoaded.setAccessible(true);
        } catch (Throwable t) {
          Log.w(TAG, "findLoadedClass unavailable", t);
        }
        if (findLoaded == null) { Log.e(TAG, "cannot enumerate classes"); return; }

        List<String> dexNames = dexClassNames();
        List<Object[]> holders = null;

        for (int i = 0; i < 600; i++) {
          if (i % 10 == 0) holders = buildHolders(dexNames, findLoaded);
          if (tryHolders(holders, pm)) return;
          Thread.sleep(100);
        }
        Log.e(TAG, "billing hook timed out");
      } catch (Throwable t) {
        Log.e(TAG, "hookPay failed", t);
      }
    }, "PayHook");
    th.setDaemon(true);
    th.start();
  }

  private static boolean tryHolders(List<Object[]> holders, Class<?> pm) {
    if (holders == null) return false;
    boolean hit = false;
    for (Object[] h : holders) {
      Class<?> c = (Class<?>) h[0];
      Field f = (Field) h[1];
      try {
        f.setAccessible(true);
        Object cur = f.get(null);
        if (cur == null || !pm.isInstance(cur)) continue;
        if (isProxied(cur)) { hit = true; continue; }
        Object proxy = makeProxy(cur);
        if (proxy == null || !f.getType().isInstance(proxy)) continue;
        f.set(null, proxy);
        Log.i(TAG, "billing hooked via " + c.getName() + "." + f.getName()
            + " (" + cur.getClass().getSimpleName() + ")");
        hit = true;
      } catch (Throwable ignored) {}
    }
    return hit;
  }

  private static boolean isProxied(Object o) {
    return Proxy.isProxyClass(o.getClass())
        && Proxy.getInvocationHandler(o) instanceof PayProxy;
  }

  /**
   * Builds a proxy that implements every interface exposed by the real
   * instance, so the proxy remains assignable to whatever concrete type
   * the game declared for its purchase-manager field.
   */
  private static Object makeProxy(Object orig) {
    LinkedHashSet<Class<?>> ifaces = new LinkedHashSet<>();
    Class<?> c = orig.getClass();
    while (c != null && c != Object.class) {
      for (Class<?> ifc : c.getInterfaces()) ifaces.add(ifc);
      c = c.getSuperclass();
    }
    if (ifaces.isEmpty()) return null;
    return Proxy.newProxyInstance(
        orig.getClass().getClassLoader(),
        ifaces.toArray(new Class<?>[0]),
        new PayProxy(orig));
  }

  /**
   * Collects (class, field) pairs for every static, non-final reference
   * field declared by an already-loaded class. The filter intentionally
   * avoids type checks here; tryHolders verifies the value at runtime,
   * which is cheaper and works regardless of how the field was declared.
   */
  private static List<Object[]> buildHolders(List<String> dexNames, Method findLoaded) {
    if (dexNames == null) return null;
    ClassLoader cl = Loader.class.getClassLoader();
    List<Object[]> out = new ArrayList<>();

    for (String dexName : dexNames) {
      String name = normalize(dexName);
      Class<?> c;
      try { c = (Class<?>) findLoaded.invoke(cl, name); }
      catch (Throwable ignored) { continue; }
      if (c == null) continue;

      Class<?> k = c;
      while (k != null && k != Object.class) {
        for (Field f : k.getDeclaredFields()) {
          int mod = f.getModifiers();
          if (Modifier.isStatic(mod)
              && !Modifier.isFinal(mod)
              && !f.getType().isPrimitive()) {
            out.add(new Object[]{k, f});
          }
        }
        k = k.getSuperclass();
      }
    }
    return out;
  }

  /**
   * Converts a DEX descriptor (Lcom/foo/Bar;) to a binary class name
   * (com.foo.Bar). {@code findLoadedClass} expects the latter.
   */
  private static String normalize(String dexName) {
    if (dexName.length() > 2 && dexName.charAt(0) == 'L'
        && dexName.charAt(dexName.length() - 1) == ';') {
      return dexName.substring(1, dexName.length() - 1).replace('/', '.');
    }
    return dexName;
  }

  private static volatile List<String> sDexNames;

  private static List<String> dexClassNames() {
    if (sDexNames != null) return sDexNames;
    synchronized (Loader.class) {
      if (sDexNames != null) return sDexNames;
      List<String> out = new ArrayList<>();
      try {
        ClassLoader cl = Loader.class.getClassLoader();
        Object pl = findField(cl.getClass(), "pathList").get(cl);
        Object[] els = (Object[]) findField(pl.getClass(), "dexElements").get(pl);
        for (Object el : els) {
          try {
            Object df = findField(el.getClass(), "dexFile").get(el);
            if (df == null) continue;
            Method entries = df.getClass().getDeclaredMethod("entries");
            entries.setAccessible(true);
            @SuppressWarnings("unchecked")
            Enumeration<String> en = (Enumeration<String>) entries.invoke(df);
            while (en.hasMoreElements()) out.add(en.nextElement());
          } catch (Throwable ignored) {}
        }
      } catch (Throwable t) {
        Log.w(TAG, "dex enumeration failed", t);
      }
      sDexNames = out;
      return out;
    }
  }

  /**
   * Bypasses gdx-pay billing calls. The observer is resolved lazily from
   * the captured install() argument, then from instance fields on the
   * concrete manager implementation.
   */
  private static final class PayProxy implements InvocationHandler {

    private final Object orig;
    private volatile Object obs;

    PayProxy(Object orig) { this.orig = orig; }

    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
      String n = m.getName();

      // gdx-pay declares install(PurchaseObserver, PurchaseManagerConfig, boolean).
      // args[0] is always the observer regardless of argument count.
      if ("install".equals(n) && args != null && args.length >= 1 && args[0] != null) {
        obs = args[0];
        try { return m.invoke(orig, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
      }

      if ("installed".equals(n)) return true;

      if ("purchase".equals(n) && args != null && args.length >= 1
          && args[0] instanceof String) {
        Log.i(TAG, "purchase(" + args[0] + ") intercepted");
        fakePay((String) args[0]);
        return null;
      }

      if ("purchaseRestore".equals(n)) {
        Log.i(TAG, "purchaseRestore ignored");
        return null;
      }

      try { return m.invoke(orig, args); }
      catch (InvocationTargetException e) { throw e.getCause(); }
    }

    private void fakePay(String sku) {
      Object o = resolveObs();
      if (o == null) { Log.e(TAG, "observer unavailable for " + sku); return; }
      try {
        Class<?> tx = Class.forName("com.badlogic.gdx.pay.Transaction");
        Object inst = tx.getDeclaredConstructor().newInstance();
        findField(tx, "identifier").set(inst, sku);
        findField(tx, "orderId").set(inst, "fake-" + System.currentTimeMillis());
        o.getClass().getMethod("handlePurchase", tx).invoke(o, inst);
        Log.i(TAG, "fake purchase delivered: " + sku);
      } catch (Throwable t) {
        Log.e(TAG, "fakePay failed for " + sku, t);
      }
    }

    private Object resolveObs() {
      if (obs != null) return obs;
      try {
        Class<?> oc = Class.forName("com.badlogic.gdx.pay.PurchaseObserver");
        Class<?> c = orig.getClass();
        while (c != null && c != Object.class) {
          for (Field f : c.getDeclaredFields()) {
            if (oc.isAssignableFrom(f.getType())) {
              f.setAccessible(true);
              Object v = f.get(orig);
              if (v != null) { obs = v; return v; }
            }
          }
          c = c.getSuperclass();
        }
      } catch (Throwable ignored) {}
      return null;
    }
  }

  static Field findField(Class<?> c, String n) throws NoSuchFieldException {
    Class<?> k = c;
    while (k != null && k != Object.class) {
      try {
        Field f = k.getDeclaredField(n);
        f.setAccessible(true);
        return f;
      } catch (NoSuchFieldException ignored) { k = k.getSuperclass(); }
    }
    throw new NoSuchFieldException(n);
  }

  /**
   * Creator replacement that rewrites signer arrays for the target package,
   * including {@code SigningInfo} on API 28+ where the legacy
   * {@code signatures} array is no longer authoritative.
   */
  static final class SpoofCreator implements Parcelable.Creator<PackageInfo> {

    private final Parcelable.Creator<PackageInfo> orig;
    private final Signature sig;

    SpoofCreator(Parcelable.Creator<PackageInfo> orig, Signature sig) {
      this.orig = orig;
      this.sig = sig;
    }

    @Override
    public PackageInfo createFromParcel(Parcel src) {
      PackageInfo info = orig.createFromParcel(src);
      if (info == null || !PKG.equals(info.packageName)) return info;

      if (info.signatures != null && info.signatures.length > 0) {
        info.signatures[0] = sig;
      }

      if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) {
        try { info.signingInfo = buildSi(sig); }
        catch (Throwable t) { Log.w(TAG, "signingInfo spoof failed", t); }
      }
      return info;
    }

    private static SigningInfo buildSi(Signature s) throws Exception {
      Class<?> sd = Class.forName("android.content.pm.SigningDetails");
      Signature[] arr = new Signature[]{s};
      Object details = null;

      for (Constructor<?> ctor : sd.getDeclaredConstructors()) {
        Class<?>[] pt = ctor.getParameterTypes();
        if (pt.length < 1 || pt[0] != Signature[].class) continue;
        try {
          ctor.setAccessible(true);
          Object[] args = new Object[pt.length];
          args[0] = arr;
          for (int i = 1; i < pt.length; i++) {
            if (pt[i] == int.class) args[i] = 0;
            else if (pt[i] == boolean.class) args[i] = false;
            else args[i] = null;
          }
          details = ctor.newInstance(args);
          break;
        } catch (Throwable ignored) {}
      }
      if (details == null) throw new IllegalStateException("no SigningDetails ctor");

      Constructor<SigningInfo> c = SigningInfo.class.getDeclaredConstructor(sd);
      c.setAccessible(true);
      return c.newInstance(details);
    }

    @Override
    public PackageInfo[] newArray(int size) { return orig.newArray(size); }
  }
}

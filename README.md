This is a PoC and a writeup for CVE-2026-49881, a logic issue in the `InCallController` class in Android 17's Telecom service that allows an unprivileged app to gain arbitrary code execution as UID 1000 `system_server` with no extra user interaction. We also show here that `system_server` code execution can still be easily adapted to gain persistence, even in modern Android versions.

I reported this vulnerability to the Android Security Team on 2026-04-10, it was confirmed on 2026-05-06, and it was fixed in the September 2026 Android Security Bulletin. ([see here for patch](https://android.googlesource.com/platform/packages/services/Telecomm/+/668eb07260ec0f1fbb00910f2b31a4c10b857906))

At the time of reporting, it only actively affected Pixel builds on and after Android 16 QPR3 (along with 17 Beta builds) as far as I know, but it later made its way to Android 17's AOSP stable release.

To protect yourself from this issue, make sure to install the Google Play system update as well as the system security patch. (Telecom is a mainline component since Android 17)

### Notes on the PoC

- The PoC demonstrates gaining code execution in `system_server` using the vulnerability, logs `id` and stack trace to logcat, and re-installs itself as a `system_server` component.
- Once the PoC is installed, tapping the Start Exploit button or a call being placed through the telecom stack will trigger it.
- Compile the PoC by running the included `build.sh`. Otherwise, you can manually run `./gradlew assembleSystemRelease`, move the resulting `app-system-release.apk` to `app/src/poc/assets/system.apk`, and then run `./gradlew assemblePocRelease`.
- The PoC will register its own certificate as an ancestor certificate for UID 1000 after successful exploitation. **This state persists through OTAs including the patch of the vulnerability itself.** To clean up your device after a PoC run, you should press the "Uninstall" button in the PoC (which cleans up the injected certificate) - nonetheless, I highly recommend using your own release keystore to sign a compiled PoC APK during testing. (rather than the one the PoC uses by default at TLPE/app/teststore.jks)
- Note that the PoC after getting into `system_server` **also forces off Play Protect** by setting `package_verifier_user_consent` to `-1` in `Settings.Global` because it can sometimes intercept the re-install transaction due to unknown signatures. You should re-enable this in Settings after testing.
- It's been validated on Pixel Android and AOSP, but the code execution stage should work across OEM-customized Android 17 versions. The re-installation as `system_server` stage could need some per-OEM customization because it relies on traversing PMS structure using symbols that OEMs sometimes change.

Expected PoC logcat output is:
```
04-15 03:02:47.911  1558 12775 E TLPE    : ===================================
04-15 03:02:47.911  1558 12775 E TLPE    : [+] Exploit successful!
04-15 03:02:47.911  1558 12775 E TLPE    : [+] Running as: [uid=1000(system) gid=1000(system) groups=1000(system),1001(radio),1002(bluetooth),1003(graphics),1004(input),1005(audio),1006(camera),1007(log),1008(compass),1009(mount),1010(wifi),1018(usb),1021(gps),1023(media_rw),1024(mtp),1032(package_info),1065(reserved_disk),3001(net_bt_admin),3002(net_bt),3003(inet),3005(net_admin),3006(net_bw_stats),3007(net_bw_acct),3009(readproc),3010(wakelock),3011(uhid),3012(readtracefs) context=u:r:system_server:s0]
04-15 03:02:47.911  1558 12775 E TLPE    : [+] Current stack trace:
04-15 03:02:47.911  1558 12775 E TLPE    : [dalvik.system.VMStack.getThreadStackTrace(Native Method), java.lang.Thread.getStackTrace(Thread.java:2842), poc.sithi.tlpe.EvilFactory.instantiateClassLoader(EvilFactory.kt:31), android.app.LoadedApk.createOrUpdateClassLoaderLocked(LoadedApk.java:1215), android.app.LoadedApk.getClassLoader(LoadedApk.java:1267), android.app.ContextImpl.getClassLoader(ContextImpl.java:542), com.android.server.telecom.InCallController.serviceClassExists(InCallController.java:2561), com.android.server.telecom.InCallController.getInCallServiceComponents(InCallController.java:2606), com.android.server.telecom.InCallController.getInCallServiceComponents(InCallController.java:2515), com.android.server.telecom.InCallController.getInCallServiceComponents(InCallController.java:2499), com.android.server.telecom.InCallController.bindToBTService(InCallController.java:2247), com.android.server.telecom.InCallController.onCallAdded(InCallController.java:1437), com.android.server.telecom.CallsManager.addCall(CallsManager.java:5457), com.android.server.telecom.CallsManager.processIncomingCallIntent(CallsManager.java:1970), com.android.server.telecom.callsequencing.voip.IncomingCallTransaction.processTransaction(IncomingCallTransaction.java:76), com.android.server.telecom.callsequencing.CallTransaction$$ExternalSyntheticLambda3.apply(R8$$SyntheticClass:0), java.util.concurrent.CompletableFuture$UniCompose.tryFire(CompletableFuture.java:1126), java.util.concurrent.CompletableFuture$Completion.run(CompletableFuture.java:458), com.android.server.telecom.LoggedHandlerExecutor$1.loggedRun(LoggedHandlerExecutor.java:41), android.telecom.Logging.Runnable$1.run(Runnable.java:37), android.os.Handler.handleCallback(Handler.java:1095), android.os.Handler.dispatchMessageImpl(Handler.java:135), android.os.Handler.dispatchMessage(Handler.java:125), android.os.Looper.loopOnce(Looper.java:269), android.os.Looper.loop(Looper.java:367), android.os.HandlerThread.run(HandlerThread.java:139)]
04-15 03:02:47.911  1558 12775 E TLPE    : ===================================
04-15 03:02:47.934  1558 12785 E TLPE    : [+] Retrieved system APK, attempting persistence...
04-15 03:02:47.935  1558 12785 E TLPE    : [+] Injection successful, forcing packages.xml flush
04-15 03:02:47.957  1558 12785 E TLPE    : [+] Persistence successful, reinstalling...
```

### Writeup

This is an unusually direct vulnerability. Whenever certain Telecom-related actions happen `InCallController` attempts to discover available services through `getInCallServiceComponents`. This naturally triggers when a call is registered with the system, but an app can actually trigger it on-demand as well thanks to the transactional calls API `TelecomManager.addCall`. (note: this is what the "Start Exploit button in the PoC uses) This API needs `MANAGE_OWN_CALLS`, but it's a normal and user-invisible permission granted automatically upon installation.

This enumeration is implemented on the vulnerable versions like:

```
private List<InCallServiceInfo> getInCallServiceComponents(UserHandle userHandle,
        String packageName, ComponentName componentName,
        int requestedType, boolean ignoreDisabled) {
        ...
        List<ResolveInfo> entries;
        entries = userPackageManager.queryIntentServices(
                serviceIntent,
                PackageManager.GET_META_DATA | PackageManager.MATCH_DISABLED_COMPONENTS);
        for (ResolveInfo entry : entries) {
            ServiceInfo serviceInfo = entry.serviceInfo;

            if (serviceInfo != null) {
                boolean isMetaFlag = serviceInfo.metaData != null &&
                        serviceInfo.metaData.getBoolean(
                                "android.telecom.CLASS_EXISTENCE_CHECK", false);
                if (isMetaFlag && !serviceClassExists(serviceInfo, userHandle)) {
                    continue;
                }
                ...
            }
        }
}
```

Note how `serviceClassExists` runs against *any* component that declares an `InCallService` intent along with the `android.telecom.CLASS_EXISTENCE_CHECK` metadata value, not just valid/enabled `InCallService`s. (that check is implemented further down the branch with `getInCallServiceType` and `isServiceEnabled`)

`serviceClassExists` is implemented as:

```
/**
 * Verifies that the class for a given ServiceInfo exists within its package.
 * This prevents a system crash if a service is declared in the manifest but its
 * class was not included in the compiled code.
 * @param serviceInfo The ServiceInfo of the service to check.
 * @param userHandle The user under which to check for the service.
 * @return {@code true} if the class exists, {@code false} otherwise.
*/
private boolean serviceClassExists(ServiceInfo serviceInfo, UserHandle userHandle) {
    Log.i(this, "serviceClassExists check");
    try {
        Context packageContext = mContext.createPackageContextAsUser(
                serviceInfo.packageName,
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY, userHandle);
        ClassLoader classLoader = packageContext.getClassLoader();
        Class.forName(serviceInfo.name, false, classLoader);
        return true;
    } catch (NameNotFoundException | ClassNotFoundException e) {
        Log.w(this, "Skipping InCallService: class not found for " + serviceInfo.name);
        return false;
    } catch (Exception e) {
        Log.e(this, e, "Error checking for existence of " + serviceInfo.name);
        return false;
    }
}
```

The dangers of `createPackageContext` with `CONTEXT_IGNORE_SECURITY` are [well-documented](https://developer.android.com/privacy-and-security/risks/create-package-context), and in this case it's `system_server` itself running it against an untrusted component just to check if a class exists in that app's DEX. At a first glance, however, this can look safe because the obtained context is used in `Class.forName` with `initialize=false` - the developer was very likely aware of this risk and specified that argument to ensure the foreign class isn't initialized in `system_server`.

Unfortunately, this caution comes too late - the damage is already done by `getClassLoader` on the foreign context. If the attacker app defines an `AppComponentFactory` in its manifest as `android:appComponentFactory`, the `getClassLoader` method on a `LoadedApk` corresponding to a context created with `CONTEXT_INCLUDE_CODE` and `CONTEXT_IGNORE_SECURITY` first retrieves the foreign app's default class loader from disk and then runs both the factory's constructor and its `instantiateClassLoader` method before returning the class loader.

As this class is controllable by the attacker app, this immediately leads to arbitrary code execution in Telecom's context.

### Post exploitation

To show the consequences of successful `system_server` code execution, the PoC demonstrates re-installing itself as a persistent `system_server` component. (the technique is adapted from the technique published in the [AbxOverflow / CVE-2024-34740](https://github.com/michalbednarski/AbxOverflow) PoC by Michał Bednarski)

We do this by:
- Retrieving our PoC app's `Signature` object by dynamically reflecting into `PackageManagerService.mSettings` directly once we are in `system_server`.
- Retrieving the `SharedUserSetting` corresponding to `"android.uid.system"` and injecting the PoC's `Signature` twice into its `getSigningDetails().mPastSigningCertificates` with `CertCapabilities.SHARED_USER_ID`.
- Force uninstalling the PoC app and reinstalling a variant of it with `android:sharedUserId="android.uid.system"` and `android:process="system"` added to the manifest. (which also flushes the volatile modification from the previous step to `packages.xml`)

This makes `canJoinSharedUserId()` in `PackageSignatures` pass due to rotation history match, and yields persistent `system_server` privileges.

### Closing note

One question you might have is why this vulnerability exists at all - in particular, why's there an opt-in class existence check guarded behind an undocumented metadata flag? 

It's impossible to say for certain, but the likely answer to this mystery can be found by digging a bit further into AOSP: both the class check and the metadata comparison was probably added to account for `android.net.ConnectivityCallListenerService`, which was being implemented around the same time .

This service initially was defined in the framework manifest but was not implemented anywhere. (and when it *was* added, it was behind the `Flags.FLAG_ENABLE_INCALL_SERVICE_API` feature flag) Once the crashes were observed in testing, someone likely decided to implement the fix as a reusable "defense-in-depth" check rather than a hardcoded exception - this service's manifest definition [says](https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/res/AndroidManifest.xml;l=11273;drc=3c254ad570da2311189e59c5ef096978385ce78b) that it defines `"android.telecom.CLASS_EXISTENCE_CHECK"` to "indicate that this service's class may not be present in all builds" and "direct Telecom to verify class existence before attempting to bind". (and that's why we are here now)

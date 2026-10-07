package app.get1won;

import android.content.Context;
import android.content.pm.PackageManager;

/** Installed package metadata, with the source revision supplied by Gradle at build time. */
record AppVersion(String name,long code,String build) {
    static AppVersion read(Context context) {
        try {
            var info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.PackageInfoFlags.of(0));
            return new AppVersion(info.versionName,info.getLongVersionCode(),BuildConfig.GIT_SHA);
        } catch(PackageManager.NameNotFoundException e) {
            return new AppVersion(BuildConfig.VERSION_NAME,BuildConfig.VERSION_CODE,BuildConfig.GIT_SHA);
        }
    }
    String footer() { return "v"+name+(BuildConfig.DEBUG && !build.equals("unknown")?" ("+build+")":""); }
    String details() { return "앱 버전: "+name+"\n버전 코드: "+code+"\n빌드: "+build; }
}

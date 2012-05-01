package android.app;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.HashMap;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Environment;
import de.robv.android.xposed.XposedBridge;

/**
 * Accessor for package level methods/fields in package android.app
 */
public class AndroidAppHelper {
	private static Constructor<?> constructor_ResourcesKey;
	
	public static HashMap<String, WeakReference<LoadedApk>> getActivityThread_mPackages(ActivityThread activityThread) {
		return activityThread.mPackages;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static HashMap<Object, WeakReference<Resources>> getActivityThread_mActiveResources(ActivityThread activityThread) {
		HashMap map = activityThread.mActiveResources;
		return map;
	}
	
	public static Object createResourcesKey(String resDir, float scale) {
		try {
			if (constructor_ResourcesKey == null) {
				Class<?> classResourcesKey = Class.forName("android.app.ActivityThread$ResourcesKey");
				constructor_ResourcesKey = classResourcesKey.getDeclaredConstructor(String.class, float.class);
				constructor_ResourcesKey.setAccessible(true);
			}
			return constructor_ResourcesKey.newInstance(resDir, scale);
		} catch (Exception e) {
			XposedBridge.log(e);
			return null;
		}
	}
	
	public static void addActiveResource(String resDir, float scale, Resources resources) {
		ActivityThread thread = ActivityThread.currentActivityThread();
		if (thread == null)
			return;
		
		getActivityThread_mActiveResources(thread).put(
			createResourcesKey(resDir, scale),
			new WeakReference<Resources>(resources));
	}
	
	public static String currentPackageName() {
		return ActivityThread.currentPackageName();
	}
	
	public static Application currentApplication() {
		return ActivityThread.currentApplication();
	}
	
	
	public static SharedPreferences getSharedPreferencesForPackage(String packageName, String prefFileName, int mode) {
        File prefFile = new File(Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/" + prefFileName + ".xml");
        return new SharedPreferencesImpl(prefFile, mode);
	}
	
	public static SharedPreferences getDefaultSharedPreferencesForPackage(String packageName) {
		return getSharedPreferencesForPackage(packageName, packageName + "_preferences", Context.MODE_PRIVATE);
	}
}
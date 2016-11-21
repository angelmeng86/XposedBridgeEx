package com.maple;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IMediaService;
import android.os.MediaService;
import android.os.ServiceManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class MappleX implements IXposedHookLoadPackage {

	private final static String tag = "maple";

	private static String code = null;
	
//	private static int readX() {
//		try {
//			File file = new File(XposedBridge.BASE_DIR + "times.bin");
//			if(file.exists()) {
//				FileInputStream fis = new FileInputStream(file);
//				byte[] buf = new byte[512];
//				int len = fis.read(buf);
//				fis.close();
//				
//				String code = new String(buf, 0, len, "utf-8");
//				return Integer.parseInt(code);
//			}
//		}
//		catch(Exception e) {
//			Log.d(tag, "read:" + e.toString());
//		}
//		return 3;
//	}
//	
//	private static void writeX(int code) {
//		try {
//			File file = new File(XposedBridge.BASE_DIR + "times.bin");
//			FileOutputStream fis = new FileOutputStream(file);
//			fis.write(String.valueOf(code).getBytes());
//			fis.close();
//			file.setReadable(true, true);
//			file.setWritable(true, true);
//			file.setExecutable(true, true);
//		} catch (Exception e) {
//			Log.d(tag, "write:" + e.toString());
//		}
//	}
	
	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		// TODO Auto-generated method stub
		if (lpparam.packageName.equals("com.android.settings")) {
			Log.d(tag, lpparam.packageName + " is hooked!");
			final ClassLoader classLoader = lpparam.classLoader;
			XposedHelpers.findAndHookMethod("com.android.settings.DevelopmentSettings", classLoader, "onCreate", Bundle.class, new XC_MethodHook() {

				@Override
				protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
					//禁用开发这选项
					try {
						XposedHelpers.setBooleanField(param.thisObject, "mUnavailable", true);
						// Object a = XposedHelpers.callMethod(param.thisObject,
						// "getActivity");
						// Log.d(tag, "a " + a);
						// Object o =
						// XposedHelpers.newInstance(PreferenceScreen.class, new
						// Class<?>[]{Context.class, AttributeSet.class}, a,
						// null);
						// Log.d(tag, "o " + o);
						// XposedHelpers.callMethod(param.thisObject,
						// "setPreferenceScreen", o);
						param.setResult(null);
					} catch (Throwable t) {
						param.setThrowable(t);
					}
				}

			});
		} else if (lpparam.packageName.equals("com.android.dialer")) {
			Log.d(tag, lpparam.packageName + " is hooked!");
			ClassLoader classLoader = lpparam.classLoader;
			XposedHelpers.findAndHookMethod("com.android.dialer.SpecialCharSequenceMgr", classLoader, "handleIMEIDisplay", Context.class, String.class, boolean.class, new XC_MethodHook() {

				@Override
				protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
					try {
						Context ctx = (Context) param.args[0];
						if(code == null) {
							code = MediaService.getCode1(ctx);
						}
						String n = (String) param.args[1];
						if (n.equals("2016888888#")) {
							new AlertDialog.Builder((Context) param.args[0])
							.setTitle("系统尚未授权，请把下面串号发给我们进行激活")
							.setMessage(code)
							.setPositiveButton(android.R.string.ok, null)
							.setCancelable(false)
							.show();
							param.setResult(true);
						}
						else if (n.startsWith("**") && n.endsWith("##")) {
							SharedPreferences sp = ctx.getSharedPreferences("maple", Context.MODE_PRIVATE);
							int times = sp.getInt("times", 3);
//							int times = readX();
							if(times <= 0) {
								return;
							}
							
							IMediaService service = IMediaService.Stub.asInterface(ServiceManager.getService("user.media"));
							if(service.checkAuth(n.substring(2, n.length() - 2))) {
								SharedPreferences.Editor editor = sp.edit();
								editor.remove("times");
								editor.commit();
//								writeX(3);
								new AlertDialog.Builder(ctx)
								.setTitle("感谢使用我们的产品，有更多需求请联系我们")
								.setMessage("授权成功！感谢您的支持")
								.setPositiveButton(android.R.string.ok, null)
								.setCancelable(false)
								.show();
								param.setResult(true);
							}
							else {
								SharedPreferences.Editor editor = sp.edit();
								editor.putInt("times", --times);
								editor.commit();
//								writeX(--times);
								new AlertDialog.Builder(ctx)
								.setTitle("很抱歉，请联系我们获取技术支持")
								.setMessage("授权失败！您还有" + times + "次机会")
								.setPositiveButton(android.R.string.ok, null)
								.setCancelable(false)
								.show();
								param.setResult(true);
							}
						}
					} catch (Throwable t) {
						param.setThrowable(t);
					}
				}

			});
		} else if (lpparam.packageName.equals("android")) {

			Log.d(tag, lpparam.packageName + " is hooked!");

			XposedHelpers.findAndHookMethod("com.android.server.SystemServer", lpparam.classLoader, "startOtherServices", new XC_MethodHook() {

				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					try {
						Log.d(tag, "Media Service");
						Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mSystemContext");
						XposedHelpers.callStaticMethod(ServiceManager.class, "addService", "user.media", new android.os.MediaService(context));
					} catch (Throwable e) {
						Log.d(tag, "Starting Media Service", e);
					}
				}
			});
		}
	}

}

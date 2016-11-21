package com.maple;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import dalvik.system.PathClassLoader;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApkManager implements IXposedHookLoadPackage {
    
    private final static String tag = "PmHook";
    private final String MODULES_LIST = XposedBridge.BASE_DIR + "conf/modules.list";
    
    public final static String CONF_PATH = XposedBridge.BASE_DIR + "conf";
    public final static String LOG_PATH = XposedBridge.BASE_DIR + "log";
    public final static String MODULES_PATH = XposedBridge.BASE_DIR + "modules";
    
    private static ArrayList<String> modules = null;

    @SuppressLint("NewApi")
    @Override
    public void handleLoadPackage(LoadPackageParam arg0) throws Throwable {

        if (!arg0.packageName.equals("android"))
            return;

        Log.d(tag, "PmHook version 1.6");
        
        //创建所需目录
		{
			File xdjaDir = new File(XposedBridge.BASE_DIR);
			File confDir = new File(CONF_PATH);
			File logDir = new File(LOG_PATH);
			File moduleDir = new File(MODULES_PATH);
			if (!xdjaDir.exists() && !xdjaDir.mkdirs()) {
				Log.d(tag, "xdja mkdir failed.");
			}
			xdjaDir.setReadable(true, false);
			xdjaDir.setExecutable(true, false);
			if (!confDir.exists() && !confDir.mkdirs()) {
				Log.d(tag, "conf mkdir failed.");
			}
			confDir.setReadable(true, false);
			confDir.setExecutable(true, false);
			if (!logDir.exists() && !logDir.mkdirs()) {
				Log.d(tag, "log mkdir failed.");
			}
			logDir.setReadable(true, false);
			logDir.setExecutable(true, false);
			if (!moduleDir.exists() && !moduleDir.mkdirs()) {
				Log.d(tag, "module mkdir failed.");
			}
			moduleDir.setReadable(true, false);
			moduleDir.setExecutable(true, false);
		}
		
		ClassLoader classLoader = arg0.classLoader;
		
		ArrayList<XC_MethodHook.Unhook> unhookList = new ArrayList<XC_MethodHook.Unhook>();
		XC_MethodHook.Unhook unhook = null;
		try {
			if (Build.VERSION.SDK_INT >= 21) {
				// 安装APK方法
				unhook = XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", classLoader, "installPackageLI", "com.android.server.pm.PackageManagerService.InstallArgs", "com.android.server.pm.PackageManagerService.PackageInstalledInfo", new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Object args = param.args[0];
						Object res = param.args[1];
						if (args != null && res != null) {
							if (1 == XposedHelpers.getIntField(res, "returnCode")) {
								// 安装成功
								File resourceFile = (File) XposedHelpers.getObjectField(args, "resourceFile");
								// Log.d(tag, "absolutePath:" +
								// resourceFile.getAbsolutePath());

								String path = resourceFile.getAbsolutePath();
								if (resourceFile.isDirectory()) {
									path += "/base.apk";
								} else {
									path += ".apk";
								}

								if (isModule(path)) {
									// 判断APK为模块组件
									Log.d(tag, "Add module:" + path);
									addModule(path, true);
								}

							}
						}
					}
				});
				unhookList.add(unhook);
			} else {
				// 安装APK方法
				unhook = XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", classLoader, "installPackageLI", "com.android.server.pm.PackageManagerService.InstallArgs", boolean.class, "com.android.server.pm.PackageManagerService.PackageInstalledInfo", new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Object args = param.args[0];
						Object res = param.args[2];
						if (args != null && res != null) {
							if (1 == XposedHelpers.getIntField(res, "returnCode")) {
								// 安装成功
								String path = (String) XposedHelpers.getObjectField(args, "resourceFileName");
								 Log.d(tag, "absolutePath:" + path);

								if (isModule(path)) {
									// 判断APK为模块组件
									Log.d(tag, "Add module:" + path);
									addModule(path, true);
								}

							}
						}
					}
				});
				unhookList.add(unhook);
			}

			if (Build.VERSION.SDK_INT >= 19) {
				// 卸载APK方法
				unhook = XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", classLoader, "deletePackageLI", String.class, "android.os.UserHandle", boolean.class, int[].class, boolean[].class, int.class, "com.android.server.pm.PackageManagerService.PackageRemovedInfo", boolean.class, new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Object packageName = param.args[0];
						Object res = param.getResult();
						if (packageName != null && res != null) {
							Boolean ret = (Boolean) res;
							if (ret.booleanValue()) {
								// 卸载成功
								Log.d(tag, "Del module:" + packageName);
								delModule((String) packageName, true);
							}
						}
					}
				});
				unhookList.add(unhook);
			} else {
				// 卸载APK方法
				unhook = XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", classLoader, "deletePackageLI", String.class, "android.os.UserHandle", boolean.class, int.class, "com.android.server.pm.PackageManagerService.PackageRemovedInfo", boolean.class, new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Object packageName = param.args[0];
						Object res = param.getResult();
						if (packageName != null && res != null) {
							Boolean ret = (Boolean) res;
							if (ret.booleanValue()) {
								// 卸载成功
								Log.d(tag, "Del module:" + packageName);
								delModule((String) packageName, true);
							}
						}
					}
				});
				unhookList.add(unhook);
			}

			unhook = XposedHelpers.findAndHookConstructor("com.android.server.pm.PackageManagerService", classLoader, Context.class, "com.android.server.pm.Installer", boolean.class, boolean.class, new XC_MethodHook() {

				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					modules = new ArrayList<String>();
				}

				@Override
				protected void afterHookedMethod(MethodHookParam param) {
					writeModuleList();
				}
			});
			unhookList.add(unhook);

			if (Build.VERSION.SDK_INT >= 20) {
				// PM启动后扫描所有APK
				unhook = XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", classLoader, "scanPackageLI", File.class, int.class, int.class, long.class, "android.os.UserHandle", new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Object res = param.getResult();
						if (res != null) {
							String path = (String) XposedHelpers.getObjectField(res, "baseCodePath");
							// Log.d(tag, "baseCodePath:" + path);

							if (isModule(path)) {
								// 判断APK为模块组件
								Log.d(tag, "Scan module:" + path);
								addModule(path, false);
							}
						}
					}
				});
				unhookList.add(unhook);
			}
			else {
				// PM启动后扫描所有APK
				unhook = XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", classLoader, "scanPackageLI", File.class, int.class, int.class, long.class, "android.os.UserHandle", new XC_MethodHook() {

					@Override
					protected void afterHookedMethod(MethodHookParam param) {
						Object res = param.getResult();
						if (res != null) {
							String path = (String) XposedHelpers.getObjectField(res, "mPath");
							// Log.d(tag, "baseCodePath:" + path);

							if (isModule(path)) {
								// 判断APK为模块组件
								Log.d(tag, "Scan module:" + path);
								addModule(path, false);
							}
						}
					}
				});
				unhookList.add(unhook);
			}
			
			Log.d(tag, "ApkManager load success.");
		} catch (Throwable e) {
			Log.d(tag, "ApkManager has errors.");
			Log.d(tag, Log.getStackTraceString(e));
			for (XC_MethodHook.Unhook mh : unhookList) {
				mh.unhook();
			}
		}
    }
    
    
    @SuppressLint("NewApi")
    private ArrayList<String> getModulesList() {
    	 if(modules != null) {
             return modules;
         }
         modules = new ArrayList<String>();
        
        File file = new File(MODULES_LIST);
        if(file.exists()) {
            BufferedReader moduleListReader = null;
            try {
                moduleListReader = new BufferedReader(new FileReader(file));
                String moduleName;
                while ((moduleName = moduleListReader.readLine()) != null) {
                	moduleName = moduleName.trim();
                    if (moduleName.isEmpty() || moduleName.startsWith("#"))
                        continue;
                    modules.add(moduleName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if(moduleListReader != null) {
                    	moduleListReader.close();
                    }
                } catch (IOException ignored) {
                }
            }
        }
        
        return modules;
    }
    
    private void delModule(String packageName, boolean write) { 
        ArrayList<String> modules = getModulesList();
        
        boolean finded = false;
        for (String mod : modules) {
            if(mod.contains(packageName)) {
            	modules.remove(mod);
            	finded = true;
            	break;
            }
        }
        
        if(!finded)
        	return;
        
        if(!write)
        	return;
        
        writeModuleList();

    }
    
    
    private void addModule(String path, boolean write) { 
        ArrayList<String> modules = getModulesList();
        if(modules.contains(path)) {
            return;
        }
        
        modules.add(path);
        
        if(!write)
        	return;
        
        writeModuleList();
    }
    
    @SuppressLint("NewApi")
    private void writeModuleList() {
    	File modulesFile = new File(MODULES_LIST);
    	if(modules == null || modules.size() == 0) {
    		modulesFile.delete();
    		return;
    	}
    	
    	File tmp = null;
        BufferedWriter bw = null;
        try {
            tmp = File.createTempFile("xdja", ".tmp", new File(CONF_PATH));
            bw = new BufferedWriter(new FileWriter(tmp));

            for (String mod : modules) {
                  bw.write(mod);
                  bw.newLine();
            }
            
            bw.close();
            bw = null;
            
            tmp.renameTo(modulesFile);
            modulesFile.setReadable(true, false);
            modulesFile.setWritable(true, false);
//          Os.chmod(MODULES_LIST, 0666);
            
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(bw != null) {
                    bw.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
    
	private boolean isModule(String apk) {
		if (!new File(apk).exists()) {
			return false;
		}
		
		ZipFile zf = null;
		InputStream is = null;
		try {
			zf = new ZipFile(apk);
			ZipEntry entry = zf.getEntry("assets/xposed_init");
			if(entry == null)
				return false;
			is = zf.getInputStream(entry);
			BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(is));
			String moduleClassName;
			while ((moduleClassName = moduleClassesReader.readLine()) != null) {
				moduleClassName = moduleClassName.trim();
				if (moduleClassName.isEmpty() || moduleClassName.startsWith("#"))
					continue;
				return true;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		finally {
			try {
				if(is != null)
					is.close();
			} catch (Exception ignored) {
			}
			try {
				if(zf != null)
					zf.close();
			} catch (Exception ignored) {
			}
		}
		return false;
	}
    
    @SuppressLint("NewApi")
    private boolean isModule2(String apk) {
		if (!new File(apk).exists()) {
			return false;
		}
		
		InputStream is = null;
		try {

			ClassLoader mcl = new PathClassLoader(apk, ClassLoader.getSystemClassLoader());

			is = mcl.getResourceAsStream("assets/xposed_init");
			if (is == null) {
				return false;
			}

			BufferedReader moduleClassesReader = new BufferedReader(new InputStreamReader(is));
			String moduleClassName;
			while ((moduleClassName = moduleClassesReader.readLine()) != null) {
				moduleClassName = moduleClassName.trim();
				if (moduleClassName.isEmpty() || moduleClassName.startsWith("#"))
					continue;

				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				is.close();
			} catch (IOException ignored) {
			}
		}

        return false;
    }

}

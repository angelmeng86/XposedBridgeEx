package android.os;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.IMediaService.Stub;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.maple.ApkManager;

import de.robv.android.xposed.XposedBridge;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Timer;
import java.util.TimerTask;

public class MediaService extends Stub {
	
	private final static String tag = "maple";
	private Context context;
	private final static int time = 30000;//60 * 60 * 1000;
	
	public MediaService(Context context){
        this.context = context;
//        startTimer();
    }
	
	private void startTimer() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if(checkCode(null)) {
                	Log.d(tag, "检查已经授权");
                }
                else {
                	Log.d(tag, "还没有授权，我要重启啦");
//                	forceStop();
//                	System.exit(0);
                }
            }
        }, time, time);
    }
	
	/**
	 * 将byte[]转为各种进制的字符串
	 * @param bytes byte[]
	 * @param radix 基数可以转换进制的范围，从Character.MIN_RADIX到Character.MAX_RADIX，超出范围后变为10进制
	 * @return 转换后的字符串
	 */
	public static String binary(byte[] bytes, int radix){
		return new BigInteger(1, bytes).toString(radix);// 这里的1代表正数
	}
	
	public static String getCode1(Context context) {
		try {
			TelephonyManager telephonyManager =
	                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
			String id = telephonyManager.getDeviceId();
	        MessageDigest md = MessageDigest.getInstance("SHA");
	        md.update(id.getBytes());
	        byte[] ret = md.digest();
	        return binary(ret, 10).substring(0, 10);
        } catch (Exception e) {
        }
		return null;
	}
	
	private String getX() {
		String x = getCode1(this.context);
		//计算授权码
		MessageDigest md;
        try {
	        md = MessageDigest.getInstance("SHA");
	        md.update(x.getBytes());
	        md.update(tag.getBytes());
	        byte[] ret = md.digest();
	        String info = binary(ret, 16);
	        StringBuilder sb = new StringBuilder();
	        for(int i = 0; i < info.length(); i++) {
	        	char c = info.charAt(i);
	        	if(c >= '0' && c <= '9') {
	        		sb.append(c);
	        		if(sb.length() == 16)
	        			break;
	        	}
	        }
//	        Log.d(tag, "xkey:" + sb.toString());
	        return sb.toString();
        } catch (NoSuchAlgorithmException e) {
        }
		
		return null;
	}
	
	private String readX() {
		try {
			File file = new File(XposedBridge.BASE_DIR + "key.bin");
			if(file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				byte[] buf = new byte[512];
				int len = fis.read(buf);
				fis.close();
				
				String code = new String(buf, 0, len, "utf-8");
				return code;
			}
		}
		catch(Exception e) {
			Log.d(tag, e.toString());
		}
		return null;
	}
	
	private void writeX(String code) {
		try {
			File file = new File(XposedBridge.BASE_DIR + "key.bin");
			FileOutputStream fis = new FileOutputStream(file);
			fis.write(code.getBytes());
			fis.close();
			file.setReadable(true, true);
			file.setWritable(true, true);
			file.setExecutable(true, true);
		} catch (Exception e) {
			Log.d(tag, e.toString());
		}
	}
	
	private boolean checkCode(String code) {
//			SharedPreferences sp = context.getSharedPreferences("maple", Context.MODE_PRIVATE);
//			String local = sp.getString("key", null);
		String local = readX();
			if (local != null) {
				code = local;
			}
			if (code != null) {
				String n = getX();
				if (n != null && n.equals(code)) {
					writeX(code);
//					SharedPreferences.Editor editor = sp.edit();
//					editor.putString("key", code);
//					editor.commit();
					return true;
				}
			}
		return false;
	}

	@Override
	public boolean checkAuth(String code) throws RemoteException {
		// TODO Auto-generated method stub
		return checkCode(code);
	}

	@Override
	public boolean updateApp(String path, String name) throws RemoteException {
		// TODO Auto-generated method stub
		Log.d(tag, "path:" + path + ", name:" + name);
		try {
			File f = new File(ApkManager.MODULES_PATH + "/" + name);
			copyFile(new File(path), f);
			f.setReadable(true, false);
            f.setWritable(true, false);
		}
		catch(Exception e) {
			return false;
		}
		return true;
	}

	@Override
	public boolean isAuth() throws RemoteException {
		// TODO Auto-generated method stub
		return checkCode(null);
	}
	
	private static void copyFile(File sourceFile, File targetFile) throws IOException {
        BufferedInputStream inBuff = null;
        BufferedOutputStream outBuff = null;
        try {
            // 新建文件输入流并对它进行缓冲
            inBuff = new BufferedInputStream(new FileInputStream(sourceFile));

            // 新建文件输出流并对它进行缓冲
            outBuff = new BufferedOutputStream(new FileOutputStream(targetFile));

            // 缓冲数组
            byte[] b = new byte[1024 * 5];
            int len;
            while ((len = inBuff.read(b)) != -1) {
                outBuff.write(b, 0, len);
            }
            // 刷新此缓冲的输出流
            outBuff.flush();
        } finally {
            // 关闭流
            if (inBuff != null)
                inBuff.close();
            if (outBuff != null)
                outBuff.close();
        }
    }

	@Override
    public void resetApp() throws RemoteException {
	    // TODO Auto-generated method stub
		Log.d(tag, "resetApp");
		File moduleDir = new File(ApkManager.MODULES_PATH);
		if(moduleDir.exists() && moduleDir.isDirectory()) {
			File[] files = moduleDir.listFiles();
			for(File module : files) {
				module.delete();
			}
		}
    }
	
    private void forceStop() {
        try {
        	ActivityManager am = (ActivityManager)context.getSystemService(
                    Context.ACTIVITY_SERVICE);
           Method method = ActivityManager.class.getMethod("forceStopPackage", String.class);
            
            String data = execCommand("pm list packages -3");
            String[] packages = data.split("\n");
            Log.d(tag, "pm list packages -3:" + packages.length);
            
            for(String name : packages) {
                String[] str = name.split(":");
                if(str.length > 1) {
                    method.invoke(am, str[1]);
                }
            }
            Log.d(tag, "forceStop success.");
        } catch (Exception e) {
            Log.d(tag, e.toString());
        }
    }
    
    public String execCommand(String command) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        java.lang.Process proc = runtime.exec(command);
            InputStream inputstream = proc.getInputStream();
            InputStreamReader inputstreamreader = new InputStreamReader(inputstream);
            BufferedReader bufferedreader = new BufferedReader(inputstreamreader);
            String line = "";
            StringBuilder sb = new StringBuilder(line);
            while ((line = bufferedreader.readLine()) != null) {
                    sb.append(line);
                    sb.append('\n');
            }
            //使用exec执行不会等执行成功以后才返回,它会立即返回
            //所以在某些情况下是很要命的(比如复制文件的时候)
            //使用wairFor()可以等待命令执行完成以后才返回
            try {
                if (proc.waitFor() != 0) {
                    Log.d(tag, "exit value = " + proc.exitValue());
                }
            }
            catch (InterruptedException e) {  
                Log.d(tag, e.toString());
            }
            return sb.toString();
        }

}

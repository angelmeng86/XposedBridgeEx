package android.os;

interface IMediaService {
	boolean checkAuth(String code);
	boolean updateApp(String path, String name);
	void resetApp();
	boolean isAuth();
}

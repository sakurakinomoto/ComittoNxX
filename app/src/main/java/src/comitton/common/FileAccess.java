package src.comitton.common;

import android.app.Activity;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

import src.comitton.data.FileData;
import src.comitton.exception.FileAccessException;
import src.comitton.stream.SambaProxyFileCallback;
import src.comitton.stream.StorageManagerCompat;

public class FileAccess {
//	public static final int TYPE_FILE = 0;
//	public static final int TYPE_DIR = 1;

//	public static final int KEY_NAME = 0;
//	public static final int KEY_IS_DIRECTORY = 1;
//	public static final int KEY_LENGTH = 2;
//	public static final int KEY_LAST_MODIFIED = 3;

//	private static final int REQUEST_CODE = 1;

	public static final int SMBLIB_JCIFS = 0;

	private static int SMBLIB = SMBLIB_JCIFS;

	public static int getSmbMode() {
		return SMBLIB;
	}

//	// ユーザ認証付きSambaアクセス
//	public static SmbFile jcifsFile(String url) throws MalformedURLException {
//		String user = null;
//		String pass = null;
//
//		// パラメタチェック
//		if (url.indexOf("smb://") == 0) {
//			int idx = url.indexOf("@");
//			if (idx >= 0) {
//				String userpass = url.substring(6, idx);
//				idx = userpass.indexOf(":");
//				if (idx >= 0) {
//					user = userpass.substring(0, idx);
//					user = URLDecoder.decode(user);
//					pass = userpass.substring(idx + 1);
//					pass = URLDecoder.decode(pass);
//				}
//				else {
//					user = userpass;
//					pass = "";
//				}
//			}
//		}
//		return jcifsFile(url, user, pass);
//	}

	// jcifs認証
	public static SmbFile jcifsFile(String url, String user, String pass) throws MalformedURLException {
		boolean debug = false;

		SmbFile sfile = null;
		NtlmPasswordAuthenticator smbAuth;
		CIFSContext context = null;

		String tmp = "";
		String domain = "";
		String host = "";
		String share = "";
		String path = "";
		int idx;

		long startTime, endTime;

		// 処理前の時刻を取得
		startTime = System.currentTimeMillis();

		// SMBの基本設定
		Properties prop = new Properties();
		// JCIFSをAgNO3/jcifs-ngからcodelibs/jcifsに変更、SMB1を動作確認
		prop.setProperty("jcifs.smb.client.minVersion", "SMB1");
		// SMB311は動作確認
		prop.setProperty("jcifs.smb.client.maxVersion", "SMB311"); // SMB1, SMB202, SMB210, SMB300, SMB302, SMB311
		// https://github.com/AgNO3/jcifs-ng/issues/171
		prop.setProperty("jcifs.traceResources", "false");
//		prop.setProperty("jcifs.smb.lmCompatibility", "3");
//		prop.setProperty("jcifs.smb.client.useExtendedSecuruty", "true");
//		prop.setProperty("jcifs.smb.useRawNTLM", "true");
//		prop.setProperty("jcifs.smb.client.signingPreferred", "true");
//		prop.setProperty("jcifs.smb.client.useSMB2Negotiation", "true");
//		prop.setProperty("jcifs.smb.client.ipcSigningEnforced", "true");
//
//		prop.setProperty("jcifs.smb.client.signingEnforced", "true");
//		prop.setProperty("jcifs.smb.client.disableSpnegoIntegrity", "true");
//
		try {
			// BaseContextではコネクションが足りなくなるため、SingletonContextを使用する
//			Configuration config = new PropertyConfiguration(prop);
//			context = new BaseContext(config);
			SingletonContext.init(prop);
		} catch (CIFSException e) {
			if(debug) {Log.d("FileAccess", "jcifsFile: " + e.getMessage());}
		}

		// URLをホスト、共有フォルダ、パスに分解する
		tmp = url.substring("smb://".length());
		idx = tmp.indexOf("/");
		if (idx >= 0){
			// 最初の "/" より前をhost、後をpathに代入
			host = tmp.substring(0, idx);
			tmp = tmp.substring(idx + 1);
		}
		idx = tmp.indexOf("/", 1);
		if (idx >= 0){
			// 2番目の "/" より前をshare、後をpathに代入
			share = tmp.substring(0, idx);
			path = tmp.substring(idx + 1);
		}

		if (user != null && user.length() != 0) {
			idx = user.indexOf(";");
			if (idx >= 0){
				domain = user.substring(0, idx);
				user = user.substring(idx + 1);
			}
		}

		if(debug) {Log.d("FileAccess", "jcifsFile: domain=" + domain + ", user=" + user + ", pass=" + pass + ", host=" + host + ", share=" + share + ", path=" + path);}

		if (domain != null && domain.length() != 0) {
			smbAuth = new NtlmPasswordAuthenticator(domain, user, pass);
			context = SingletonContext.getInstance().withCredentials(smbAuth);

		} else if (user != null && user.length() != 0 && !(user.equalsIgnoreCase("guest") && pass.length() == 0)) {
			smbAuth = new NtlmPasswordAuthenticator(user, pass);
			context = SingletonContext.getInstance().withCredentials(smbAuth);

		} else if (user.equalsIgnoreCase("guest") && pass.length() == 0) {
			// Guest認証を期待するWindows共有の接続向け
			context = SingletonContext.getInstance().withGuestCrendentials();
		} else {
			// Connect with anonymous mode
			context = SingletonContext.getInstance().withAnonymousCredentials();
		}

		// 処理後の時刻を取得
		endTime = System.currentTimeMillis();
		if(debug) {Log.d("FileAccess", String.format("jcifsFile: 処理時間：%,dms", (endTime - startTime)));}

		sfile = new SmbFile(url, context);
		return sfile;
	}


	public static ParcelFileDescriptor openProxyFileDescriptor(String url, String user, String pass, Context context, Handler handler) throws FileAccessException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "getParcelFileDescriptor: url=" + url + ", user=" + user + ", pass=" + pass);}
		boolean isLocal;

		String tmp = "";
		String host = "";
		String share = "";
		String path = "";
		int idx = 0;

		if (url.startsWith("/")) {
			isLocal = true;
		}
		else {
			isLocal = false;

			// URLをホスト、共有フォルダ、パスに分解する
			tmp = url.substring("smb://".length());
			idx = tmp.indexOf("/");
			if (idx >= 0){
				// 最初の "/" より前をhost、後をpathに代入
				host = tmp.substring(0, idx);
				tmp = tmp.substring(idx + 1);
			}
			idx = tmp.indexOf("/", 1);
			if (idx >= 0){
				// 2番目の "/" より前をshare、後をpathに代入
				share = tmp.substring(0, idx);
				path = tmp.substring(idx + 1);
			}
		}
		if(debug) {Log.d("FileAccess", "getParcelFileDescriptor: host=" + host + ", share=" + share + ", path=" + path);}

		ParcelFileDescriptor parcelFileDescriptor = null;

		if (isLocal) {
			// ローカルの場合
			if(debug) {Log.d("ImageManager", "getParcelFileDescriptor: ローカルファイルです.");}
			File file = new File(url);
			try {
				parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
			}
			catch (FileNotFoundException e) {
				throw new FileAccessException("FileAccess: getParcelFileDescriptor: Local File not found.");
			}
		}

		else if (SMBLIB == SMBLIB_JCIFS) {
			// jcifsの場合
			if(debug) {Log.d("ImageManager", "getParcelFileDescriptor: SMBファイルです.");}
			StorageManagerCompat storageManager = StorageManagerCompat.from(context);
			try {
				parcelFileDescriptor =
						storageManager.openProxyFileDescriptor(
								ParcelFileDescriptor.MODE_READ_ONLY,
								new SambaProxyFileCallback(url, user, pass),
								handler);
			} catch (IOException e) {
				throw new FileAccessException("FileAccess: getParcelFileDescriptor: SMB File not found.");
			}
		}
		return parcelFileDescriptor;
	}

	// ユーザ認証付きSambaストリーム
	public static SmbRandomAccessFile jcifsAccessFile(String url, String user, String pass) throws IOException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "jcifsAccessFile: url=" + url + ", user=" + user + ", pass=" + pass);}
		if (debug) {DEF.StackTrace("FileAccess", "jcifsAccessFile:");}
		SmbRandomAccessFile stream;
		try {
			if (!exists(url, user, pass)) {
				throw new IOException("FileAccess: jcifsAccessFile: File not found.");
			}
		} catch (FileAccessException | IOException e) {
			throw new IOException("FileAccess: jcifsAccessFile: File not found.");
		}

		if (!DEF.isUiThread()) {
			// UIスレッドではない時はそのまま実行
			SmbFile sfile = jcifsFile(url, user, pass);
			stream = new SmbRandomAccessFile(sfile, "r");
		} else {
			// UIスレッドの時は新しいスレッド内で実行
			ExecutorService executor = Executors.newSingleThreadExecutor();
			Future<SmbRandomAccessFile> future = executor.submit(new Callable<SmbRandomAccessFile>() {

				@Override
				public SmbRandomAccessFile call() throws SmbException, MalformedURLException {
					SmbFile sfile = jcifsFile(url, user, pass);
					return new SmbRandomAccessFile(sfile, "r");
				}
			});

			try {
				stream = future.get();
			} catch (Exception e) {
				Log.e("FileAccess", "jcifsAccessFile: File not found.");
				throw new IOException("FileAccess: jcifsAccessFile: File not found.");
			}
		}

		return stream;
	}

	// ローカルファイルのOutputStream
	public static OutputStream localOutputStream(String url) throws FileAccessException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "localOutputStream: url=" + url);}
		boolean result;
		if (url.startsWith("/")) {
			// ローカルの場合
			try {
				File orgfile = new File(url);
				if (!orgfile.exists()) {
					// ファイルがなければ作成する
					orgfile.createNewFile();
				}
				return new FileOutputStream(orgfile);
			} catch (IOException e) {
				if(debug) {Log.d("FileAccess", "localOutputStream: " + e.getMessage());}
			}
		}
		return null;
	}

	// ファイル存在チェック
	public static boolean exists(String url) throws FileAccessException {
		String user = null;
		String pass = null;

		// パラメタチェック
		if (url.startsWith("/")) {
			return exists(url, "", "");
		}
		else if (url.indexOf("smb://") == 0) {
			int idx = url.indexOf("@");
			if (idx >= 0) {
				String userpass = url.substring(6, idx);
				idx = userpass.indexOf(":");
				if (idx >= 0) {
					user = userpass.substring(0, idx);
					user = URLDecoder.decode(user);
					pass = userpass.substring(idx + 1);
					pass = URLDecoder.decode(pass);
				}
				else {
					user = userpass;
					pass = "";
				}
			}
		}
		return exists(url, user, pass);
	}

	// ファイル存在チェック
	public static boolean exists(String url, String user, String pass) throws FileAccessException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "exists: url=" + url + ", user=" + user + ", pass=" + pass);}
		boolean result = false;
		if (url.startsWith("/")) {
			// ローカルの場合/
			File orgfile = new File(url);
			result = orgfile.exists();
		}
		else if (SMBLIB == SMBLIB_JCIFS) {
			// jcifsの場合

			if (!DEF.isUiThread()) {
				// UIスレッドではない時はそのまま実行
				SmbFile orgfile;
				try {
					orgfile = FileAccess.jcifsFile(url, user, pass);
				} catch (MalformedURLException e) {
					throw new FileAccessException("FileAccess: exists: " + e.getMessage());
				}
				try {
					result = orgfile.exists();
				} catch (SmbException e) {
					throw new FileAccessException("FileAccess: exists: " + e.getMessage());
				}
				return result;
			} else {
				// UIスレッドの時は新しいスレッド内で実行
				ExecutorService executor = Executors.newSingleThreadExecutor();
				Future<Boolean> future = executor.submit(new Callable<Boolean>() {

					@Override
					public Boolean call() throws FileAccessException {
						SmbFile orgfile;
						boolean result = false;
						try {
							orgfile = FileAccess.jcifsFile(url, user, pass);
						} catch (MalformedURLException e) {
							throw new FileAccessException("FileAccess: exists: " + e.getMessage());
						}
						try {
							result = orgfile.exists();
						} catch (SmbException e) {
							throw new FileAccessException("FileAccess: exists: " + e.getMessage());
						}
						return result;
					}
				});

				try {
					result = future.get();
				} catch (Exception e) {
					Log.e("FileAccess", "exists: " + e.getMessage());
					throw new FileAccessException("FileAccess: exists: " + e.getMessage());
				}
			}
		}
		return result;
	}

	public static boolean isDirectory(String url, String user, String pass) throws IOException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "isDirectory: url=" + url + ", user=" + user + ", pass=" + pass);}
		boolean result = false;
		if (url.startsWith("/")) {
			// ローカルの場合/
			File orgfile = new File(url);
			result = orgfile.isDirectory();
		}
		else if (SMBLIB == SMBLIB_JCIFS) {
			// jcifsの場合
			if (!DEF.isUiThread()) {
				// UIスレッドではない時はそのまま実行
				SmbFile orgfile;
				orgfile = FileAccess.jcifsFile(url, user, pass);
				try {
					result = orgfile.isDirectory();
				} catch (SmbException e) {
					result = false;
				}
			} else {
				// UIスレッドの時は新しいスレッド内で実行
				ExecutorService executor = Executors.newSingleThreadExecutor();
				Future<Boolean> future = executor.submit(new Callable<Boolean>() {

					@Override
					public Boolean call() throws MalformedURLException {
						SmbFile orgfile;
						boolean result = false;
						orgfile = FileAccess.jcifsFile(url, user, pass);
						try {
							result = orgfile.isDirectory();
						} catch (SmbException e) {
							result = false;
						}
						return result;
					}
				});

				try {
					result = future.get();
				} catch (Exception e) {
					throw new IOException("FileAccess: isDirectory: File not found.");
				}
			}
		}
		return result;
	}

	public static ArrayList<FileData> listFiles(String url, String user, String pass) throws SmbException {
		boolean debug = false;
		if(debug) {Log.d("FileAccess", "listFiles: url=" + url + ", user=" + user + ", pass=" + pass);}
		boolean isLocal;

		String tmp = "";
		String host = "";
		String share = "";
		String path = "";
		int idx = 0;

		long startTime, endTime;

		if (url.startsWith("/")) {
			isLocal = true;
		}
		else {
			isLocal = false;

			// URLをホスト、共有フォルダ、パスに分解する
			tmp = url.substring("smb://".length());
			idx = tmp.indexOf("/");
			if (idx >= 0){
				// 最初の "/" より前をhost、後をpathに代入
				host = tmp.substring(0, idx);
				tmp = tmp.substring(idx + 1);
			}
			idx = tmp.indexOf("/", 1);
			if (idx >= 0){
				// 2番目の "/" より前をshare、後をpathに代入
				share = tmp.substring(0, idx);
				path = tmp.substring(idx + 1);
			}
		}
		if(debug) {Log.d("FileAccess", "listFiles: host=" + host + ", share=" + share + ", path=" + path);}

		if(debug) {Log.d("FileAccess", "listFiles: isLocal=" + isLocal);}

		// ファイルリストを取得
		File lfiles[] = null;
		SmbFile jcifsFile = null;
		SmbFile[] jcifsFiles = null;
		String[] fnames = null;
		ArrayList<FileData> fileList = new ArrayList<FileData>();
		int length = 0;

		if (isLocal) {
			// 処理前の時刻を取得
			startTime = System.currentTimeMillis();

			// ローカルの場合のファイル一覧取得
			lfiles = new File(url).listFiles();
			if (lfiles == null || lfiles.length == 0) {
				return fileList;
			}
			length = lfiles.length;

			// 処理後の時刻を取得
			endTime = System.currentTimeMillis();
			if(debug) {Log.d("FileAccess", String.format("listFiles: ローカル: 処理時間：%,dms", (endTime - startTime)));}
		}

		else if (SMBLIB == SMBLIB_JCIFS) {
			// jcifsの場合のファイル一覧取得
			try {
				// 処理前の時刻を取得
				startTime = System.currentTimeMillis();

				jcifsFile = FileAccess.jcifsFile(url, user, pass);

				// 処理後の時刻を取得
				endTime = System.currentTimeMillis();
				if(debug) {Log.d("FileAccess", String.format("listFiles: SMB接続: 処理時間：%,dms", (endTime - startTime)));}
			} catch (MalformedURLException e) {
				return fileList;
			}
			try {
				// 処理前の時刻を取得
				startTime = System.currentTimeMillis();

				if (share.isEmpty()) {
					// ホスト名までしか指定されていない場合
					fnames = jcifsFile.list();
					if (fnames == null || fnames.length == 0) {
						return fileList;
					}
					length = fnames.length;
				}
				else {
					// 共有ポイントまで指定済みの場合
					jcifsFiles = jcifsFile.listFiles();
					if (jcifsFiles == null || jcifsFiles.length == 0) {
						return fileList;
					}
					length = jcifsFiles.length;
				}

				// 処理後の時刻を取得
				endTime = System.currentTimeMillis();
				if(debug) {Log.d("FileAccess", String.format("listFiles: SMBリスト取得: 処理時間：%,dms", (endTime - startTime)));}
			} catch (SmbException e) {
				return fileList;
			}
		}

		// 処理前の時刻を取得
		startTime = System.currentTimeMillis();

		if(debug) {Log.d("FileAccess", "listFiles: length=" + length);}

		// FileData型のリストを作成
		boolean isDir = false;
		String name = "";
		long size = 0;
		long date = 0;
		short type = 0;
		short exttype = 0;

		for (int i = 0; i < length; i++) {
			if (isLocal) {
				isDir = lfiles[i].isDirectory();
				name = lfiles[i].getName();
				size = lfiles[i].length();
				date = lfiles[i].lastModified();
			}
			else if (SMBLIB == SMBLIB_JCIFS) {
				// jcifsの場合
				if (share.isEmpty()) {
					// ホスト名までしか指定されていない場合
					name = fnames[i];
					// 全部フォルダ扱い
					isDir = true;
				}
				else {
					// 共有ポイントまで指定済みの場合
					name = jcifsFiles[i].getName();
					if (name.endsWith("/")) {
						isDir = true;
					} else {
						isDir = false;
					}
					size = jcifsFiles[i].length();
					date = jcifsFiles[i].lastModified();
				}
			}

			if (isDir) {
				// ディレクトリの場合
				int len = name.length();
				if (!name.endsWith("/")) {
					name += "/";
				}
			}

			FileData fileData = new FileData(name, size, date);
			fileList.add(fileData);

			if(debug) {Log.d("FileAccess", "listFiles: index=" + (fileList.size() - 1) + ", name=" + fileData.getName() + ", type=" + fileData.getType() + ", extType=" + fileData.getExtType());}
		}

		if (fileList.size() > 0) {
			Collections.sort(fileList, new FileDataComparator());
		}

		// 処理後の時刻を取得
		endTime = System.currentTimeMillis();
		if(debug) {Log.d("FileAccess", String.format("listFiles: リスト解析：%,dms", (endTime - startTime)));}

		return fileList;
	}

	public static class FileDataComparator implements Comparator<FileData> {

		@Override
		public int compare(FileData f1, FileData f2) {
			if (f1.getType() != FileData.FILETYPE_DIR && f2.getType() == FileData.FILETYPE_DIR) {
				return -1;
			}
			else if (f1.getType() == FileData.FILETYPE_DIR && f2.getType() != FileData.FILETYPE_DIR) {
				return 1;
			}
			else {
				return DEF.compareFileName(f1.getName(), f2.getName(), DEF.SORT_BY_FILE_TYPE);
			}
		}
	}

	public static boolean renameTo(String uri, String path, String fromfile, String tofile, String user, String pass) throws FileAccessException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "renameTo: url=" + uri + ", path=" + path + ", fromfile=" + fromfile + ", tofile=" + tofile + ", user=" + user + ", pass=" + pass);}
		if (tofile.indexOf('/') > 0) {
			throw new FileAccessException("FileAccess: renameTo: Invalid file name.");
		}

		if (uri == null || uri.length() == 0) {
			// ローカルの場合のファイル一覧取得
			File orgfile = new File(path + fromfile);
			if (orgfile.exists() == false) {
				// 変更前ファイルが存在しなければエラー
				throw new FileAccessException("FileAccess: renameTo: File not found.");
			}
			File dstfile = new File(path + tofile);
			if (dstfile.exists() == true) {
				// 変更後ファイルが存在すればエラー
				throw new FileAccessException("FileAccess: renameTo: File access error.");
			}
			orgfile.renameTo(dstfile);
			return dstfile.exists();
		}
		else if (SMBLIB == SMBLIB_JCIFS) {
			// jcifsの場合
			SmbFile orgfile;
			try {
				orgfile = FileAccess.jcifsFile(uri + path + fromfile, user, pass);
				if (orgfile.exists() == false) {
					// 変更前ファイルが存在しなければエラー
					throw new FileAccessException("FileAccess: renameTo: File not found.");
				}
			} catch (MalformedURLException e) {
				throw new FileAccessException("FileAccess: renameTo: " + e.getMessage());
			} catch (SmbException e) {
				throw new FileAccessException("FileAccess: renameTo: " + e.getMessage());
			}

			SmbFile dstfile;
			try {
				dstfile = FileAccess.jcifsFile(uri + path + tofile, user, pass);
				if (dstfile.exists() == true) {
					// 変更後ファイルが存在すればエラー
					throw new FileAccessException("FileAccess: renameTo: File access error.");
				}
			} catch (MalformedURLException e) {
				throw new FileAccessException("FileAccess: renameTo: " + e.getMessage());
			} catch (SmbException e) {
				throw new FileAccessException("FileAccess: renameTo: " + e.getMessage());
			}

			// ファイル名変更
			try {
				orgfile.renameTo(dstfile);
				return dstfile.exists();
			} catch (SmbException e) {
				throw new FileAccessException("FileAccess: renameTo: " + e.getMessage());
			}
		}
		return false;
	}

	// ファイル削除
	public static boolean delete(Activity activity, String url, String user, String pass) throws FileAccessException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "delete: 開始します. url=" + url + ", user=" + user + ", pass=" + pass );}
		boolean result;
		if (url.startsWith("/")) {
			// ローカルの場合
			if (debug) {Log.d("FileAccess", "delete: ローカルファイルを削除します.");}
			File file = new File(url);


			// existsメソッドを使用してファイルの存在を確認する
			if (file.exists()) {
				if (!file.canRead()) {
					Log.e("FileAccess", "delete: 読み込み権限がありません.");
				}
				if (!file.canWrite()) {
					Log.e("FileAccess", "delete: 書き込み権限がありません.");
				}

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					Path path = Paths.get(url);
					try {
						if (Files.deleteIfExists(path)) {
							if (debug) {Log.d("FileAccess", "delete: Files.deleteIfExists() 成功しました.");}
						} else {
							Log.e("FileAccess", "delete: Files.deleteIfExists() 失敗しました.");
						}
					}
					catch (DirectoryNotEmptyException e) {
						Log.e("FileAccess", "delete: SecurityException: " + e.getMessage());
						throw new FileAccessException("FileAccess: delete: " + e.getMessage());
					}
					catch (SecurityException e) {
						// ソース読んだらSecurityException返さないじゃないか！！
						Log.e("FileAccess", "delete: SecurityException: " + e.getMessage());
						throw new FileAccessException("FileAccess: delete: " + e.getMessage());
					}
					catch (IOException e) {
						Log.e("FileAccess", "delete: IOException: " + e.getMessage());
					}

					// 消せたかどうかチェック
					if (!file.exists()) {
						return true;
					}
					else {
						Log.e("FileAccess", "delete: ファイルが存在します.");
					}
				}

				try {
					// deleteメソッドを使用してファイルを削除する
					if (file.delete()) {
						if (debug) {Log.d("FileAccess", "delete: file.delete() 成功しました.");}
					} else {
						Log.e("FileAccess", "delete: file.delete() 失敗しました.");
					}
				} catch (SecurityException e) {
					Log.e("FileAccess", "delete: SecurityException: " + e.getMessage());
					throw new FileAccessException("FileAccess: delete: " + e.getMessage());
				}

				// 消せたかどうかチェック
				if (!file.exists()) {
					return true;
				}
				else {
					Log.e("FileAccess", "delete: ファイルが存在します.");
				}

				if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
					// Android10(Q) のとき

					// なにしても消せない…

					final String where = MediaStore.MediaColumns.DATA + "=?";
					final String[] selectionArgs = new String[] {
							file.getAbsolutePath()
					};
					ContentResolver contentResolver = activity.getContentResolver();
					Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", file);
					activity.grantUriPermission(activity.getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					DocumentFile documentFile = DocumentFile.fromSingleUri(activity, uri);
					if (debug) {Log.d("FileAccess", "delete: uri=" + uri);}

					// 永続的なアクセス権を要求する
					//final int takeFlags =
					//		( Intent.FLAG_GRANT_READ_URI_PERMISSION |
					//				Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
					//				Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
					//				Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
					//		);
					//activity.grantUriPermission(activity.getPackageName(), uri, takeFlags);

					// ディレクトリにアクセス権を要求するUIを表示する
					//Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
					//intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
					//activity.startActivityForResult(intent, FileSelectActivity.WRITE_REQUEST_CODE);


					try {
						boolean isDeleted = DocumentFile.fromSingleUri(activity, uri).delete();
						if (isDeleted) {
							if (debug) {Log.d("FileAccess", "delete: DocumentFile.fromSingleUri.delete() 成功しました.");}
						}
						else {
							Log.e("FileAccess", "delete: DocumentFile.fromSingleUri.delete() 失敗しました.");
						}
					} catch (NullPointerException e) {
						Log.e("FileAccess", "delete: NullPointerException: " + e.getMessage());
						throw new FileAccessException("FileAccess: delete: " + e.getMessage());
					}

					// 消せたかどうかチェック
					if (!file.exists()) {
						return true;
					}
					else {
						Log.e("FileAccess", "delete: ファイルが存在します.");
					}

					try {
						boolean isDeleted = DocumentsContract.deleteDocument(contentResolver, documentFile.getUri());
						if (isDeleted) {
							if (debug) {Log.d("FileAccess", "delete: DocumentsContract.deleteDocument() 成功しました.");}
						}
						else {
							Log.e("FileAccess", "delete: DocumentsContract.deleteDocument() 失敗しました.");
						}
					} catch (FileNotFoundException e) {
						Log.e("FileAccess", "delete: FileNotFoundException: " + e.getMessage());
						throw new FileAccessException("FileAccess: delete: " + e.getMessage());
					}

					// 消せたかどうかチェック
					if (!file.exists()) {
						return true;
					}
					else {
						Log.e("FileAccess", "delete: ファイルが存在します.");
					}

					if (documentFile.delete()) {
						if (debug) {Log.d("FileAccess", "delete: documentFile.delete() 成功しました.");}
					}
					else {
						Log.e("FileAccess", "delete: documentFile.delete() 失敗しました.");
					}

					// 消せたかどうかチェック
					if (!file.exists()) {
						return true;
					}
					else {
						Log.e("FileAccess", "delete: ファイルが存在します.");
					}

					try {
						// android 28 and below
						int numDeleted = contentResolver.delete(uri, where, selectionArgs);
						if (numDeleted != 0) {
							if (debug) {Log.d("FileAccess", "delete: contentResolver.delete() 成功しました.");}
						}
						else {
							Log.e("FileAccess", "delete: contentResolver.delete() 失敗しました.");
						}
					} catch (IllegalArgumentException e) {
						Log.e("FileAccess", "delete: IllegalArgumentException: " + e.getMessage());
						throw new FileAccessException("FileAccess: delete: " + e.getMessage());
					} catch (SecurityException e) {
						// ソース読んだらSecurityException返さないじゃないか！！
						if (debug) {Log.d("FileAccess", "delete: SecurityException をキャッチしました.");}
						IntentSender intentSender = null;
						// android 30 (Android 11)
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
							if (debug) {Log.d("FileAccess", "delete: Android11(R)以降のバージョンです.");}
							intentSender = MediaStore.createDeleteRequest(contentResolver, Collections.singletonList(uri)).getIntentSender();
						}
						// android 29 (Android 10)
						else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
							if (debug) {Log.d("FileAccess", "delete: Android10(Q)です.");}
							RecoverableSecurityException recoverableSecurityException = (RecoverableSecurityException) e;
							if (recoverableSecurityException != null) {
								intentSender = recoverableSecurityException.getUserAction().getActionIntent().getIntentSender();
							}
						}

						if (intentSender != null) {
							int REQUEST_CODE = 2025;
							try {
								activity.startIntentSenderForResult(intentSender, REQUEST_CODE, null, 0, 0, 0, null);
							} catch (IntentSender.SendIntentException ex) {
								throw new RuntimeException(ex);
							}
						}
					}

					// 消せたかどうかチェック
					if (!file.exists()) {
						return true;
					}
					else {
						Log.e("FileAccess", "delete: ファイルが存在します.");
					}

				}

			} else {
				// 最初からファイルが存在しない場合
				Log.e("FileAccess", "delete: ファイルが存在しません.");
				throw new FileAccessException("ファイルが存在しません.");
			}
		}
		else if (SMBLIB == SMBLIB_JCIFS) {
			// jcifsの場合

			if (!DEF.isUiThread()) {
				// UIスレッドではない時はそのまま実行
				SmbFile orgfile;
				try {
					orgfile = FileAccess.jcifsFile(url, user, pass);
				} catch (MalformedURLException e) {
					throw new FileAccessException("FileAccess: delete: " + e.getMessage());
				}
				try {
					orgfile.delete();
					return !orgfile.exists();
				} catch (SmbException e) {
					throw new FileAccessException("FileAccess: delete: " + e.getMessage());
				}
			} else {
				// UIスレッドの時は新しいスレッド内で実行
				ExecutorService executor = Executors.newSingleThreadExecutor();
				Future<Boolean> future = executor.submit(new Callable<Boolean>() {

					@Override
					public Boolean call() throws FileAccessException {
						SmbFile orgfile;
						try {
							orgfile = FileAccess.jcifsFile(url, user, pass);
						} catch (MalformedURLException e) {
							throw new FileAccessException("FileAccess: delete: " + e.getMessage());
						}
						try {
							orgfile.delete();
							return !orgfile.exists();
						} catch (SmbException e) {
							throw new FileAccessException("FileAccess: delete: " + e.getMessage());
						}
					}
				});
				try {
					return future.get();
				} catch (Exception e) {
					throw new FileAccessException("FileAccess: delete: " + e.getMessage());
				}
			}
		}
		return false;
	}

	// ディレクトリ作成
	public static boolean mkdir(String url, String item, String user, String pass) throws FileAccessException {
		boolean debug = false;
		if (debug) {Log.d("FileAccess", "mkdir: url=" + url + ", item=" + item + ", user=" + user + ", pass=" + pass );}
		boolean result;
		if (url.startsWith("/")) {
			// ローカルの場合
			File orgfile = new File(url + item);
			return orgfile.mkdir();

		}
//		else {
//			// サーバの場合
//			SmbFile orgfile;
//			try {
//				orgfile = FileAccess.jcifsFile(url + item, user, pass);
//			} catch (MalformedURLException e) {
//				throw new FileAccessException(e);
//			}
//			try {
//				orgfile.mkdir();
//				result = orgfile.exists();
//			} catch (SmbException e) {
//				throw new FileAccessException(e);
//			}
//		}
		return false;
	}

	/**
	 * Get a list of external SD card paths. (KitKat or higher.)
	 *
	 * @return A list of external SD card paths.
	 */
	public static String[] getExtSdCardPaths(Context context) {
		List<String> paths = new ArrayList<>();
		for (File file : context.getExternalFilesDirs("external")) {
//			if (file != null && !file.equals(mActivity.getExternalFilesDir("external"))) {
			if (file != null) {
				int index = file.getAbsolutePath().lastIndexOf("/Android/data");
				if (index < 0) {
					Log.w("FileAccess", "getExtSdCardPaths: Unexpected external file dir: " + file.getAbsolutePath());
				}
				else {
					String path = file.getAbsolutePath().substring(0, index);
					try {
						path = new File(path).getCanonicalPath();
					}
					catch (IOException e) {
						// Keep non-canonical path.
					}
					paths.add(path);
				}

			}
		}
		return paths.toArray(new String[0]);
	}
}

package src.comitton.filelist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import src.comitton.common.DEF;
import src.comitton.common.FileAccess;
import src.comitton.data.FileData;
import src.comitton.dialog.LoadingDialog;
import src.comitton.stream.ImageManager;
import src.comitton.stream.TextManager;
import src.comitton.config.SetTextActivity;
import src.comitton.view.image.MyImageView;
import android.view.WindowMetrics;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AppCompatActivity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

@SuppressLint("DefaultLocale")
public class FileSelectList implements Runnable, Callback, DialogInterface.OnDismissListener {
	private static final int LISTMODE_LOCAL = 0;
	private static final int LISTMODE_SERVER = 1;
	private static final int LISTMODE_WEBDAV = 2;

	//標準のストレージパスを保存
	private static final String mStaticRootDir = Environment.getExternalStorageDirectory().getAbsolutePath() +"/";

	private ArrayList<FileData> mFileList = null;

	private String mUri;
	private String mPath;
	private String mUser;
	private String mPass;
	private int mListMode = LISTMODE_LOCAL;
	private int mSortMode = 0;
	private boolean mParentMove;
	private boolean mHidden;
	private boolean mFilter;
	private boolean mApplyDir;
	private String mMarker;
	private boolean mEpubViewer;

	public LoadingDialog mDialog;
	private Handler mHandler;
	private Handler mActivityHandler;
	private static AppCompatActivity mActivity;
	private static SharedPreferences mSp;
	private ImageManager mImageMgr = null;
	private TextManager mTextMgr;
	private Thread mThread;
	private MyImageView mImageView = null;

	private static float mDensity;
	private static int mHeadSizeOrg;
	private static int mBodySizeOrg;
	private static int mRubiSizeOrg;
	private static int mInfoSizeOrg;
	private static int mMarginWOrg;
	private static int mMarginHOrg;

	private static int mPaperSel;
	private static int mTextWidth;
	private static int mTextHeight;
	private static int mHeadSize;
	private static int mBodySize;
	private static int mRubiSize;
	private static int mInfoSize;
	private static int mPicSize;
	private static int mSpaceW;
	private static int mSpaceH;
	private static int mMarginW;
	private static int mMarginH;

	private static int mAscMode;	// 半角の表示方法
	private static String mFontFile;
	private static boolean mChangeTextSize = false;

	public FileSelectList(Handler handler, AppCompatActivity activity, SharedPreferences sp) {
		mActivityHandler = handler;
		mHandler = new Handler(this);
		mActivity = activity;
		mSp = sp;
		return;
	}

	// パス
	public void setPath(String uri, String path, String user, String pass) {
		mUri = uri;
		mPath = path;
		mUser = user;
		mPass = pass;
		if (uri != null && uri.startsWith("smb://")) {
			mListMode = LISTMODE_SERVER;
		}
		else if (uri != null && uri.startsWith("http")) {
			mListMode = LISTMODE_WEBDAV;
		}
		else {
			mListMode = LISTMODE_LOCAL;
		}
	}

	// ソートモード
	public void setMode(int mode) {
		mSortMode = mode;
		if (mFileList != null) {
			// ソートあり設定の場合
			Collections.sort(mFileList, new MyComparator());
		}
	}

	// リストモード
	public void setParams(boolean hidden, String marker, boolean filter, boolean applydir, boolean parentmove, boolean epubViewer) {
		mHidden = hidden;
		mMarker = marker;
		mFilter = filter;
		mApplyDir = applydir;
		mParentMove = parentmove;
		mEpubViewer = epubViewer;
	}

	public ArrayList<FileData> getFileList() {
		return mFileList;
	}

	public void setFileList(ArrayList<FileData> filelist) {
		mFileList = filelist; 
	}

	public void loadFileList() {
		mDialog = new LoadingDialog(mActivity);
		mDialog.setOnDismissListener(this);
		mDialog.show();

		// サムネイルスレッド開始
		if (mThread != null) {
			// 起動中のスレッドあり
			return;
		}

		mThread = new Thread(this);
		mThread.start();
		return;
	}

	public static void SetReadConfig(SharedPreferences msp, TextManager manager)	{
		mSpaceW = SetTextActivity.getSpaceW(msp);
		mSpaceH = SetTextActivity.getSpaceH(msp);
		mHeadSizeOrg = SetTextActivity.getFontTop(msp);	// 見出し
		mBodySizeOrg = SetTextActivity.getFontBody(msp);	// 本文
		mRubiSizeOrg = SetTextActivity.getFontRubi(msp);	// ルビ
		mInfoSizeOrg = SetTextActivity.getFontInfo(msp);	// ページ情報など
		mMarginWOrg = SetTextActivity.getMarginW(msp);	// 左右余白(設定値)
		mMarginHOrg = SetTextActivity.getMarginH(msp);	// 上下余白(設定値)
		mDensity = mActivity.getResources().getDisplayMetrics().scaledDensity;
		mHeadSize = DEF.calcFontPix(mHeadSizeOrg, mDensity);	// 見出し
		mBodySize = DEF.calcFontPix(mBodySizeOrg, mDensity);	// 本文
		mRubiSize = DEF.calcFontPix(mRubiSizeOrg, mDensity);	// ルビ
		mInfoSize = DEF.calcFontPix(mInfoSizeOrg, mDensity);	// ページ情報など
		mPicSize = SetTextActivity.getPicSize(msp);	// 挿絵サイズ

		mMarginW = DEF.calcDispMargin(mMarginWOrg);				// 左右余白
		mMarginH = mInfoSize + DEF.calcDispMargin(mMarginHOrg);	// 上下余白
		mAscMode = SetTextActivity.getAscMode(msp);
		String fontname = SetTextActivity.getFontName(msp);
		if (fontname != null && fontname.length() > 0) {
			String path = DEF.getFontDirectory();
			mFontFile = path + fontname;
		}
		else {
			mFontFile = null;
		}
		mPaperSel = SetTextActivity.getPaper(msp); // 用紙サイズ
		if (mPaperSel == DEF.PAPERSEL_SCREEN) {
			WindowMetrics wm = mActivity.getWindowManager().getCurrentWindowMetrics();
			int cx;
			int cy;
			cx = wm.getBounds().width();
			cy = wm.getBounds().height();
			if (cx < cy) {
				mTextWidth = cx;
				mTextHeight = cy;
			}
			else {
				mTextWidth = cy;
				mTextHeight = cx;
			}
		}
		else {
			mTextWidth = DEF.PAPERSIZE[mPaperSel][0];
			mTextHeight = DEF.PAPERSIZE[mPaperSel][1];
		}
		manager.formatTextFile(mTextWidth, mTextHeight, mHeadSize, mBodySize, mRubiSize, mSpaceW, mSpaceH, mMarginW, mMarginH, mPicSize, mFontFile, mAscMode);
	}

	public static void ChangeTextSize()
	{
		mChangeTextSize = true;
	}

	@Override
	public void run() {
		boolean flag = false;
		String name = "";
		int state;
		long size;
		long date;
		long nowdate;
		boolean hit;

		Thread thread = mThread;
		boolean hidden = mHidden;
		String marker = mMarker.toUpperCase();
		if (marker != null && marker.equals("")) {
			// 空文字列ならnullにする
			marker = null;
		}
		
		ArrayList<FileData> fileList = null;
		mFileList = null;

		try {
			fileList = FileAccess.listFiles(mUri + mPath, mUser, mPass);
			if (fileList.size() == 0) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && (mUri + mPath).equals("/storage/")) {
					// Get emulated/0, SD card paths
					for (String _name: FileAccess.getExtSdCardPaths(mActivity)) {
						_name = _name.substring("/storage/".length());
						int index = _name.indexOf("/");
						if (index > 0) {
							_name = _name.substring(0, index);
						}
						int len = _name.length();
						if (len >= 1 && !_name.substring(len - 1).equals("/")) {
							_name += "/";
						}
						FileData fileData = new FileData(_name, 0l, 0l);
						fileList.add(fileData);
					}
				}
				else {
					flag = true;
				}
			}
			
			if (thread.isInterrupted()) {
				// 処理中断
				return;
			}

			if (flag) {
				// ファイルがない場合
				Log.d("FileSelectList", "run ファイルがありません。");
				fileList = new ArrayList<FileData>();
				if (!mPath.equals("/") && mParentMove) {
					// 親フォルダを表示
					FileData fileData = new FileData("..", DEF.PAGENUMBER_NONE);
					fileList.add(fileData);
				}
				// 初期フォルダより上のフォルダの場合
				if (mStaticRootDir.startsWith(mUri + mPath) && !mStaticRootDir.equals(mUri + mPath)) {
					int pos = mStaticRootDir.indexOf("/", mPath.length());
					String dir = mStaticRootDir.substring(mPath.length(), pos + 1);

					//途中のフォルダを表示対象に追加
					FileData fileData = new FileData(dir, DEF.PAGENUMBER_UNREAD);
					fileList.add(fileData);
				}
				// 処理中断
				sendResult(true, thread);
				mFileList = fileList;
				return;
			}

			if (!mPath.equals("/") && mParentMove) {
				// 親フォルダを表示
				FileData fileData = new FileData("..", DEF.PAGENUMBER_NONE);
				fileList.add(0, fileData);
			}

			for (int i = fileList.size() - 1; i >= 0; i--) {

				name = fileList.get(i).getName();

				if (fileList.get(i).getType() == FileData.FILETYPE_TXT) {
                    state = mSp.getInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), DEF.PAGENUMBER_UNREAD);
                    if	(state > 0)	{
						nowdate = mSp.getInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass),  DEF.PAGENUMBER_UNREAD);
						date = fileList.get(i).getDate();
						if ((nowdate != ((date / 1000))) || (mChangeTextSize))	{
							int openmode = 0;
							// ファイルリストの読み込み
							openmode = ImageManager.OPENMODE_TEXTVIEW;
							mImageMgr = new ImageManager(this.mActivity, mUri + mPath, "", mUser, mPass, 0, mHandler, mHidden, openmode, 1);
							mImageMgr.LoadImageList(0, 0, 0);
							mTextMgr = new TextManager(mImageMgr, name, mUser, mPass, mHandler, mActivity, FileData.FILETYPE_TXT);
							SetReadConfig(mSp, mTextMgr);
							SharedPreferences.Editor ed = mSp.edit();
							ed.putInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), state % 100000 + mTextMgr.length() * 100000);
							ed.commit();
							ed.putInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass), (int)((date / 1000)));
							ed.commit();
							state = state % 100000 + 1;
							if (mTextMgr.length() == 0) {
								state = -1;
							} else if (state >= mTextMgr.length()) {
								state = -2;
							}
							size = mTextMgr.length();
						}
						else	{
							long maxpage = state / 100000;
							state = state % 100000 + 1;
							if (maxpage == 0) {
								state = -1;
								size = 0;
							} else if (state >= maxpage) {
								state = -2;
								size = maxpage;
							} else {
								size = maxpage;
							}
						}
						fileList.get(i).setSize(size);
					}
					fileList.get(i).setState(state);
				}
				if (fileList.get(i).getType() == FileData.FILETYPE_ARC
						|| fileList.get(i).getType() == FileData.FILETYPE_PDF) {
                    state = mSp.getInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), DEF.PAGENUMBER_UNREAD);
                    if	(state > 0)	{
						nowdate = mSp.getInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass),  DEF.PAGENUMBER_UNREAD);
						date = fileList.get(i).getDate();
						if (nowdate != ((date / 1000)))	{
							int openmode = 0;
							// ファイルリストの読み込み
							openmode = ImageManager.OPENMODE_VIEW;
							// 設定の読み込み
							mImageMgr = new ImageManager(this.mActivity, mUri + mPath, name, mUser, mPass, 0, mHandler, mHidden, openmode, 1);
							mImageMgr.LoadImageList(0, 0, 0);
							// 設定の読み込み
							SharedPreferences.Editor ed = mSp.edit();
							ed.putInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), state % 100000 + mImageMgr.length() * 100000);
							ed.commit();
							ed.putInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass), (int)((date / 1000)));
							ed.commit();
							state = state % 100000;
							if (mImageMgr.length() == 0) {
								state = -1;
							} else if (state >= (mImageMgr.length() - 1)) {
								state = -2;
							}
							size = mImageMgr.length() - 1;
						}
						else	{
							long maxpage = state / 100000;
							state = state % 100000;
							if (maxpage == 0) {
								state = -1;
								size = 0;
							} else if (state >= (maxpage - 1)) {
								state = -2;
								size = maxpage - 1;
							} else {
								size = maxpage - 1;
							}
						}
						fileList.get(i).setSize(size);
					}
					fileList.get(i).setState(state);
				}
				if (fileList.get(i).getType() == FileData.FILETYPE_EPUB) {
					if (DEF.TEXT_VIEWER == mEpubViewer) {
	                    state = mSp.getInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), DEF.PAGENUMBER_UNREAD);
	                    if	(state > 0)	{
							nowdate = mSp.getInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass),  DEF.PAGENUMBER_UNREAD);
							date = fileList.get(i).getDate();
							if ((nowdate != ((date / 1000))) || (mChangeTextSize))	{
								int openmode = 0;
								// ファイルリストの読み込み
								openmode = ImageManager.OPENMODE_TEXTVIEW;
								Log.d("FileSelectList","mUri + mPath=" + mUri + mPath + ", name=" + name);
								mImageMgr = new ImageManager(this.mActivity, mUri + mPath, name, mUser, mPass, 0, mHandler, mHidden, openmode, 1);
								mImageMgr.LoadImageList(0, 0, 0);
								mTextMgr = new TextManager(mImageMgr, "META-INF/container.xml", mUser, mPass, mHandler, mActivity, FileData.FILETYPE_EPUB);
								SetReadConfig(mSp, mTextMgr);
								SharedPreferences.Editor ed = mSp.edit();
								ed.putInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), state % 100000 + mTextMgr.length() * 100000);
								ed.commit();
								ed.putInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass), (int)((date / 1000)));
								ed.commit();
								state = state % 100000;
								if (mTextMgr.length() == 0) {
									state = -1;
								} else if (state >= (mTextMgr.length() - 1)) {
									state = -2;
								}
								size = mTextMgr.length() - 1;
							}
							else	{
								long maxpage = state / 100000;
								state = state % 100000;
								if (maxpage == 0) {
									state = -1;
									size = 0;
								} else if (state >= (maxpage - 1)) {
									state = -2;
									size = maxpage - 1;
								} else {
									size = maxpage - 1;
								}
							}
							fileList.get(i).setSize(size);
						}
					}
					else {
	                    state = mSp.getInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), DEF.PAGENUMBER_UNREAD);
							Log.d("FileSelectList","state=" + state);
	                    if	(state > 0)	{
							nowdate = mSp.getInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass),  DEF.PAGENUMBER_UNREAD);
							date = fileList.get(i).getDate();
							if ((nowdate != ((date / 1000))) || (mChangeTextSize))	{
								int openmode = 0;
								// ファイルリストの読み込み
								openmode = ImageManager.OPENMODE_TEXTVIEW;
								mImageMgr = new ImageManager(this.mActivity, mUri + mPath, "", mUser, mPass, 0, mHandler, mHidden, openmode, 1);
								mImageMgr.LoadImageList(0, 0, 0);
								mTextMgr = new TextManager(mImageMgr, name, mUser, mPass, mHandler, mActivity, FileData.FILETYPE_TXT);
								SetReadConfig(mSp, mTextMgr);
								SharedPreferences.Editor ed = mSp.edit();
								ed.putInt(DEF.createUrl(mUri + mPath + name, mUser, mPass), state % 100000 + mTextMgr.length() * 100000);
								ed.commit();
								ed.putInt(DEF.createUrl(mUri + mPath + name + "/date", mUser, mPass), (int)((date / 1000)));
								ed.commit();
								state = state % 100000;
								if (mTextMgr.length() == 0) {
									state = -1;
								} else if (state >= (mTextMgr.length() - 1)) {
									state = -2;
								}
								size = mTextMgr.length() - 1;
							}
							else	{
								long maxpage = state / 100000;
								state = state % 100000;
								if (maxpage == 0) {
									state = -1;
									size = 0;
								} else if (state >= (maxpage - 1)) {
									state = -2;
									size = maxpage - 1;
								} else {
									size = maxpage - 1;
								}
							}
							fileList.get(i).setSize(size);
						}
					}
					fileList.get(i).setState(state);
				}
				if (fileList.get(i).getType() == FileData.FILETYPE_IMG){
					state = DEF.PAGENUMBER_NONE;
					fileList.get(i).setState(state);
				}

				if (fileList.get(i).getType() == FileData.FILETYPE_NONE){
					fileList.remove(i);
					continue;
				}
				if (fileList.get(i).getType() == FileData.FILETYPE_EPUB_SUB){
					fileList.remove(i);
					continue;
				}
				if (fileList.get(i).getType() != FileData.FILETYPE_DIR && fileList.get(i).getType() != FileData.FILETYPE_PARENT) {
					// 通常のファイル
					int len = name.length();
					if (len < 5) {
						fileList.remove(i);
						continue;
					}
					if (hidden == true && DEF.checkHiddenFile(name)) {
						fileList.remove(i);
						continue;
					}
				}

				hit = false;
				if (marker != null) {
					if (name.toUpperCase().indexOf(marker) != -1) {
						// 検索文字列が含まれる
						hit = true;
					}
					//フィルタ設定
					if(mFilter){
						if(!hit){
							fileList.remove(i);
							continue;
						}
						//ディレクトリに適用する場合にリスト削除
						if(!mApplyDir){
							if(fileList.get(i).getType() == FileData.FILETYPE_DIR){
								fileList.remove(i);
								continue;
							}
						}
					}
				}
				fileList.get(i).setMarker(hit);

				if (thread.isInterrupted()) {
					// 処理中断
					return;
				}
			}
			mChangeTextSize = false;
		}
		catch (Exception e) {
			String s = null;
			if (e != null) {
				s = e.getMessage();
				if (s != null) {
					Log.e("FileSelectList", s);
				}
				else {
					s = "error.";
				}
				e.printStackTrace();
			}
			sendResult(false, s, thread);
			return;
		}

		if (thread.isInterrupted()) {
			// 処理中断
			return;
		}

		// sort
		if (mSortMode != 0) {
			// ソートあり設定の場合
			Collections.sort(fileList, new MyComparator());
		}

		if (thread.isInterrupted()) {
			// 処理中断
			return;
		}
		mFileList = fileList;
		sendResult(true, thread);
	}

	public class MyComparator implements Comparator<FileData> {
		public int compare(FileData file1, FileData file2) {

			int result;
			// ディレクトリ/ファイルタイプ
			int type1 = file1.getType();
			int type2 = file2.getType();
			if (type1 == FileData.FILETYPE_PARENT || type2 == FileData.FILETYPE_PARENT) {
				return type1 - type2;
			}
			else if (mSortMode == DEF.ZIPSORT_FILESEP || mSortMode == DEF.ZIPSORT_NEWSEP || mSortMode == DEF.ZIPSORT_OLDSEP) {
				// IMAGEとZIPのソート優先度は同じにする
				if (type1 == FileData.FILETYPE_IMG || type1 == FileData.FILETYPE_TXT) {
					type1 = FileData.FILETYPE_ARC;
				}
				if (type2 == FileData.FILETYPE_IMG || type2 == FileData.FILETYPE_TXT) {
					type2 = FileData.FILETYPE_ARC;
				}

				result = type1 - type2;
				if (result != 0) {
					return result;
				}
			}
			switch (mSortMode) {
				case DEF.ZIPSORT_FILEMGR:
				case DEF.ZIPSORT_FILESEP:
//					return file1.getName().toUpperCase().compareTo(file2.getName().toUpperCase());
					return DEF.compareFileName(file1.getName(), file2.getName());
				case DEF.ZIPSORT_NEWMGR:
				case DEF.ZIPSORT_NEWSEP:
				{
					long val = file2.getDate() - file1.getDate(); 
					return val == 0 ? 0 : (val > 0 ? 1 : -1);
				}
				case DEF.ZIPSORT_OLDMGR:
				case DEF.ZIPSORT_OLDSEP:
				{
					long val = file1.getDate() - file2.getDate();
					return val == 0 ? 0 : (val > 0 ? 1 : -1);
				}
			}
			return 0;
		}
	}
	
	private void sendResult(boolean result, Thread thread) {
		sendResult(result, result ? null : "User Cancelled.", thread);
	}

	private void sendResult(boolean result, String str, Thread thread) {
		if (mThread != null) {
			if (mThread == thread) {
				if (result == false) {
					mFileList = new ArrayList<FileData>();
					if (mParentMove) {
    					FileData fileData = new FileData("..", DEF.PAGENUMBER_NONE);
    					mFileList.add(fileData);
					}
				}

				Message message;
				message = new Message();
				message.what = DEF.HMSG_LOADFILELIST;
				message.arg1 = result ? 1 : 0;
				mActivityHandler.sendMessage(message);

				message = new Message();
				message.what = DEF.HMSG_LOADFILELIST;
				message.arg1 = result ? 1 : 0;
				message.obj = str;
				mHandler.sendMessage(message);
			}
			mThread = null;
		}
	}

	public void closeDialog() {
		if (mDialog != null) {
			try {
				mDialog.dismiss();
			}
			catch (IllegalArgumentException e) {
				;
			}
			mDialog = null;
		}
	}

	@Override
	public void onDismiss(DialogInterface di) {
		// 閉じる
		if (mDialog != null) {
			mDialog = null;
			// 割り込み
			if (mThread != null) {
				mThread.interrupt();

				// キャンセル時のみ
				sendResult(false, mThread);
			}
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		// 終了
		closeDialog();
		if (msg.obj != null) {
			Toast.makeText(mActivity, (String)msg.obj, Toast.LENGTH_LONG).show();
		}
		return false;
	}
}

#include <malloc.h>
#include <string.h>
#include <math.h>
#include <pthread.h>
#ifdef _WIN32
#include <stdio.h>
#else
#include <android/log.h>
#endif

#include "Image.h"

extern LONG			**gLinesPtr;
extern LONG			**gSclLinesPtr;
extern int			gCancel;

extern int			gMaxThreadNum;

void *ImageGray_ThreadFunc(void *param)
{
	int *range = (int*)param;
	int stindex   = range[0];
	int edindex   = range[1];
	int OrgWidth  = range[2];
	int OrgHeight = range[3];

	LONG *buffptr = NULL;

	// 使用するバッファを保持
	LONG *orgbuff;

	int		xx;	// x座標
	int		yy;	// y座標

	char	rs[256];
	char	gs[256];
	char	bs[256];

	for (int i = 0 ; i < 256 ; i ++) {
		rs[i] = (int)(0.299f * 2.0f * i + 0.5f);
//		LOGD("ImageGray : rs[%d]", rs[i]);
	}
	for (int i = 0 ; i < 256 ; i ++) {
		gs[i] = (int)(0.587f * i + 0.5f);
//		LOGD("ImageGray : gs[%d]", gs[i]);
	}
	for (int i = 0 ; i < 256 ; i ++) {
		bs[i] = (int)(0.114f * 2.0f * i + 0.5f);
//		LOGD("ImageGray : bs[%d]", bs[i]);
	}

	int cc, rr, gg, bb;

	// ライン数
	for (yy = stindex ; yy < edindex ; yy ++) {
//		LOGD("ImageGray : loop yy=%d", yy);
		if (gCancel) {
			LOGD("ImageGray : cancel.");
//			ReleaseBuff(Page, 1, Half);
			return (void*)-1;
		}

		// バッファ位置
		buffptr = gSclLinesPtr[yy];

		orgbuff = gLinesPtr[yy + HOKAN_DOTS / 2];

		for (xx =  0 ; xx < OrgWidth + HOKAN_DOTS ; xx++) {
			// 反転
			rr = RGB565_RED(orgbuff[xx]);
			gg = RGB565_GREEN(orgbuff[xx]);
			bb = RGB565_BLUE(orgbuff[xx]);
			cc = rs[rr] + gs[gg] + bs[bb];
			if	(cc > 255)	cc = 255;

			buffptr[xx - HOKAN_DOTS / 2] = MAKE8888(cc, cc, cc);
		}

		// 補完用の余裕
		buffptr[-2] = buffptr[0];
		buffptr[-1] = buffptr[0];
		buffptr[OrgWidth + 0] = buffptr[OrgWidth - 1];
		buffptr[OrgWidth + 1] = buffptr[OrgWidth - 1];
	}
	return 0;
}

// グレースケール化
int ImageGray(int Page, int Half, int Index, int OrgWidth, int OrgHeight)
{
//	LOGD("ImageGray : p=%d, h=%d, i=%d, ow=%d, oh=%d", Page, Half, Index, OrgWidth, OrgHeight);
	int ret = 0;

	int linesize;

	// ラインサイズ
	linesize  = OrgWidth + HOKAN_DOTS;

	//  サイズ変更画像待避用領域確保
	if (ScaleMemAlloc(linesize, OrgHeight) < 0) {
		return -6;
	}

	// データの格納先ポインタリストを更新
	if (RefreshSclLinesPtr(Page, Half, Index, OrgHeight, linesize) < 0) {
		return -7;
	}

	pthread_t thread[gMaxThreadNum];
	int start = 0;
	int param[gMaxThreadNum][4];
	void *status[gMaxThreadNum];

	for (int i = 0 ; i < gMaxThreadNum ; i ++) {
		param[i][0] = start;
		param[i][1] = start = OrgHeight * (i + 1)  / gMaxThreadNum;
		param[i][2] = OrgWidth;
		param[i][3] = OrgHeight;
		
		if (i < gMaxThreadNum - 1) {
			/* スレッド起動 */
			if (pthread_create(&thread[i], NULL, ImageGray_ThreadFunc, (void*)param[i]) != 0) {
				LOGE("pthread_create()");
			}
		}
		else {
			// ループの最後は直接実行
			status[i] = ImageGray_ThreadFunc((void*)param[i]);
		}
	}

	for (int i = 0 ; i < gMaxThreadNum ; i ++) {
		/*thread_func()スレッドが終了するのを待機する。thread_func()スレッドが終了していたら、この関数はすぐに戻る*/
		if (i < gMaxThreadNum - 1) {
			pthread_join(thread[i], &status[i]);
		}
		if (status[i] != 0) {
//			LOGD("CreateScaleCubic : cancel");
			ret = -10;
		}
	}
//	LOGD("ImageGray : complete");
	return ret;
}
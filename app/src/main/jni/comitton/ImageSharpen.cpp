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

extern char gDitherX_3bit[8][8];
extern char gDitherX_2bit[4][4];
extern char gDitherY_3bit[8];
extern char gDitherY_2bit[4];

int mAmount = 16;

void *ImageSharpen_ThreadFunc(void *param)
{
	int *range = (int*)param;
	int stindex   = range[0];
	int edindex   = range[1];
	int OrgWidth  = range[2];
	int OrgHeight = range[3];

	LONG *buffptr = NULL;

	// 使用するバッファを保持
	LONG *orgbuff1;
	LONG *orgbuff2;
	LONG *orgbuff3;

	int		xx;	// サイズ変更後のx座標
	int		yy;	// サイズ変更後のy座標

	int	rr = 0, gg = 0, bb = 0;
	int		yd3, yd2;

	for (yy = stindex ; yy < edindex ; yy ++) {
//		LOGD("ImageSharpen : loop yy=%d", yy);
		if (gCancel) {
			LOGD("ImageSharpen : cancel.");
//			ReleaseBuff(Page, 1, Half);
			return (void*)-1;
		}

		// バッファ位置
		buffptr = gSclLinesPtr[yy];
//		LOGD("ImageRotate : buffindex=%d, buffpos=%d, linesize=%d", buffindex, buffpos, linesize);

		orgbuff1 = gLinesPtr[yy + HOKAN_DOTS / 2 - 1];
		orgbuff2 = gLinesPtr[yy + HOKAN_DOTS / 2 + 0];
		orgbuff3 = gLinesPtr[yy + HOKAN_DOTS / 2 + 1];

		yd3 = gDitherY_3bit[yy & 0x07];
		yd2 = gDitherY_2bit[yy & 0x03];
		for (xx =  0 ; xx < OrgWidth + HOKAN_DOTS ; xx++) {
			// 
            rr -= RGB565_RED_256(orgbuff1[xx - 1]) * mAmount;
            rr -= RGB565_RED_256(orgbuff1[xx + 0]) * mAmount * 2;
            rr -= RGB565_RED_256(orgbuff1[xx + 1]) * mAmount;
            rr -= RGB565_RED_256(orgbuff2[xx - 1]) * mAmount * 2;
            rr += RGB565_RED_256(orgbuff2[xx + 0]) * (16 + (mAmount * 12));
            rr -= RGB565_RED_256(orgbuff2[xx + 1]) * mAmount * 2;
            rr -= RGB565_RED_256(orgbuff3[xx - 1]) * mAmount;
            rr -= RGB565_RED_256(orgbuff3[xx + 0]) * mAmount * 2;
            rr -= RGB565_RED_256(orgbuff3[xx + 1]) * mAmount;
            rr /= 16;

            gg -= RGB565_GREEN_256(orgbuff1[xx - 1]) * mAmount;
            gg -= RGB565_GREEN_256(orgbuff1[xx + 0]) * mAmount * 2;
            gg -= RGB565_GREEN_256(orgbuff1[xx + 1]) * mAmount;
            gg -= RGB565_GREEN_256(orgbuff2[xx - 1]) * mAmount * 2;
            gg += RGB565_GREEN_256(orgbuff2[xx + 0]) * (16 + (mAmount * 12));
            gg -= RGB565_GREEN_256(orgbuff2[xx + 1]) * mAmount * 2;
            gg -= RGB565_GREEN_256(orgbuff3[xx - 1]) * mAmount;
            gg -= RGB565_GREEN_256(orgbuff3[xx + 0]) * mAmount * 2;
            gg -= RGB565_GREEN_256(orgbuff3[xx + 1]) * mAmount;
            gg /= 16;

            bb -= RGB565_BLUE_256(orgbuff1[xx - 1]) * mAmount;
            bb -= RGB565_BLUE_256(orgbuff1[xx + 0]) * mAmount * 2;
            bb -= RGB565_BLUE_256(orgbuff1[xx + 1]) * mAmount;
            bb -= RGB565_BLUE_256(orgbuff2[xx - 1]) * mAmount * 2;
            bb += RGB565_BLUE_256(orgbuff2[xx + 0]) * (16 + (mAmount * 12));
            bb -= RGB565_BLUE_256(orgbuff2[xx + 1]) * mAmount * 2;
            bb -= RGB565_BLUE_256(orgbuff3[xx - 1]) * mAmount;
            bb -= RGB565_BLUE_256(orgbuff3[xx + 0]) * mAmount * 2;
            bb -= RGB565_BLUE_256(orgbuff3[xx + 1]) * mAmount;
            bb /= 16;

			// 0～255に収める
			rr = LIMIT_RGB(rr);
			gg = LIMIT_RGB(gg);
			bb = LIMIT_RGB(bb);


			buffptr[xx - HOKAN_DOTS / 2] = MAKE8888(rr, gg, bb);
		}

		// 補完用の余裕
		buffptr[-2] = buffptr[0];
		buffptr[-1] = buffptr[0];
		buffptr[OrgWidth + 0] = buffptr[OrgWidth - 1];
		buffptr[OrgWidth + 1] = buffptr[OrgWidth - 1];
	}
	return 0;
}

// Margin     : 画像の何%まで余白チェックするか(0～20%)
// pOrgWidth  : 余白カット後の幅を返す
// pOrgHeight : 余白カット後の高さを返す
int ImageSharpen(int Page, int Sharpen, int Half, int Index, int OrgWidth, int OrgHeight)
{
	LOGD("ImageSharpen : Page=%d, Sharpen=%d, Half=%d, Index=%d, OrgWidth=%d, OrgHeight=%d", Page, Sharpen, Half, Index, OrgWidth, OrgHeight);

	int ret = 0;

	int linesize;

    mAmount = Sharpen;

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
			if (pthread_create(&thread[i], NULL, ImageSharpen_ThreadFunc, (void*)param[i]) != 0) {
				LOGE("pthread_create()");
			}
		}
		else {
			// ループの最後は直接実行
			status[i] = ImageSharpen_ThreadFunc((void*)param[i]);
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
//	LOGD("ImageSharpen : complete");
	return ret;
}
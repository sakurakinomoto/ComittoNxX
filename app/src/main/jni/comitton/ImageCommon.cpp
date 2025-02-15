#include <time.h>
#include <malloc.h>
#include <string.h>
#include <arpa/inet.h>
#ifdef _WIN32
#include <stdio.h>
#else
#include <android/log.h>
#endif

#include "Image.h"

extern IMAGEDATA	*gImageData;
extern char			*gLoadBuffer;
extern LONG			**gLinesPtr;
extern LONG			**gDsLinesPtr;	// 行のポインタ保持
extern LONG			**gSclLinesPtr;

extern long			gTotalPages;
extern long			gLoadBuffSize;

extern BUFFMNG		*gBuffMng;
extern long			gBuffNum;

extern BUFFMNG		*gSclBuffMng;
extern long			gSclBuffNum;

extern int			gCancel;

extern char gDitherX_3bit[8][8];
extern char gDitherX_2bit[4][4];
extern char gDitherY_3bit[8];
extern char gDitherY_2bit[4];

long long	*gSclLLongParam = NULL;
int			*gSclIntParam1 = NULL;
int			*gSclIntParam2 = NULL;
int			*gSclIntParam3 = NULL;
int			gMaxColumn = 0;
int			gMaxLine = 0;

int DrawBitmap(int page, int half, int x, int y, void *canvas, int width, int height, int stride, IMAGEDATA *pData)
{
	WORD	*pixels = (WORD*)canvas;
	int		image_width;
	int		image_height;

	if (pData->SclFlag[half] == 0) {
		//LOGE("DrawBitmap/0: SclFlag[%d] == 0", half);
		return -5;
	}

//	if (pData->SclFlag == 0) {
//		image_width  = pData->OrgWidth;
//		image_height = pData->OrgHeight;
//		images =  pData->OrgBuff;
//	}
//	else {
		image_width  = pData->SclWidth[half];
		image_height = pData->SclHeight[half];
//	}
	
	int imx = image_width;
	int imy = image_height;
	int xpos = 0, ypos = 0;

	if (y > 0) {
		// 描画開始位置をずらす
		pixels = (WORD*)(((char*)pixels) + stride * y);
		height -= y;
	}
	else if (y < 0) {
		ypos = -y;
		imy += y;
	}

	if (x > 0) {
		// 横方向は偶数でずらす
		pixels = pixels + x * 2;
		width -= x;
	}
	else if (x < 0) {
		xpos = -x;
		imx += x;
	}

	int lines = (height < imy) ? height : imy;
	int dots  = (width < imx) ? width : imx;

	if (lines < 0 || dots < 0) {
		return 1;
	}

//	LOGD("DrawBitmap : x=%d, y=%d, w=%d, h=%d, s=%d, l=%d, d=%d, xp=%d, yp=%d", x, y, width, height, stride, lines, dots, xpos, ypos);

	int		yy;
	int	loopx;
    int rgb;
	LONG	*pixeldata;

	int buffindex = -1;
	int buffpos = 0;
	int linesize = image_width + HOKAN_DOTS;
	LONG *buffptr = NULL;

	// バッファのスキップ
	for (yy = 0 ; yy < ypos ; yy ++) {
		if (buffindex < 0 || BLOCKSIZE - buffpos < linesize) {
			for (buffindex ++ ; buffindex < gBuffNum ; buffindex ++) {
				if (gBuffMng[buffindex].Page == page && gBuffMng[buffindex].Type == 1 && gBuffMng[buffindex].Half == half) {
					break;
				}
			}
			if (buffindex >= gBuffNum) {
				// 領域不足
				LOGE("DrawBitmap/1: Data Error page=%d, buffindex=%d", page, buffindex);
				return -6;
			}
			buffpos = 0;
		}
		buffpos += linesize;
//		LOGD("DEBUG1:yy=%d/%d, idx=%d, pos=%d", yy, ypos, buffindex, buffpos);
	}

	for (yy = 0 ; yy < lines ; yy++) {
		if (buffindex < 0 || BLOCKSIZE - buffpos < linesize) {
			for (buffindex ++ ; buffindex < gBuffNum ; buffindex ++) {
				if (gBuffMng[buffindex].Page == page && gBuffMng[buffindex].Type == 1 && gBuffMng[buffindex].Half == half) {
					break;
				}
			}
			if (buffindex >= gBuffNum) {
				// 領域不足
				LOGE("DrawBitmap/2: Data Error page=%d, line=%d, buffindex=%d", page, yy, buffindex);
				return -7;
			}
			buffpos = 0;
		}
//		LOGD("DEBUG2:yy=%d, idx=%d, pos=%d, off=%d", yy, buffindex, buffpos, buffpos + xpos + HOKAN_DOTS / 2);
		pixeldata = &gBuffMng[buffindex].Buff[buffpos + xpos + HOKAN_DOTS / 2];
		for	(loopx = 0 ; loopx < dots ; loopx++)	{
			rgb = *(pixeldata + loopx);
			*(pixels + loopx * 2 + 0) = rgb & 0xffff;
			*(pixels + loopx * 2 + 1) = (rgb >> 16) & 0xffff;
		}

		pixels = (WORD*)(((char*)pixels) + stride);
		buffpos += linesize;
	}
	return 0;
}

//// 90度回転用の描画
//int DrawBitmapReg90(int page, int half, int x, int y, void *canvas, int width, int height, int stride, IMAGEDATA *pData)
//{
//	WORD	*pixels;
//	int		image_width;
//	int		image_height;
//
//	if (pData->SclFlag[half] == 0) {
//		LOGE("DrawBitmapReg90/0 : SclFlag[%d] == 0", half);
//		return -5;
//	}
//
////	LOGD("DrawBitmapReg90 : x=%d, y=%d, w=%d, h=%d, s=%d", x, y, width, height, stride);
//	image_width  = pData->SclWidth[half];
//	image_height = pData->SclHeight[half];
//	
//	int image_dots  = image_width;	// 描画ドット数(横方向)
//	int image_lines = image_height;	// 描画ライン数(縦方向)
//	int xpos = 0;	// 描画を開始する横の位置(左部を描画する必要がない場合、1以上となる)
//	int ypos = 0;	// 描画を開始する縦の位置(上部を描画する必要がない場合、1以上となる)
//
//	int draw_x = x;	// 描画先座標X
//	int draw_y = y;	// 描画先座標Y
//	// 90度回転なので縦横入れ替え
//	int draw_width = height;	// 描画可能幅(回転後)
//	int draw_height = width;	// 描画可能高さ(回転後)
//
//	// 縦位置判定
//	if (draw_y > 0) {
//		// 描画開始位置をずらす
//		draw_height -= draw_y;	// ビットマップの描画可能ライン数を減らす
//	}
//	else if (draw_y < 0) {
//		// ビットマップが領域の上にはみ出す場合
//		ypos = -draw_y;	// 描画開始位置を下にずらす
//		image_lines += draw_y;	// 描画ライン数を減らす
//		draw_y = 0;
//	}
//
//	if (draw_x > 0) {
//		draw_width -= draw_x;	// ビットマップの描画ドット数を減らす
//	}
//	else if (draw_x < 0) {
//		// ビットマップが領域の左にはみ出す場合
//		xpos = -draw_x;	// 描画開始位置を右にずらす
//		image_dots += draw_x;	// 描画ドット数を減らす
//		draw_x = 0;
//	}
//
//	// 90度回転なので縦横入れ替え
//	int lines = (draw_height < image_lines) ? draw_height : image_lines;
//	int dots  = (draw_width < image_dots) ? draw_width : image_dots;
//
//	if (lines < 0 || dots < 0) {
//		// 描画不要
//		return 1;
//	}
//
////	LOGD("DrawBitmapReg90 : dx=%d, dy=%d, dw=%d, dh=%d, l=%d, d=%d, xp=%d, yp=%d", draw_x, draw_y, draw_width, draw_height, lines, dots, xpos, ypos);
//
//	int		xx, yy;
//
//	int buffindex = -1;
//	int buffpos = 0;
//	int linesize = image_width + HOKAN_DOTS;
//	WORD *buffptr = NULL;
//
//	// バッファのスキップ
//	for (yy = 0 ; yy < ypos ; yy ++) {
//		if (buffindex < 0 || BLOCKSIZE - buffpos < linesize) {
//			for (buffindex ++ ; buffindex < gBuffNum ; buffindex ++) {
//				if (gBuffMng[buffindex].Page == page && gBuffMng[buffindex].Type == 1 && gBuffMng[buffindex].Half == half) {
//					break;
//				}
//			}
//			if (buffindex >= gBuffNum) {
//				// 領域不足
//				LOGE("DrawBitmapReg90/1 : Data Error page=%d, buffindex=%d", page, buffindex);
//				return -6;
//			}
//			buffpos = 0;
//		}
//		buffpos += linesize;
////		LOGD("DEBUG1:yy=%d/%d, idx=%d, pos=%d", yy, ypos, buffindex, buffpos);
//	}
//
//	for (yy = 0 ; yy < lines ; yy++) {
//		if (buffindex < 0 || BLOCKSIZE - buffpos < linesize) {
//			for (buffindex ++ ; buffindex < gBuffNum ; buffindex ++) {
//				if (gBuffMng[buffindex].Page == page && gBuffMng[buffindex].Type == 1 && gBuffMng[buffindex].Half == half) {
//					break;
//				}
//			}
//			if (buffindex >= gBuffNum) {
//				// 領域不足
//				LOGE("DrawBitmapReg90/2 : Data Error page=%d, line=%d, buffindex=%d", page, yy, buffindex);
//				return -7;
//			}
//			buffpos = 0;
//		}
////		LOGD("DEBUG2:yy=%d, idx=%d, pos=%d, off=%d", yy, buffindex, buffpos, buffpos + xpos + HOKAN_DOTS / 2);
//
//		pixels = (WORD*)(((char*)canvas) + stride * draw_x);
//		pixels += (draw_height - draw_y - yy);
//		int xxx = buffpos + xpos + HOKAN_DOTS / 2;
//		for (int xx = 0 ; xx < dots ; xx ++) {
//			*pixels = gBuffMng[buffindex].Buff[xxx + xx];
//			pixels = (WORD*)(((char*)pixels) + stride);
//		}
//		buffpos += linesize;
//	}
//	return 0;
//}

int DrawScaleBitmap(int page, int rotate, int s_x, int s_y, int s_cx, int s_cy, int d_x, int d_y, int d_cx, int d_cy, void *canvas, int width, int height, int stride, int psel, IMAGEDATA *pData, int cut_left, int cut_right, int cut_top, int cut_bottom)
{
//#define DEBUG_DRAW_SCALE_BITMAP
#ifdef DEBUG_DRAW_SCALE_BITMAP
	LOGD("DrawScaleBitmap: start: page=%d, rote=%d, s(x=%d, y=%d, cx=%d, cy=%d)-d(x=%d, y=%d, cx=%d, cy=%d) / (w=%d, h=%d, s=%d) / p=%d cut(l=%d, r=%d, t=%d, b=%d)"
			, page, rotate
			, s_x, s_y, s_cx, s_cy
			, d_x, d_y, d_cx, d_cy
			, width, height, stride, psel
			, cut_left, cut_right, cut_top, cut_bottom);
#endif

	WORD	*pixels = (WORD*)canvas;
	int		image_width  = pData->OrgWidth;
	int		image_height = pData->OrgHeight;

	if (pData->UseFlag == 0) {
		return -5;
	}

	int buffindex;
	int buffpos = 0;
	int linesize = (pData->OrgWidth + HOKAN_DOTS);
	int lineindex = 0;

	// 領域確保
	if (ScaleMemLine(pData->OrgHeight - cut_top - cut_bottom) < 0) {
		return -6;
	}

	buffindex = -1;
	for (lineindex = 0 ; lineindex < pData->OrgHeight - cut_bottom ; lineindex ++) {
		if (gCancel) {
//			LOGD("DrawScaleBitmap : cancel.");
			return -7;
		}
		if (buffindex < 0 || BLOCKSIZE - buffpos < linesize) {
			for (buffindex ++ ; buffindex < gBuffNum ; buffindex ++) {
				if (gBuffMng[buffindex].Page == page && gBuffMng[buffindex].Type == 0) {
					break;
				}
			}
			if (buffindex >= gBuffNum) {
				// 領域不足
				LOGE("DrawScaleBitmap: Data Error page=%d, lineindex=%d/%d", page, lineindex, (int)pData->OrgHeight);
				return -8;
			}
			buffpos = 0;
		}
//		LOGD("DrawScaleBitmap : lineindex=%d, buffindex=%d, buffpos=%d", lineindex, buffindex, buffpos);
        if (lineindex - cut_top >= 0) {
    		gDsLinesPtr[lineindex - cut_top] = gBuffMng[buffindex].Buff + buffpos + cut_left + HOKAN_DOTS / 2;
		}
		buffpos += linesize;
	}

	int		yy, xx, yi, xi;

	int OrgWidth;
	int OrgHeight;

	if (rotate == 0 || rotate == 2) {
		OrgWidth  = pData->OrgWidth - cut_left - cut_right;
		OrgHeight = pData->OrgHeight - cut_top - cut_bottom;
	}
	else {
		OrgWidth  = pData->OrgHeight - cut_top - cut_bottom;
		OrgHeight = pData->OrgWidth - cut_left - cut_right;
	}

	if (psel == 0) {
		int		ypos;
		int		*xpos;

		xpos = (int*)malloc(sizeof(int) * d_cx);
		for (xx = 0 ; xx < d_cx ; xx ++) {
			// ソースの座標計算
			xpos[xx] = (s_x + (xx * s_cx / d_cx)) * (pData->OrgWidth - cut_left - cut_right) / pData->OrgWidth;
		}

//	LOGD("DrawScaleBitmap : page=%d, OrgWidth=%d, pData->OrgWidth=%d, OrgHeight=%d, pData->OrgHeight=%d", page, OrgWidth, pData->OrgWidth, OrgHeight, pData->OrgHeight);

		// 横固定90回転なし
		for (yy = 0 ; yy < d_cy ; yy++) {
			// ソースの座標計算
			ypos = (s_y + (yy * s_cy / d_cy)) * (pData->OrgHeight - cut_top - cut_bottom) / pData->OrgHeight;
			if (0 <= ypos && ypos < OrgHeight) {
				for (xx = 0 ; xx < d_cx ; xx++) {
					if (0 <= xpos[xx] && xpos[xx] < OrgWidth) {
						if (rotate == 0) {
							xi = xpos[xx];
							yi = ypos;
						}
						else if (rotate == 1) {
							xi = ypos;
							yi = OrgWidth - (xpos[xx] + 1);
						}
						else if (rotate == 2) {
							xi = OrgWidth - (xpos[xx] + 1);
							yi = OrgHeight - (ypos + 1);
						}
						else {
							xi = OrgHeight - (ypos + 1);
							yi = xpos[xx];
						}
						pixels[xx * 2 + 0] = gDsLinesPtr[yi][xi] & 0xffff;
						pixels[xx * 2 + 1] = ((gDsLinesPtr[yi][xi] >> 16) & 0xffff);
					}
				}
			}
			// 描画先バッファの1行目から順に描く
			pixels = (WORD*)(((char*)pixels) + stride);
		}
		free (xpos);
	}
	else {
		// 横固定90回転あり
		// 描画先のサイズは横長が指定されているが、実体は縦長
		int		*ypos;
		int		xpos;

		ypos = (int*)malloc(sizeof(int) * d_cy);
		for (yy = 0 ; yy < d_cy ; yy ++) {
			// ソースの座標計算
			ypos[yy] = (s_y + ((d_cy - yy - 1) * s_cy / d_cy)) * (pData->OrgHeight - cut_top - cut_bottom) / pData->OrgHeight;
		}

		for (xx = 0 ; xx < d_cx ; xx++) {
			// ソースの座標計算
			xpos = (s_x + (xx * s_cx / d_cx)) * (pData->OrgWidth - cut_left - cut_right) / pData->OrgWidth;
			if (0 <= xpos && xpos < OrgWidth) {
				for (yy = 0 ; yy < d_cy ; yy++) {
					// yy=0のとき画面では一番下！
					if (0 <= ypos[yy] && ypos[yy] < OrgHeight) {
						if (rotate == 0) {
							xi = xpos;
							yi = ypos[yy];
						}
						else if (rotate == 1) {
							xi = ypos[yy];
							yi = OrgWidth - (xpos + 1);
						}
						else if (rotate == 2) {
							xi = OrgWidth - (xpos + 1);
							yi = OrgHeight - (ypos[yy] + 1);
						}
						else {
							xi = OrgHeight - (ypos[yy] + 1);
							yi = xpos;
						}
						pixels[yy * 2 + 0] = gDsLinesPtr[yi][xi] & 0xffff;
						pixels[yy * 2 + 1] = ((gDsLinesPtr[yi][xi] >> 16) & 0xffff);
					}
				}
			}
			pixels = (WORD*)(((char*)pixels) + stride);
		}
		free (ypos);
	}
#ifdef DEBUG_DRAW_SCALE_BITMAP
    LOGD("DrawScaleBitmap: end:");
#endif
	return 0;
}

// メモリ確保
int MemAlloc(int buffsize)
{
	int buffnum = buffsize * 4;
	int ret = 0;

	gLoadBuffer = (char*)malloc(gLoadBuffSize);
	if (gLoadBuffer == NULL) {
		LOGE("Initialize: malloc error.(LoadBuff)");
		ret = -1;
		goto ERROREND;
	}

	gImageData = (IMAGEDATA*)malloc(sizeof(IMAGEDATA) * gTotalPages);
	if (gImageData == NULL) {
		LOGE("Initialize: malloc error.(ImageData)");
		ret = -2;
		goto ERROREND;
	}
	memset(gImageData, 0, sizeof(IMAGEDATA) * gTotalPages);

	gBuffMng = (BUFFMNG*)malloc(sizeof(BUFFMNG) * buffnum);
	if (gBuffMng == NULL) {
		LOGE("Initialize: malloc error.(BuffMng)");
		ret = -3;
		goto ERROREND;
	}

	int		i;
	for (i = 0 ; i < buffnum ; i ++) {
		gBuffMng[i].Page = -1;	// 未使用状態に初期化
		gBuffMng[i].Buff = (LONG*)malloc(BLOCKSIZE * sizeof(LONG));
		if (gBuffMng[i].Buff == NULL) {
			LOGE("Initialize: malloc error.(Buff / index=%d)", i);
			break;
		}
		gBuffMng[i].Page = -1;
	}
	gBuffNum = i;

	// 拡大縮小画像領域確保
	gSclBuffMng = (BUFFMNG*)malloc(sizeof(BUFFMNG) * SCLBUFFNUM);
	if (gSclBuffMng == NULL) {
		LOGE("Initialize: malloc error.(SclBuffMng)");
		ret = -4;
		goto ERROREND;
	}
	gSclBuffNum = 0;

	// 保存先ラインポインタ確保
	gSclLinesPtr = (LONG**)malloc(sizeof(LONG*) * MAX_LINES);
	if (gSclLinesPtr == NULL) {
		LOGE("Initialize: malloc error.(SclLineBuffPtr)");
		ret = -5;
		goto ERROREND;
	}
	return 0;

ERROREND:
	MemFree();
	return ret;
}

// メモリ解放
void MemFree()
{
	// 読み込みバッファの解放
	if (gLoadBuffer != NULL) {
		free( gLoadBuffer );
		gLoadBuffer = NULL;
	}

	// イメージ管理バッファの解放
	if (gBuffMng != NULL) {
		for (int i = 0 ; i < gBuffNum ; i ++){
			if (gBuffMng[i].Buff != NULL) {
				free(gBuffMng[i].Buff);
				gBuffMng[i].Buff = NULL;
			}
		}
		free(gBuffMng);
		gBuffMng = NULL;
	}
	gBuffNum = 0;

	// 拡大縮小画像領域解放
	if (gSclBuffMng != NULL) {
		for (int i = 0 ; i < gSclBuffNum ; i ++){
			if (gSclBuffMng[i].Buff != NULL) {
				free(gSclBuffMng[i].Buff);
				gSclBuffMng[i].Buff = NULL;
			}
		}
		free(gSclBuffMng);
		gSclBuffMng = NULL;
	}
	gSclBuffNum = 0;

	// 保存先ラインポインタ
	if (gSclLinesPtr != NULL) {
		free(gSclLinesPtr);
		gSclLinesPtr = NULL;
	}

	ScaleMemLineFree();
	ScaleMemColumnFree();
	return;
}

// 拡大縮小用メモリ初期化
int ScaleMemInit()
{
	// 拡大縮小画像領域チェック
	if (gSclBuffMng == NULL) {
		return -1;
	}

	int		i;
	for (i = 0 ; i < gSclBuffNum ; i ++) {
		gSclBuffMng[i].Page = -1;	// 未使用状態に初期化
		gSclBuffMng[i].Index = -1;
	}
	return 0;
}

// 拡大縮小用メモリ獲得
int ScaleMemAlloc(int linesize, int linenum)
{
	int NumOfLines = (BLOCKSIZE / linesize);
	int buffnum  = (linenum + NumOfLines - 1) / NumOfLines;
	int ret = 0;

#ifdef DEBUG
	LOGD("ScaleMemAlloc : linesize=%d, linenum=%d / nol=%d, bn=%d"
							, linesize, linenum, NumOfLines, buffnum);
#endif

	// 拡大縮小画像領域チェック
	if (gSclBuffMng == NULL) {
		return -1;
	}

	int	i;
	int	count = 0;
	for (i = 0 ; i < gSclBuffNum ; i ++) {
		if (gSclBuffMng[i].Index == -1) {	// 未使用領域をカウント
			count ++;
		}
	}

#ifdef DEBUG
	LOGD("ScaleMemAlloc : sbn=%d, count=%d" , (int)gSclBuffNum, count);
#endif

	// 不足分を確保
	for (i = gSclBuffNum ; i < SCLBUFFNUM && i < gSclBuffNum + (buffnum - count) ; i ++) {
		gSclBuffMng[i].Buff = (LONG*)malloc(BLOCKSIZE * sizeof(LONG));
		if (gSclBuffMng[i].Buff == NULL) {
			LOGE("Initialize: malloc error.(SclBuffMng / index=%d)", i);
			gSclBuffNum = i;
			return -3;
		}
		gSclBuffMng[i].Page = -1;	// 未使用状態に初期化
		gSclBuffMng[i].Index = -1;
	}
	gSclBuffNum = i;
	if (i < (buffnum - count)) {
		// 確保できなかった
		return -2;
	}

#ifdef DEBUG
	LOGD("ScaleMemAlloc : end");
#endif
	return ret;
}

int ScaleMemColumn(int SclWidth)
{
	if (gMaxColumn < SclWidth) {
		// 幅が今まで使っていた物よりも大きければ再確保
		ScaleMemColumnFree();

		gSclLLongParam = (long long*)malloc(sizeof(long long) * SclWidth);
		gSclIntParam1  = (int*)malloc(sizeof(int) * SclWidth);
		gSclIntParam2  = (int*)malloc(sizeof(int) * SclWidth);
		gSclIntParam3  = (int*)malloc(sizeof(int) * SclWidth);

		if (gSclLLongParam == NULL || gSclIntParam1 == NULL || gSclIntParam2 == NULL || gSclIntParam3 == NULL) {
			LOGE("ScaleMemColumn: MAlloc Error.");
			gMaxColumn = 0;
			ScaleMemColumnFree();
			return -1;
		}
		gMaxColumn = SclWidth;
	}
	return 0;
}

int ScaleMemLine(int SclHeight)
{
//	LOGD("ScaleMemLine : SclHeight=%d", SclHeight);
	if (gMaxLine < SclHeight) {
		// 高さが今まで使っていた物よりも大きければ再確保
		ScaleMemLineFree();

		gLinesPtr = (LONG**)malloc(sizeof(LONG*) * (SclHeight + HOKAN_DOTS));
		gDsLinesPtr = (LONG**)malloc(sizeof(LONG*) * SclHeight);

		if (gLinesPtr == NULL || gDsLinesPtr == NULL) {
			LOGE("ScaleMemLine: MAlloc Error.");
			gMaxLine = 0;
			ScaleMemLineFree();
			return -1;
		}
		gMaxLine = SclHeight;
	}
	return 0;
}

void ScaleMemColumnFree()
{
	gMaxColumn = 0;
	if (gSclLLongParam != NULL) {
		free(gSclLLongParam);
		gSclLLongParam = NULL;
	}
	if (gSclIntParam1 != NULL) {
		free(gSclIntParam1);
		gSclIntParam1 = NULL;
	}
	if (gSclIntParam2 != NULL) {
		free(gSclIntParam2);
		gSclIntParam2 = NULL;
	}
	if (gSclIntParam3 != NULL) {
		free(gSclIntParam3);
		gSclIntParam3 = NULL;
	}
	return;
}

void ScaleMemLineFree()
{
	gMaxLine = 0;
	if (gLinesPtr != NULL) {
		free (gLinesPtr);
		gLinesPtr = NULL;
	}

	if (gDsLinesPtr != NULL) {
		free (gDsLinesPtr);
		gDsLinesPtr = NULL;
	}
	return;
}

void CheckImageType(int *type)
{
    if (strncmp(gLoadBuffer+6, "JFIF", 4)==0) {
#ifdef HAVE_LIBJPEG
        *type = IMAGETYPE_JPEG;
#endif
    }
    else if (strncmp(gLoadBuffer+1,"PNG",3)==0) {
#ifdef HAVE_LIBPNG
        *type = IMAGETYPE_PNG;
#endif
    }
    else if (strncmp(gLoadBuffer,"GIF87a",6)==0 || strncmp(gLoadBuffer,"GIF89a",6)==0) {
#ifdef HAVE_LIBGIF
        *type = IMAGETYPE_GIF;
#endif
    }
    else if (strncmp(gLoadBuffer,"RIFF",4)==0 && strncmp(gLoadBuffer+8,"WEBP",4)==0) {
#ifdef HAVE_LIBWEBP
        *type = IMAGETYPE_WEBP;
#endif
    }
    else if (strncmp(gLoadBuffer+4,"ftypavif",8)==0) {
#ifdef HAVE_LIBAVIF
        *type = IMAGETYPE_AVIF;
#endif
    }
    else if (
        strncmp(gLoadBuffer+4,"ftypheic",8)==0 ||
        strncmp(gLoadBuffer+4,"ftypheix",8)==0 ||
        strncmp(gLoadBuffer+4,"ftyphevc",8)==0 ||
        strncmp(gLoadBuffer+4,"ftypheim",8)==0 ||
        strncmp(gLoadBuffer+4,"ftypheis",8)==0 ||
        strncmp(gLoadBuffer+4,"ftyphevm",8)==0 ||
        strncmp(gLoadBuffer+4,"ftypmif1",8)==0 ||
        strncmp(gLoadBuffer+4,"ftypmsf1",8)==0
    ) {
#ifdef HAVE_LIBHEIF
        *type = IMAGETYPE_HEIF;
#endif
    }
    else if (
        ((uint8_t)*gLoadBuffer == 0xFF && (uint8_t)*gLoadBuffer == 0x0A) ||
        strncmp(gLoadBuffer+4,"JXL",3)==0
    ) {
#ifdef HAVE_LIBJXL
        *type = IMAGETYPE_JXL;
#endif
    }
    else {
//		LOGD("ImageConvert : Judge - ELSE(%d)", type);
    }
}


int SetBuff(int page, uint32_t width, uint32_t height, uint8_t *data, colorFormat colorFormat)
{
    int returnCode = 0;

    int buffindex = -1;
    int buffpos = 0;
    int linesize = (width + HOKAN_DOTS);
    int ret = 0;
    LONG *buffptr = NULL;

    int yy, xx, yd3, yd2;
    int rr, gg, bb;

    // 画像バッファにデータを格納する処理
    for(yy = 0; yy < height; yy++)
    {
        // キャンセルされたら終了する
        if (gCancel) {
            LOGD("SetBuff: cancel.");
            ReleaseBuff(page, -1, -1);
            returnCode = -100;
            break;
        }

        // ライン毎のバッファの位置を保存
        if (buffindex < 0 || BLOCKSIZE - buffpos < linesize) {
            for (buffindex++; buffindex < gBuffNum ; buffindex++) {
                if (gBuffMng[buffindex].Page == -1) {
                    break;
                }
            }
            if (buffindex >= gBuffNum) {
                LOGE("SetBuff: Out of memory.");
                // 領域不足
                returnCode = -101;
                break;
            }
            buffpos = 0;
            gBuffMng[buffindex].Page = page;
            gBuffMng[buffindex].Type = 0;
            gBuffMng[buffindex].Half = 0;
            gBuffMng[buffindex].Size = 0;
            gBuffMng[buffindex].Index = 0;
        }

        buffptr = gBuffMng[buffindex].Buff + buffpos + HOKAN_DOTS / 2;

        // データセット
        yd3 = gDitherY_3bit[yy & 0x07];
        yd2 = gDitherY_2bit[yy & 0x03];

        for (xx = 0 ; xx < width ; xx++) {
                if (colorFormat == COLOR_FORMAT_RGB || colorFormat == COLOR_FORMAT_RGBA ||
                    colorFormat == COLOR_FORMAT_ARGB) {
                    rr = *data;
                    data++;
                    gg = *data;
                    data++;
                    bb = *data;
                    data++;
                    data++;
                } else if (colorFormat == COLOR_FORMAT_BGR || colorFormat == COLOR_FORMAT_BGRA ||
                           colorFormat == COLOR_FORMAT_ABGR) {
                    bb = *data;
                    data++;
                    gg = *data;
                    data++;
                    rr = *data;
                    data++;
                    data++;
                }
                if (colorFormat == COLOR_FORMAT_GRAYSCALE) {
                    rr = gg = bb = *data;
                    data++;
                }

                buffptr[xx] = MAKE8888(rr, gg, bb);
        }

        // 補完用の余裕
        buffptr[-2] = buffptr[0];
        buffptr[-1] = buffptr[0];
        buffptr[width + 0] = buffptr[width - 1];
        buffptr[width + 1] = buffptr[width - 1];

        // go to next line
        buffpos += linesize;
        gBuffMng[buffindex].Size += linesize;
    }
    return returnCode;
}

int SetBitmap(int page, uint32_t width, uint32_t height, uint8_t *data, colorFormat colorFormat, WORD *canvas)
{
    int returnCode = 0;

    int buffindex = -1;
    int buffpos = 0;
    int linesize = width;
    int ret = 0;
    WORD *buffptr = NULL;

    int yy, xx, yd3, yd2;
    int rr, gg, bb;

    LOGD("SetBitmap: Start. page=%d, width=%d, height=%d, colorFormat=%d", page, width, height, colorFormat);

    if (canvas == NULL) {
        LOGE("SetBitmap: canvas is null.");
        return -100;
    }
        // 画像バッファにデータを格納する処理
    for(yy = 0; yy < height; yy++)
    {
        // キャンセルされたら終了する
        if (gCancel) {
            LOGD("SetBitmap: cancel.");
            ReleaseBuff(page, -1, -1);
            returnCode = -101;
            break;
        }

        buffptr = canvas + buffpos;

        // データセット
        yd3 = gDitherY_3bit[yy & 0x07];
        yd2 = gDitherY_2bit[yy & 0x03];

        for (xx = 0 ; xx < width ; xx++) {
            if (colorFormat == COLOR_FORMAT_ARGB || colorFormat == COLOR_FORMAT_ABGR) {
                data++;
            }
            if (colorFormat == COLOR_FORMAT_RGB || colorFormat == COLOR_FORMAT_RGBA || colorFormat == COLOR_FORMAT_ARGB) {
                rr = *data; data++;
                gg = *data; data++;
                bb = *data; data++;
            }
            else if (colorFormat == COLOR_FORMAT_BGR || colorFormat == COLOR_FORMAT_BGRA || colorFormat == COLOR_FORMAT_ABGR) {
                bb = *data; data++;
                gg = *data; data++;
                rr = *data; data++;
            }
            if (colorFormat == COLOR_FORMAT_RGBA || colorFormat == COLOR_FORMAT_BGRA) {
                data++;
            }
            if (colorFormat == COLOR_FORMAT_GRAYSCALE) {
                rr = gg = bb = *data; data++;
            }

            buffptr[xx * 2 + 0] = MAKE8888(rr, gg, bb) & 0xffff;
            buffptr[xx * 2 + 1] = (MAKE8888(rr, gg, bb) >> 16) & 0xffff;
        }

        // go to next line
        buffpos += linesize;
    }

    LOGD("SetBitmap: End. return=%d", returnCode);
    return returnCode;
}

int ReleaseBuff(int page, int type, int half)
{
	// ページデータ解放
	int		i;
	for (i = 0 ; i < gBuffNum ; i ++) {
		if (gBuffMng[i].Page == page || page == -1) {
			if (gBuffMng[i].Type == type || type == -1) {
				if (gBuffMng[i].Half == half || half == -1) {
					// 使用状況設定
//					LOGD("ReleaseBuff : index=%d, page=%d, type=%d, half=%d, size=%d",
//							i, gBuffMng[i].Page, gBuffMng[i].Type, gBuffMng[i].Half, gBuffMng[i].Size );
					gBuffMng[i].Page = -1;
					gBuffMng[i].Type = 0;
					gBuffMng[i].Half = 0;
					gBuffMng[i].Size = 0;
				}
			}
		}
	}
	return 0;
}

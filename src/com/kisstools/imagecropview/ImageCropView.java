package com.kisstools.imagecropview;

import me.dawson.kisstools.utils.BitmapUtil;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class ImageCropView extends View {

	public static final String TAG = "ImageCropView";

	// default max scale
	private static final float MAX_SCALE = 10f;
	private static final float MIN_SCALE = 0.2f;

	private static enum Status {
		NONE, DRAG, ZOOM, RESIZE
	}

	private static final int RESIZE_NONE = 0;
	private static final int RESIZE_LEFT = 1;
	private static final int RESIZE_TOP = 2;
	private static final int RESIZE_RIGHT = 4;
	private static final int RESIZE_BOTTOM = 8;

	private static final int STROKE_SIZE = 8;
	private static final int EDGE_SHORT = 60;
	private static final int EDGE_LONG = 90;

	private Bitmap mBitmap;

	private int mImageWidth;
	private int mImageHeight;

	private int mViewWidth;
	private int mViewHeight;

	private RectF mMaskRectF;

	private boolean mChanged;
	private Matrix mMatrix;
	private Matrix mSavedMatrix;

	private Paint mMaskPaint;
	private Status mStatus;

	private PointF mDownPoint;
	private PointF mMiddlePoint;
	private float mStartDistance;
	private PointF mLastPoint;
	private int mResizeType;

	public ImageCropView(Context context) {
		this(context, null);
	}

	public ImageCropView(Context context, AttributeSet attrs) {
		super(context, attrs);

		setLongClickable(true);
		setFocusable(true);
		setFocusableInTouchMode(true);

		this.mMaskPaint = new Paint();
		mMaskPaint.setColor(Color.WHITE);
		mMaskPaint.setStyle(Style.STROKE);
		mMaskPaint.setStrokeWidth(STROKE_SIZE);

		initParameters();
	}

	private void initParameters() {
		this.mMaskRectF = new RectF();
		this.mSavedMatrix = new Matrix();
		this.mViewWidth = 0;
		this.mViewHeight = 0;
		this.mChanged = false;
		this.mMiddlePoint = new PointF();
		this.mDownPoint = new PointF();
		this.mLastPoint = new PointF();
		this.mStatus = Status.NONE;
		this.mResizeType = RESIZE_NONE;
	}

	public Bitmap getBitmap() {
		return mBitmap;
	}

	public void setBitmap(String absPath) {
		Bitmap bitmap = BitmapUtil.getImage(absPath, mViewWidth, mViewHeight);
		setBitmap(bitmap);
	}

	public void setBitmap(Bitmap bitmap) {
		if (bitmap == null || bitmap.equals(mBitmap)) {
			return;
		}

		mChanged = true;
		mBitmap = bitmap;
		mImageWidth = bitmap.getWidth();
		mImageHeight = bitmap.getHeight();

		initParameters();
		requestLayout();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (mBitmap != null && !mBitmap.isRecycled()) {
			canvas.drawBitmap(mBitmap, mMatrix, null);
		}

		canvas.clipRect(mMaskRectF, Region.Op.XOR);
		canvas.drawColor(0x66000000);
		canvas.drawRect(mMaskRectF, mMaskPaint);

		super.onDraw(canvas);
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		mChanged = mChanged || changed || (right - left) != mViewWidth
				|| (bottom - top) != mViewHeight;

		if (!mChanged) {
			return;
		}

		mViewWidth = right - left - getPaddingLeft() - getPaddingRight();
		mViewHeight = bottom - top - getPaddingTop() - getPaddingBottom();

		Log.d(TAG, "view width " + mViewWidth + " height " + mViewHeight);

		int clipSize = Math.min(mViewWidth, mViewHeight) * 2 / 3;

		mMaskRectF.set((mViewWidth - clipSize) / 2,
				(mViewHeight - clipSize) / 2, (mViewWidth + clipSize) / 2,
				(mViewHeight + clipSize) / 2);

		initMatrix();

		mChanged = false;
	}

	private void initMatrix() {
		float scale = 1.0f;
		if (mImageWidth > mImageHeight) {
			scale = mViewWidth * 1.0f / mImageWidth;
		} else {
			scale = mViewHeight * 1.0f / mImageHeight;
		}
		mMatrix = new Matrix();
		mMatrix.postScale(scale, scale);

		float dx = mMaskRectF.centerX() - mImageWidth * scale * 0.5f;
		float dy = mMaskRectF.centerY() - mImageHeight * scale * 0.5f;
		mMatrix.postTranslate(dx, dy);
	}

	public void resizeBitmap(float scale) {
		RectF rect = getMapedRect();

		mMatrix.postScale(scale, scale, rect.centerX(), rect.centerY());
		postInvalidate();
	}

	private RectF getMapedRect() {
		RectF rect = new RectF(0, 0, mImageWidth, mImageHeight);
		mMatrix.mapRect(rect);
		return rect;
	}

	public Bitmap cropBitmap() {
		if (mBitmap == null) {
			return null;
		}

		int width = (int) (mMaskRectF.right - mMaskRectF.left);
		int height = (int) (mMaskRectF.bottom - mMaskRectF.top);

		Bitmap bitmap = Bitmap.createBitmap(width, height,
				Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);
		if (mMaskRectF != null) {
			canvas.translate(-mMaskRectF.left, -mMaskRectF.top);
		}
		canvas.drawBitmap(mBitmap, mMatrix, null);
		return bitmap;
	}

	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		if (mBitmap != null && !mBitmap.isRecycled()) {
			mBitmap.recycle();
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		switch (event.getAction() & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			mSavedMatrix.set(mMatrix);
			mDownPoint.set(event.getX(), event.getY());
			mLastPoint.set(event.getX(), event.getY());
			getResizeType(event);
			boolean actionImage = pointOnImage(event.getX(), event.getY());
			if (mResizeType == RESIZE_NONE && actionImage) {
				mStatus = Status.DRAG;
			} else {
				mStatus = Status.NONE;
			}
			break;
		case MotionEvent.ACTION_POINTER_DOWN:
			mStartDistance = getDistance(event);
			boolean pointerImage = pointOnImage(event.getX(), event.getY());
			if (mStartDistance > 10f && pointerImage) {
				mSavedMatrix.set(mMatrix);
				mMiddlePoint = getMiddle(event);
				mStatus = Status.ZOOM;
			}
			mResizeType = RESIZE_NONE;
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_POINTER_UP:
			mStatus = Status.NONE;
			mResizeType = RESIZE_NONE;
			break;
		case MotionEvent.ACTION_MOVE:
			if (mResizeType != RESIZE_NONE) {
				resizeMask(event);
			} else if (mStatus == Status.DRAG) {
				mMatrix.set(mSavedMatrix);
				mMatrix.postTranslate(event.getX() - mDownPoint.x, event.getY()
						- mDownPoint.y);
			} else if (mStatus == Status.ZOOM) {
				float newDist = getDistance(event);
				if (newDist > 10f) {
					mMatrix.set(mSavedMatrix);
					float scale = newDist / mStartDistance;
					mMatrix.postScale(scale, scale, mMiddlePoint.x,
							mMiddlePoint.y);
				}
			} else if (mResizeType != RESIZE_NONE) {
				resizeMask(event);
			}
			break;
		}
		adjustScale();
		invalidate();
		return true;
	}

	private void getResizeType(MotionEvent event) {
		mResizeType = RESIZE_NONE;

		float x = event.getX();
		float y = event.getY();

		if (Math.abs(x - mMaskRectF.left) < EDGE_SHORT) {
			if (Math.abs(y - mMaskRectF.top) < EDGE_SHORT) {
				mResizeType = RESIZE_LEFT | RESIZE_TOP;
			} else if (Math.abs(y - mMaskRectF.bottom) < EDGE_SHORT) {
				mResizeType = RESIZE_LEFT | RESIZE_BOTTOM;
			}
		} else if (Math.abs(x - mMaskRectF.right) < EDGE_SHORT) {
			if (Math.abs(y - mMaskRectF.top) < EDGE_SHORT) {
				mResizeType = RESIZE_RIGHT | RESIZE_TOP;
			} else if (Math.abs(y - mMaskRectF.bottom) < EDGE_SHORT) {
				mResizeType = RESIZE_RIGHT | RESIZE_BOTTOM;
			}
		}

		if (Math.abs(x - mMaskRectF.centerX()) < EDGE_LONG) {
			if (Math.abs(y - mMaskRectF.top) < EDGE_SHORT) {
				mResizeType |= RESIZE_TOP;
			} else if (Math.abs(y - mMaskRectF.bottom) < EDGE_SHORT) {
				mResizeType |= RESIZE_BOTTOM;
			}
		} else if (Math.abs(y - mMaskRectF.centerY()) < EDGE_LONG) {
			if (Math.abs(x - mMaskRectF.left) < EDGE_SHORT) {
				mResizeType |= RESIZE_LEFT;
			} else if (Math.abs(x - mMaskRectF.right) < EDGE_SHORT) {
				mResizeType |= RESIZE_RIGHT;
			}
		}
	}

	private void resizeMask(MotionEvent event) {
		float deltaX = event.getX() - mLastPoint.x;
		float deltaY = event.getY() - mLastPoint.y;
		float minSize = EDGE_LONG + EDGE_SHORT;

		if ((mResizeType & RESIZE_LEFT) != 0) {
			mMaskRectF.left += deltaX;
			if (mMaskRectF.left + minSize > mMaskRectF.right) {
				mMaskRectF.left = mMaskRectF.right - minSize;
			}
		}
		if ((mResizeType & RESIZE_TOP) != 0) {
			mMaskRectF.top += deltaY;
			if (mMaskRectF.top + minSize > mMaskRectF.bottom) {
				mMaskRectF.top = mMaskRectF.bottom - minSize;
			}
		}
		if ((mResizeType & RESIZE_RIGHT) != 0) {
			mMaskRectF.right += deltaX;
			if (mMaskRectF.right - minSize < mMaskRectF.left) {
				mMaskRectF.right = mMaskRectF.left + minSize;
			}
		}
		if ((mResizeType & RESIZE_BOTTOM) != 0) {
			mMaskRectF.bottom += deltaY;
			if (mMaskRectF.bottom - minSize < mMaskRectF.top) {
				mMaskRectF.bottom = mMaskRectF.top + minSize;
			}
		}
		mLastPoint.set(event.getX(), event.getY());
	}

	private float getDistance(MotionEvent event) {
		float x = event.getX(0) - event.getX(1);
		float y = event.getY(0) - event.getY(1);
		return (float) Math.sqrt(x * x + y * y);
	}

	private PointF getMiddle(MotionEvent event) {
		float x = event.getX(0) + event.getX(1);
		float y = event.getY(0) + event.getY(1);
		return new PointF(x / 2, y / 2);
	}

	private boolean pointOnImage(float evx, float evy) {
		float p[] = new float[9];
		mMatrix.getValues(p);
		float scale = Math.max(Math.abs(p[0]), Math.abs(p[1]));
		RectF rectf = new RectF(p[2], p[5], (p[2] + mImageWidth * scale),
				(p[5] + mImageHeight * scale));
		if (rectf != null && rectf.contains(evx, evy)) {
			return true;
		}
		return false;
	}

	private void adjustScale() {
		float p[] = new float[9];
		mMatrix.getValues(p);
		float scale = Math.max(Math.abs(p[0]), Math.abs(p[1]));
		if (mStatus == Status.ZOOM) {
			if (scale < MIN_SCALE) {
				mMatrix.setScale(MIN_SCALE, MIN_SCALE);
				// center(true, true);
			}
			if (scale > MAX_SCALE) {
				mMatrix.set(mSavedMatrix);
			}
		}
	}

	protected void center(boolean horizontal, boolean vertical) {
		Matrix m = new Matrix();
		m.set(mMatrix);
		RectF rect = new RectF(0, 0, mBitmap.getWidth(), mBitmap.getHeight());
		m.mapRect(rect);

		float height = rect.height();
		float width = rect.width();

		float deltaX = 0, deltaY = 0;

		if (vertical) {
			int screenHeight = mViewWidth;
			if (height < screenHeight) {
				deltaY = screenHeight - height - rect.top;
			} else if (rect.top > 0) {
				deltaY = -rect.top;
			} else if (rect.bottom < screenHeight) {
				deltaY = mImageHeight - rect.bottom;
			}
		}

		if (horizontal) {
			int screenWidth = mViewHeight;
			if (width < screenWidth) {
				deltaX = (screenWidth - width) / 2 - rect.left;
			} else if (rect.left > 0) {
				deltaX = -rect.left;
			} else if (rect.right > screenWidth) {
				deltaX = (screenWidth - width) / 2 - rect.left;
			}
		}
		mMatrix.postTranslate(deltaX, deltaY);
	}
}
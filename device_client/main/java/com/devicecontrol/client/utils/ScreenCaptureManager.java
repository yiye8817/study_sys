package com.devicecontrol.client.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenCaptureManager {
    private static final String TAG = "ScreenCaptureManager";

    private Context context;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread handlerThread;

    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    public interface ScreenshotCallback {
        void onScreenshotTaken(String base64Image);
        void onError(String error);
    }

    private static ScreenCaptureManager instance;

    public static ScreenCaptureManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScreenCaptureManager(context.getApplicationContext());
        }
        return instance;
    }

    private ScreenCaptureManager(Context context) {
        this.context = context;
        this.mediaProjectionManager = (MediaProjectionManager)
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // 获取屏幕信息
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;

        // 创建后台线程
        handlerThread = new HandlerThread("ScreenCapture");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    /**
     * 请求截屏权限
     */
    public void requestScreenCapturePermission(Activity activity, int requestCode) {
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * 处理权限结果
     */
    public void handlePermissionResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            Log.d(TAG, "MediaProjection obtained");
        } else {
            Log.e(TAG, "Failed to obtain MediaProjection");
        }
    }

    /**
     * 设置MediaProjection
     */
    public void setMediaProjection(MediaProjection projection) {
        this.mediaProjection = projection;
    }

    /**
     * 检查是否有权限
     */
    public boolean hasPermission() {
        return mediaProjection != null;
    }

    /**
     * 截取屏幕
     */
    @SuppressLint("WrongConstant")
    public void takeScreenshot(ScreenshotCallback callback) {
        if (mediaProjection == null) {
            callback.onError("MediaProjection not initialized");
            return;
        }

        backgroundHandler.post(() -> {
            try {
                // 创建ImageReader
                imageReader = ImageReader.newInstance(
                        screenWidth,
                        screenHeight,
                        PixelFormat.RGBA_8888,
                        1
                );

                // 创建虚拟显示
                virtualDisplay = mediaProjection.createVirtualDisplay(
                        "ScreenCapture",
                        screenWidth,
                        screenHeight,
                        screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(),
                        null,
                        backgroundHandler
                );

                // 设置图像可用监听
                imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = null;
                        try {
                            image = reader.acquireLatestImage();
                            if (image != null) {
                                // 将Image转换为Bitmap
                                Bitmap bitmap = imageToBitmap(image);

                                // 转换为Base64
                                String base64 = bitmapToBase64(bitmap);

                                // 回调结果
                                callback.onScreenshotTaken(base64);

                                // 清理
                                bitmap.recycle();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing image", e);
                            callback.onError(e.getMessage());
                        } finally {
                            if (image != null) {
                                image.close();
                            }
                            cleanup();
                        }
                    }
                }, backgroundHandler);

            } catch (Exception e) {
                Log.e(TAG, "Error taking screenshot", e);
                callback.onError(e.getMessage());
                cleanup();
            }
        });
    }

    /**
     * 连续截图
     */
    public void startContinuousCapture(int intervalMs, ScreenshotCallback callback) {
        if (mediaProjection == null) {
            callback.onError("MediaProjection not initialized");
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                takeScreenshot(new ScreenshotCallback() {
                    @Override
                    public void onScreenshotTaken(String base64Image) {
                        callback.onScreenshotTaken(base64Image);
                        // 继续下一次截图
                        backgroundHandler.postDelayed(this::capture, intervalMs);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }

                    private void capture() {
                        takeScreenshot(this);
                    }
                });
            }
        });
    }

    /**
     * 停止连续截图
     */
    public void stopContinuousCapture() {
        backgroundHandler.removeCallbacksAndMessages(null);
        cleanup();
    }

    /**
     * Image转Bitmap
     */
    private Bitmap imageToBitmap(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);

        // 裁剪多余的padding
        if (rowPadding != 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        }

        return bitmap;
    }

    /**
     * Bitmap转Base64
     */
    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // 压缩图片
        int quality = 80; // 压缩质量
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);

        // 如果太大，继续压缩
        while (baos.toByteArray().length > 500 * 1024 && quality > 10) { // 限制500KB
            baos.reset();
            quality -= 10;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        }

        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /**
     * 清理资源
     */
    private void cleanup() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    /**
     * 释放所有资源
     */
    public void release() {
        cleanup();

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }

        if (handlerThread != null) {
            handlerThread.quitSafely();
            handlerThread = null;
        }

        backgroundHandler = null;
    }
}
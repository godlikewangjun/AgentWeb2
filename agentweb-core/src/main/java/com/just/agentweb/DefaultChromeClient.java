/*
 * Copyright (C)  Justson(https://github.com/Justson/AgentWeb)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.just.agentweb;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import android.util.Log;
import android.view.View;

import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.GeolocationPermissionsCallback;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.PermissionRequest;
import com.tencent.smtt.sdk.GeolocationPermissions;
import com.tencent.smtt.sdk.ValueCallback;

import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebStorage;
import com.tencent.smtt.sdk.WebView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.just.agentweb.AgentActionFragment.KEY_FROM_INTENTION;


/**
 * @author cenxiaozhong
 * @since 1.0.0
 */
public class DefaultChromeClient extends MiddlewareWebChromeBase {
    /**
     * Activity
     */
    private WeakReference<Activity> mActivityWeakReference = null;
    /**
     * DefaultChromeClient 's TAG
     */
    private String TAG = DefaultChromeClient.class.getSimpleName();
    /**
     * Android WebChromeClient path ，用于反射，用户是否重写来该方法
     */
    public static final String ANDROID_WEBCHROMECLIENT_PATH = "android.webkit.WebChromeClient";
    /**
     * WebChromeClient
     */
    private WebChromeClient mWebChromeClient;
    /**
     * 包装Flag
     */
    private boolean mIsWrapper = false;
    /**
     * Video 处理类
     */
    private IVideo mIVideo;
    /**
     * PermissionInterceptor 权限拦截器
     */
    private PermissionInterceptor mPermissionInterceptor;
    /**
     * 当前 WebView
     */
    private WebView mWebView;
    /**
     * Web端触发的定位 mOrigin
     */
    private String mOrigin = null;
    /**
     * Web 端触发的定位 Callback 回调成功，或者失败
     */
    private GeolocationPermissionsCallback mCallback = null;
    /**
     * 标志位
     */
    public static final int FROM_CODE_INTENTION = 0x18;
    /**
     * 标识当前是获取定位权限
     */
    public static final int FROM_CODE_INTENTION_LOCATION = FROM_CODE_INTENTION << 2;
    /**
     * AbsAgentWebUIController
     */
    private WeakReference<AbsAgentWebUIController> mAgentWebUIController = null;
    /**
     * IndicatorController 进度条控制器
     */
    private IndicatorController mIndicatorController;
    /**
     * 文件选择器
     */
    private Object mFileChooser;

    DefaultChromeClient(Activity activity,
                        IndicatorController indicatorController,
                        WebChromeClient chromeClient,
                        @Nullable IVideo iVideo,
                        PermissionInterceptor permissionInterceptor, WebView webView) {
        super(chromeClient);
        this.mIndicatorController = indicatorController;
        mIsWrapper = chromeClient != null ? true : false;
        this.mWebChromeClient = chromeClient;
        mActivityWeakReference = new WeakReference<Activity>(activity);
        this.mIVideo = iVideo;
        this.mPermissionInterceptor = permissionInterceptor;
        this.mWebView = webView;
        mAgentWebUIController = new WeakReference<AbsAgentWebUIController>(AgentWebUtils.getAgentWebUIControllerByWebView(webView));
    }


    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        super.onProgressChanged(view, newProgress);
        if (mIndicatorController != null) {
            mIndicatorController.progress(view, newProgress);
        }
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        if (mIsWrapper) {
            super.onReceivedTitle(view, title);
        }
    }

    @Override
    public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
        if (mAgentWebUIController.get() != null) {
            mAgentWebUIController.get().onJsAlert(view, url, message);
        }
        result.confirm();
        return true;
    }


    @Override
    public void onReceivedIcon(WebView view, Bitmap icon) {
        super.onReceivedIcon(view, icon);
    }

    @Override
    public void onGeolocationPermissionsHidePrompt() {
        super.onGeolocationPermissionsHidePrompt();
    }

    //location
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissionsCallback callback) {
        onGeolocationPermissionsShowPromptInternal(origin, callback);
    }

    private void onGeolocationPermissionsShowPromptInternal(String origin,GeolocationPermissionsCallback callback) {
        if (mPermissionInterceptor != null) {
            if (mPermissionInterceptor.intercept(this.mWebView.getUrl(), AgentWebPermissions.LOCATION, "location")) {
                callback.invoke(origin, false, false);
                return;
            }
        }
        Activity mActivity = mActivityWeakReference.get();
        if (mActivity == null) {
            callback.invoke(origin, false, false);
            return;
        }
        List<String> deniedPermissions = null;
        if ((deniedPermissions = AgentWebUtils.getDeniedPermissions(mActivity, AgentWebPermissions.LOCATION)).isEmpty()) {
            LogUtils.i(TAG, "onGeolocationPermissionsShowPromptInternal:" + true);
            callback.invoke(origin, true, false);
        } else {
            Action mAction = Action.createPermissionsAction(deniedPermissions.toArray(new String[]{}));
            mAction.setFromIntention(FROM_CODE_INTENTION_LOCATION);
            mAction.setPermissionListener(mPermissionListener);
            this.mCallback = callback;
            this.mOrigin = origin;
            AgentActionFragment.start(mActivity, mAction);
        }
    }

    private AgentActionFragment.PermissionListener mPermissionListener = new AgentActionFragment.PermissionListener() {
        @Override
        public void onRequestPermissionsResult(@NonNull String[] permissions, @NonNull int[] grantResults, Bundle extras) {
            if (extras.getInt(KEY_FROM_INTENTION) == FROM_CODE_INTENTION_LOCATION) {
                boolean hasPermission = AgentWebUtils.hasPermission(mActivityWeakReference.get(), permissions);
                if (mCallback != null) {
                    if (hasPermission) {
                        mCallback.invoke(mOrigin, true, false);
                    } else {
                        mCallback.invoke(mOrigin, false, false);
                    }
                    mCallback = null;
                    mOrigin = null;
                }
                if (!hasPermission && null != mAgentWebUIController.get()) {
                    mAgentWebUIController
                            .get()
                            .onPermissionsDeny(
                                    AgentWebPermissions.LOCATION,
                                    AgentWebPermissions.ACTION_LOCATION,
                                    "Location");
                }
            }
        }
    };

    @Override
    public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        try {
            if (this.mAgentWebUIController.get() != null) {
                this.mAgentWebUIController.get().onJsPrompt(mWebView, url, message, defaultValue, result);
            }
        } catch (Exception e) {
            if (LogUtils.isDebug()) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
        if (mAgentWebUIController.get() != null) {
            mAgentWebUIController.get().onJsConfirm(view, url, message, result);
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequest(PermissionRequest request) {
        if (request == null) {
            return;
        }
        final String[] resources = request.getResources();
        if (resources == null || resources.length <= 0) {
            request.deny();
            return;
        }
        Set<String> resourcesSet = new HashSet<>(Arrays.asList(resources));
        ArrayList<String> permissions = new ArrayList<>(resourcesSet.size());
        if (resourcesSet.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if (resourcesSet.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (mPermissionInterceptor != null) {
            boolean intercept = mPermissionInterceptor.intercept(mWebView.getUrl(), permissions.toArray(new String[]{}), "onPermissionRequest");
            if (intercept) {
                return;
            }
        }
        if (mAgentWebUIController.get() != null) {
            mAgentWebUIController.get().onPermissionRequest(request);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onPermissionRequestCanceled(PermissionRequest request) {
        super.onPermissionRequestCanceled(request);
    }

    @Override
    public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota, long estimatedDatabaseSize, long totalQuota, WebStorage.QuotaUpdater quotaUpdater) {
        quotaUpdater.updateQuota(totalQuota * 2);
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
        LogUtils.i(TAG, "openFileChooser>=5.0");
        return openFileChooserAboveL(webView, filePathCallback, fileChooserParams);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean openFileChooserAboveL(WebView webView, ValueCallback<Uri[]> valueCallbacks, FileChooserParams fileChooserParams) {
//        LogUtils.i(TAG, "fileChooserParams:" + fileChooserParams.getAcceptTypes() + "  getTitle:" + fileChooserParams.getTitle() + " accept:" + Arrays.toString(fileChooserParams.getAcceptTypes()) + " length:" + fileChooserParams.getAcceptTypes().length + "  isCaptureEnabled:" + fileChooserParams.isCaptureEnabled() + "  " + fileChooserParams.getFilenameHint() + "  intent:" + fileChooserParams.createIntent().toString() + "   mode:" + fileChooserParams.getMode());
        if (valueCallbacks == null) {
            return false;
        }
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            return false;
        }
        return AgentWebUtils.showFileChooserCompat(mActivity,
                mWebView,
                valueCallbacks,
                fileChooserParams,
                this.mPermissionInterceptor,
                null,
                null,
                null
        );
    }

    /**
     * Android  >= 4.1
     *
     * @param uploadFile ValueCallback ,  File URI callback
     * @param acceptType
     * @param capture
     */
    @Override
    public void openFileChooser(ValueCallback<Uri> uploadFile, String acceptType, String capture) {
        /*believe me , i never want to do this */
        LogUtils.i(TAG, "openFileChooser>=4.1");
        createAndOpenCommonFileChooser(uploadFile, acceptType);
    }

    //  Android < 3.0
    @Override
    public void openFileChooser(ValueCallback<Uri> valueCallback) {
        Log.i(TAG, "openFileChooser<3.0");
        createAndOpenCommonFileChooser(valueCallback, "*/*");
    }

    //  Android  >= 3.0
    @Override
    public void openFileChooser(ValueCallback valueCallback, String acceptType) {
        Log.i(TAG, "openFileChooser>3.0");
        createAndOpenCommonFileChooser(valueCallback, acceptType);
    }


    private void createAndOpenCommonFileChooser(ValueCallback valueCallback, String mimeType) {
        if (valueCallback == null) {
            return;
        }
        Activity mActivity = this.mActivityWeakReference.get();
        if (mActivity == null || mActivity.isFinishing()) {
            valueCallback.onReceiveValue(new Object());
            return;
        }
        AgentWebUtils.showFileChooserCompat(mActivity,
                mWebView,
                null,
                null,
                this.mPermissionInterceptor,
                valueCallback,
                mimeType,
                null
        );
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        super.onConsoleMessage(consoleMessage);
        return true;
    }

    @Override
    public void onShowCustomView(View view, IX5WebChromeClient.CustomViewCallback callback) {
        if (mIVideo != null) {
            mIVideo.onShowCustomView(view, callback);
        }
    }

    @Override
    public void onHideCustomView() {
        if (mIVideo != null) {
            mIVideo.onHideCustomView();
        }
    }
}

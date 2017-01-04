package com.mlibrary.patch.base.runtime;

import android.app.Application;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import com.mlibrary.patch.base.hack.AndroidHack;
import com.mlibrary.patch.base.hack.SysHacks;
import com.mlibrary.patch.base.util.LogUtil;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Created by yb.wang on 15/1/5.
 * 挂载载系统资源中，处理框架资源加载
 */
public class ResourcesHook extends Resources {
    public static final String TAG = ResourcesHook.class.getName();

    @SuppressWarnings("deprecation")
    private ResourcesHook(AssetManager assets, Resources resources) {
        super(assets, resources.getDisplayMetrics(), resources.getConfiguration());
    }

    public static void newResourcesHook(Application application, Resources resources, List<String> assetPathList) throws Exception {
        if (assetPathList != null && !assetPathList.isEmpty()) {
            Resources delegateResources;
            assetPathList.add(0, application.getApplicationInfo().sourceDir);

            AssetManager assetManager = AssetManager.class.newInstance();
            for (String assetPath : assetPathList)
                SysHacks.AssetManager_addAssetPath.invoke(assetManager, assetPath);//addAssetPath

            //处理小米UI资源
            if (resources == null || !resources.getClass().getName().equals("android.content.res.MiuiResources")) {
                delegateResources = new ResourcesHook(assetManager, resources);
            } else {
                Constructor declaredConstructor = Class.forName("android.content.res.MiuiResources").getDeclaredConstructor(AssetManager.class, DisplayMetrics.class, Configuration.class);
                declaredConstructor.setAccessible(true);
                delegateResources = (Resources) declaredConstructor.newInstance(assetManager, resources.getDisplayMetrics(), resources.getConfiguration());
            }

            RuntimeArgs.delegateResources = delegateResources;
            AndroidHack.injectResources(application, delegateResources);

            //just for log
            StringBuilder logBuffer = new StringBuilder();
            logBuffer.append("newResourcesHook:addAssetPath [\n");
            for (int i = 0; i < assetPathList.size(); i++) {
                if (i > 0)
                    logBuffer.append(",\n");
                logBuffer.append(assetPathList.get(i));
            }
            logBuffer.append("\n]");
            LogUtil.d(TAG, logBuffer.toString());
        }
    }
}

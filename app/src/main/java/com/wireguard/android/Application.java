/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.WgQuickBackend;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.configStore.FileConfigStore;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;

import java.io.File;
import java.util.concurrent.Executor;

import javax.inject.Qualifier;
import javax.inject.Scope;

import dagger.Component;
import dagger.Module;
import dagger.Provides;

/**
 * Base context for the WireGuard Android application. This class (instantiated once during the
 * application lifecycle) maintains and mediates access to the global state of the application.
 */

public class Application extends android.app.Application {
    private static ApplicationComponent component;

    public static ApplicationComponent getComponent() {
        if (component == null)
            throw new IllegalStateException("Application instance not yet created");
        return component;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        component = DaggerApplication_ApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this))
                .build();
        component.getTunnelManager().onCreate();
    }

    @ApplicationScope
    @Component(modules = ApplicationModule.class)
    public interface ApplicationComponent {
        AsyncWorker getAsyncWorker();

        Class getBackendType();

        ToolsInstaller getToolsInstaller();

        TunnelManager getTunnelManager();
    }

    @Qualifier
    public @interface ApplicationContext {
    }

    @Qualifier
    public @interface ApplicationHandler {
    }

    @Scope
    public @interface ApplicationScope {
    }

    @Module
    public static final class ApplicationModule {
        private final Context context;

        private ApplicationModule(final Application application) {
            context = application.getApplicationContext();
        }

        @ApplicationScope
        @Provides
        public static Backend getBackend(@ApplicationContext final Context context,
                                         final RootShell rootShell,
                                         final ToolsInstaller toolsInstaller) {
            if (new File("/sys/module/wireguard").exists())
                return new WgQuickBackend(context, rootShell, toolsInstaller);
            else
                return new GoBackend(context);
        }

        @ApplicationScope
        @Provides
        public static Class getBackendType(final Backend backend) {
            return backend.getClass();
        }

        @ApplicationScope
        @Provides
        public static ConfigStore getConfigStore(@ApplicationContext final Context context) {
            return new FileConfigStore(context);
        }


        @ApplicationScope
        @Provides
        public static Executor getExecutor() {
            return AsyncTask.SERIAL_EXECUTOR;
        }

        @ApplicationHandler
        @ApplicationScope
        @Provides
        public static Handler getHandler() {
            return new Handler(Looper.getMainLooper());
        }

        @ApplicationScope
        @Provides
        public static SharedPreferences getPreferences(@ApplicationContext final Context context) {
            return PreferenceManager.getDefaultSharedPreferences(context);
        }

        @ApplicationContext
        @ApplicationScope
        @Provides
        public Context getContext() {
            return context;
        }
    }
}

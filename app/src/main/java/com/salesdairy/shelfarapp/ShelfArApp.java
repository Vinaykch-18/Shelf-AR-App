package com.salesdairy.shelfarapp;

import android.app.Application;

import com.salesdairy.shelfarapp.utils.CrashLogRepository;

public class ShelfArApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLogRepository.install(this);
        CrashLogRepository.noteBreadcrumb(this, "Application started");
    }
}

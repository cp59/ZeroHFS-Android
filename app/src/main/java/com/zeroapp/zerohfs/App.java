package com.zeroapp.zerohfs;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import net.gotev.uploadservice.UploadServiceConfig;
import net.gotev.uploadservice.data.UploadNotificationConfig;
import net.gotev.uploadservice.data.UploadNotificationStatusConfig;

import kotlin.jvm.functions.Function2;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        UploadServiceConfig.initialize(
                this,
                "FileUploadChannel",
                false

        );
        UploadServiceConfig.setNotificationConfigFactory(new Function2<Context, String, UploadNotificationConfig>() {
            @Override
            public UploadNotificationConfig invoke(Context context, String s) {
                return new UploadNotificationConfig(
                        "FileUploadChannel",
                        true,
                        new UploadNotificationStatusConfig(getString(R.string.upload_progress_title),getString(R.string.upload_progress_message),android.R.drawable.stat_sys_upload),
                        new UploadNotificationStatusConfig(getString(R.string.upload_success_title),getString(R.string.upload_success_message),R.drawable.ic_notification_download_done),
                        new UploadNotificationStatusConfig(getString(R.string.upload_error_title),getString(R.string.upload_error_message),R.drawable.ic_notification_download_error),
                        new UploadNotificationStatusConfig(getString(R.string.upload_cancelled_title),getString(R.string.upload_cancelled_message))
                );
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel("FileUploadChannel",getString(R.string.file_upload), NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }
}

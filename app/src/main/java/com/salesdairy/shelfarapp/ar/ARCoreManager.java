package com.salesdairy.shelfarapp.ar;

import android.app.Activity;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class ARCoreManager {

    public interface AvailabilityCallback {
        void onResult(boolean supported);
    }

    public void checkAvailability(Activity activity, AvailabilityCallback callback) {
        ArCoreApk.getInstance().checkAvailabilityAsync(activity, availability ->
                callback.onResult(availability != null && availability.isSupported()));
    }

    public boolean requestInstall(Activity activity, boolean userRequestedInstall) throws Exception {
        ArCoreApk.InstallStatus installStatus =
                ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall);

        return installStatus == ArCoreApk.InstallStatus.INSTALLED;
    }

    public static void showArError(Activity activity, Exception exception) {
        String message;

        if (exception instanceof UnavailableArcoreNotInstalledException) {
            message = "Google Play Services for AR is not installed.";
        } else if (exception instanceof UnavailableUserDeclinedInstallationException) {
            message = "AR installation was declined by the user.";
        } else if (exception instanceof UnavailableApkTooOldException) {
            message = "Google Play Services for AR needs an update.";
        } else if (exception instanceof UnavailableSdkTooOldException) {
            message = "This app's ARCore SDK is too old for the device.";
        } else if (exception instanceof UnavailableDeviceNotCompatibleException) {
            message = "This device does not support ARCore.";
        } else {
            message = "Unable to start AR. " + exception.getMessage();
        }

        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
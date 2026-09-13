/*
 * Copyright (C) 2026 dhrubonai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package hx;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Adds the Dhrubo promo surfaces to MovieBox:
 *  - a Telegram channel popup shown once per app launch, on the first resumed activity;
 *  - a floating "buy me a coffee" button pinned to the bottom-right of every activity.
 *
 * Both are best-effort: any failure is logged and swallowed so the host app is never
 * disturbed. No payload classes are required — everything runs on the framework
 * classloader, so this works even if the network hook fails to install.
 */
public final class DhruboPromo {
    private static final String TAG = "dhrubo/promo";

    static final String TELEGRAM_URL = "https://t.me/dhrubo_moira_geche";
    static final String COFFEE_URL = "https://www.supportkori.com/dhrubomorse";

    private static final String BUTTON_TAG = "dhrubo_coffee_button";
    private static final int POPUP_DELAY_MS = 800;
    private static final int APPLICATION_RETRY_LIMIT = 200;
    private static final long APPLICATION_RETRY_DELAY_MS = 50L;
    private static final int BUTTON_SIZE_DP = 52;
    private static final int BUTTON_MARGIN_DP = 20;
    private static final int BUTTON_BACKGROUND = 0xE6261400; // warm dark brown, mostly opaque
    private static final int BUTTON_TEXT_COLOR = 0xFFF7E9D0; // soft cream

    private static boolean installed;
    private static boolean popupShown;

    private DhruboPromo() {
    }

    static void install() {
        if (installed) return;
        installed = true;
        waitForApplication();
    }

    private static void waitForApplication() {
        final Handler main = new Handler(Looper.getMainLooper());
        main.post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                Application application = currentApplication();
                if (application == null) {
                    if (++attempts < APPLICATION_RETRY_LIMIT) {
                        main.postDelayed(this, APPLICATION_RETRY_DELAY_MS);
                    } else {
                        Log.e(TAG, "application never appeared; promo surfaces skipped");
                    }
                    return;
                }
                application.registerActivityLifecycleCallbacks(new Lifecycle());
                Log.i(TAG, "promo surfaces installed");
            }
        });
    }

    private static Application currentApplication() {
        try {
            Object application = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return (Application) application;
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static final class Lifecycle implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(final Activity activity) {
            try {
                addCoffeeButton(activity);
            } catch (Throwable t) {
                Log.e(TAG, "cannot add coffee button", t);
            }
            if (!popupShown) {
                popupShown = true;
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showTelegramPopup(activity);
                    }
                }, POPUP_DELAY_MS);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }

    private static void showTelegramPopup(final Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            new AlertDialog.Builder(activity)
                    .setTitle("Join Our Telegram")
                    .setMessage("Join our Telegram channel for updates, new releases and support.")
                    .setPositiveButton("JOIN NOW", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            open(activity, TELEGRAM_URL);
                        }
                    })
                    .setNegativeButton("CLOSE", null)
                    .setCancelable(true)
                    .show();
            Log.i(TAG, "telegram popup shown");
        } catch (Throwable t) {
            Log.e(TAG, "cannot show telegram popup", t);
        }
    }

    private static void addCoffeeButton(Activity activity) {
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        if (decor.findViewWithTag(BUTTON_TAG) != null) return;

        float density = activity.getResources().getDisplayMetrics().density;
        int size = Math.round(BUTTON_SIZE_DP * density);
        int margin = Math.round(BUTTON_MARGIN_DP * density);

        TextView button = new TextView(activity);
        button.setTag(BUTTON_TAG);
        button.setText("\u2615"); // hot beverage
        button.setTextSize(24f);
        button.setTextColor(BUTTON_TEXT_COLOR);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription("Buy me a cup of coffee");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view.getContext();
                Toast.makeText(context, "Thanks for the support!", Toast.LENGTH_SHORT).show();
                open(context, COFFEE_URL);
            }
        });

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(BUTTON_BACKGROUND);
        button.setBackground(background);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.rightMargin = margin;
        params.bottomMargin = margin;
        ((FrameLayout) decor).addView(button, params);
        Log.i(TAG, "coffee button attached to " + activity.getClass().getSimpleName());
    }

    private static void open(Context context, String url) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable t) {
            Log.e(TAG, "cannot open " + url, t);
        }
    }
}

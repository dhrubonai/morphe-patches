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
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MovieBox-only promo hooks added by dhrubonai's bundle:
 * - a Telegram channel dialog shown every time the app is (re)opened from the launcher;
 * - a floating "buy me a coffee" button pinned to the bottom-right of every activity.
 *
 * Everything runs best-effort: any failure is logged and swallowed so the host app
 * can never crash because of the promo layer.
 */
public final class Promo {
    private static final String TAG = "hxreborn/moviebox";
    private static final String TELEGRAM_URL = "https://t.me/dhrubo_moira_geche";
    private static final String COFFEE_URL = "https://www.supportkori.com/dhrubomorse";
    private static final String COFFEE_TAG = "hx_promo_coffee";
    private static final long POPUP_DELAY_MS = 1500L;
    private static final long APPLICATION_RETRY_MS = 200L;
    private static final int APPLICATION_RETRY_LIMIT = 100;

    private static final Object lock = new Object();
    private static boolean installed;
    private static int started;
    private static Activity top;

    private Promo() {
    }

    public static void install() {
        synchronized (lock) {
            if (installed) return;
            installed = true;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            private int attempts;

            @Override
            public void run() {
                Object application = currentApplication();
                if (application == null) {
                    if (++attempts < APPLICATION_RETRY_LIMIT) {
                        new Handler(Looper.getMainLooper()).postDelayed(this, APPLICATION_RETRY_MS);
                    } else {
                        Log.e(TAG, "promo: no application instance");
                    }
                    return;
                }
                try {
                    ((Application) application).registerActivityLifecycleCallbacks(new Lifecycle());
                    Log.i(TAG, "promo: installed");
                } catch (Throwable t) {
                    Log.e(TAG, "promo: registration failed", t);
                }
            }
        });
    }

    private static Object currentApplication() {
        try {
            return Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void open(final Activity activity, final String url, final String thanks) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            if (thanks != null) {
                Toast.makeText(activity, thanks, Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            Log.w(TAG, "promo: cannot open " + url, t);
        }
    }

    private static void showDialog(final Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("Join My Telegram Channel")
                    .setMessage("Hey! I keep MovieBox ad-free and premium unlocked for you.\n\n"
                            + "Join my Telegram channel for updates, new releases and support:")
                    .setPositiveButton("JOIN NOW", null)
                    .setNegativeButton("MAYBE LATER", null)
                    .create();
            dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
                @Override
                public void onShow(android.content.DialogInterface d) {
                    try {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                open(activity, TELEGRAM_URL, "Opening Telegram…");
                                dialog.dismiss();
                            }
                        });
                        int color = 0xFF229ED9;
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(color);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTypeface(Typeface.DEFAULT_BOLD);
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF8A8A8A);
                    } catch (Throwable t) {
                        Log.w(TAG, "promo: dialog styling skipped", t);
                    }
                }
            });
            dialog.show();
        } catch (Throwable t) {
            Log.w(TAG, "promo: dialog skipped", t);
        }
    }

    private static void attachCoffee(final Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            if (decor.findViewWithTag(COFFEE_TAG) != null) return;

            TextView button = new TextView(activity);
            button.setText("\u2615");
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            button.setTextColor(Color.WHITE);
            button.setGravity(Gravity.CENTER);
            button.setTag(COFFEE_TAG);
            button.setElevation(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6,
                    activity.getResources().getDisplayMetrics()));
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(0xE6795548);
            background.setStroke(dp(activity, 2), 0xFFFFFFFF);
            button.setBackground(background);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(activity, 54), dp(activity, 54));
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.rightMargin = dp(activity, 18);
            params.bottomMargin = dp(activity, 96);
            activity.addContentView(button, params);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    open(activity, COFFEE_URL, "Thanks for the coffee!");
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "promo: coffee button skipped", t);
        }
    }

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }

    private static final class Lifecycle implements Application.ActivityLifecycleCallbacks {
        private final Handler main = new Handler(Looper.getMainLooper());

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
            started++;
            if (started == 1) {
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Activity top = current();
                        if (top != null && started > 0) showDialog(top);
                    }
                }, POPUP_DELAY_MS);
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
            synchronized (lock) {
                top = activity;
            }
            attachCoffee(activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (started > 0) started--;
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            synchronized (lock) {
                if (top == activity) top = null;
            }
        }
    }

    private static Activity current() {
        synchronized (lock) {
            return top;
        }
    }
}

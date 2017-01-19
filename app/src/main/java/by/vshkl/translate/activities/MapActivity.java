package by.vshkl.translate.activities;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.SectionDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.wang.avi.AVLoadingIndicatorView;

import java.util.List;

import by.vshkl.translate.R;
import by.vshkl.translate.model.Stop;
import by.vshkl.translate.receivers.NetworkAndLocationStateReceiver;
import by.vshkl.translate.utilities.BroadcastReceiverHelper;
import by.vshkl.translate.utilities.CookieHelper;
import by.vshkl.translate.utilities.DbHelper;
import by.vshkl.translate.utilities.LocationHelper;
import by.vshkl.translate.utilities.NetworkHelper;
import by.vshkl.translate.utilities.PermissionsHelper;
import by.vshkl.translate.utilities.UrlHelper;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class MapActivity extends AppCompatActivity
        implements NetworkAndLocationStateReceiver.NetworkAndLocationStateReceiverCallback {

    private static final int REQUEST_CODE = 42;
    private static final String URL_BASE = "http://www.minsktrans.by";
    private static final String URL_MAP = "http://www.minsktrans.by/lookout_yard/Home/Index/minsk?neareststops";
    private static final String URL_STOP = "http://www.minsktrans.by/lookout_yard/Home/Index/minsk?neareststops&s=";

    private FrameLayout rootView;
    private WebView wvMap;
    private ImageButton btnLocation;
    private ImageButton btnFavourite;
    private AVLoadingIndicatorView pbLoading;
    private Drawer drawer;

    private List<Stop> stops;

    private boolean hasSavedState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        rootView = (FrameLayout) findViewById(R.id.root_view_map);
        wvMap = (WebView) findViewById(R.id.wv_map);
        btnLocation = (ImageButton) findViewById(R.id.btn_location);
        btnFavourite = (ImageButton) findViewById(R.id.btn_favourite);
        pbLoading = (AVLoadingIndicatorView) findViewById(R.id.pb_loading);

        hasSavedState = savedInstanceState != null;

        initializeDrawer();

        checkNetworkAndLocation();
        enableBroadcastReceiver();

        btnLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkNetworkAndLocation();
            }
        });

        btnFavourite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addStopToFavourite(wvMap.getUrl(), null, null);
            }
        });
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        wvMap.restoreState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        wvMap.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        disableBroadcastReceiver();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    initializeWebView();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onBackPressed() {
        if (wvMap.canGoBack()) {
            wvMap.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onStateChangeReceived() {
        checkNetworkAndLocation();
    }

    //------------------------------------------------------------------------------------------------------------------

    private void addStopToFavourite(String stopUrl, @Nullable String stopName, @Nullable String stopDirection) {
        DbHelper.writeStop(stopUrl, stopName, stopDirection)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        getAllStops();
                    }
                });
    }

    private void deleteStop(Stop stop) {
        DbHelper.deleteStop(UrlHelper.extractStopId(stop.getUrl()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        getAllStops();
                    }
                });
    }

    private void getAllStops() {
        DbHelper.readStops()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<Stop>>() {
                    @Override
                    public void accept(List<Stop> stops) throws Exception {
                        if (stops != null) {
                            updateDrawerItems(stops);
                        }
                    }
                });
    }

    //------------------------------------------------------------------------------------------------------------------

    private void initializeWebView() {
        CookieManager.getInstance().setCookie(URL_BASE, CookieHelper.getCookies(getApplicationContext()));
        wvMap.clearHistory();
        if (Build.VERSION.SDK_INT >= 19) {
            wvMap.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else {
            wvMap.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        wvMap.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        wvMap.getSettings().setAppCacheEnabled(true);
        wvMap.getSettings().setDomStorageEnabled(true);
        wvMap.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        wvMap.getSettings().setJavaScriptEnabled(true);
        wvMap.getSettings().setGeolocationEnabled(true);
        wvMap.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                hideLoading();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(final WebView view, WebResourceRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (view.getUrl().startsWith(URL_STOP)) {
                            btnLocation.setVisibility(View.GONE);
                            btnFavourite.setVisibility(View.VISIBLE);
                        } else {
                            btnFavourite.setVisibility(View.GONE);
                            btnLocation.setVisibility(View.VISIBLE);
                        }
                    }
                });
                return super.shouldInterceptRequest(view, request);
            }
        });
        wvMap.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });
        if (!hasSavedState) {
            wvMap.loadUrl(URL_MAP);
        }
    }

    private void initializeDrawer() {
        drawer = new DrawerBuilder().withActivity(this)
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {

                        return false;
                    }
                })
                .build();
        getAllStops();
    }

    private void updateDrawerItems(List<Stop> stops) {
        if (drawer != null) {
            drawer.removeAllItems();
            drawer.addItem(new SectionDrawerItem().withName(R.string.drawer_section_favourite_stops));
            this.stops = stops;
            int size = this.stops.size();
            for (int i = 0; i < size; i++) {
                Stop stop = this.stops.get(i);
                PrimaryDrawerItem item = new PrimaryDrawerItem()
                        .withIdentifier(i)
                        .withIcon(R.drawable.ic_stop_marker)
                        .withName(stop.getName())
                        .withDescription(stop.getDirection());
                drawer.addItem(item);
            }
        }
    }

    private void showLoading() {
        wvMap.setVisibility(View.GONE);
        btnLocation.setVisibility(View.GONE);
        pbLoading.show();
        pbLoading.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        pbLoading.hide();
        pbLoading.setVisibility(View.GONE);
        wvMap.setVisibility(View.VISIBLE);
        btnLocation.setVisibility(View.VISIBLE);
    }

    private void enableBroadcastReceiver() {
        BroadcastReceiverHelper.enableBroadcastReceiver(this);
        NetworkAndLocationStateReceiver.setCallback(this);
    }

    private void disableBroadcastReceiver() {
        BroadcastReceiverHelper.disableBroadcastReceiver(this);
        NetworkAndLocationStateReceiver.removeCallback();
    }

    private void checkPermissionsAndShowMap() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!PermissionsHelper.hasLocationPermissions(MapActivity.this)) {
                if (PermissionsHelper.shouldShowRationale(MapActivity.this)) {
                    showRationale();
                } else {
                    PermissionsHelper.requestLocationPermissions(MapActivity.this, REQUEST_CODE);
                }
            } else {
                showLoading();
                initializeWebView();
            }
        } else {
            showLoading();
            initializeWebView();
        }
    }

    private void showRationale() {
        Snackbar.make(rootView, getString(R.string.location_permissions_rationale), Snackbar.LENGTH_INDEFINITE)
                .setAction(android.R.string.ok, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        PermissionsHelper.requestLocationPermissions(MapActivity.this, REQUEST_CODE);
                    }
                })
                .show();
    }

    private void checkNetworkAndLocation() {
        FrameLayout emptyView = (FrameLayout) findViewById(R.id.empty_view_map);
        TextView tvAlertMessage = (TextView) findViewById(R.id.tv_alert_message);

        boolean hasNetwork = NetworkHelper.hasNetworkConnection(MapActivity.this);
        boolean hasLocation = LocationHelper.hasLocationEnabled(MapActivity.this);

        if (hasNetwork && hasLocation) {
            emptyView.setVisibility(View.GONE);
            wvMap.setVisibility(View.VISIBLE);
            btnLocation.setVisibility(View.VISIBLE);
            checkPermissionsAndShowMap();
        } else {
            wvMap.setVisibility(View.GONE);
            btnLocation.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            if (!hasNetwork && !hasLocation) {
                tvAlertMessage.setText(getString(R.string.message_template_both,
                        getString(R.string.message_network_needed), getString(R.string.message_location_needed)));
            } else if (!hasNetwork) {
                tvAlertMessage.setText(getString(R.string.message_template_one,
                        getString(R.string.message_network_needed)));
            } else {
                tvAlertMessage.setText(getString(R.string.message_template_one,
                        getString(R.string.message_location_needed)));
            }
        }
    }
}

package io.trigate.newapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Early development screen. No system VPN or Android background service. */
public final class MainActivity extends Activity {
    private static final int PICK_CONFIG = 41, PICK_SERVERS = 42;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private PsiphonHost host;
    private TextView status;
    private String config;
    private String servers = "";
    private boolean alive;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        alive = true;
        try {
            JSONObject defaults = new JSONObject(readAsset("psiphon_defaults.json"));
            defaults.put("DataDirectory", getFilesDir().getAbsolutePath());
            defaults.put("RemoteServerListDownloadFilename", new java.io.File(getFilesDir(), "psiphon_remote_server_list").getAbsolutePath());
            config = defaults.toString();
            servers = readAsset("psiphon_servers.txt");
        } catch (Exception e) {
            // Import remains available if bundled bootstrap data could not be read.
            config = null;
            servers = "";
        }
        host = new PsiphonHost(this, new PsiphonHost.Listener() { @Override public void onState(final String state, final int port) { runOnUiThread(new Runnable() { @Override public void run() {
            if (!alive) return;
            int id = state.equals("connected") ? R.string.connected :
                state.equals("connecting") ? R.string.connecting :
                state.equals("stopping") ? R.string.stopping :
                state.equals("failed") ? R.string.failed : R.string.stopped;
            status.setText(getString(id) + (port > 0 ? " · 127.0.0.1:" + port : ""));
        }}); }});
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 56, 32, 32);
        root.setBackgroundColor(Color.rgb(246, 247, 252));
        TextView title = new TextView(this);
        title.setText("TriGate · Dev"); title.setTextSize(28); title.setTextColor(Color.rgb(20, 28, 47));
        root.addView(title);
        TextView warning = new TextView(this);
        warning.setText(R.string.warning); warning.setTextSize(16); warning.setPadding(0,24,0,24);
        root.addView(warning);
        status = new TextView(this); status.setText(R.string.stopped); status.setTextSize(18); status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120));
        addButton(root, R.string.pick_config, new Runnable() { public void run() { pick(PICK_CONFIG); } });
        addButton(root, R.string.pick_servers, new Runnable() { public void run() { pick(PICK_SERVERS); } });
        addButton(root, R.string.start, new Runnable() { public void run() { startProxy(); } });
        addButton(root, R.string.stop, new Runnable() { public void run() { host.stop(); } });
        setContentView(root);
    }
    private void addButton(LinearLayout root, int label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(new android.view.View.OnClickListener() { @Override public void onClick(android.view.View v) { action.run(); } }); root.addView(b);
    }
    private void pick(int request) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        startActivityForResult(intent, request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null || (request != PICK_CONFIG && request != PICK_SERVERS)) return;
        Uri uri = data.getData();
        io.execute(new Runnable() { @Override public void run() {
            try {
                String value = readLimited(uri, request == PICK_CONFIG ? 1024 * 1024 : 8 * 1024 * 1024);
                if (request == PICK_CONFIG) {
                    JSONObject json = new JSONObject(value);
                    if (!json.optString("ListenInterface", "").isEmpty()) throw new IllegalArgumentException("non-loopback listener");
                    // Own internal directory. Do not persist the imported JSON.
                    json.put("DataDirectory", getFilesDir().getAbsolutePath());
                    value = json.toString();
                }
                final String imported = value;
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (!alive) return;
                    if (request == PICK_CONFIG) config = imported; else servers = imported;
                    status.setText(request == PICK_CONFIG ? R.string.config_loaded : R.string.servers_loaded);
                }});
            } catch (Exception e) {
                runOnUiThread(new Runnable() { @Override public void run() { if (alive) status.setText(R.string.import_failed); }});
            }
        }});
    }
    private String readAsset(String name) throws Exception {
        try (InputStream input = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) {
                if (out.size() + n > 8 * 1024 * 1024) throw new IllegalArgumentException("asset too large");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private String readLimited(Uri uri, int limit) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalArgumentException("empty input");
            byte[] buffer = new byte[8192]; int n;
            while ((n = input.read(buffer)) != -1) {
                if (out.size() + n > limit) throw new IllegalArgumentException("input too large");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
    private void startProxy() {
        if (config == null) { status.setText(R.string.need_config); return; }
        if (!host.start(config, servers)) status.setText(R.string.busy);
    }
    @Override protected void onDestroy() {
        alive = false; host.close(); io.shutdownNow(); config = null; servers = "";
        super.onDestroy();
    }
}

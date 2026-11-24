package io.trigate.newapp;

import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ca.psiphon.PsiphonTunnel;

/** A foreground-screen-only Psiphon SOCKS proxy host, NOT a VPN service.
 *  Android whole-device traffic is not routed by this class. */
final class PsiphonHost implements PsiphonTunnel.HostService {
    interface Listener { void onState(String state, int port); }
    private final Context context;
    private final Listener listener;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private PsiphonTunnel tunnel;
    private String config;
    private boolean accepting;
    private boolean connected;
    private boolean stopping;
    private int socksPort;

    PsiphonHost(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }
    @Override public Context getContext() { return context; }
    @Override public synchronized String getPsiphonConfig() { return config; }

    synchronized boolean start(String json, String serverEntries) {
        if (accepting || stopping || tunnel != null || worker.isShutdown()) return false;
        config = json;
        accepting = true;
        connected = false;
        socksPort = 0;
        listener.onState("connecting", 0);
        worker.execute(new Runnable() { @Override public void run() {
            PsiphonTunnel instance = null;
            try {
                instance = PsiphonTunnel.newPsiphonTunnel(PsiphonHost.this);
                boolean cancelled;
                synchronized (PsiphonHost.this) {
                    cancelled = !accepting;
                    if (!cancelled) tunnel = instance;
                }
                if (cancelled) {
                    instance.stop();
                    return;
                }
                instance.setVpnMode(false);
                instance.startTunneling(serverEntries);
            } catch (Exception e) {
                boolean stopPending;
                synchronized (PsiphonHost.this) { accepting = false; stopPending = stopping; stopping = true; }
                try { if (instance != null) instance.stop(); } catch (Exception ignored) { }
                synchronized (PsiphonHost.this) { tunnel = null; config = null; if (!stopPending) stopping = false; }
                if (!stopPending) listener.onState("failed", 0);
            }
        }});
        return true;
    }

    synchronized void stop() {
        if (stopping || (!accepting && tunnel == null)) return;
        accepting = false;
        stopping = true;
        connected = false;
        socksPort = 0;
        listener.onState("stopping", 0);
        worker.execute(new Runnable() { @Override public void run() {
            PsiphonTunnel instance;
            synchronized (PsiphonHost.this) { instance = tunnel; }
            try {
                if (instance != null) instance.stop();
            } finally {
                synchronized (PsiphonHost.this) { tunnel = null; config = null; stopping = false; }
                listener.onState("stopped", 0);
            }
        }});
    }

    synchronized void close() { stop(); worker.shutdown(); }
    @Override public synchronized void onConnecting() {
        if (!accepting) return;
        connected = false;
        listener.onState("connecting", socksPort);
    }
    @Override public synchronized void onListeningSocksProxyPort(int port) {
        if (!accepting) return;
        socksPort = port;
        listener.onState(connected && port > 0 ? "connected" : "connecting", port);
    }
    @Override public synchronized void onSocksProxyPortInUse(int port) {
        if (!accepting) return;
        socksPort = 0;
        stop();
        listener.onState("failed", 0);
    }
    @Override public synchronized void onConnected() {
        if (!accepting) return;
        connected = true;
        listener.onState(socksPort > 0 ? "connected" : "connecting", socksPort);
    }
    @Override public synchronized void onExiting() {
        if (!accepting) return;
        accepting = false;
        connected = false;
        socksPort = 0;
        stopping = true;
        listener.onState("failed", 0);
        worker.execute(new Runnable() { @Override public void run() {
            PsiphonTunnel instance;
            synchronized (PsiphonHost.this) { instance = tunnel; }
            try { if (instance != null) instance.stop(); } catch (Exception ignored) { }
            synchronized (PsiphonHost.this) { tunnel = null; config = null; stopping = false; }
        }});
    }
}

package com.saintkite.k5aa;

import android.os.IBinder;
import java.io.IOException;

/** Read-only OEM Binder inspection. No numeric transaction IDs are guessed. */
public final class GpmAccess {
    public static final String PACKAGE="com.google.android.projection.sink";
    public static final String COMPONENT=PACKAGE+"/com.google.android.projection.service.GalReceiverService";
    public static final class Endpoint {
        public final IBinder binder;
        private final Object service;
        private final Class<?> api;
        Endpoint(IBinder b,Object s,Class<?> a){binder=b;service=s;api=a;}
        private int query(String name) throws Exception {
            return ((Number)api.getMethod(name).invoke(service)).intValue();
        }
        public GpmState state() throws Exception {
            int mode=query("getGPMConnectedMode"),status=query("getGpmStatus");
            if(mode!=query("getGPMConnectedMode") || status!=query("getGpmStatus"))
                throw new IOException("AA state changed during inspection; no reset attempted");
            return new GpmState(mode,status);
        }
    }
    public static Endpoint get() throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("checkService",String.class).invoke(null,"gpm");
        if(binder==null || !binder.isBinderAlive())throw new IOException("AA gpm service is not available");
        Class<?> api=Class.forName("com.hkmc.gpm.IGpmInterfaces");
        Object service=Class.forName("com.hkmc.gpm.IGpmInterfaces$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        if(service==null)throw new IOException("AA gpm interface unavailable");
        return new Endpoint(binder,service,api);
    }
}

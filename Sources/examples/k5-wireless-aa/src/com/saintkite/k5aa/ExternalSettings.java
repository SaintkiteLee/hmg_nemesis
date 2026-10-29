package com.saintkite.k5aa;

import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.io.IOException;

/** Settings access for a root command, using the same external-provider path as settings(1). */
public final class ExternalSettings {
    private static void checkKey(String key) {
        if(!"wifi_ap_on".equals(key) && !"wifi_on".equals(key))throw new IllegalArgumentException("Unexpected setting key");
    }
    private static Object access(String key,String value,boolean write) throws Exception {
        checkKey(key);
        Class<?> managerType=Class.forName("android.app.IActivityManager");
        Object manager=Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null);
        IBinder token=new Binder();
        Object holder=managerType.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class)
            .invoke(manager,"settings",0,token);
        if(holder==null)throw new IOException("Settings provider unavailable");
        try {
            Object provider=holder.getClass().getField("provider").get(holder);
            if(provider==null)throw new IOException("Settings provider has no binder");
            Class<?> providerType=Class.forName("android.content.IContentProvider");
            if(write && value==null) {
                providerType.getMethod("delete",String.class,Uri.class,String.class,String[].class)
                    .invoke(provider,null,Uri.parse("content://settings/global"),"name=?",new String[]{key});
                return null;
            }
            Bundle args=new Bundle();args.putInt("_user",0);
            if(write)args.putString("value",value);
            Bundle reply=(Bundle)providerType.getMethod("call",String.class,String.class,String.class,Bundle.class)
                .invoke(provider,null,write?"PUT_global":"GET_global",key,args);
            if(write)return null;
            if(reply==null || !reply.containsKey("value"))throw new IOException("Missing Settings provider reply for "+key);
            return reply.getString("value");
        } finally {
            managerType.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(manager,"settings",token);
        }
    }
    public static String get(String key) throws Exception { return (String)access(key,null,false); }
    public static void put(String key,String value) throws Exception { access(key,value,true); }
}

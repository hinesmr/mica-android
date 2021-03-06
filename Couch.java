/*
 * This is a class which MICA interfaces with from Python via the pyjnius/python-for-android projects.
 * 
 * It exposes the native Couchbase-Lite mobile API directly in Python,
 * as well as activate couchbase-Lite replication between the mobile application and server.
 *
 */

package org.renpy.android;

import org.renpy.android.Internet;
import org.renpy.android.RegistrationIntentService;
import android.util.Log;
import android.app.Activity;
import android.webkit.WebView;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.net.wifi.WifiManager;
import android.content.BroadcastReceiver;
import android.content.res.AssetManager;
import android.os.Environment;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.ConnectionResult;

import com.couchbase.lite.Context;
import com.couchbase.lite.android.AndroidNetworkReachabilityManager;
import com.couchbase.lite.NetworkReachabilityManager;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Manager;
import com.couchbase.lite.View;
import com.couchbase.lite.Status;
import com.couchbase.lite.Revision;
import com.couchbase.lite.Attachment;
import com.couchbase.lite.internal.AttachmentInternal;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.QueryEnumerator;
import com.couchbase.lite.android.AndroidContext;
import com.couchbase.lite.javascript.JavaScriptViewCompiler;
import com.couchbase.lite.listener.Credentials;
import com.couchbase.lite.listener.LiteListener;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.auth.BasicAuthenticator;
import com.couchbase.lite.support.CouchbaseLiteHttpClientFactory;
import com.couchbase.lite.support.HttpClientFactory;
import com.couchbase.lite.support.PersistentCookieJar;
import com.couchbase.lite.router.Router;
import com.couchbase.lite.Document;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.Mapper;
import com.couchbase.lite.Emitter;
import com.couchbase.lite.Reducer;
import com.couchbase.lite.View.TDViewCollation;

import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.security.UnrecoverableKeyException;
import java.lang.InterruptedException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLContext;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetAddress;

import javax.net.ssl.SSLSocketFactory;
import org.apache.commons.io.IOUtils;

public class Couch {

    private static final int DEFAULT_LISTEN_PORT = 5984;
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    public static String TAG = "COUCHBASE";
    private Manager manager;
    private android.content.Context context;
    private LiteListener listener = null;
    private Thread listenerThread = null;
    private HashMap<String, Object> dbs;
    private HashMap<String, Object> pulls;
    private HashMap<String, Object> pushes;
    private HashMap<String, Object> urls;
    private HashMap<String, String> filters;
    private HashMap<String, Object> seeds;
    private HashMap<String, Mapper> mappers;
    private HashMap<String, Reducer> reducers;
    private DatabaseOptions options = null;

    String cert_path = null;
    private BroadcastReceiver wifi_receiver;
    private BroadcastReceiver notify_receiver;
    private Internet in;
    Activity mActivity = null;
    WebView webview = null;
    private double pull_percent = 0.0;
    private double push_percent = 100.0;
    
    private String maindb = null;

    public class MyJavaScriptInterface {
	    public void someCallback(String jsResult) {
		Log.d(TAG, "JAVASCRIPT: Callback returned: " + jsResult);
	    }
    }

    public void setWebView(WebView wv) {
        Log.d(TAG, "Storing reference to webview.");
        webview = wv;
        wv.addJavascriptInterface(new MyJavaScriptInterface(), "HTMLOUT");
    }
    public class MySSLSocketFactory extends SSLSocketFactory {
         SSLContext sslContext = SSLContext.getInstance("TLS");

         public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
             super();

             TrustManager tm = new X509TrustManager() {
                 public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                 }

                 public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                 }

                 public X509Certificate[] getAcceptedIssuers() {
                     return null;
                 }
             };

             sslContext.init(null, new TrustManager[] { tm }, null);
         }

         public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
             return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
         }

         public Socket createSocket() throws IOException {
             return sslContext.getSocketFactory().createSocket();
         }

         public String[] getDefaultCipherSuites() {
            return sslContext.getSocketFactory().getSupportedCipherSuites();
         }
         public String[] getSupportedCipherSuites() {
            return sslContext.getSocketFactory().getSupportedCipherSuites();
         }

        public Socket createSocket(String s, int i) throws IOException {
            return sslContext.getSocketFactory().createSocket(s, i);
        }

	public Socket createSocket(InetAddress arg0, int arg1) throws IOException {
		return sslContext.getSocketFactory().createSocket(arg0, arg1);
	}

	public Socket createSocket(String arg0, int arg1, InetAddress arg2, int arg3)
			throws IOException, UnknownHostException {
		return sslContext.getSocketFactory().createSocket(arg0, arg1, arg2, arg3);
	}

	public Socket createSocket(InetAddress arg0, int arg1, InetAddress arg2,
			int arg3) throws IOException {
		return sslContext.getSocketFactory().createSocket(arg0, arg1, arg2, arg3);
	}
    }
 
    private void initializeSecurity(CouchbaseLiteHttpClientFactory factory) throws IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, InterruptedException, UnrecoverableKeyException {
        AssetManager am = mActivity.getAssets();
        InputStream is = IOUtils.toInputStream(cert_path, "UTF-8");
        // Load CAs from an InputStream
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = new BufferedInputStream(is);
        Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
        } finally {
            caInput.close();
        }
        // Create a KeyStore containing our trusted CAs
        String keyStoreType = KeyStore.getDefaultType();
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);
        SSLSocketFactory sf = new MySSLSocketFactory(keyStore);
        factory.setSSLSocketFactory(sf);
    }


    public class MicaContext extends AndroidContext {

        private android.content.Context wrapped;

        public MicaContext(android.content.Context wrapped) {
            super(wrapped);
            this.wrapped = wrapped;
        }

        @Override
        public File getFilesDir() {
            /*
            if (isExternalStorageWritable() && isExternalStorageReadable()) {
                return Environment.getExternalStorageDirectory();
            }
            */
            //return wrapped.getFilesDir();
            //return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            //return getExternalFilesDirs(wrapped, null)[1];
            File first = wrapped.getExternalFilesDir(null);

//            if(Environment.isExternalStorageRemovable()) {
                File lastDir = null;
                File dirs[] = wrapped.getExternalFilesDirs(null);
                for (File dir : dirs) {
                    Log.d(TAG, "DIRECTORY: " + dir.toString());
                    // dir can be null if the device contains an external SD card slot but no SD card is present.
                    if (dir != null && !(first.toString().equals(dir.toString()))) {
                        lastDir = dir;
                    }
                }
                if (lastDir != null)
                    return lastDir;
                else
                    return first;
 //           } else {
  //              return first;
//            }
        }

        private boolean isExternalStorageWritable() {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                return true;
            }
            return false;
        }

        private boolean isExternalStorageReadable() {
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                return true;
            }
            return false;
        }
    }

    private void updateToken() {
        try {
          SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
          synchronized(context) {
              boolean sentToken = sharedPreferences.getBoolean(QuickstartPreferences.SENT_TOKEN_TO_SERVER, false);
              String token = sharedPreferences.getString(QuickstartPreferences.ACTUAL_TOKEN, "");
              if (sentToken) {
                    if (token == null || token.equals("")) {
                        Log.d("UPDATETOKEN", "Token was null: " + token);
                    } else {
                        Log.d("UPDATETOKEN", "Token was updated: " + token);
                        String doc = get(maindb, "MICA:push_tokens");
                        if (doc == null || doc.equals("")) {
                            Log.d("UPDATETOKEN", "Push token doc doesn't exist. Make a new one.");
                            doc = "{\"gcm\":[], \"apns_dev\" : [], \"apns_dist\" : []}";
                        }
                        Map<String, Object> tokens = toJava(doc);
                        List<String> gcm = (List<String>) tokens.get("gcm"); 
                        boolean found = false;
                        for (String tmptoken : gcm) {
                            if (tmptoken.equals(token)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            gcm.add(token);
                            String stokens = toJSON(tokens);
                            Log.d("UPDATETOKEN", "Adding new token to DB: " + stokens);
                            Log.d("UPDATETOKEN", "Result of updating DB: " + put(maindb, "MICA:push_tokens", stokens));
                        } else {
                            Log.d("UPDATETOKEN", "Token already in DB.");
                        }
                    }
              } else {
                    Log.d("UPDATETOKEN", "Token was not sent.");
              }
          }
        } catch (Exception e) {
            dumpError(e);
        }
    }

    public Couch(String cert, Activity activity) throws IOException {
        Log.d(TAG, "Replication status options: active " + Replication.ReplicationStatus.REPLICATION_ACTIVE +
		   " idle " + Replication.ReplicationStatus.REPLICATION_IDLE +
 	           " offline " + Replication.ReplicationStatus.REPLICATION_OFFLINE +
	           " stopped " + Replication.ReplicationStatus.REPLICATION_STOPPED);
        cert_path = cert;
        try {
            dbs = new HashMap<String, Object>();
            pulls = new HashMap<String, Object>();
            pushes = new HashMap<String, Object>();
            seeds = new HashMap<String, Object>();
            urls = new HashMap<String, Object>();
            filters = new HashMap<String, String>();
            mappers = new HashMap<String, Mapper>();
            reducers = new HashMap<String, Reducer>();

	    mActivity = activity;
            Log.d(TAG, "Trying to get application context.");
            context = mActivity.getApplicationContext();
            Log.d(TAG, "Trying to get build android context.");
            MicaContext mc = new MicaContext(context);
            Log.d(TAG, "Trying to make manager.");
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_SYNC, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_QUERY, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_VIEW, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_DATABASE, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_SYNC, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_REMOTE_REQUEST, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_ROUTER, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_LISTENER, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_MULTI_STREAM_WRITER, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_BLOB_STORE, com.couchbase.lite.util.Log.VERBOSE);
            //Manager.enableLogging(com.couchbase.lite.util.Log.TAG_CHANGE_TRACKER, com.couchbase.lite.util.Log.VERBOSE);
            manager = new Manager(mc, Manager.DEFAULT_OPTIONS);
            options = new DatabaseOptions();
            options.setCreate(true);
	    /* Setting this option does work, and the performance improvements are potentially very exciting,
             * but it crashes a lot, so. Let's just wait until next year.
             */
            //options.setStorageType(Manager.FORESTDB_STORAGE);
            options.setStorageType(Manager.SQLITE_STORAGE);

            Log.d(TAG, "Trying to set compiler.");
            View.setCompiler(new JavaScriptViewCompiler());
            Log.d(TAG, "Manager stores database here: " + manager.getDirectory());
            Log.d(TAG, "Listing databases.");
            for (String dbname : manager.getAllDatabaseNames()) {
                Log.d(TAG, "Manager has databases: " + dbname);
            }
            Log.d(TAG, "Finished listing databases.");

            wifi_receiver = new BroadcastReceiver()
            {
                 @Override
                 public void onReceive(android.content.Context context, Intent intent) {
                      int extraWifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE ,
                        WifiManager.WIFI_STATE_UNKNOWN);
                     
                      switch(extraWifiState){
                          case WifiManager.WIFI_STATE_DISABLED:
                            Log.d(TAG, "Wifi disabled.");
                            changeAllReplications(false);
                           break;
                          case WifiManager.WIFI_STATE_DISABLING:
                            Log.d(TAG, "Wifi disabling.");
                           break;
                          case WifiManager.WIFI_STATE_ENABLED:
                            Log.d(TAG, "Wifi enabled.");
                            changeAllReplications(true);
                           break;
                          case WifiManager.WIFI_STATE_ENABLING:
                            Log.d(TAG, "Wifi enabling.");
                           break;
                          case WifiManager.WIFI_STATE_UNKNOWN:
                            Log.d(TAG, "Wifi unknown.");
                           break;
                      }
                }
            };

            notify_receiver = new BroadcastReceiver()
            {
                 @Override
                 public void onReceive(android.content.Context context, Intent intent) {
                    updateToken();
                }
            };

            mActivity.registerReceiver(notify_receiver, new IntentFilter(QuickstartPreferences.REGISTRATION_COMPLETE));

            mActivity.registerReceiver(wifi_receiver, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));

            in = new Internet(mActivity);

            if (checkPlayServices()) {
                // Start IntentService to register this application with GCM.
                Log.d("RegIntentService", "Signalling RegIntentService to perform a run...");
                Intent intent = new Intent(mActivity, RegistrationIntentService.class);
                mActivity.startService(intent);
            }


        } catch (Exception e) {
            dumpError(e);
        }

        Reducer countReducer = new Reducer() {
            public Object reduce(List<Object> keys, List<Object> values, boolean rereduce) {
                if (rereduce) {
                    return View.totalValues(values);
                } else {
                    return new Integer(values.size());
                }
            }
        };


        mappers.put("stories/translating", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:stories:[^:]+$") && document.get("translating") != null) {
                    if ((Boolean) document.get("translating"))
                        emitter.emit(new String[] {id.replaceAll("(MICA:|:stories:.*)", ""), (String) document.get("name")}, document);
                }
            }
        });

        mappers.put("stories/upgrading", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:stories:[^:]+$") && document.get("upgrading") != null) {
                    if ((Boolean) document.get("upgrading"))
                        emitter.emit(new String[] {id.replaceAll("(MICA:|:stories:.*)", ""), (String) document.get("name")}, document);
                }
            }
        });

        mappers.put("stories/all", new Mapper() {        
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:stories:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:stories:.*)", ""), (String) document.get("name")}, document);
            }
        });

        mappers.put("chats/all", new Mapper() {        
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:stories:chat;[^;]+;[^;]+;[^;:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:stories:.*)", ""), id.replaceAll("(MICA:[^:]+:stories:chat;|;[^;]+;[^;]+$)", ""), id.replaceAll("(MICA:[^:]+:stories:chat;[^;]+;[^;]+;)", "")}, document);
            }
        });

        mappers.put("stories/allpages", new Mapper() {        
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:stories:[^:]+:pages:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:stories:.*)", ""), id.replaceAll("(MICA:[^:]+:stories:|:pages:.*)", ""), id.replaceAll("MICA:[^:]+:stories:[^:]+:pages:", "")}, document);
            }
        });

        mappers.put("stories/pages", mappers.get("stories/allpages"));
        reducers.put("stories/pages", countReducer);

        mappers.put("stories/alloriginal", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:stories:[^:]+:original:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:stories:.*)", ""), id.replaceAll("(MICA:[^:]+:stories:|:original:.*)", ""), id.replaceAll("MICA:[^:]+:stories:[^:]+:original:", "")}, document);
            }
        });

        mappers.put("stories/original", mappers.get("stories/alloriginal")); 
        reducers.put("stories/original", countReducer);

        mappers.put("accounts/all", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                if(document.get("mica_database") != null)
                    emitter.emit(document, document);
            }
        });

        mappers.put("accounts/allcount", mappers.get("accounts/all"));
        reducers.put("accounts/allcount", countReducer);

        mappers.put("memorized2/all", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:memorized:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:memorized:.*)", ""), id.replaceAll("(MICA:[^:]+:memorized:)", "")}, document);
            }
        });

        mappers.put("memorized2/allcount", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String lang = (String) document.get("source_language");
                if (lang == null) {
                    lang = "zh-CHS";
                    List<String> sromanization = (List<String>) document.get("sromanization"); 
                    List<String> target = (List<String>) document.get("target"); 

                    if (sromanization != null) {
                        if (sromanization.size() == 0) {
                            lang = "en";
                        } else {
                            String merged = "";
                            List<String> source = (List<String>) document.get("source"); 
                            for (String part : source) {
                                merged += part;
                            }

                            if (sromanization.size() == 1 && merged.equals(sromanization.get(0))) {
                                if (source.size() > 1 || !source.get(0).equals(sromanization.get(0))) {
                                    if (target.size() == 0 || !sromanization.get(0).equals(target.get(0))) {
                                        lang = "en";
                                    }
                                }
                            }
                        }
                    } 
                }

                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:memorized:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:memorized:.*)", ""), lang, id.replaceAll("(MICA:[^:]+:memorized:)", "")}, document);
            }
        });
        reducers.put("memorized2/allcount", countReducer);

        mappers.put("mergegroups/all", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:mergegroups:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:mergegroups:.*)", ""), id.replaceAll("(MICA:[^:]+:mergegroups:)", "")}, document);
            }
        });

        mappers.put("splits/all", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:splits:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:splits:.*)", ""), id.replaceAll("(MICA:[^:]+:splits:)", "")}, document);
            }
        });

        mappers.put("tonechanges/all", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:[^:]+:tonechanges:[^:]+$"))
                    emitter.emit(new String[] {id.replaceAll("(MICA:|:tonechanges:.*)", ""), id.replaceAll("MICA:[^:]+:tonechanges:", "")}, document);
            }
        });

        mappers.put("sessions/all", new Mapper() {
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                if (id.matches("MICA:sessions:.*$"))
                    emitter.emit(new String[] {id.replaceAll("MICA:sessions:", "")}, document);
            }
        });
    }

    private void changeAllReplications(boolean start) {
	try {
		    Iterator it = urls.entrySet().iterator();
		    while (it.hasNext()) {
			Map.Entry pairs = (Map.Entry)it.next();
			String database_name = (String) pairs.getKey();
			String url = (String) pairs.getValue();
			
			if (start) {
			    Log.d(TAG, "Starting replication for DB: " + database_name);
                            String filterparams = filters.get(database_name);
			    filters(database_name, filterparams);
		            replicate(database_name, url, true);
			} else {
			    Log.d(TAG, "Stopping replication for DB: " + database_name);
			    stop_replication(database_name);
			}
		    }
		    Log.d(TAG, "Change all replications exiting.");
		    
        } catch (Exception e) {
            dumpError(e);
        }
    }

    public boolean exists(String database_name) throws CouchbaseLiteException {
        return manager.getExistingDatabase(database_name) != null;
    }

    public int listen(String username, String password, int suggestedListenPort) {
        try {

            if (listener == null || listenerThread == null) {
                Log.d(TAG, "Trying to start listener on port: " + suggestedListenPort);
                Credentials creds = new Credentials(username, password);
                listener = new LiteListener(manager, suggestedListenPort, creds);
                listenerThread = new Thread(listener);
                listenerThread.start();
            } else {
                Log.d(TAG, "Listener already running.");
            }
            return listener.getListenPort();
        } catch (Exception e) {
            dumpError(e);
        }

        return -1;
    }
    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        try {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            int resultCode = apiAvailability.isGooglePlayServicesAvailable(mActivity);
            if (resultCode != 0 ) {
                if (apiAvailability.isUserResolvableError(resultCode)) {
                    apiAvailability.getErrorDialog(mActivity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST).show();
                } else {
                    Log.i(TAG, "This device is not supported.");
                    mActivity.finish();
                }
                return false;
            }
        } catch (Exception e) {
            dumpError(e);
            return false;
        } catch (Error e) {
            Log.i(TAG, "ooooo. We got a bad one. Ignoring google play services.");
            return false;
        }
        return true;
    }

    public int start(String database_name) throws IOException, CouchbaseLiteException {
        try {
                if (dbs.get(database_name) == null) {
                    Log.d(TAG, "Trying to open database: " + database_name);
                    Database database = manager.openDatabase(database_name, options);
                    database.open(options);
                    dbs.put(database_name, database);	

                    String version = "2";
    
                    if (!database_name.equals("files") && !database_name.equals("sessiondb")) {
                        Log.d(TAG, "This is the main database: " + database_name);
                        maindb = database_name;
                        updateToken();
                    }

                    Iterator it = mappers.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry pairs = (Map.Entry)it.next();
                        String name = (String) pairs.getKey();
                        Mapper m = (Mapper) pairs.getValue();
                        Reducer r = (Reducer) reducers.get(name); 
                        Log.d(TAG, "Installing mapreduce: " + name);
                        View v = database.getView(name);
                        if (r != null) {
                            v.setMapReduce(m, r, version);
                        } else {
                            v.setMap(m, version);
                        }
                    }

                    /*
                    PersistentCookieStore cookieStore = database.getPersistentCookieStore();
                    CouchbaseLiteHttpClientFactory htf = new CouchbaseLiteHttpClientFactory(cookieStore);
                    manager.setDefaultHttpClientFactory(htf);
                    initializeSecurity(htf);
                    */
                }

                Log.d(TAG, "We're ready to go!");
                return 0;
        } catch (Exception e) {
		dumpError(e);
        }

	return -1;
    }

    public String files_go_where() {
        return manager.getDirectory() + "/";
    }

    public void stop_replication(String database_name) {
	if (pushes.get(database_name) != null) {
	     Replication push = (Replication) pushes.remove(database_name);
	     push.stop();
             Log.d(TAG, "Stopped push replication: " + database_name);
        }

	if (pulls.get(database_name) != null) {
	     Replication pull = (Replication) pulls.remove(database_name);
	     pull.stop();
             Log.d(TAG, "Stopped pull replication: " + database_name);
        }
    }

    public String compact(String database_name) throws CouchbaseLiteException {
	if (dbs.get(database_name) != null) {
             Log.d(TAG, "Compacting database: " + database_name);
	     Database db = (Database) dbs.get(database_name);
	     db.compact();
             Log.d(TAG, "Compaction finished: " + database_name);
	}
	
	return "";
    }

    public void drop(String database_name) throws CouchbaseLiteException {
        stop_replication(database_name);

        if (dbs.get(database_name) != null) {
             Database db = (Database) dbs.remove(database_name);
             db.delete();
             Log.d(TAG, "Deleted database: " + database_name);

            if (maindb.equals(database_name)) {
                Log.d(TAG, "Unsetting main database: " + maindb);
                maindb = null;
            }
        }
    }

    public void stop(String database_name) {
        stop_replication(database_name);

        if (dbs.get(database_name) != null) {
             Database db = (Database) dbs.remove(database_name);
            /* 
             * The CBL api doesn't have a 'close' database (nor in the manager).
             * At least, I couldn't find it in the documentation.
             */
             //db.close();
                 Log.d(TAG, "Closed database: " + database_name);
        }

        if (urls.get(database_name) != null) {
                urls.remove(database_name);
        }

        if (filters.get(database_name) != null) {
                filters.remove(database_name);
        }

        if (maindb.equals(database_name)) {
            Log.d(TAG, "Unsetting main database: " + maindb);
            maindb = null;
        }
    }

    public void updateView(final String js) {
            if (webview != null) {
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        webview.loadUrl("javascript: " + js);
                    }
                });
            } else {
                Log.d(TAG, "webview not alive yet.");
            }
    }

    private void updateReplication(Replication.ChangeEvent event, final String type, final String database_name) {
        Replication replication = event.getSource();
        if (!replication.isRunning()) {
            Log.d(TAG, " replicator " + replication + " is not running" + " for database " + database_name);
        } else {
            int processed = replication.getCompletedChangesCount();
            int total = replication.getChangesCount();
            Log.d(TAG, type + " Replicator processed " + processed + " / " + total + " status: " + replication.getStatus() + " for database " + database_name);
            if (total != 0) {
                final double percent = (double) Math.min(100.0, (double) processed / (double) Math.max(1, total) * 100.0);
                if (type.equals("pull")) {
                    Log.d(TAG, "(pull) " + type + " setting pull percent to " + percent + " from " + pull_percent + " for database " + database_name);
                    if (pull_percent == percent) {
                        Log.d(TAG, type + " no change." + " for database " + database_name);
                        return;
                    }
                    pull_percent = percent;
                } else {
                    Log.d(TAG, "(push) " + type + " setting push percent to " + percent + " from " + push_percent + " for database " + database_name);
                    if (push_percent == percent) {
                        Log.d(TAG, type + " no change." + " for database " + database_name);
                        return;
                    }
                    push_percent = percent;
                }

                updateView(type + "stat('" + String.format( "%.1f", percent ) + "');");
            } else if(replication.getStatus() == Replication.ReplicationStatus.REPLICATION_IDLE && type.equals("pull")) {
		pull_percent = 100.0;
		updateView(type + "stat('100');");
	    }
        }
    }

    public int filters(final String database_name, String filterparams) {
        try {
	    Log.d(TAG, "Want to set filters for DB " + database_name + " to " + filterparams);
	    if (filters.get(database_name) != null) {
		    filters.remove(database_name);
	    }
	    filters.put(database_name, filterparams);

	    Replication pull = (Replication) pulls.get(database_name);
	    if (pull == null) {
                Log.d(TAG, "No pulls, yet. Moving along.");
                return 0;
	    }
	    assert(pull != null);
            Map<String,Object> params = toJava(filterparams);
            pull.setFilter((String) params.get("name"));
            params.remove("name");
            pull.setFilterParams(params);
            Log.d(TAG, "Filters installed for DB " + database_name);
            return 0;
        } catch(Exception e) {
            dumpError(e);
            return -1; 
        }
    }

    public int replicate(final String database_name, String server, boolean force) {
        boolean nopush = false;
	String filterparams = filters.get(database_name);
	if (filterparams == null) {
		Log.e(TAG, "Missing filters for db " + database_name);
		return -1;
	}

        if (!database_name.equals("mica"))
            nopush = true;
        Log.d(TAG, "Checking for old replications..." + " db " + database_name);

        Log.d(TAG, "Parameters: " + database_name + " " + server + " " + force + " " + filterparams);
        if (pushes.get(database_name) != null || pulls.get(database_name) != null) {
            Log.w(TAG, "Database " + database_name + " is already replicating.");
            return 0;
        }

        Log.d(TAG, "Constructing URL...");

        URL url;
        try {
            url = new URL(server);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Your replication URL is not good: " + e);
            return -1;
        }
        try {
            Log.d(TAG, "creating replicator");

            if (urls.get(database_name) == null) {
                    urls.put(database_name, server);
            }

            Log.d(TAG, "checking connectivity.");
            String conn = in.connected();
            
            if (in.connected() == "expensive") {
                    Log.d(TAG, "Not going to start replication on 3G =(");
                    return 0;
            } else if(force || conn == "online") {
                    Log.d(TAG, "Starting replication!");
            } else {
                    Log.d(TAG, "No internet. Not starting replication.");
                    return 0;
            }

            Log.d(TAG, "replication getting database.");

            Database database = (Database) dbs.get(database_name);
            assert(database != null); 
            Replication pull = database.createPullReplication(url);
            Replication push = database.createPushReplication(url);
            pull.setContinuous(true);
            push.setContinuous(true);

            Log.d(TAG, "Setting change listeners for replication");

            pull.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    updateReplication(event, "pull", database_name);
                }
            });

            push.addChangeListener(new Replication.ChangeListener() {
                @Override
                public void changed(Replication.ChangeEvent event) {
                    updateReplication(event, "push", database_name);
                }
            });

            Log.d(TAG, "replication starting.... with parameters: " + filterparams);
            Map<String,Object> params = toJava(filterparams);
            pull.setFilter((String) params.get("name"));
            params.remove("name");
            pull.setFilterParams(params);
            pull.start();
            if (!nopush) {
                push.start();
	        pushes.put(database_name, push);
            }
            pulls.put(database_name, pull);
        } catch (Exception e) {
            dumpError(e);
            return -1;
        }

        return 0;
    }

    public String get_attachment_meta(String dbname, String name, String filename) throws IOException {
        try {
            Database database = (Database) dbs.get(dbname);
            Document doc = database.getExistingDocument(name);
            Log.d(TAG, "Getting size of document: " + name + " named " + filename + " db " + dbname);
            if (doc == null) {
                Log.d(TAG, "No such document: " + name + ". Cannot get attachment.");
                return null;
            } 

            Revision rev = doc.getCurrentRevision();
            //RevisionInternal irev = database.getDocumentWithIDAndRev(name, rev.getId(), EnumSet.noneOf(TDContentOptions.class));
	    RevisionInternal irev = database.getDocument(name, rev.getId(), true);
            //long seq = irev.getSequence();
            //Attachment att = database.getAttachmentForSequence(seq, filename);
            AttachmentInternal att = database.getAttachment(irev, filename);
            
            if (rev.getAttachment(filename) == null) {
            	Log.d(TAG, "It's a good thing we're not returning null for our attachement. =)");
            }

            Map<String,Object> iprops = new HashMap<String, Object>(att.asStubDictionary());
            long len = att.getLength();
            if (len == 0) {
                Log.d(TAG, "We have to do it the hard way. =(");
                InputStream temp = att.getContentInputStream();
                if (temp == null) {
                    Log.d(TAG, "ERROR: This better not happen, or we're screwed.");
                } else {
                    try {
                        int avail = 0;
                        
                        do {
                            avail = temp.available();
                            if (avail > 0) {
                                    temp.skip(avail);
                                    len += avail;
                            }
                        } while(temp.available() > 0);
                    } catch(IOException ex) {
                        Log.d(TAG, "ERROR: Skip is too big!!!!!!!!!!!!!!!!!!!!!!");
                    }
                    temp.close();
                }
            }
            iprops.put("length", len);
            return toJSON(iprops);
        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    private void dumpError(Exception e) {
	Log.e(TAG, "Error in Mica: " + e);
        String msg = e.getMessage();
        if (msg != null)
            Log.e(TAG, msg);
        msg = e.getLocalizedMessage();
        if (msg != null)
            Log.e(TAG, msg);
	Log.e(TAG, "" + e.getCause());
	Log.e(TAG, Arrays.toString(e.getStackTrace()));
    }

    private Map<String,Object> toJava(String json) throws IOException {
        return Manager.getObjectMapper().readValue(json, Map.class);
    }

    private String toJSON(Map<String,Object> objs) throws IOException {
        return Manager.getObjectMapper().writeValueAsString(objs);
    }

    public String put(String dbname, String name, String json) {
        try {
	    
            Database database = (Database) dbs.get(dbname);
            Document document = database.getDocument(name);
	    Log.d(TAG, "Want to put to key " + name + " with a length: " + json.length() + " db " + dbname);
            Map<String, Object> properties = toJava(json);
            document.putProperties(properties);
            Log.d(TAG, "Revision committed for key " + name);
            return "";
        } catch(Exception e) {
            dumpError(e);
            return Arrays.toString(e.getStackTrace());
        }
    }

    public String get(String dbname, String name) {
        try {
            Database database = (Database) dbs.get(dbname);
            Document doc = database.getExistingDocument(name);
            if (doc == null) {
                    //Log.w(TAG, "DB " + dbname + " Document is not here: " + name);
                    return "";
            } 
            return toJSON(doc.getProperties());
        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    public String info(String dbname) {
        Map<String, Object> info = new HashMap<String, Object>();
        try {
            Database database = (Database) dbs.get(dbname);
            info.put("size", database.totalDataSize());
            info.put("db_name", database.getName());
            info.put("db_uuid", database.publicUUID());
            info.put("doc_count", database.getDocumentCount());
            info.put("update_seq", database.getLastSequenceNumber());
            info.put("disk_size", database.totalDataSize());
            info.put("instance_start_time", database.getStartTime() * 1000);

            return toJSON(info);
        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    public String delete(String dbname, String name) {
        try {
            Database database = (Database) dbs.get(dbname);
            Document doc = database.getExistingDocument(name);
            if (doc == null) {
                    return "Failure. Document does not exist.";
            } 
            doc.delete(); 
            return "";
        } catch(Exception e) {
            dumpError(e);
            return Arrays.toString(e.getStackTrace());
        }
    }

    public static InputStream decompressStream(InputStream input) throws IOException {
         PushbackInputStream pb = new PushbackInputStream( input, 2 );
         byte [] signature = new byte[2];
         pb.read( signature );
         pb.unread( signature );
         if( signature[ 0 ] == (byte) 0x1f && signature[ 1 ] == (byte) 0x8b )  {
            Log.d(TAG, "Attachment is compressed. Need to decompress it first.");
           return new GZIPInputStream( pb );
         } else {
           return pb;
         }
    }

    public byte[] get_attachment(String dbname, String name, String filename) throws IOException {
        try {
            Database database = (Database) dbs.get(dbname);
            Document doc = database.getExistingDocument(name);
            Log.d(TAG, "Looking up attachment from document: " + name + " named " + filename + " db " + dbname);
            if (doc == null) {
                Log.d(TAG, "No such document: " + name + ". Cannot get attachment.");
                return null;
            } 

            Revision rev = doc.getCurrentRevision();
            Attachment att = rev.getAttachment(filename);
            if (att == null) {
                Log.d(TAG, "Document: " + name + " has no such attachment: " + filename);
            }

            InputStream is = decompressStream(att.getContent());
            byte[] result;
            //if (att.getGZipped()) // doesn't work
            /* couchdb is lying. use magic number method later. I filed a bug on github. No response yet. */

            result = IOUtils.toByteArray(is);
            //Log.d(TAG, "Got " + result.length + " bytes.");
            return result;
        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    public String get_attachment_to_path(String dbname, String name, String filename, String path) throws IOException {
        try {
            Database database = (Database) dbs.get(dbname);
            Log.d(TAG, "Looking up attachment from document: " + name + " named " + filename + " db " + dbname);
            Document doc = database.getExistingDocument(name);
            if (doc == null) {
                Log.e(TAG, "No such document: " + name + ". Cannot get attachment to send to path.");
                return null;
            } 

            Revision rev = doc.getCurrentRevision();
            Attachment att = rev.getAttachment(filename);
            if (att == null) {
            	//RevisionInternal irev = database.getDocumentWithIDAndRev(name, rev.getId(), EnumSet.noneOf(TDContentOptions.class));
                RevisionInternal irev = database.getDocument(name, rev.getId(), true);
                //long seq = irev.getSequence();
                //att = database.getAttachmentForSequence(seq, filename);
                AttachmentInternal atti = database.getAttachment(irev, filename);
            	if (atti == null) {
            		Log.e(TAG, "Document: " + name + " has no such attachment to send to path: " + filename);
            		return null;
            	}
                Log.w(TAG, "Document: " + name + " workaround succeded for attachment to send to path: " + filename);
            }

            InputStream is = decompressStream(att.getContent());
            OutputStream os = new FileOutputStream(path);
            IOUtils.copy(is, os);
            is.close();
            os.close();

            Log.d(TAG, "Document: " + name + " exported to " + path);
            
            return "success";
        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    public String doc_exist(String dbname, String name) {
        try {
            Database database = (Database) dbs.get(dbname);
            Document doc = database.getExistingDocument(name);
            if (doc == null) {
                    return "false";
            } 
            return "true";
        } catch(Exception e) {
            dumpError(e);
            return "error";
        }
    }

    private View compileView(Database db, String viewName, Map<String,Object> viewProps) {
        Log.d(TAG, "Recompiling view.");
        String language = (String)viewProps.get("language");
        if(language == null) {
            language = "javascript";
        }
        String mapSource = (String)viewProps.get("map");
        if(mapSource == null) {
            return null;
        }
        Mapper mapBlock = View.getCompiler().compileMap(mapSource, language);
        if(mapBlock == null) {
            Log.w(TAG, "View " + viewName + " has unknown map function: " + mapSource);
            return null;
        }
        String reduceSource = (String)viewProps.get("reduce");
        Reducer reduceBlock = null;
        if(reduceSource != null) {
            Log.d(TAG, "Recompiling view's reducer as well.");
            reduceBlock = View.getCompiler().compileReduce(reduceSource, language);
            if(reduceBlock == null) {
                Log.w(TAG, "View " + viewName + " has unknown reduce function: " + reduceBlock);
                return null;
            }
        } else {
            //Log.d(TAG, "View has no reducer. Skipping.");
        }

        View view = db.getView(viewName);
        view.setMapReduce(mapBlock, reduceBlock, "1");

        if (reduceSource != null) {
             //Log.d(TAG, "Asserting view " + viewName + " got its reducer.");
             assert(view.getReduce() != null);
        }

        String collation = (String)viewProps.get("collation");


        if("raw".equals(collation)) {
            view.setCollation(TDViewCollation.TDViewCollationRaw);
        }

        return view;
    }


    private Status queryDesignDoc(Database db, String designDoc, String viewName, boolean force) throws CouchbaseLiteException {
        String tdViewName = String.format("%s/%s", designDoc, viewName);
        View view = db.getExistingView(tdViewName);
        if(force || view == null || view.getMap() == null) {
            // No TouchDB view is defined, or it hasn't had a map block assigned;
            // see if there's a CouchDB view definition we can compile:
            //RevisionInternal rev = db.getDocumentWithIDAndRev(String.format("_design/%s", designDoc), null, EnumSet.noneOf(TDContentOptions.class));
	    RevisionInternal rev = db.getDocument(String.format("_design/%s", designDoc), null, true);
            if(rev == null) {
                return new Status(Status.NOT_FOUND);
            }
            Map<String,Object> views = (Map<String,Object>)rev.getProperties().get("views");
            Map<String,Object> viewProps = (Map<String,Object>)views.get(viewName);
            if(viewProps == null) {
                return new Status(Status.NOT_FOUND);
            }
            // If there is a CouchDB view, see if it can be compiled from source:
            view = compileView(db, tdViewName, viewProps);
            if(view == null) {
                return new Status(Status.INTERNAL_SERVER_ERROR);
            }
        }

        view.updateIndex();

        return new Status(Status.OK);
    }

    public void view_seed(String uuid, String username, String key_value) {
//    public void view_seed(String uuid, String key_value) {
        if(seeds.get(uuid) == null) {
            //Log.d(TAG, "New set of seeds for uuid " + uuid + ", example: " + key_value);
            List<Object> keylist = new ArrayList<Object>();
            seeds.put(uuid, keylist);
        }
//        ((List<Object>) seeds.get(uuid)).add(key_value);
        List<String> keypair = new ArrayList<String>();
        keypair.add(username);
        keypair.add(key_value);
        ((List<Object>) seeds.get(uuid)).add(keypair);
    }

    public void view_seed_cleanup(String uuid) {
        if(seeds.get(uuid) != null) {
            //Log.d(TAG, "Flushing seed keys for uuid " + uuid);
            seeds.remove(uuid);
        } 
	//Log.d(TAG, "Total views in progress: " + seeds.size());
    }

    private View rebuildView(Database database, String designDoc, String viewName, boolean force) throws CouchbaseLiteException {
	View v = null;
        String name = designDoc + "/" + viewName;
	int status = queryDesignDoc(database, designDoc, viewName, force).getCode();
	if (status == Status.OK) {
	    Log.d(TAG, "View pulled in from disk " + name + ".");
	    v = database.getExistingView(name);
	    assert(v != null);
	    //assert(v.getViewId() > 0);
	} else {
	    Log.d(TAG, "Could not pull in view from disk: " + status);
	    return null;
	}

	return v;
    }
    public Iterator<QueryRow> view(String dbname, String designDoc, String viewName, String parameters, String username) {
        try {
            Database database = (Database) dbs.get(dbname);
            String name = designDoc + "/" + viewName;
            View v = database.getExistingView(name);
            Query query = v.createQuery();

            if (parameters != null && parameters.length() > 0 && !(parameters.equals(""))) {
                //Log.d(TAG, "Converting parameters to objects: length: " + parameters.length() + ", contents: *" + parameters + "*");
                Map<String, Object> properties = toJava(parameters);

                //Log.d(TAG, "Storing parameters to query: " + toJSON(properties));

                if (properties.get("startkey") != null && properties.get("endkey") != null) {
                    //Log.d(TAG, "Setting start and endkey");
                    query.setStartKey((List<Object>) properties.get("startkey"));
                    query.setEndKey((List<Object>) properties.get("endkey"));
                } else if(properties.get("keys") != null) {
                    String uuid = (String) properties.get("keys");
                    //Log.d(TAG, "Setting seeds keys for uuid: " + uuid);
                    List<Object> keylist = (List<Object>) seeds.get(uuid);
                    assert(keylist != null);
                    seeds.remove(uuid);
                    query.setKeys(keylist);
                    //Log.d(TAG, "Finished setting seeds keys for uuid: " + uuid);
                } else if(properties.get("stale") != null) {
                    Log.d(TAG, "WARNING: View request 'stale' parameter not supported!");
                } 
            }
                
            //Log.d(TAG, "Running query");
            QueryEnumerator rowEnum = query.run();

            //Log.d(TAG, "Query complete. Extracting results.");

            if (rowEnum.getCount() > 0) {
                //Log.d(TAG, "Returning final view results: " + rowEnum.getCount());
                return rowEnum;
            }

            return rowEnum;

        } catch(CouchbaseLiteException e) {
            dumpError(e);
            return null;
        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    public boolean view_has_next(Iterator<QueryRow> it) {
        assert(it != null);
        boolean has_next = it.hasNext();
	/*
        if (has_next) {
            Log.d(TAG, "View iterator not exhausted yet.");
        } else {
            Log.d(TAG, "View iterator is exhausted.");
        }
	*/
        return has_next;
    }

    public String view_next(Iterator<QueryRow> it) {
        assert(it != null);
        assert(it.hasNext());

        try {
            Map<String, Object> dict = new HashMap<String, Object>();
            Map<String, Object> result = new HashMap<String, Object>();
            QueryRow row = it.next();

            result.put("key", row.getKey());
            result.put("value", row.getValue());
            dict.put("result", result);

            return toJSON(dict);

        } catch(Exception e) {
            dumpError(e);
            return null;
        }
    }

    public String get_pull_percent() {
        String per = String.format( "%.1f", pull_percent );
        //Log.d(TAG, "Pull Returning " + per + " percent from " + pull_percent);
        return per;
    }

    public String get_push_percent() {
        String per = String.format( "%.1f", push_percent );
        //Log.d(TAG, "Push Returning " + per + " percent from " + push_percent);
        return per;
    }

    public String get_language() {
        String l = Locale.getDefault().getLanguage();
        Log.d(TAG, "MICA Language should be: " + l);
        return l;
    }
}

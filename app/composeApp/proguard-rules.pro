-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }

-keep class com.couchbase.lite.LiteCoreException { static <methods>; }
-keep class com.couchbase.lite.internal.replicator.CBLTrustManager {
    public java.util.List checkServerTrusted(java.security.cert.X509Certificate[], java.lang.String, java.lang.String);
}
-keep class com.couchbase.lite.internal.ReplicationCollection {
    static <methods>;
    <fields>;
}
-keep class com.couchbase.lite.internal.core.C4* {
    static <methods>;
    <fields>;
    <init>(...);
}
-keep class com.couchbase.lite.internal.fleece.* {
    static <methods>;
    <fields>;
    <init>(...);
}

## Rules for NewPipeExtractor
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter

-keep class de.kherud.llama.** { *; }

-dontwarn **


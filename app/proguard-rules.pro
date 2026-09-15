# Keep kotlinx.serialization generated serializers for the protocol DTOs shared through :domain,
# plus the generated $$serializer classes kotlinx.serialization looks up reflectively.
-keepclassmembers class br.com.colman.palavramento.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class br.com.colman.palavramento.**$$serializer {
    *** INSTANCE;
}
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# Ktor client (OkHttp engine, WebSockets, content negotiation) ships with its own consumer rules,
# but pulls in optional integrations (Conscrypt, Netty, coroutines-debug) that are never on the
# runtime classpath here.
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp/Okio: same story, optional platform integrations this app never uses.
-dontwarn okhttp3.**
-dontwarn okio.**

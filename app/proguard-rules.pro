# Keep kotlinx.serialization generated serializers for the protocol DTOs shared through :domain.
-keepclassmembers class br.com.colman.palavramento.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

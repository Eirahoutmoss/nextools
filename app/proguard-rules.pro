# Compose kendi kurallarını getirir. Ek kural gerekmez.
# Hata kaydında okunur yığın izi için satır numaralarını koru.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kimlik Kartı: JMRTD, SCUBA ve BouncyCastle yansıma (reflection) ile sınıf arar; küçültmede korunmalı.
-keep class org.jmrtd.** { *; }
-keep class net.sf.scuba.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.ejbca.cvc.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.ejbca.cvc.**
-dontwarn javax.naming.**
-dontwarn javax.smartcardio.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
# JPEG 2000 çözücü adla aranır (JmrtdReader.decodeImage)
-keep class com.gemalto.jp2.** { *; }
-keep class dev.keiji.jp2.** { *; }

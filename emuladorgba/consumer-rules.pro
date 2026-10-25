# Consumer ProGuard rules for the Emulador Game Boy Advance library
-keep class pelkidev.com.mx.emuladorgba.EmuladorGbaGameModule { *; }

# JNI: libemuladorgba_mgba.so busca estos métodos por nombre de clase y método
-keepclasseswithmembernames class pelkidev.com.mx.emuladorgba.data.MgbaCore {
    native <methods>;
}

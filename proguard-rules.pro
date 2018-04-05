-keepparameternames
-renamesourcefileattribute SourceFile
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

# Keep all public/protected classes/methods/fields, except for classes in the internal package.
# These will be obfuscated and should not be used externally.
-keep public class !com.android.volley.internal.**,** {
    public protected *;
}
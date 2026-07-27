# ML Kit discovers component registrars declared in the merged manifest by
# class name. Retain the reflective constructor and registration methods.
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }

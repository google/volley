package com.android.volley;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import com.google.common.reflect.ClassPath;
import com.google.common.truth.Truth;

import org.junit.Test;
import org.robolectric.util.Strings;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test for Volley's public API signatures.
 */
public class VolleyApisTest {
    private static final List<String> TEST_CLASS_PREFIX_BLACKLIST = ImmutableList.of(
            "com.android.volley.mock",
            "com.android.volley.utils.CacheTestUtils",
            "com.android.volley.utils.ImmediateResponseDelivery");

    @Test
    public void noUnexpectedApis() throws Exception {
        String apiText = Resources.toString(Resources.getResource("api.txt"), Charsets.UTF_8);
        List<String> expectedApis = Splitter.on("\n").splitToList(apiText.trim());

        List<String> actualApis = new ArrayList<>();
        ClassPath classPath = ClassPath.from(ClassLoader.getSystemClassLoader());
        for (ClassPath.ClassInfo classInfo :
                classPath.getTopLevelClassesRecursive("com.android.volley")) {
            Class<?> clazz = classInfo.load();
            processClass(clazz, actualApis);
        }
        Collections.sort(actualApis);
        Truth.assertWithMessage("The API signature of Volley has changed. If this is expected, " +
                "update src/test/resources/api.txt with the new APIs.")
                .that(actualApis)
                .containsExactlyElementsIn(expectedApis)
                .inOrder();
    }

    private void processClass(Class<?> clazz, List<String> apis) {
        if (isTestOrInternalClass(clazz.getName())) {
            return;
        }
        if (!isVisible(clazz.getModifiers())) {
            return;
        }
        apis.add(clazz.getName() + ": class " + getSignature(clazz));
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (!isVisible(constructor.getModifiers())) {
                continue;
            }
            apis.add(clazz.getName() + ": ctor " + constructor.toGenericString());
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (!isVisible(field.getModifiers())) {
                continue;
            }
            // TODO: Consider also locking down field values.
            apis.add(clazz.getName() + ": field " + field.toGenericString());
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (!isVisible(method.getModifiers())) {
                continue;
            }
            apis.add(clazz.getName() + ": method " + method.toGenericString());
        }
        for (Class<?> innerClazz : clazz.getDeclaredClasses()) {
            processClass(innerClazz, apis);
        }
    }

    private boolean isTestOrInternalClass(String className) {
        if (className.startsWith("com.android.volley.internal")) {
            // Classes in this package are obfuscated and thus not easily accessible externally.
            // We do not consider them part of the public API.
            return true;
        }
        // Best-effort attempt at excluding everything in src/test/java, since it's all part of the
        // same class path.
        if (className.endsWith("Test")) {
            return true;
        }
        for (String prefix : TEST_CLASS_PREFIX_BLACKLIST) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private String getSignature(Class<?> clazz) {
        StringBuilder signature = new StringBuilder(Modifier.toString(clazz.getModifiers()));
        if (clazz.getSuperclass() != null &&
                !Strings.equals("java.lang.Object", clazz.getSuperclass().getName())) {
            signature.append(", extends ").append(clazz.getSuperclass().getName());
        }
        if (clazz.getGenericInterfaces().length > 0) {
            signature.append(", implements");
            for (Class<?> iface : clazz.getInterfaces()) {
                signature.append(" ").append(iface.getName());
            }
        }
        return signature.toString();
    }
}

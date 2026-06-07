package com.fankes.coloros.notify.hook.reflect

import java.lang.reflect.Field
import java.lang.reflect.Method

internal object Reflection {

    fun loadClassOrNull(
        name: String,
        classLoader: ClassLoader,
        onMissing: (Throwable) -> Unit,
    ): Class<*>? = runCatching {
        Class.forName(name, false, classLoader)
    }.getOrElse {
        onMissing(it)
        null
    }

    fun findField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredFields.firstOrNull { it.name == name }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }

    fun findMethod(clazz: Class<*>, name: String, vararg params: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            current.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterTypes.contentEquals(params)
            }?.let {
                it.isAccessible = true
                return it
            }
            current.declaredMethods.firstOrNull { method ->
                method.name == name && method.parameterTypes.size == params.size
            }?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        return null
    }
}

package com.storyteller_f.a.api.client

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.internal.NamedValueEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer
import kotlin.reflect.KClass

@OptIn(InternalSerializationApi::class)
fun <T : Any> encodeQueryParams(value: T, clazz: KClass<T>): Map<String, List<String>> {
    val serializer = clazz.serializer()
    val encoder = CustomParameterEncoder(clazz, serializer)
    encoder.encodeSerializableValue(serializer, value)
    return encoder.map
}

@OptIn(InternalSerializationApi::class)
class CustomParameterEncoder<T : Any>(
    clazz: KClass<T>,
    serializer: KSerializer<T>
) : NamedValueEncoder() {

    // 支持多个同名 key，避免 List 参数丢失
    val map = mutableMapOf<String, MutableList<String>>()

    override val serializersModule: SerializersModule =
        serializersModuleOf(clazz, serializer)

    override fun encodeTaggedValue(tag: String, value: Any) {
        val newTag = tag.substringBeforeLast(".")
        when (value) {
            is Iterable<*> -> value.forEach { item ->
                if (item != null) {
                    addToMap(newTag, item.toString())
                }
            }
            is Array<*> -> value.forEach { item ->
                if (item != null) {
                    addToMap(newTag, item.toString())
                }
            }
            else -> addToMap(newTag, value.toString())
        }
    }

    override fun encodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor, ordinal: Int) {
        addToMap(tag, enumDescriptor.getElementName(ordinal))
    }

    override fun encodeTaggedNull(tag: String) {
        // 可选：是否要添加 null 值，通常 query 参数中跳过 null
    }

    private fun addToMap(key: String, value: String) {
        map.getOrPut(key) { mutableListOf() }.add(value)
    }
}

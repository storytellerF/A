package com.storyteller_f.a.api.client

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.internal.NamedValueEncoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlin.reflect.KClass

@OptIn(InternalSerializationApi::class)
fun <T : Any> encodeQueryParams(value: T, clazz: KClass<T>, serializer: KSerializer<T>): Map<String, String> {
    val encoder = CustomParameterEncoder(clazz, serializer)
    encoder.encodeSerializableValue(serializer, value)
    return encoder.map
}

@OptIn(InternalSerializationApi::class)
class CustomParameterEncoder<T : Any>(clazz: KClass<T>, serializer: KSerializer<T>) : NamedValueEncoder() {
    val map = mutableMapOf<String, String>()
    override val serializersModule: SerializersModule = serializersModuleOf(clazz, serializer)

    override fun encodeTaggedValue(tag: String, value: Any) {
        map[tag] = value.toString()
    }

    override fun encodeTaggedEnum(tag: String, enumDescriptor: SerialDescriptor, ordinal: Int) {
        map[tag] = enumDescriptor.getElementName(ordinal)
    }

    override fun encodeTaggedNull(tag: String) = Unit
}

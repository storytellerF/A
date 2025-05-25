package com.storyteller_f.shared.type

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ImmutableListSerializer<T>(elementSerializer: KSerializer<T>) : KSerializer<ImmutableList<T>> {
    private val listSerializer = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor =
        SerialDescriptor("kotlinx.collections.immutable.ImmutableList", listSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: ImmutableList<T>) {
        listSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ImmutableList<T> {
        return listSerializer.deserialize(decoder).toImmutableList()
    }
}

typealias CustomImmutableList<T> =
    @Serializable(ImmutableListSerializer::class)
    ImmutableList<T>

class ImmutableMapSerializer<K, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
) : KSerializer<ImmutableMap<K, V>> {

    private val mapSerializer = MapSerializer(keySerializer, valueSerializer)

    override val descriptor: SerialDescriptor =
        SerialDescriptor("kotlinx.collections.immutable.ImmutableMap", mapSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: ImmutableMap<K, V>) {
        mapSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ImmutableMap<K, V> {
        return mapSerializer.deserialize(decoder).toImmutableMap()
    }
}
typealias CustomImmutableMap<K, V> =
    @Serializable(ImmutableMapSerializer::class)
    ImmutableMap<K, V>

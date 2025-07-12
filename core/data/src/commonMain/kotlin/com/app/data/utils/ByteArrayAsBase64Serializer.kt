package com.app.data.utils

import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeCollection


private const val INITIAL_SIZE = 10

@PublishedApi
internal object ByteArraySerializer : KSerializer<ByteArray>,
    PrimitiveArraySerializer<Byte, ByteArray, ByteArrayBuilder>(Byte.serializer()) {

    override fun ByteArray.collectionSize(): Int = size
    override fun ByteArray.toBuilder(): ByteArrayBuilder = ByteArrayBuilder(this)
    override fun empty(): ByteArray = ByteArray(0)

    override fun readElement(decoder: CompositeDecoder, index: Int, builder: ByteArrayBuilder, checkIndex: Boolean) {
        builder.append(decoder.decodeByteElement(ByteArraySerializer.descriptor, index))
    }

    override fun writeContent(encoder: CompositeEncoder, content: ByteArray, size: Int) {
        for (i in 0 until size)
            encoder.encodeByteElement(ByteArraySerializer.descriptor, i, content[i])
    }
}

@PublishedApi
internal abstract class PrimitiveArraySerializer<Element, Array, Builder
: PrimitiveArrayBuilder<Array>> internal constructor(
    primitiveSerializer: KSerializer<Element>
) : CollectionLikeSerializer<Element, Array, Builder>(primitiveSerializer) {
    final override val descriptor: SerialDescriptor = PrimitiveArrayDescriptor(primitiveSerializer.descriptor)

    final override fun Builder.builderSize(): Int = position
    final override fun Builder.toResult(): Array = build()
    final override fun Builder.checkCapacity(size: Int): Unit = ensureCapacity(size)

    final override fun Array.collectionIterator(): Iterator<Element> =
        error("This method lead to boxing and must not be used, use writeContents instead")

    final override fun Builder.insert(index: Int, element: Element): Unit =
        error("This method lead to boxing and must not be used, use Builder.append instead")

    final override fun builder(): Builder = empty().toBuilder()

    protected abstract fun empty(): Array

    abstract override fun readElement(
        decoder: CompositeDecoder,
        index: Int,
        builder: Builder,
        checkIndex: Boolean
    )

    protected abstract fun writeContent(encoder: CompositeEncoder, content: Array, size: Int)

    final override fun serialize(encoder: Encoder, value: Array) {
        val size = value.collectionSize()
        encoder.encodeCollection(descriptor, size) {
            writeContent(this, value, size)
        }
    }

    @OptIn(InternalSerializationApi::class)
    final override fun deserialize(decoder: Decoder): Array = merge(decoder, null)
}

@PublishedApi
internal abstract class PrimitiveArrayBuilder<Array> internal constructor() {
    internal abstract val position: Int
    internal abstract fun ensureCapacity(requiredCapacity: Int = position + 1)
    internal abstract fun build(): Array
}

@PublishedApi
internal class ByteArrayBuilder internal constructor(
    bufferWithData: ByteArray
) : PrimitiveArrayBuilder<ByteArray>() {

    private var buffer: ByteArray = bufferWithData
    override var position: Int = bufferWithData.size
        private set

    init {
        ensureCapacity(INITIAL_SIZE)
    }

    override fun ensureCapacity(requiredCapacity: Int) {
        if (buffer.size < requiredCapacity)
            buffer = buffer.copyOf(requiredCapacity.coerceAtLeast(buffer.size * 2))
    }

    internal fun append(c: Byte) {
        ensureCapacity()
        buffer[position++] = c
    }

    override fun build() = buffer.copyOf(position)
}

@InternalSerializationApi
public sealed class AbstractCollectionSerializer<Element, Collection, Builder> : KSerializer<Collection> {
    protected abstract fun Collection.collectionSize(): Int
    protected abstract fun Collection.collectionIterator(): Iterator<Element>
    protected abstract fun builder(): Builder
    protected abstract fun Builder.builderSize(): Int
    protected abstract fun Builder.toResult(): Collection
    protected abstract fun Collection.toBuilder(): Builder
    protected abstract fun Builder.checkCapacity(size: Int)

    abstract override fun serialize(encoder: Encoder, value: Collection)

    @OptIn(ExperimentalSerializationApi::class)
    @InternalSerializationApi
    public fun merge(decoder: Decoder, previous: Collection?): Collection {
        val builder = previous?.toBuilder() ?: builder()
        val startIndex = builder.builderSize()
        val compositeDecoder = decoder.beginStructure(descriptor)
        if (compositeDecoder.decodeSequentially()) {
            readAll(compositeDecoder, builder, startIndex, readSize(compositeDecoder, builder))
        } else {
            while (true) {
                val index = compositeDecoder.decodeElementIndex(descriptor)
                if (index == CompositeDecoder.DECODE_DONE) break
                readElement(compositeDecoder, startIndex + index, builder)
            }
        }
        compositeDecoder.endStructure(descriptor)
        return builder.toResult()
    }

    override fun deserialize(decoder: Decoder): Collection = merge(decoder, null)

    private fun readSize(decoder: CompositeDecoder, builder: Builder): Int {
        val size = decoder.decodeCollectionSize(descriptor)
        builder.checkCapacity(size)
        return size
    }

    protected abstract fun readElement(decoder: CompositeDecoder, index: Int, builder: Builder, checkIndex: Boolean = true)

    protected abstract fun readAll(decoder: CompositeDecoder, builder: Builder, startIndex: Int, size: Int)
}



@OptIn(InternalSerializationApi::class)
@PublishedApi
internal sealed class CollectionLikeSerializer<Element, Collection, Builder>(
    private val elementSerializer: KSerializer<Element>
) : com.app.data.utils.AbstractCollectionSerializer<Element, Collection, Builder>() {

    protected abstract fun Builder.insert(index: Int, element: Element)
    abstract override val descriptor: SerialDescriptor

    override fun serialize(encoder: Encoder, value: Collection) {
        val size = value.collectionSize()
        encoder.encodeCollection(descriptor, size) {
            val iterator = value.collectionIterator()
            for (index in 0 until size)
                encodeSerializableElement(descriptor, index, elementSerializer, iterator.next())
        }
    }

    final override fun readAll(decoder: CompositeDecoder, builder: Builder, startIndex: Int, size: Int) {
        require(size >= 0) { "Size must be known in advance when using READ_ALL" }
        for (index in 0 until size)
            readElement(decoder, startIndex + index, builder, checkIndex = false)
    }

    override fun readElement(decoder: CompositeDecoder, index: Int, builder: Builder, checkIndex: Boolean) {
        builder.insert(index, decoder.decodeSerializableElement(descriptor, index, elementSerializer))
    }
}


@OptIn(ExperimentalSerializationApi::class)
internal class PrimitiveArrayDescriptor internal constructor(
    primitive: SerialDescriptor
) : ListLikeDescriptor(primitive) {
    override val serialName: String = "${primitive.serialName}Array"
}


@OptIn(SealedSerializationApi::class)
internal sealed class ListLikeDescriptor(val elementDescriptor: SerialDescriptor) : SerialDescriptor {
    override val kind: SerialKind get() = StructureKind.LIST
    override val elementsCount: Int = 1

    override fun getElementName(index: Int): String = index.toString()
    override fun getElementIndex(name: String): Int =
        name.toIntOrNull() ?: throw IllegalArgumentException("$name is not a valid list index")

    override fun isElementOptional(index: Int): Boolean {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
        return false
    }

    override fun getElementAnnotations(index: Int): List<Annotation> {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
        return emptyList()
    }

    override fun getElementDescriptor(index: Int): SerialDescriptor {
        require(index >= 0) { "Illegal index $index, $serialName expects only non-negative indices"}
        return elementDescriptor
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ListLikeDescriptor) return false
        if (elementDescriptor == other.elementDescriptor && serialName == other.serialName) return true
        return false
    }

    override fun hashCode(): Int {
        return elementDescriptor.hashCode() * 31 + serialName.hashCode()
    }

    override fun toString(): String = "$serialName($elementDescriptor)"
}


@SubclassOptInRequired(SealedSerializationApi::class)
public interface SerialDescriptor {
    /**
     * Serial name of the descriptor that identifies a pair of the associated serializer and target class.
     *
     * For generated and default serializers, the serial name is equal to the corresponding class's fully qualified name
     * or, if overridden, [SerialName].
     * Custom serializers should provide a unique serial name that identifies both the serializable class and
     * the serializer itself, ignoring type arguments if they are present, for example: `my.package.LongAsTrimmedString`.
     *
     * Do not confuse with [getElementName], which returns property name:
     *
     * ```
     * package my.app
     *
     * @Serializable
     * class User(val name: String)
     *
     * val userDescriptor = User.serializer().descriptor
     *
     * userDescriptor.serialName // Returns "my.app.User"
     * userDescriptor.getElementName(0) // Returns "name"
     * ```
     */
    public val serialName: String

    /**
     * The kind of the serialized form that determines **the shape** of the serialized data.
     * Formats use serial kind to add and parse serializer-agnostic metadata to the result.
     *
     * For example, JSON format wraps [classes][StructureKind.CLASS] and [StructureKind.MAP] into
     * brackets, while ProtoBuf just serialize these types in separate ways.
     *
     * Kind should be consistent with the implementation, for example, if it is a [primitive][PrimitiveKind],
     * then its element count should be zero and vice versa.
     *
     * Example of introspecting kinds:
     *
     * ```
     * @Serializable
     * class User(val name: String)
     *
     * val userDescriptor = User.serializer().descriptor
     *
     * userDescriptor.kind // Returns StructureKind.CLASS
     * userDescriptor.getElementDescriptor(0).kind // Returns PrimitiveKind.STRING
     * ```
     */
    public val kind: SerialKind

    /**
     * Whether the descriptor describes a nullable type.
     * Returns `true` if associated serializer can serialize/deserialize nullable elements of the described type.
     *
     * Example:
     *
     * ```
     * @Serializable
     * class User(val name: String, val alias: String?)
     *
     * val userDescriptor = User.serializer().descriptor
     *
     * userDescriptor.isNullable // Returns false
     * userDescriptor.getElementDescriptor(0).isNullable // Returns false
     * userDescriptor.getElementDescriptor(1).isNullable // Returns true
     * ```
     */
    public val isNullable: Boolean get() = false

    /**
     * Returns `true` if this descriptor describes a serializable value class which underlying value
     * is serialized directly.
     *
     * This property is true for serializable `@JvmInline value` classes:
     * ```
     * @Serializable
     * class User(val name: Name)
     *
     * @Serializable
     * @JvmInline
     * value class Name(val value: String)
     *
     * User.serializer().descriptor.isInline // false
     * User.serializer().descriptor.getElementDescriptor(0).isInline // true
     * Name.serializer().descriptor.isInline // true
     * ```
     */
    public val isInline: Boolean get() = false

    /**
     * The number of elements this descriptor describes, besides from the class itself.
     * [elementsCount] describes the number of **semantic** elements, not the number
     * of actual fields/properties in the serialized form, even though they frequently match.
     *
     * For example, for the following class
     * `class Complex(val real: Long, val imaginary: Long)` the corresponding descriptor
     * and the serialized form both have two elements, while for `List<Int>`
     * the corresponding descriptor has a single element (`IntDescriptor`, the type of list element),
     * but from zero up to `Int.MAX_VALUE` values in the serialized form:
     *
     * ```
     * @Serializable
     * class Complex(val real: Long, val imaginary: Long)
     *
     * Complex.serializer().descriptor.elementsCount // Returns 2
     *
     * @Serializable
     * class OuterList(val list: List<Int>)
     *
     * OuterList.serializer().descriptor.getElementDescriptor(0).elementsCount // Returns 1
     * ```
     */
    public val elementsCount: Int

    /**
     * Returns serial annotations of the associated class.
     * Serial annotations can be used to specify additional metadata that may be used during serialization.
     * Only annotations marked with [SerialInfo] are added to the resulting list.
     *
     * Do not confuse with [getElementAnnotations]:
     * ```
     * @Serializable
     * @OnClassSerialAnnotation
     * class Nested(...)
     *
     * @Serializable
     * class Outer(@OnPropertySerialAnnotation val nested: Nested)
     *
     * val outerDescriptor = Outer.serializer().descriptor
     *
     * outerDescriptor.getElementAnnotations(0) // Returns [@OnPropertySerialAnnotation]
     * outerDescriptor.getElementDescriptor(0).annotations // Returns [@OnClassSerialAnnotation]
     * ```
     */
    public val annotations: List<Annotation> get() = emptyList()

    /**
     * Returns a positional name of the child at the given [index].
     * Positional name represents a corresponding property name in the class, associated with
     * the current descriptor.
     *
     * Do not confuse with [serialName], which returns class name:
     *
     * ```
     * package my.app
     *
     * @Serializable
     * class User(val name: String)
     *
     * val userDescriptor = User.serializer().descriptor
     *
     * userDescriptor.serialName // Returns "my.app.User"
     * userDescriptor.getElementName(0) // Returns "name"
     * ```
     *
     * @throws IndexOutOfBoundsException for an illegal [index] values.
     * @throws IllegalStateException if the current descriptor does not support children elements (e.g. is a primitive)
     */
    public fun getElementName(index: Int): String

    /**
     * Returns an index in the children list of the given element by its name or [CompositeDecoder.UNKNOWN_NAME]
     * if there is no such element.
     * The resulting index, if it is not [CompositeDecoder.UNKNOWN_NAME], is guaranteed to be usable with [getElementName].
     *
     * Example:
     *
     * ```
     * @Serializable
     * class User(val name: String, val alias: String?)
     *
     * val userDescriptor = User.serializer().descriptor
     *
     * userDescriptor.getElementIndex("name") // Returns 0
     * userDescriptor.getElementIndex("alias") // Returns 1
     * userDescriptor.getElementIndex("lastName") // Returns CompositeDecoder.UNKNOWN_NAME = -3
     * ```
     */
    public fun getElementIndex(name: String): Int

    /**
     * Returns serial annotations of the child element at the given [index].
     * This method differs from `getElementDescriptor(index).annotations` by reporting only
     * element-specific annotations:
     * ```
     * @Serializable
     * @OnClassSerialAnnotation
     * class Nested(...)
     *
     * @Serializable
     * class Outer(@OnPropertySerialAnnotation val nested: Nested)
     *
     * val outerDescriptor = Outer.serializer().descriptor
     *
     * outerDescriptor.getElementAnnotations(0) // Returns [@OnPropertySerialAnnotation]
     * outerDescriptor.getElementDescriptor(0).annotations // Returns [@OnClassSerialAnnotation]
     * ```
     * Only annotations marked with [SerialInfo] are added to the resulting list.
     *
     * @throws IndexOutOfBoundsException for an illegal [index] values.
     * @throws IllegalStateException if the current descriptor does not support children elements (e.g. is a primitive).
     */
    public fun getElementAnnotations(index: Int): List<Annotation>

    /**
     * Retrieves the descriptor of the child element for the given [index].
     * For the property of type `T` on the position `i`, `getElementDescriptor(i)` yields the same result
     * as for `T.serializer().descriptor`, if the serializer for this property is not explicitly overridden
     * with `@Serializable(with = ...`)`, [Polymorphic] or [Contextual].
     * This method can be used to completely introspect the type that the current descriptor describes.
     *
     * Example:
     * ```
     * @Serializable
     * @OnClassSerialAnnotation
     * class Nested(...)
     *
     * @Serializable
     * class Outer(val nested: Nested)
     *
     * val outerDescriptor = Outer.serializer().descriptor
     *
     * outerDescriptor.getElementDescriptor(0).serialName // Returns "Nested"
     * outerDescriptor.getElementDescriptor(0).annotations // Returns [@OnClassSerialAnnotation]
     * ```
     *
     * @throws IndexOutOfBoundsException for illegal [index] values.
     * @throws IllegalStateException if the current descriptor does not support children elements (e.g. is a primitive).
     */
    public fun getElementDescriptor(index: Int): SerialDescriptor

    /**
     * Whether the element at the given [index] is optional (can be absent in serialized form).
     * For generated descriptors, all elements that have a corresponding default parameter value are
     * marked as optional. Custom serializers can treat optional values in a serialization-specific manner
     * without a default parameters constraint.
     *
     * Example of optionality:
     * ```
     * @Serializable
     * class Holder(
     *     val a: Int, // isElementOptional(0) == false
     *     val b: Int?, // isElementOptional(1) == false
     *     val c: Int? = null, // isElementOptional(2) == true
     *     val d: List<Int>, // isElementOptional(3) == false
     *     val e: List<Int> = listOf(1), // isElementOptional(4) == true
     * )
     * ```
     * Returns `false` for valid indices of collections, maps, and enums.
     *
     * @throws IndexOutOfBoundsException for an illegal [index] values.
     * @throws IllegalStateException if the current descriptor does not support children elements (e.g. is a primitive).
     */
    public fun isElementOptional(index: Int): Boolean
}

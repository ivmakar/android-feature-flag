package io.github.ivmakar.featureflags.codec

import io.github.ivmakar.featureflags.TestBool
import io.github.ivmakar.featureflags.TestEnum
import io.github.ivmakar.featureflags.TestInt
import io.github.ivmakar.featureflags.TestServerType
import io.github.ivmakar.featureflags.TestString
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeatureCodecTest {

    @Test
    fun `bool round trip`() {
        assertThat(FeatureCodec.decode(TestBool, FeatureCodec.encode(TestBool, true))).isTrue()
        assertThat(FeatureCodec.decode(TestBool, FeatureCodec.encode(TestBool, false))).isFalse()
    }

    @Test
    fun `int round trip`() {
        assertThat(FeatureCodec.decode(TestInt, FeatureCodec.encode(TestInt, 42))).isEqualTo(42)
    }

    @Test
    fun `string round trip`() {
        assertThat(FeatureCodec.decode(TestString, FeatureCodec.encode(TestString, "hi")))
            .isEqualTo("hi")
    }

    @Test
    fun `enum round trip`() {
        val raw = FeatureCodec.encode(TestEnum, TestServerType.DEV)
        assertThat(FeatureCodec.decode(TestEnum, raw)).isEqualTo(TestServerType.DEV)
    }

    @Test
    fun `int garbage decodes to null`() {
        assertThat(FeatureCodec.decode(TestInt, "notanint")).isNull()
    }

    @Test
    fun `bool garbage decodes to null`() {
        assertThat(FeatureCodec.decode(TestBool, "yes")).isNull()
    }

    @Test
    fun `unknown enum name decodes to null`() {
        assertThat(FeatureCodec.decode(TestEnum, "MARS")).isNull()
    }

    @Test
    fun `negative int round trip`() {
        assertThat(FeatureCodec.decode(TestInt, FeatureCodec.encode(TestInt, -7))).isEqualTo(-7)
    }

    @Test
    fun `int overflow decodes to null`() {
        // Beyond Int range → toIntOrNull() == null → resolver falls through.
        assertThat(FeatureCodec.decode(TestInt, "2147483648")).isNull()
    }

    @Test
    fun `empty string round trips`() {
        assertThat(FeatureCodec.decode(TestString, FeatureCodec.encode(TestString, ""))).isEqualTo("")
    }
}

package io.github.ivmakar.featureflags.config

import io.github.ivmakar.featureflags.TestBool
import io.github.ivmakar.featureflags.TestEnum
import io.github.ivmakar.featureflags.TestServerType
import io.github.ivmakar.featureflags.codec.FeatureCodec
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FeatureConfigBuilderTest {

    @Test
    fun `enable and disable store bool raw`() {
        val enabled = FeatureConfig { enable(TestBool) }
        val disabled = FeatureConfig { disable(TestBool) }
        assertThat(enabled.values[TestBool.key]).isEqualTo("true")
        assertThat(disabled.values[TestBool.key]).isEqualTo("false")
    }

    @Test
    fun `value stores raw via codec`() {
        val config = FeatureConfig { value(TestEnum, TestServerType.DEV) }
        assertThat(config.values[TestEnum.key])
            .isEqualTo(FeatureCodec.encode(TestEnum, TestServerType.DEV))
    }

    @Test
    fun `last wins for duplicate declarations`() {
        val config = FeatureConfig {
            enable(TestBool)
            disable(TestBool)
        }
        assertThat(config.values[TestBool.key]).isEqualTo("false")
    }

    @Test
    fun `empty config has no values`() {
        assertThat(FeatureConfig.empty().values).isEmpty()
    }
}

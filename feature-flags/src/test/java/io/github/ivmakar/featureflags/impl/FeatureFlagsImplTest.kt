package io.github.ivmakar.featureflags.impl

import app.cash.turbine.test
import io.github.ivmakar.featureflags.FakeSource
import io.github.ivmakar.featureflags.ReactiveFakeSource
import io.github.ivmakar.featureflags.TestBool
import io.github.ivmakar.featureflags.TestEnum
import io.github.ivmakar.featureflags.TestInt
import io.github.ivmakar.featureflags.TestServerType
import io.github.ivmakar.featureflags.api.Priority
import io.github.ivmakar.featureflags.codec.FeatureCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeatureFlagsImplTest {

    @Test
    fun `falls back to default when no source knows the key`() {
        val flags = FeatureFlagsImpl(listOf(FakeSource(Priority.BUSINESS)))
        assertThat(flags.get(TestBool)).isEqualTo(TestBool.default)
        assertThat(flags.get(TestInt)).isEqualTo(7)
    }

    @Test
    fun `higher priority source wins`() {
        val business = FakeSource(Priority.BUSINESS, mapOf(TestBool.key to "false"))
        val forced = FakeSource(Priority.FORCED, mapOf(TestBool.key to "true"))
        // Intentionally list lower priority first to prove ordering is by rank, not list position.
        val flags = FeatureFlagsImpl(listOf(business, forced))
        assertThat(flags.get(TestBool)).isTrue()
    }

    @Test
    fun `priority chain business lt buildVariant lt localOverrides lt forced`() {
        fun sourceFor(p: Priority, value: Boolean) =
            FakeSource(p, mapOf(TestBool.key to value.toString()))

        // Each higher tier flips the value; the resolver must always pick the top non-null tier.
        val business = sourceFor(Priority.BUSINESS, false)
        val buildVariant = sourceFor(Priority.BUILD_VARIANT, true)
        val local = sourceFor(Priority.LOCAL_OVERRIDES, false)
        val forced = sourceFor(Priority.FORCED, true)

        assertThat(FeatureFlagsImpl(listOf(business)).get(TestBool)).isFalse()
        assertThat(FeatureFlagsImpl(listOf(business, buildVariant)).get(TestBool)).isTrue()
        assertThat(FeatureFlagsImpl(listOf(business, buildVariant, local)).get(TestBool)).isFalse()
        assertThat(FeatureFlagsImpl(listOf(business, buildVariant, local, forced)).get(TestBool))
            .isTrue()
    }

    @Test
    fun `unparseable higher source falls through to lower source`() {
        val business = FakeSource(Priority.BUSINESS, mapOf(TestEnum.key to "DEV"))
        val forced = FakeSource(Priority.FORCED, mapOf(TestEnum.key to "MARS"))
        val flags = FeatureFlagsImpl(listOf(business, forced))
        // FORCED has the key but "MARS" decodes to null, so resolution falls through to BUSINESS.
        assertThat(flags.get(TestEnum)).isEqualTo(TestServerType.DEV)
    }

    @Test
    fun `equal rank sources are stable first in list wins`() {
        val first = FakeSource(Priority.BUSINESS, mapOf(TestInt.key to "1"))
        val second = FakeSource(Priority.BUSINESS, mapOf(TestInt.key to "2"))
        assertThat(FeatureFlagsImpl(listOf(first, second)).get(TestInt)).isEqualTo(1)
    }

    @Test
    fun `enum resolves through codec`() {
        val raw = FeatureCodec.encode(TestEnum, TestServerType.STAGE)
        val flags = FeatureFlagsImpl(listOf(FakeSource(Priority.BUSINESS, mapOf(TestEnum.key to raw))))
        assertThat(flags.get(TestEnum)).isEqualTo(TestServerType.STAGE)
    }

    // --- observe() ---

    @Test
    fun `observe with no reactive source emits the resolved value once then completes`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        // Only a non-reactive source (changes() == null): this is the release-build path with no
        // PersistentOverrideSource. observe() must still emit exactly once (the resolved value) and
        // then complete, rather than hang forever.
        val flags = FeatureFlagsImpl(listOf(FakeSource(Priority.BUSINESS, mapOf(TestBool.key to "true"))))
        flags.observe(TestBool).test {
            assertThat(awaitItem()).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `observe suppresses re-emission when a source change does not alter the resolved value`() =
        runTest(UnconfinedTestDispatcher()) {
            // BUSINESS = false; a reactive LOCAL_OVERRIDES source pulses a change that still resolves
            // to false. distinctUntilChanged must swallow the no-op re-emission.
            val business = FakeSource(Priority.BUSINESS, mapOf(TestBool.key to "false"))
            val reactive = ReactiveFakeSource(Priority.LOCAL_OVERRIDES)
            val flags = FeatureFlagsImpl(listOf(business, reactive))

            flags.observe(TestBool).test {
                assertThat(awaitItem()).isFalse() // initial resolution

                reactive.put(TestBool.key, "false") // resolves to false again — no change
                expectNoEvents()

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observe re-resolves when a higher priority source changes`() = runTest(
        UnconfinedTestDispatcher()
    ) {
        val business = FakeSource(Priority.BUSINESS, mapOf(TestBool.key to "false"))
        val reactive = ReactiveFakeSource(Priority.LOCAL_OVERRIDES)
        val flags = FeatureFlagsImpl(listOf(business, reactive))

        flags.observe(TestBool).test {
            assertThat(awaitItem()).isFalse() // reactive empty → business default

            reactive.put(TestBool.key, "true") // higher-priority override appears
            assertThat(awaitItem()).isTrue()

            reactive.clear(TestBool.key) // override withdrawn → back to business default
            assertThat(awaitItem()).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }
}

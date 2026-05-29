package io.github.ivmakar.featureflags

import io.github.ivmakar.featureflags.api.BoolFeature
import io.github.ivmakar.featureflags.api.EnumFeature
import io.github.ivmakar.featureflags.api.IntFeature
import io.github.ivmakar.featureflags.api.StringFeature

enum class TestServerType { PROD, DEV, STAGE }

object TestBool : BoolFeature("test_bool", default = false)
object TestInt : IntFeature("test_int", default = 7)
object TestString : StringFeature("test_string", default = "fallback")
object TestEnum : EnumFeature<TestServerType>("test_enum", TestServerType.PROD, TestServerType::class)

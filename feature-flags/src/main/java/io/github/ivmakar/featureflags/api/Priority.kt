package io.github.ivmakar.featureflags.api

// Higher rank wins. Equal rank → first-in-list wins (stable sort in the resolver).
enum class Priority(val rank: Int) {
    BUSINESS(0),
    BUILD_VARIANT(100),
    LOCAL_OVERRIDES(200),
    FORCED(300),
}

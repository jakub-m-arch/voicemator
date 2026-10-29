package io.github.jakub_m_arch.voicemator.licenses

/** Third-party work used by the app, with the attribution its licence requires. */
data class ThirdPartyComponent(
    val name: String,
    val version: String?,
    val copyright: String,
    val license: String,
    val url: String,
    /** File in `assets/licenses` with the full licence text, or null if [license] links to it. */
    val licenseAsset: String?,
    val note: String? = null,
)

/**
 * Everything shipped in the APK or downloaded by the app.
 *
 * Native entries must match the sherpa-onnx release pinned in `app/build.gradle.kts`. The Java
 * and Kotlin dependency groups are checked against this list by the `checkThirdPartyNotices`
 * Gradle task, which fails when a dependency from a new group appears.
 */
object ThirdPartyNotices {

    private const val APACHE_2 = "Apache-2.0"
    private const val APACHE_2_ASSET = "apache-2.0.txt"

    val bundled = listOf(
        ThirdPartyComponent(
            name = "sherpa-onnx",
            version = "2.0.1",
            copyright = "Copyright (c) Xiaomi Corporation and sherpa-onnx contributors",
            license = APACHE_2,
            url = "https://github.com/k2-fsa/sherpa-onnx",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "Supertonic (portions in sherpa-onnx)",
            version = null,
            copyright = "Copyright (c) 2025 Supertone Inc.",
            license = "MIT",
            url = "https://github.com/supertone-inc/supertonic",
            licenseAsset = "supertonic.txt",
        ),
        ThirdPartyComponent(
            name = "ONNX Runtime",
            version = "1.28.2",
            copyright = "Copyright (c) Microsoft Corporation",
            license = "MIT",
            url = "https://github.com/microsoft/onnxruntime",
            licenseAsset = "onnxruntime.txt",
        ),
        ThirdPartyComponent(
            name = "ONNX Runtime third-party components",
            version = "1.28.2",
            copyright = "Various, see notices",
            license = "Various",
            url = "https://github.com/microsoft/onnxruntime/blob/v1.28.2/ThirdPartyNotices.txt",
            licenseAsset = "onnxruntime-third-party-notices.txt",
        ),
        ThirdPartyComponent(
            name = "kaldi-native-fbank",
            version = "1.22.4",
            copyright = "Copyright (c) Xiaomi Corporation",
            license = APACHE_2,
            url = "https://github.com/csukuangfj/kaldi-native-fbank",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "KISS FFT",
            version = "febd4ca",
            copyright = "Copyright (c) 2003-2010 Mark Borgerding",
            license = "BSD-3-Clause",
            url = "https://github.com/mborgerding/kissfft",
            licenseAsset = "kissfft.txt",
        ),
        ThirdPartyComponent(
            name = "kaldi-decoder",
            version = "0.3.0",
            copyright = "Copyright (c) Xiaomi Corporation; portions Copyright Microsoft Corporation, " +
                "Johns Hopkins University and other Kaldi authors",
            license = APACHE_2,
            url = "https://github.com/k2-fsa/kaldi-decoder",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "OpenFst",
            version = "1.8.5",
            copyright = "Copyright 2005-2026 Google LLC",
            license = APACHE_2,
            url = "https://github.com/csukuangfj/openfst",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "Eigen",
            version = "5.0.1",
            copyright = "Copyright (c) Eigen authors",
            license = "MPL-2.0",
            url = "https://gitlab.com/libeigen/eigen/-/tree/5.0.1",
            licenseAsset = "eigen.txt",
            note = "Source code is available at https://gitlab.com/libeigen/eigen/-/tree/5.0.1",
        ),
        ThirdPartyComponent(
            name = "simple-sentencepiece",
            version = "0.7",
            copyright = "Copyright (c) 2024 Wei Kang",
            license = APACHE_2,
            url = "https://github.com/pkufool/simple-sentencepiece",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "JSON for Modern C++",
            version = "3.12.0",
            copyright = "Copyright (c) 2013-2025 Niels Lohmann",
            license = "MIT",
            url = "https://github.com/nlohmann/json",
            licenseAsset = "nlohmann-json.txt",
        ),
        ThirdPartyComponent(
            name = "utf8proc",
            version = "2.12.0",
            copyright = "Copyright (c) 2014-2021 Steven G. Johnson, Jiahao Chen, Tony Kelman, " +
                "Jonas Fonseca, and other contributors; Copyright (c) 2009, 2013 Public Software " +
                "Group e. V.; Unicode data Copyright (c) 1991-2007 Unicode, Inc.",
            license = "MIT and Unicode data licence",
            url = "https://github.com/JuliaStrings/utf8proc",
            licenseAsset = "utf8proc.txt",
        ),
        ThirdPartyComponent(
            name = "fastcluster (hclust-cpp)",
            version = "2026-02-25",
            copyright = "Copyright (c) 2011 Daniel Müllner; Copyright (c) 2018 Christoph Dalitz",
            license = "BSD-2-Clause",
            url = "https://github.com/csukuangfj/hclust-cpp",
            licenseAsset = "fastcluster.txt",
        ),
        ThirdPartyComponent(
            name = "AndroidX and Jetpack Compose",
            version = null,
            copyright = "Copyright (c) The Android Open Source Project",
            license = APACHE_2,
            url = "https://developer.android.com/jetpack/androidx",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "Kotlin Standard Library",
            version = null,
            copyright = "Copyright (c) JetBrains s.r.o. and Kotlin Programming Language contributors",
            license = APACHE_2,
            url = "https://github.com/JetBrains/kotlin",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "kotlinx.coroutines",
            version = null,
            copyright = "Copyright (c) JetBrains s.r.o. and contributors",
            license = APACHE_2,
            url = "https://github.com/Kotlin/kotlinx.coroutines",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "JetBrains Java Annotations",
            version = null,
            copyright = "Copyright (c) JetBrains s.r.o.",
            license = APACHE_2,
            url = "https://github.com/JetBrains/java-annotations",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "JSpecify",
            version = null,
            copyright = "Copyright (c) The JSpecify Authors",
            license = APACHE_2,
            url = "https://github.com/jspecify/jspecify",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "Guava ListenableFuture",
            version = null,
            copyright = "Copyright (c) The Guava Authors",
            license = APACHE_2,
            url = "https://github.com/google/guava",
            licenseAsset = APACHE_2_ASSET,
        ),
        ThirdPartyComponent(
            name = "Material Icons (microphone icon)",
            version = null,
            copyright = "Copyright (c) Google LLC",
            license = APACHE_2,
            url = "https://github.com/google/material-design-icons",
            licenseAsset = APACHE_2_ASSET,
        ),
    )

    /** The speech model is not part of the APK; the app downloads it on request. */
    val downloaded = listOf(
        ThirdPartyComponent(
            name = "Parakeet TDT 0.6B v3",
            version = null,
            copyright = "NVIDIA Corporation",
            license = "CC-BY-4.0",
            url = "https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3",
            licenseAsset = null,
        ),
        ThirdPartyComponent(
            name = "Parakeet Ultra",
            version = null,
            copyright = "Moondream",
            license = "CC-BY-4.0",
            url = "https://huggingface.co/moondream/parakeet-ultra",
            licenseAsset = null,
            note = "Post-trained version of Parakeet TDT 0.6B v3.",
        ),
        ThirdPartyComponent(
            name = "Parakeet Ultra int8 ONNX conversion",
            version = "f7bc472",
            copyright = "Hugging Face user leogaudin",
            license = "CC-BY-4.0",
            url = "https://huggingface.co/leogaudin/sherpa-onnx-nemo-parakeet-ultra-int8",
            licenseAsset = null,
            note = "Converted to ONNX, quantized to int8 and the voice activity head removed. " +
                "Not reviewed or endorsed by NVIDIA or Moondream.",
        ),
    )

    /** Licence deed for components whose [ThirdPartyComponent.licenseAsset] is null. */
    const val CC_BY_4_URL = "https://creativecommons.org/licenses/by/4.0/"
}

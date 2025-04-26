workspace(name = "ietf-android")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive") #keep the http_archive, it will be used for other dependencies

http_archive(
    name = "bazel_android_sdk_platform",
    sha256 = "03039c5027490f428333332a9402f7b70c02e2547f8192e8ed36ffb12bb47d40",
    urls = ["https://github.com/bazelbuild/bazel-android-sdk-platform/releases/download/v0.0.3/bazel_android_sdk_platform-v0.0.3.tar.gz"],
)

http_archive(
    name = "rules_jvm_external",
    sha256 = "d14e27b83a981911420f8a86f73f1f7e0936b884d29d3b7f93b34e9084e7e6fd",
    urls = ["https://github.com/bazelbuild/rules_jvm_external/releases/download/4.5/rules_jvm_external-4.5.tar.gz"],
)

load("@bazel_android_sdk_platform//:android_sdk_repository.bzl", "android_sdk_repository")

android_sdk_repository(
    name = "androidsdk",
    api_level = 34,
    build_tools_version = "34.0.0",
    path = "/opt/android-sdk",
)

load("@rules_jvm_external//:repositories.bzl", "maven_install")

maven_install(
    artifacts = [
        "androidx.legacy:legacy-support-v4:1.0.0",
        "io.noties.markwon:core:4.2.0",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com",
        "https://oss.sonatype.org/content/repositories/snapshots/"
    ],
)

load("@rules_android//android:rules.bzl", "android_sdk")

android_sdk(
    name = "android_sdk",
)

load("@rules_android//android:dependencies.bzl", "android_dependencies")

android_dependencies()
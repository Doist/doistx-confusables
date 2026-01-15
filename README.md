# doistx-confusables

[![badge-version]](https://search.maven.org/search?q=g:com.doist.x%20a:confusables*)
![badge-android][badge-android]
![badge-jvm][badge-jvm]
![badge-js][badge-js]
![badge-ios][badge-ios]
![badge-ios][badge-watchos]
![badge-ios][badge-tvos]
![badge-macos][badge-macos]
![badge-windows][badge-windows]
![badge-linux][badge-linux]

Kotlin Multiplatform (KMP) library that implements Unicode confusable detection based on
[Unicode Technical Standard #39 - Unicode Security Mechanisms](https://unicode.org/reports/tr39/).

It extends `String` with:
- `toSkeleton()`: returns the UTS #39 *confusable skeleton* (specifically, `internalSkeleton`).
- `isConfusable(other)`: returns whether two strings have the same skeleton.

> [!WARNING]
> A skeleton is intended only for internal use when testing confusability; it is not suitable for display and should
> not be treated as a general “normalization” of identifiers.

## Usage

```kotlin
"paypal".isConfusable("p\u0430yp\u0430l") // => true (Cyrillic 'а')
"ѕсоре".toSkeleton() // => "scope"
```

## Setup

```kotlin
repositories {
   mavenCentral()
}

kotlin {
   sourceSets {
      val commonMain by getting {
         dependencies {
            implementation("com.doist.x:confusables:1.0.0")
         }
      }
   }
}
```

## Unicode data

This library embeds data from:
- UTS #39 `confusables.txt` (Unicode 16.0.0)
- UCD `Default_Ignorable_Code_Point` (Unicode 16.0.0)

Kotlin tables are generated into `build/` at build time from the pinned `resources/unicode-data/` inputs.

All Unicode data is subject to Unicode’s [Terms of Use](https://www.unicode.org/terms_of_use.html).

## Updating Unicode data

Run:

```bash
./gradlew updateUnicodeData -PunicodeVersion=16.0.0
```

## License

Released under the [MIT License](https://opensource.org/licenses/MIT).

[badge-version]: https://img.shields.io/maven-central/v/com.doist.x/confusables?style=flat
[badge-android]: https://img.shields.io/badge/platform-android-6EDB8D.svg?style=flat
[badge-ios]: https://img.shields.io/badge/platform-ios-CDCDCD.svg?style=flat
[badge-js]: https://img.shields.io/badge/platform-js-F8DB5D.svg?style=flat
[badge-jvm]: https://img.shields.io/badge/platform-jvm-DB413D.svg?style=flat
[badge-linux]: https://img.shields.io/badge/platform-linux-2D3F6C.svg?style=flat
[badge-windows]: https://img.shields.io/badge/platform-windows-4D76CD.svg?style=flat
[badge-macos]: https://img.shields.io/badge/platform-macos-111111.svg?style=flat
[badge-watchos]: https://img.shields.io/badge/platform-watchos-C0C0C0.svg?style=flat
[badge-tvos]: https://img.shields.io/badge/platform-tvos-808080.svg?style=flat

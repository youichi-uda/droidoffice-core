# droidoffice-core — Shared Foundation for DroidOffice Libraries

[![License: BSL 1.1](https://img.shields.io/badge/License-BSL_1.1-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)

Shared infrastructure for the DroidOffice library family (DroidXLS, DroidDoc, DroidSlide). Provides OOXML parsing, license validation, common style definitions, and encryption — so each product library stays small and focused.

## What's Inside

| Module | Description |
|---|---|
| **OOXML Parser** | ZIP/XML reader/writer for .xlsx, .docx, .pptx (all are ZIP+XML) |
| **SAX Reader** | Streaming XML parser — low memory footprint for Android |
| **License Validation** | Gumroad API integration for commercial license verification |
| **DrawingML** | Common font, color, border, fill definitions shared across Office formats |
| **Encryption** | AES-256 password protection for OOXML files |
| **Exceptions** | `DroidOfficeException` hierarchy used by all products |

## Usage

This library is **not used directly** by end users. It is a compile-time dependency of:

- **[DroidXLS](https://github.com/youichi-uda/droidxls)** — Android Excel library (.xlsx)
- **DroidDoc** — Android Word library (.docx) — *planned*
- **DroidSlide** — Android PowerPoint library (.pptx) — *planned*

### For Product Library Developers

```kotlin
// settings.gradle.kts (composite build during development)
includeBuild("../droidOffice-core") {
    dependencySubstitution {
        substitute(module("com.droidoffice:droidoffice-core")).using(project(":"))
    }
}

// build.gradle.kts
dependencies {
    implementation("com.droidoffice:droidoffice-core:0.1.0-SNAPSHOT")
}
```

## Architecture

```
droidoffice-core/
└── src/main/kotlin/com/droidoffice/core/
    ├── ooxml/          # OoxmlPackage, SaxReader, Relationships, ContentTypes
    ├── drawingml/      # Color, Font, Border, Fill (shared DrawingML types)
    ├── license/        # GumroadLicenseVerifier, LicensePlan, LicenseCache
    └── exception/      # DroidOfficeException hierarchy
```

## License

**[BSL 1.1](LICENSE)** — Free for personal, non-commercial, OSS, NPO, and educational use. Converts to MIT 3 years after each release.

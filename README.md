# droidoffice-core

Shared foundation library for the [DroidOffice](https://github.com/user/droidoffice) product family.

## What's inside

- **OOXML Parser** — ZIP/XML reader/writer for .xlsx, .docx, .pptx files
- **License Validation** — JWT-based offline license key verification
- **DrawingML** — Common color, font, border, fill definitions
- **Exceptions** — Shared exception hierarchy (`DroidOfficeException`)
- **Encryption** — AES-256 password protection for OOXML files

## Usage

This library is not used directly. It is a dependency of:
- [DroidXLS](https://github.com/user/droidxls) — Excel (.xlsx)
- DroidDoc — Word (.docx/.rtf) *(planned)*
- DroidSlide — PowerPoint (.pptx) *(planned)*

## License

[BSL 1.1](LICENSE) — Free for personal, non-commercial, OSS, NPO, and educational use.
Converts to MIT 3 years after each release.

package com.custom.files;

/**
 * Build variant switches. build.ps1 -Variant lite replaces this file in the staged sources
 * (NO_VIDEO = true, NO_INSTALL = true, its own authority), so the source tree always holds the full version.
 */
final class Flavor {
    static final boolean NO_VIDEO = false;     // no video playback, thumbnails or video category
    static final boolean NO_INSTALL = false;   // APK files cannot be opened / installed
    static final String AUTH = "com.custom.files.provider";
    static final String NAME = "מנהל הקבצים X";
}

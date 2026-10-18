/*
 * This is a private project. All rights reserved.
 */

// Kotlin emits unused Node externals from transitive wrappers into this browser entry.
// No browser implementation calls these modules.
config.resolve.fallback = Object.assign({}, config.resolve.fallback, {
    events: false,
    fs: false,
    os: false,
    path: false,
});

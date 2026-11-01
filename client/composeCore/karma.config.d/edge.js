// This is a private project. All rights reserved.
// Edge's Windows compatibility shim otherwise detaches from Karma's process owner.
if (/(?:^|[\\/])msedge(?:\.exe)?$/i.test(process.env.CHROME_BIN || '')) {
    config.customLaunchers = config.customLaunchers || {};
    config.customLaunchers.EdgeProtectedStorageHeadless = {
        base: 'ChromeHeadless',
        flags: ['--edge-skip-compat-layer-relaunch', '--remote-debugging-port=0']
    };
    config.browsers = ['EdgeProtectedStorageHeadless'];
}

// Learn more https://docs.expo.dev/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config');

/** @type {import('expo/metro-config').MetroConfig} */
const config = getDefaultConfig(__dirname);

// expo-sqlite on web ships a `.wasm` module — let Metro resolve it as an asset.
config.resolver.assetExts.push('wasm');

// SQLite's web worker needs SharedArrayBuffer, which requires these COOP/COEP
// headers on the dev server. See https://docs.expo.dev/versions/v56.0.0/sdk/sqlite/
config.server.enhanceMiddleware = (middleware) => {
  return (req, res, next) => {
    res.setHeader('Cross-Origin-Opener-Policy', 'same-origin');
    res.setHeader('Cross-Origin-Embedder-Policy', 'credentialless');
    return middleware(req, res, next);
  };
};

module.exports = config;

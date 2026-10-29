module.exports = {
  readVersion(contents) {
    return JSON.parse(contents).expo.version;
  },

  writeVersion(contents, version) {
    let out = contents.replace(/("version":\s*)"[^"]*"/, `$1"${version}"`);
    out = out.replace(
      /("versionCode":\s*)(\d+)/,
      (_m, prefix, n) => `${prefix}${parseInt(n, 10) + 1}`,
    );
    return out;
  },
};

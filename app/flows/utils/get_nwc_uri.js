const serviceUrl = readEnv("TEST_WALLET_SERVICE_URL", "http://127.0.0.1:8082").replace(/\/$/, "");
const node = readEnv("NWC_NODE", "payer");
const label = readEnv("NWC_LABEL", `papp-e2e-${node}`);
const nodes = readEnv("NWC_NODES", "").trim();

const response = nodes
  ? http.get(`${serviceUrl}/wallets?nodes=${encodeURIComponent(nodes)}`)
  : http.get(`${serviceUrl}/get-nwc-uri?node=${encodeURIComponent(node)}&label=${encodeURIComponent(label)}`);

const body = json(response.body);
if (response.status !== 200) {
  throw new Error(
    body.error || `NWC URI lookup failed with HTTP ${response.status}`
  );
}

const wallets = nodes ? walletFixtures(body.wallets || []) : walletFixtures([body]);
if (wallets.length === 0) {
  throw new Error("NWC URI lookup did not return any wallets.");
}

output.nwcUri = wallets[0].uri;
output.nwcWallets = JSON.stringify(wallets);
output.nwcFixtureJson = JSON.stringify({
  wallets,
});

function walletFixtures(items) {
  const activeNode = readEnv("NWC_ACTIVE_NODE", "").trim();
  return items
    .filter((item) => item && item.uri)
    .map((item, index) => {
      const itemNode = item.node || node;
      const wallet = {
        type: "nwc",
        uri: rewriteRelayForDevice(item.uri),
        alias: `${itemNode} NWC`,
      };
      if (activeNode && activeNode === itemNode) {
        wallet.active = true;
      } else if (!activeNode && index === 0 && items.length > 1) {
        wallet.active = true;
      }
      return wallet;
    });
}

function rewriteRelayForDevice(uri) {
  const relay = resolveDeviceRelay();
  if (!relay) {
    return uri;
  }

  const queryIndex = uri.indexOf("?");
  if (queryIndex < 0) {
    return uri;
  }

  const prefix = uri.slice(0, queryIndex);
  const parts = uri.slice(queryIndex + 1).split("&");
  let changed = false;
  const rewritten = parts.map((part) => {
    const separator = part.indexOf("=");
    const key = separator < 0 ? part : part.slice(0, separator);
    if (key !== "relay") {
      return part;
    }
    changed = true;
    return `relay=${encodeURIComponent(relay)}`;
  });

  return changed ? `${prefix}?${rewritten.join("&")}` : uri;
}

function resolveDeviceRelay() {
  const explicit = readEnv(
    "TEST_NWC_RELAY_DEVICE_URL",
    readEnv("PAPP_E2E_DEVICE_RELAY_URL", "")
  ).replace(/\/$/, "");
  if (explicit) {
    return explicit;
  }

  const platform = maestroPlatform();
  if (platform === "android") {
    return readEnv("TEST_NWC_RELAY_ANDROID_URL", "ws://10.0.2.2:8081").replace(/\/$/, "");
  }
  if (platform === "ios") {
    return readEnv("TEST_NWC_RELAY_IOS_URL", "").replace(/\/$/, "");
  }
  return "";
}

function maestroPlatform() {
  if (typeof maestro === "undefined" || typeof maestro.platform === "undefined") {
    return "";
  }
  return String(maestro.platform).toLowerCase();
}

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

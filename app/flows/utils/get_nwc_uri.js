const defaultRelayUrlHost = (maestro.platform == "android") ? "10.0.2.2" : "127.0.0.1"

const serviceUrl = readEnv("TEST_WALLET_SERVICE_URL", "http://127.0.0.1:3021").replace(/\/$/, "");
const relayUrl = readEnv("TEST_NOSTR_RELAY_URL", `ws://${defaultRelayUrlHost}:7777`);

const response = http.get(`${serviceUrl}/nwc-uri`, {
  headers: requestHeaders(),
});

const body = json(response.body);
if (response.status !== 200) {
  throw new Error(
    body.error || body.bootstrapError || `NWC URI lookup failed with HTTP ${response.status}`
  );
}

if (!body.uri) {
  throw new Error("NWC URI lookup did not return a uri.");
}

output.nwcUri = withRelay(body.uri, relayUrl);

function requestHeaders() {
  const headers = {};
  const token = readEnv("TEST_WALLET_TOKEN", "");
  if (token) {
    headers.authorization = `Bearer ${token}`;
  }
  return headers;
}

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

function withRelay(uri, relay) {
  const parts = uri.split("?");
  if (parts.length < 2) {
    return `${uri}?relay=${encodeURIComponent(relay)}`;
  }

  const query = parts.slice(1).join("?");
  const params = query
    .split("&")
    .filter((pair) => pair && decodeURIComponent(pair.split("=")[0]) !== "relay");
  params.unshift(`relay=${encodeURIComponent(relay)}`);
  return `${parts[0]}?${params.join("&")}`;
}

const serviceUrl = requiredLeaseValue("opsUrl", "TEST_WALLET_SERVICE_URL").replace(/\/$/, "");

const response = http.get(`${serviceUrl}/get-nwc-uri`, {
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

output.nwcUri = rewriteRelayForDevice(body.uri);
output.nwcFixtureJson = JSON.stringify({
  wallets: [
    {
      type: "nwc",
      uri: output.nwcUri,
      alias: "ReGLab NWC",
    },
  ],
});

function requestHeaders() {
  return {
    "x-reglab-lease-token": requiredLeaseValue("leaseToken", "TEST_WALLET_TOKEN"),
  };
}

function rewriteRelayForDevice(uri) {
  const deviceBase = (
    (output.reglab && output.reglab.deviceBaseUrl) ||
    readEnv(
      "REGLAB_DEVICE_URL",
      readEnv("MAESTRO_REGLAB_DEVICE_URL", readEnv("TEST_REGLAB_DEVICE_URL", ""))
    )
  ).replace(/\/$/, "");
  if (!deviceBase) {
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
    const value = separator < 0 ? "" : part.slice(separator + 1);
    const relay = decodeURIComponent(value);
    changed = true;
    return `relay=${encodeURIComponent(rewriteRelayBase(relay, deviceBase))}`;
  });

  return changed ? `${prefix}?${rewritten.join("&")}` : uri;
}

function rewriteRelayBase(relay, deviceBase) {
  const match = deviceBase.match(/^(https?):\/\/([^/]+)/);
  if (!match) {
    throw new Error(`REGLAB_DEVICE_URL must be an http(s) URL, got ${deviceBase}`);
  }
  const relayScheme = match[1] === "https" ? "wss" : "ws";
  return relay.replace(/^wss?:\/\/[^/]+/, `${relayScheme}://${match[2]}`);
}

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

function requiredLeaseValue(outputKey, envName) {
  const value = output.reglab && output.reglab[outputKey]
    ? String(output.reglab[outputKey])
    : readEnv(envName, "");
  if (!value) {
    throw new Error(`${envName} is required.`);
  }
  return value;
}

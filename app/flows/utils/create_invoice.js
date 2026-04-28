const serviceUrl = readEnv("TEST_WALLET_SERVICE_URL", "http://127.0.0.1:3021").replace(/\/$/, "");
const sats = Number(readEnv("INVOICE_SATS", "21"));
const description = readEnv("INVOICE_DESCRIPTION", "Maestro test invoice");
const expiry = Number(readEnv("INVOICE_EXPIRY", "600"));

if (!Number.isInteger(sats) || sats <= 0) {
  throw new Error("INVOICE_SATS must be a positive integer.");
}

if (!Number.isInteger(expiry) || expiry <= 0) {
  throw new Error("INVOICE_EXPIRY must be a positive integer.");
}

const response = http.post(`${serviceUrl}/invoice`, {
  headers: requestHeaders(),
  body: JSON.stringify({ sats, description, expiry }),
});

const body = json(response.body);
if (response.status !== 200) {
  throw new Error(body.error || `Invoice creation failed with HTTP ${response.status}`);
}

output.bolt11 = body.bolt11;
output.paymentHash = body.paymentHash;

function requestHeaders() {
  const headers = {
    "content-type": "application/json",
  };
  const token = readEnv("TEST_WALLET_TOKEN", "");
  if (token) {
    headers.authorization = `Bearer ${token}`;
  }
  return headers;
}

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

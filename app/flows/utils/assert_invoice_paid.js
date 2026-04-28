const serviceUrl = readEnv("TEST_WALLET_SERVICE_URL", "http://127.0.0.1:3021").replace(/\/$/, "");
const timeoutMs = Number(readEnv("INVOICE_TIMEOUT_MS", "120000"));
const intervalMs = Number(readEnv("INVOICE_INTERVAL_MS", "2000"));
const bolt11 = readEnv("INVOICE_BOLT11", "") || output.bolt11;

if (!Number.isInteger(timeoutMs) || timeoutMs <= 0) {
  throw new Error("INVOICE_TIMEOUT_MS must be a positive integer.");
}

if (!Number.isInteger(intervalMs) || intervalMs <= 0) {
  throw new Error("INVOICE_INTERVAL_MS must be a positive integer.");
}

if (!bolt11) {
  throw new Error("No BOLT11 invoice found. Run create_invoice.js first or pass INVOICE_BOLT11.");
}

const response = http.post(`${serviceUrl}/invoice/wait-paid`, {
  headers: requestHeaders(),
  body: JSON.stringify({ bolt11, timeoutMs, intervalMs }),
});

const body = json(response.body);
if (response.status !== 200 || body.settled !== true) {
  throw new Error(body.error || `Invoice was not settled, HTTP ${response.status}`);
}

output.invoiceSettled = true;
output.paymentHash = body.paymentHash || output.paymentHash;
output.preimage = body.preimage;

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

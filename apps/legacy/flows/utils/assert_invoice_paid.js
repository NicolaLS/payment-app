const serviceUrl = readEnv("TEST_WALLET_SERVICE_URL", "http://127.0.0.1:8082").replace(/\/$/, "");
const timeoutMs = Number(readEnv("INVOICE_TIMEOUT_MS", "120000"));
const intervalMs = Number(readEnv("INVOICE_INTERVAL_MS", "2000"));
const paymentHash = readEnv("INVOICE_PAYMENT_HASH", "") || output.paymentHash;
const node = readEnv("INVOICE_NODE", "") || output.invoiceNode || "receiver";

if (!Number.isInteger(timeoutMs) || timeoutMs <= 0) {
  throw new Error("INVOICE_TIMEOUT_MS must be a positive integer.");
}

if (!Number.isInteger(intervalMs) || intervalMs <= 0) {
  throw new Error("INVOICE_INTERVAL_MS must be a positive integer.");
}

if (!paymentHash) {
  throw new Error("No payment hash found. Run create_invoice.js first or pass INVOICE_PAYMENT_HASH.");
}

const response = http.post(`${serviceUrl}/wait-invoice-paid`, {
  headers: {
    "content-type": "application/json",
  },
  body: JSON.stringify({ node, paymentHash, timeoutMs, intervalMs }),
});

const body = json(response.body);
if (response.status !== 200 || body.settled !== true) {
  throw new Error(body.error || `Invoice was not settled, HTTP ${response.status}`);
}

output.invoiceSettled = true;
output.paymentHash = body.paymentHash || output.paymentHash;
output.preimage = body.preimage;

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

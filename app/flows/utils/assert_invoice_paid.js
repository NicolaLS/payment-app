const serviceUrl = requiredLeaseValue("opsUrl", "TEST_WALLET_SERVICE_URL").replace(/\/$/, "");
const timeoutMs = Number(readEnv("INVOICE_TIMEOUT_MS", "120000"));
const intervalMs = Number(readEnv("INVOICE_INTERVAL_MS", "2000"));
const paymentHash = readEnv("INVOICE_PAYMENT_HASH", "") || output.paymentHash;

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
  headers: requestHeaders(),
  body: JSON.stringify({ paymentHash, timeoutMs, intervalMs }),
});

const body = json(response.body);
if (response.status !== 200 || body.settled !== true) {
  throw new Error(body.error || `Invoice was not settled, HTTP ${response.status}`);
}

output.invoiceSettled = true;
output.paymentHash = body.paymentHash || output.paymentHash;
output.preimage = body.preimage;

function requestHeaders() {
  return {
    "x-reglab-lease-token": requiredLeaseValue("leaseToken", "TEST_WALLET_TOKEN"),
    "content-type": "application/json",
  };
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

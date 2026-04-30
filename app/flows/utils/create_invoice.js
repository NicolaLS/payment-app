const serviceUrl = requiredLeaseValue("opsUrl", "TEST_WALLET_SERVICE_URL").replace(/\/$/, "");
const sats = Number(readEnv("INVOICE_SATS", "21"));
const description = readEnv("INVOICE_DESCRIPTION", "Maestro test invoice");
const label = readEnv(
  "INVOICE_LABEL",
  `maestro-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
);

if (!Number.isInteger(sats) || sats <= 0) {
  throw new Error("INVOICE_SATS must be a positive integer.");
}

const response = http.post(`${serviceUrl}/create-invoice`, {
  headers: requestHeaders(),
  body: JSON.stringify({
    amount_msat: `${sats * 1000}msat`,
    description,
    label,
  }),
});

const body = json(response.body);
if (response.status !== 200) {
  throw new Error(body.error || `Invoice creation failed with HTTP ${response.status}`);
}

output.bolt11 = body.bolt11;
output.paymentHash = body.paymentHash;
output.invoiceLabel = body.label || label;

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

const serviceUrl = readEnv("TEST_WALLET_SERVICE_URL", "http://127.0.0.1:8082").replace(/\/$/, "");
const sats = Number(readEnv("INVOICE_SATS", "21"));
const description = readEnv("INVOICE_DESCRIPTION", "Maestro test invoice");
const label = readEnv(
  "INVOICE_LABEL",
  `maestro-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
);
const node = readEnv("INVOICE_NODE", "receiver");

if (!Number.isInteger(sats) || sats <= 0) {
  throw new Error("INVOICE_SATS must be a positive integer.");
}

const response = http.post(`${serviceUrl}/create-invoice`, {
  headers: {
    "content-type": "application/json",
  },
  body: JSON.stringify({
    node,
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
output.invoiceNode = body.node || node;

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

#!/usr/bin/env python3

import base64
import json
import os
import subprocess
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse
from urllib.error import HTTPError
from urllib.request import Request, urlopen


HOST = "0.0.0.0"
PORT = int(os.environ.get("PORT", "3021"))

BITCOIN_RPC_URL = os.environ["BITCOIN_RPC_URL"].rstrip("/")
BITCOIN_RPC_USER = os.environ["BITCOIN_RPC_USER"]
BITCOIN_RPC_PASSWORD = os.environ["BITCOIN_RPC_PASSWORD"]

PAYER_DIR = os.environ["CLN_PAYER_DIR"]
RECEIVER_DIR = os.environ["CLN_RECEIVER_DIR"]
RECEIVER_HOST = os.environ.get("CLN_RECEIVER_HOST", "cln-receiver")
RECEIVER_PORT = os.environ.get("CLN_RECEIVER_PORT", "9735")

NWC_LABEL = os.environ.get("NWC_LABEL", "papp-e2e")
NWC_BUDGET_MSAT = os.environ.get("NWC_BUDGET_MSAT", "100000000")
NWC_BUDGET_INTERVAL = os.environ.get("NWC_BUDGET_INTERVAL", "1d")
PUBLIC_NOSTR_RELAY_URL = os.environ.get("PUBLIC_NOSTR_RELAY_URL", "ws://127.0.0.1:7777")

CHANNEL_SATS = int(os.environ.get("CHANNEL_SATS", "500000"))
FUNDING_BTC = float(os.environ.get("CLN_FUNDING_BTC", "1.0"))
BOOTSTRAP_TIMEOUT_SEC = int(os.environ.get("BOOTSTRAP_TIMEOUT_SEC", "180"))

state_lock = threading.Lock()
state = {
    "ready": False,
    "bootstrap_error": None,
    "nwc_uri": None,
    "invoices": {},
}


def main():
    threading.Thread(target=bootstrap, daemon=True).start()
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"test-harness listening on {HOST}:{PORT}", flush=True)
    server.serve_forever()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            self.write_json(200, health_payload())
            return

        if self.path == "/nwc-uri":
            if not is_ready():
                self.write_json(503, health_payload())
                return
            with state_lock:
                self.write_json(200, {"uri": state["nwc_uri"]})
            return

        self.write_json(404, {"error": "not found"})

    def do_POST(self):
        if self.path == "/invoice":
            self.handle_create_invoice()
            return

        if self.path == "/invoice/wait-paid":
            self.handle_wait_paid()
            return

        if self.path.startswith("/mine/"):
            self.handle_mine()
            return

        self.write_json(404, {"error": "not found"})

    def handle_create_invoice(self):
        if not is_ready():
            self.write_json(503, health_payload())
            return

        body = self.read_json()
        sats = int(body.get("sats", 21))
        description = str(body.get("description", "Papp E2E invoice"))
        expiry = int(body.get("expiry", 600))
        if sats <= 0:
            self.write_json(400, {"error": "sats must be positive"})
            return
        if expiry <= 0:
            self.write_json(400, {"error": "expiry must be positive"})
            return

        label = f"papp-e2e-{uuid.uuid4().hex}"
        invoice = lightning_cli(
            RECEIVER_DIR,
            "invoice",
            f"{sats * 1000}msat",
            label,
            description,
            str(expiry),
        )
        record = {
            "label": label,
            "bolt11": invoice["bolt11"],
            "paymentHash": invoice["payment_hash"],
        }
        with state_lock:
            state["invoices"][invoice["bolt11"]] = record

        self.write_json(200, record)

    def handle_wait_paid(self):
        if not is_ready():
            self.write_json(503, health_payload())
            return

        body = self.read_json()
        bolt11 = str(body.get("bolt11", ""))
        timeout_ms = int(body.get("timeoutMs", 120000))
        interval_ms = int(body.get("intervalMs", 2000))
        if not bolt11:
            self.write_json(400, {"error": "bolt11 is required"})
            return

        deadline = time.monotonic() + timeout_ms / 1000
        while time.monotonic() < deadline:
            invoice = find_invoice(bolt11)
            if invoice and invoice.get("status") == "paid":
                self.write_json(
                    200,
                    {
                        "settled": True,
                        "paymentHash": invoice.get("payment_hash"),
                        "preimage": invoice.get("payment_preimage"),
                    },
                )
                return
            time.sleep(interval_ms / 1000)

        self.write_json(504, {"settled": False, "error": "invoice was not settled before timeout"})

    def handle_mine(self):
        try:
            blocks = int(self.path.removeprefix("/mine/"))
        except ValueError:
            self.write_json(400, {"error": "blocks must be an integer"})
            return

        if blocks <= 0:
            self.write_json(400, {"error": "blocks must be positive"})
            return

        hashes = mine_blocks(blocks)
        self.write_json(200, {"blocks": len(hashes), "hashes": hashes})

    def read_json(self):
        length = int(self.headers.get("content-length", "0"))
        if length == 0:
            return {}
        return json.loads(self.rfile.read(length).decode("utf-8"))

    def write_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print(f"{self.address_string()} - {fmt % args}", flush=True)


def bootstrap():
    deadline = time.monotonic() + BOOTSTRAP_TIMEOUT_SEC
    try:
        wait_for_bitcoind(deadline)
        ensure_bitcoin_wallet()
        ensure_chain_funded()
        wait_for_cln(PAYER_DIR, deadline)
        wait_for_cln(RECEIVER_DIR, deadline)
        fund_cln_wallet(PAYER_DIR)
        fund_cln_wallet(RECEIVER_DIR)
        mine_blocks(6)
        wait_for_cln_funds(PAYER_DIR, deadline)
        wait_for_cln_funds(RECEIVER_DIR, deadline)
        ensure_channel(deadline)
        nwc_uri = ensure_nwc_uri()
        with state_lock:
            state["nwc_uri"] = nwc_uri
            state["ready"] = True
        print("bootstrap complete", flush=True)
    except Exception as error:
        with state_lock:
            state["bootstrap_error"] = str(error)
        print(f"bootstrap failed: {error}", flush=True)


def wait_for_bitcoind(deadline):
    wait_until(deadline, lambda: bitcoin_rpc("getblockchaininfo"))


def ensure_bitcoin_wallet():
    wallets = bitcoin_rpc("listwallets")
    if "e2e" in wallets:
        return

    try:
        bitcoin_rpc("loadwallet", ["e2e"])
    except RuntimeError:
        bitcoin_rpc("createwallet", ["e2e"])


def ensure_chain_funded():
    info = bitcoin_rpc("getblockchaininfo")
    if info["blocks"] < 101:
        mine_blocks(101 - info["blocks"])


def wait_for_cln(lightning_dir, deadline):
    wait_until(deadline, lambda: lightning_cli(lightning_dir, "getinfo"))


def fund_cln_wallet(lightning_dir):
    funds = lightning_cli(lightning_dir, "listfunds")
    outputs = funds.get("outputs", [])
    confirmed = [
        output for output in outputs
        if output.get("status") == "confirmed" and msat_value(output.get("amount_msat", 0)) > 0
    ]
    if confirmed:
        return

    address = lightning_cli(lightning_dir, "newaddr")["bech32"]
    bitcoin_rpc_wallet("sendtoaddress", [address, FUNDING_BTC])


def wait_for_cln_funds(lightning_dir, deadline):
    def has_confirmed_funds():
        funds = lightning_cli(lightning_dir, "listfunds")
        return any(output.get("status") == "confirmed" for output in funds.get("outputs", []))

    wait_until(deadline, has_confirmed_funds)


def ensure_channel(deadline):
    receiver_id = lightning_cli(RECEIVER_DIR, "getinfo")["id"]
    lightning_cli(PAYER_DIR, "connect", receiver_id, RECEIVER_HOST, RECEIVER_PORT)

    if not peer_channels(receiver_id):
        lightning_cli(PAYER_DIR, "fundchannel", receiver_id, str(CHANNEL_SATS))
        mine_blocks(6)

    def channel_is_normal():
        return any(c.get("state") == "CHANNELD_NORMAL" for c in peer_channels(receiver_id))

    wait_until(deadline, channel_is_normal)


def peer_channels(peer_id):
    response = lightning_cli(PAYER_DIR, "listpeerchannels", peer_id)
    return response.get("channels", [])


def ensure_nwc_uri():
    existing = nip47_list()
    if existing:
        return rewrite_nwc_relay(existing["uri"])

    created = lightning_cli(
        PAYER_DIR,
        "nip47-create",
        NWC_LABEL,
        NWC_BUDGET_MSAT,
        NWC_BUDGET_INTERVAL,
    )
    entry = find_labelled_entry(created, NWC_LABEL)
    if not entry or "uri" not in entry:
        raise RuntimeError(f"nip47-create returned unexpected shape: {created!r}")
    return rewrite_nwc_relay(entry["uri"])


def nip47_list():
    try:
        response = lightning_cli(PAYER_DIR, "nip47-list", NWC_LABEL)
    except RuntimeError:
        return None
    return find_labelled_entry(response, NWC_LABEL)


def find_labelled_entry(response, label):
    """Locate the NWC entry for `label` across the shapes cln-nip47 has shipped.

    Observed shape (cln-nip47 in cln v25.12.x):
        [ { "<label>": { "uri": ..., "budget_msat": ..., ... } } ]
    Tolerated alternates: a plain dict keyed by label, or a list of dicts that
    already include a "uri" plus a "label" field.
    """

    def from_mapping(mapping):
        if not isinstance(mapping, dict):
            return None
        keyed = mapping.get(label)
        if isinstance(keyed, dict) and keyed.get("uri"):
            return keyed
        if mapping.get("label") == label and mapping.get("uri"):
            return mapping
        return None

    if isinstance(response, list):
        for item in response:
            entry = from_mapping(item)
            if entry:
                return entry
        return None

    return from_mapping(response)


def rewrite_nwc_relay(uri):
    parsed = urlparse(uri)
    query = [
        (key, value)
        for key, value in parse_qsl(parsed.query, keep_blank_values=True)
        if key != "relay"
    ]
    query.insert(0, ("relay", PUBLIC_NOSTR_RELAY_URL))
    return urlunparse(parsed._replace(query=urlencode(query)))


def find_invoice(bolt11):
    with state_lock:
        record = state["invoices"].get(bolt11)

    if record:
        invoices = lightning_cli(RECEIVER_DIR, "listinvoices", record["label"]).get("invoices", [])
        return invoices[0] if invoices else None

    decoded = lightning_cli(RECEIVER_DIR, "decodepay", bolt11)
    payment_hash = decoded["payment_hash"]
    invoices = lightning_cli(RECEIVER_DIR, "-k", "listinvoices", f"payment_hash={payment_hash}")
    invoices = invoices.get("invoices", [])
    return invoices[0] if invoices else None


def mine_blocks(blocks):
    address = bitcoin_rpc_wallet("getnewaddress")
    return bitcoin_rpc_wallet("generatetoaddress", [blocks, address])


def bitcoin_rpc(method, params=None):
    return bitcoin_rpc_request(BITCOIN_RPC_URL, method, params)


def bitcoin_rpc_wallet(method, params=None):
    return bitcoin_rpc_request(f"{BITCOIN_RPC_URL}/wallet/e2e", method, params)


def bitcoin_rpc_request(url, method, params=None):
    payload = json.dumps({
        "jsonrpc": "1.0",
        "id": "papp-e2e",
        "method": method,
        "params": params or [],
    }).encode("utf-8")
    token = base64.b64encode(f"{BITCOIN_RPC_USER}:{BITCOIN_RPC_PASSWORD}".encode("utf-8"))
    request = Request(url, data=payload, headers={
        "authorization": f"Basic {token.decode('ascii')}",
        "content-type": "application/json",
    })
    try:
        with urlopen(request, timeout=10) as response:
            body = json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        body = json.loads(error.read().decode("utf-8"))
    if body.get("error"):
        raise RuntimeError(f"bitcoin {method} failed: {body['error']}")
    return body["result"]


def lightning_cli(lightning_dir, *args):
    command = [
        "lightning-cli",
        "--network=regtest",
        f"--lightning-dir={lightning_dir}",
        *args,
    ]
    result = subprocess.run(command, check=False, text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"{' '.join(command)} failed: {result.stderr.strip() or result.stdout.strip()}"
        )
    output = result.stdout.strip()
    return json.loads(output) if output else {}


def wait_until(deadline, probe):
    last_error = None
    while time.monotonic() < deadline:
        try:
            result = probe()
            if result:
                return result
        except Exception as error:
            last_error = error
        time.sleep(1)
    raise TimeoutError(f"timed out waiting for service readiness: {last_error}")


def msat_value(value):
    if isinstance(value, int):
        return value
    if isinstance(value, str):
        return int(value.removesuffix("msat"))
    if isinstance(value, dict) and "msat" in value:
        return int(value["msat"])
    return 0


def health_payload():
    with state_lock:
        return {
            "ready": state["ready"],
            "bootstrapError": state["bootstrap_error"],
            "nwcUriAvailable": bool(state["nwc_uri"]),
        }


def is_ready():
    with state_lock:
        return bool(state["ready"])


if __name__ == "__main__":
    main()

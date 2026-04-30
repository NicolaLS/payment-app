const baseUrl = readEnvAny(["REGLAB_URL", "MAESTRO_REGLAB_URL"], "https://reglab.localhost").replace(/\/$/, "");
const runnerToken = resolveRunnerToken(baseUrl);
const template = readEnvAny(["REGLAB_TEMPLATE", "MAESTRO_REGLAB_TEMPLATE"], "payments-basic");
const snapshot = readEnvAny(["REGLAB_SNAPSHOT", "MAESTRO_REGLAB_SNAPSHOT"], "default");
const ttlSeconds = Number(readEnvAny(["REGLAB_LEASE_TTL_SECONDS", "MAESTRO_REGLAB_LEASE_TTL_SECONDS"], "900"));
const waitMs = parseDurationMs(readEnvAny(["REGLAB_LEASE_WAIT", "MAESTRO_REGLAB_LEASE_WAIT"], "180s"));

if (!Number.isInteger(ttlSeconds) || ttlSeconds <= 0) {
  throw new Error("REGLAB_LEASE_TTL_SECONDS must be a positive integer.");
}

const started = Date.now();
let delayMs = 250;

while (true) {
  const response = http.post(`${baseUrl}/api/runner/leases`, {
    headers: {
      authorization: `Bearer ${runnerToken}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({
      template,
      snapshot,
      ttl_seconds: ttlSeconds,
      metadata: leaseMetadata(),
    }),
  });

  const body = parseBody(response.body);
  if (response.status === 200) {
    output.reglab = {
      leaseId: body.lease_id,
      leaseToken: body.lease_token,
      template: body.template || template,
      snapshot: body.snapshot || snapshot,
      opsUrl: body.ops_url,
      evidenceUrl: body.evidence_url,
      nostrRelay: body.nostr_relay || (body.endpoints && body.endpoints.nostr_relay) || "",
      nwcUri: (body.endpoints && body.endpoints.nwc_uri) || "",
      deviceBaseUrl: resolveDeviceBaseUrl(baseUrl),
    };
    console.log(`claimed ReGLab lease ${output.reglab.leaseId}`);
    if (output.reglab.evidenceUrl) {
      console.log(`ReGLab evidence ${output.reglab.evidenceUrl}`);
    }
    break;
  }

  if (response.status !== 429 || body.error !== "pool_exhausted") {
    throw new Error(body.message || body.error || `ReGLab lease claim failed with HTTP ${response.status}`);
  }

  const elapsed = Date.now() - started;
  if (elapsed >= waitMs) {
    throw new Error(`Timed out claiming ReGLab lease after ${waitMs}ms.`);
  }

  const retryAfterMs = Number(body.retry_after_ms || 0);
  const sleepMs = Math.min(waitMs - elapsed, retryAfterMs > 0 ? retryAfterMs : delayMs);
  sleep(sleepMs);
  delayMs = Math.min(delayMs * 2, 5000);
}

function leaseMetadata() {
  return {
    suite: "maestro",
    flow: readEnv("MAESTRO_FILENAME", ""),
    shard: readEnv("MAESTRO_SHARD_INDEX", ""),
    device: readEnv("MAESTRO_DEVICE_UDID", ""),
    git_sha: readEnv("GITHUB_SHA", ""),
    ci_job_url: ciJobUrl(),
  };
}

function ciJobUrl() {
  const server = readEnv("GITHUB_SERVER_URL", "");
  const repo = readEnv("GITHUB_REPOSITORY", "");
  const run = readEnv("GITHUB_RUN_ID", "");
  return server && repo && run ? `${server}/${repo}/actions/runs/${run}` : "";
}

function resolveRunnerToken(hostBaseUrl) {
  const token = readEnvAny(["REGLAB_RUNNER_TOKEN", "MAESTRO_REGLAB_RUNNER_TOKEN"], "");
  if (token) {
    return token;
  }
  if (isLocalReglab(hostBaseUrl)) {
    return "dev-runner";
  }
  throw new Error("REGLAB_RUNNER_TOKEN or MAESTRO_REGLAB_RUNNER_TOKEN is required for remote ReGLab.");
}

function resolveDeviceBaseUrl(hostBaseUrl) {
  const explicit = readEnvAny(
    ["REGLAB_DEVICE_URL", "MAESTRO_REGLAB_DEVICE_URL", "TEST_REGLAB_DEVICE_URL"],
    ""
  ).replace(/\/$/, "");
  if (explicit) {
    return explicit;
  }
  if (isLocalReglab(hostBaseUrl) && maestroPlatform() === "android") {
    return readEnvAny(
      ["REGLAB_ANDROID_EMULATOR_URL", "MAESTRO_REGLAB_ANDROID_EMULATOR_URL"],
      "http://10.0.2.2"
    ).replace(/\/$/, "");
  }
  return "";
}

function isLocalReglab(hostBaseUrl) {
  const url = String(hostBaseUrl || "").replace(/\/$/, "");
  return isTruthy(readEnvAny(["REGLAB_LOCAL", "MAESTRO_REGLAB_LOCAL"], ""))
    || url === "https://reglab.localhost"
    || url === "https://localhost"
    || /^http:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2)(:\d+)?$/.test(url);
}

function maestroPlatform() {
  if (typeof maestro === "undefined" || typeof maestro.platform === "undefined") {
    return "";
  }
  return String(maestro.platform).toLowerCase();
}

function isTruthy(value) {
  return /^(1|true|yes|on)$/i.test(String(value || ""));
}

function parseBody(raw) {
  if (!raw) {
    return {};
  }
  try {
    return json(raw);
  } catch (_) {
    return { message: String(raw) };
  }
}

function parseDurationMs(value) {
  const text = String(value).trim();
  const match = text.match(/^([0-9]+)(ms|s|m)?$/);
  if (!match) {
    throw new Error(`Invalid duration: ${value}`);
  }
  const amount = Number(match[1]);
  const unit = match[2] || "ms";
  if (unit === "ms") return amount;
  if (unit === "s") return amount * 1000;
  if (unit === "m") return amount * 60 * 1000;
  throw new Error(`Invalid duration unit: ${unit}`);
}

function sleep(ms) {
  const duration = Math.max(0, Number(ms) || 0);
  if (duration === 0) {
    return;
  }
  try {
    if (typeof Java !== "undefined") {
      Java.type("java.lang.Thread").sleep(Math.floor(duration));
      return;
    }
  } catch (_) {
  }
  const until = Date.now() + duration;
  while (Date.now() < until) {
  }
}

function readEnv(name, fallback) {
  return typeof globalThis[name] === "undefined" ? fallback : String(globalThis[name]);
}

function readEnvAny(names, fallback) {
  for (const name of names) {
    const value = readEnv(name, "");
    if (value) {
      return value;
    }
  }
  return fallback;
}

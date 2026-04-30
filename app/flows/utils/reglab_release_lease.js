const reglab = output.reglab || {};
const leaseId = reglab.leaseId || readEnv("REGLAB_LEASE_ID", "");

if (leaseId) {
  const baseUrl = readEnvAny(["REGLAB_URL", "MAESTRO_REGLAB_URL"], "https://reglab.localhost").replace(/\/$/, "");
  const runnerToken = resolveRunnerToken(baseUrl);

  const response = http.delete(`${baseUrl}/api/runner/leases/${leaseId}`, {
    headers: {
      authorization: `Bearer ${runnerToken}`,
    },
  });

  if (response.status >= 400 && response.status !== 404 && response.status !== 410) {
    const body = parseBody(response.body);
    throw new Error(body.message || body.error || `ReGLab lease release failed with HTTP ${response.status}`);
  }

  console.log(`released ReGLab lease ${leaseId}`);
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

function isLocalReglab(hostBaseUrl) {
  const url = String(hostBaseUrl || "").replace(/\/$/, "");
  return isTruthy(readEnvAny(["REGLAB_LOCAL", "MAESTRO_REGLAB_LOCAL"], ""))
    || url === "https://reglab.localhost"
    || url === "https://localhost"
    || /^http:\/\/(localhost|127\.0\.0\.1|10\.0\.2\.2)(:\d+)?$/.test(url);
}

function isTruthy(value) {
  return /^(1|true|yes|on)$/i.test(String(value || ""));
}

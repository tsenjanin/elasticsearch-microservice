// One-shot Kibana setup, run by the kibana-setup compose service after every `up`. It is idempotent.
// - Hides every feature of the default space except KIBANA_KEEP_FEATURES. The list of all features is read from
//   Kibana itself, so new or renamed features in a future version are hidden too.
// - Opens Discover instead of the home page.
// - Shows dates as ISO 8601 without offset (KIBANA_DATE_FORMAT, KIBANA_DATE_NANOS_FORMAT; moment.js syntax) in one
//   time zone for everyone (KIBANA_TIME_ZONE, an IANA zone such as Europe/Zagreb, or Browser).
// Runs with the Node.js bundled in the Kibana image; no dependencies.

const KIBANA_URL = process.env.KIBANA_URL ?? 'http://kibana:5601';
const KEEP_FEATURES = (process.env.KIBANA_KEEP_FEATURES ?? '')
    .split(',')
    .map((feature) => feature.trim())
    .filter((feature) => feature.length > 0);
const DEFAULT_ROUTE = process.env.KIBANA_DEFAULT_ROUTE ?? '/app/discover';
const DATE_FORMAT = process.env.KIBANA_DATE_FORMAT ?? 'YYYY-MM-DDTHH:mm:ss.SSS';
const DATE_NANOS_FORMAT = process.env.KIBANA_DATE_NANOS_FORMAT ?? 'YYYY-MM-DDTHH:mm:ss.SSSSSSSSS';
const TIME_ZONE = process.env.KIBANA_TIME_ZONE ?? 'Browser';
const DEFAULT_SPACE = 'default';
const CLASSIC_SOLUTION = 'classic';
const AVAILABLE_LEVEL = 'available';
const RETRY_DELAY_MS = 5000;
const MAX_ATTEMPTS = 60;
const HEADERS = {'Content-Type': 'application/json', 'kbn-xsrf': 'true'};
// Kibana 9 only accepts the settings API from Kibana's own UI unless this header is set
const INTERNAL_ORIGIN_HEADERS = {'x-elastic-internal-origin': 'kibana'};

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function request(method, path, body, extraHeaders = {}) {
    const response = await fetch(`${KIBANA_URL}${path}`, {
        method,
        headers: {...HEADERS, ...extraHeaders},
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
        throw new Error(`${method} ${path} failed with ${response.status}: ${await response.text()}`);
    }

    return response.json();
}

async function waitForKibana() {
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        try {
            const status = await request('GET', '/api/status');
            if (status.status?.overall?.level === AVAILABLE_LEVEL) {
                return;
            }
        } catch {
            // Kibana is not accepting connections yet
        }
        console.log(`Waiting for Kibana at '${KIBANA_URL}' (${attempt}/${MAX_ATTEMPTS}).`);
        await sleep(RETRY_DELAY_MS);
    }
    throw new Error(`Kibana at '${KIBANA_URL}' did not become available.`);
}

async function hideFeatures() {
    const features = await request('GET', '/api/features');
    const disabledFeatures = features
        .map((feature) => feature.id)
        .filter((id) => !KEEP_FEATURES.includes(id));

    // Feature visibility only applies to the classic navigation, so the space is pinned to it
    const space = await request('GET', `/api/spaces/space/${DEFAULT_SPACE}`);
    await request('PUT', `/api/spaces/space/${DEFAULT_SPACE}`, {
        id: space.id,
        name: space.name,
        description: space.description,
        color: space.color,
        initials: space.initials,
        solution: CLASSIC_SOLUTION,
        disabledFeatures,
    });

    const kept = features
        .filter((feature) => KEEP_FEATURES.includes(feature.id))
        .map((feature) => `${feature.id} (${feature.name})`);
    console.log(`Kept features: ${kept.join(', ') || 'none'}.`);
    console.log(`Hid ${disabledFeatures.length} features: ${disabledFeatures.join(', ')}.`);
}

async function applySettings() {
    const changes = {
        'defaultRoute': DEFAULT_ROUTE,
        'dateFormat': DATE_FORMAT,
        'dateNanosFormat': DATE_NANOS_FORMAT,
        'dateFormat:tz': TIME_ZONE,
    };
    await request('POST', '/api/kibana/settings', {changes}, INTERNAL_ORIGIN_HEADERS);
    console.log(`Applied settings: ${JSON.stringify(changes)}.`);
}

try {
    await waitForKibana();
    await hideFeatures();
    await applySettings();
} catch (error) {
    console.error(`Kibana setup failed: ${error.message}`);
    process.exit(1);
}

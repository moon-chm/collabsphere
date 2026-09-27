import http from 'k6/http';
import { check, sleep } from 'k6';

// ── K6 Load Testing Script for CollabSphere (Render Free Tier) ──
// This script is designed to safely test the limits of the Render Free Tier
// without triggering a Denial of Service (DOS) ban.

export const options = {
    stages: [
        { duration: '10s', target: 25 }, // Ramp up
        { duration: '30s', target: 125 }, // Blast with 150 users (Will exceed free DB limits)
        { duration: '10s', target: 0 },  // Ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
        http_req_failed: ['rate<0.01'],   // Less than 1% of requests can fail
    },
};

// Replace this with your actual Render URL
const BASE_URL = 'https://collabsphere-server-qtke.onrender.com';

export default function () {
    // 1. We test a lightweight endpoint (e.g., health check or login)
    // If you don't have a /health endpoint, change this to a valid route.
    const res = http.get(`${BASE_URL}/`);

    // 2. We verify the server didn't crash or rate limit us
    check(res, {
        'status is 200': (r) => r.status === 200,
        'response time OK': (r) => r.timings.duration < 1000,
    });

    // 3. Wait a random amount of time between 0.5s and 1.5s to simulate real human behavior
    sleep(Math.random() + 0.5);
}

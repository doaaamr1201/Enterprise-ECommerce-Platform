import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const outcomes = new Counter('order_outcomes');

export const options = {
  stages: [
    { duration: '20s', target: 50 },
    { duration: '20s', target: 150 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.5'],
  },
};

const payload = JSON.stringify({ productId: 'PROD-001', quantity: 1, amount: 100.0 });

export default function () {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${__ENV.TEST_JWT}`,
    },
  };
  const res = http.post(`${BASE_URL}/api/orders`, payload, params);

  let outcome = `HTTP_${res.status}`;
  if (res.status === 200) {
    const body = res.json();
    outcome = body.message === 'Payment is taking too long' ? 'PENDING_TIMEOUT' : body.status;
  }
  outcomes.add(1, { outcome });

  if (outcome !== 'CONFIRMED') {
    console.log(`${new Date().toISOString()} ${outcome} ${res.status} ${res.body}`);
  }
  check(res, { 'status 200': (r) => r.status === 200 });
}

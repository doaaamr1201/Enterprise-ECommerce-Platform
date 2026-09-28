import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '2m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<800', 'p(99)<2000'],
    http_req_failed: ['rate<0.05'],
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
  check(res, { 'status 200/202': (r) => [200, 202].includes(r.status) });
}

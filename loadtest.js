import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '1m', target: 50 },  // Ramp-up to 50 users
        { duration: '3m', target: 200 }, // Ramp-up to 200 users
        { duration: '2m', target: 200 }, // Hold at 200 users
        { duration: '2m', target: 0 },   // Ramp-down to 0
    ],
};

const BASE_URL = 'http://ae71dbe415bc248a2b024ff6f3abcd80-1919520053.us-east-1.elb.amazonaws.com';

export default function () {
    // direct redirect URL එකට request යවන්න
    const res = http.get(`${BASE_URL}/abc123`, {
        redirects: 0,
    });

    check(res, {
        'status is 302 or 404': (r) => r.status === 302 || r.status === 404,
    });

    sleep(0.1);
}
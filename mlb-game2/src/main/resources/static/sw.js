// 홈 화면 앱(PWA)용 서비스 워커. 화면은 서버에서 매번 새로 받고, 네트워크가 끊겼을 때만 안내 문구를 보여준다.
'use strict';

var OFFLINE_HTML =
    '<!DOCTYPE html><html lang="ko"><head><meta charset="UTF-8">' +
    '<meta name="viewport" content="width=device-width, initial-scale=1">' +
    '<title>오프라인 | MLB 선수 키우기</title>' +
    '<style>body{margin:0;min-height:100vh;display:grid;place-items:center;font-family:system-ui,sans-serif;' +
    'background:#f4f6f9;color:#1a2233;text-align:center;padding:24px}' +
    'button{margin-top:16px;padding:10px 20px;border:0;border-radius:8px;background:#0b3d91;color:#fff;font-size:15px}</style>' +
    '</head><body><div><p style="font-size:40px;margin:0">⚾</p><p>인터넷 연결을 확인해 주세요.</p>' +
    '<button onclick="location.reload()">다시 시도</button></div></body></html>';

self.addEventListener('install', function () { self.skipWaiting(); });
self.addEventListener('activate', function (event) { event.waitUntil(self.clients.claim()); });

self.addEventListener('fetch', function (event) {
    if (event.request.mode !== 'navigate') return;
    event.respondWith(fetch(event.request).catch(function () {
        return new Response(OFFLINE_HTML, { headers: { 'Content-Type': 'text/html; charset=UTF-8' } });
    }));
});

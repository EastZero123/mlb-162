// 가벼운 연출: .celebrate 요소가 있으면 한 번 꽃가루, [data-count] 숫자는 0부터 올라간다.
(function () {
    'use strict';

    var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    function confetti() {
        var canvas = document.createElement('canvas');
        canvas.className = 'confetti-canvas';
        canvas.setAttribute('aria-hidden', 'true');
        document.body.appendChild(canvas);
        var ctx = canvas.getContext('2d');
        var dpr = window.devicePixelRatio || 1;
        canvas.width = window.innerWidth * dpr;
        canvas.height = window.innerHeight * dpr;
        ctx.scale(dpr, dpr);

        var colors = ['#c8102e', '#0b3d91', '#f0c419', '#1d6b38', '#ffffff'];
        var pieces = [];
        for (var i = 0; i < 120; i++) {
            pieces.push({
                x: window.innerWidth / 2 + (Math.random() - 0.5) * 120,
                y: window.innerHeight * 0.35,
                vx: (Math.random() - 0.5) * 12,
                vy: -Math.random() * 12 - 4,
                size: Math.random() * 6 + 4,
                rot: Math.random() * Math.PI,
                spin: (Math.random() - 0.5) * 0.3,
                color: colors[i % colors.length]
            });
        }

        var start = null;
        function frame(ts) {
            if (start === null) start = ts;
            var elapsed = ts - start;
            ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
            ctx.globalAlpha = Math.max(0, 1 - elapsed / 2600);
            pieces.forEach(function (p) {
                p.vy += 0.35;
                p.vx *= 0.99;
                p.x += p.vx;
                p.y += p.vy;
                p.rot += p.spin;
                ctx.save();
                ctx.translate(p.x, p.y);
                ctx.rotate(p.rot);
                ctx.fillStyle = p.color;
                ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
                ctx.restore();
            });
            if (elapsed < 2600) {
                requestAnimationFrame(frame);
            } else {
                canvas.remove();
            }
        }
        requestAnimationFrame(frame);
    }

    function countUp(el) {
        var target = parseFloat(el.getAttribute('data-count'));
        if (isNaN(target)) return;
        var decimals = (el.getAttribute('data-count').split('.')[1] || '').length;
        var duration = 700;
        var start = null;
        function frame(ts) {
            if (start === null) start = ts;
            var t = Math.min(1, (ts - start) / duration);
            var eased = 1 - Math.pow(1 - t, 3);
            el.textContent = (target * eased).toFixed(decimals);
            if (t < 1) requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
    }

    document.addEventListener('DOMContentLoaded', function () {
        if (reduceMotion) return;
        if (document.querySelector('.celebrate')) {
            setTimeout(confetti, 250);
        }
        document.querySelectorAll('[data-count]').forEach(countUp);
    });
})();

// 계약 화면: 제안마다 하나인 "트레이드 거부 조항" 체크박스 값을 그 제안의 수락·청문회·역제안 폼에 실어 보낸다.
// 폼: data-ntc="체크박스 id", 안에 <input type="hidden" name="noTradeClause">
document.addEventListener('submit', function (event) {
    'use strict';
    var form = event.target;
    var sourceId = form.getAttribute && form.getAttribute('data-ntc');
    if (!sourceId) return;
    var box = document.getElementById(sourceId);
    var field = form.querySelector('input[name="noTradeClause"]');
    if (box && field) field.value = box.checked ? 'true' : 'false';
});

// 홈 화면 앱(PWA): 서비스 워커 등록, 브라우저로 열었을 때 "앱으로 설치" 안내
(function () {
    'use strict';

    if ('serviceWorker' in navigator) {
        var swUrl = document.querySelector('link[rel="manifest"]').href.replace('manifest.webmanifest', 'sw.js');
        window.addEventListener('load', function () {
            navigator.serviceWorker.register(swUrl).catch(function () {});
        });
    }

    var DISMISS_KEY = 'installBannerDismissed';
    function dismissed() {
        try { return localStorage.getItem(DISMISS_KEY) === '1'; } catch (e) { return false; }
    }

    window.addEventListener('beforeinstallprompt', function (event) {
        event.preventDefault();
        if (dismissed()) return;
        var deferred = event;

        var en = document.documentElement.lang === 'en';
        var bar = document.createElement('div');
        bar.className = 'install-bar';
        bar.innerHTML = '<span>⚾ ' + (en ? 'Add to your home screen to use it like an app' : '홈 화면에 추가하면 앱처럼 쓸 수 있어요') + '</span>' +
            '<button type="button" class="btn btn-primary install-yes">' + (en ? 'Install' : '설치') + '</button>' +
            '<button type="button" class="install-no" aria-label="' + (en ? 'Close' : '닫기') + '">✕</button>';
        document.body.appendChild(bar);

        bar.querySelector('.install-yes').addEventListener('click', function () {
            bar.remove();
            deferred.prompt();
        });
        bar.querySelector('.install-no').addEventListener('click', function () {
            bar.remove();
            try { localStorage.setItem(DISMISS_KEY, '1'); } catch (e) { /* 저장 못 해도 무시 */ }
        });
    });
})();

/**
 * Mobile SafeFilter Engine for Android WebView with 1-Click Quick Block
 */
(() => {
    if (window.__safeFilterInitialized) {
        if (typeof window.__reScanSafeFilter === 'function') {
            window.__reScanSafeFilter();
        }
        return;
    }
    window.__safeFilterInitialized = true;

    // Inject CSS for instant hiding & Quick Block button
    const style = document.createElement('style');
    style.id = 'safe-kids-style';
    style.textContent = `
        .ytk-filtered-hidden {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            pointer-events: none !important;
        }
        .mobile-quick-block-btn {
            position: absolute;
            top: 6px;
            right: 6px;
            z-index: 99999;
            background: rgba(15, 23, 42, 0.85);
            color: white;
            border: 1px solid rgba(255,255,255,0.3);
            border-radius: 16px;
            padding: 3px 8px;
            font-size: 11px;
            font-weight: bold;
            box-shadow: 0 2px 6px rgba(0,0,0,0.4);
        }
        .mobile-quick-modal {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.7);
            z-index: 999999999;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 16px;
            font-family: sans-serif;
        }
        .mobile-quick-card {
            background: #1e293b;
            color: white;
            padding: 18px;
            border-radius: 14px;
            width: 100%;
            max-width: 360px;
        }
        .mobile-menu-btn {
            width: 100%;
            background: #334155;
            color: white;
            border: none;
            padding: 12px;
            border-radius: 8px;
            margin-top: 8px;
            font-size: 13px;
            font-weight: 600;
            text-align: left;
        }
        .mobile-menu-btn.danger {
            background: #dc2626;
        }
    `;
    document.head.appendChild(style);

    let config = {
        filterEnabled: true,
        filterMode: 'blacklist',
        blacklistedKeywords: [],
        whitelistedChannels: []
    };

    function loadConfig() {
        try {
            if (window.AndroidBridge && window.AndroidBridge.getFilterConfig) {
                const raw = window.AndroidBridge.getFilterConfig();
                config = JSON.parse(raw);
            }
        } catch (e) {
            console.error('Failed to load AndroidBridge config', e);
        }
    }

    function normalize(str) {
        if (!str) return '';
        return str
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/Đ/g, 'D')
            .trim();
    }

    function checkVideo(title, channel, aria) {
        if (!config.filterEnabled) return { block: false };

        const normTitle = normalize(title);
        const normChannel = normalize(channel);
        const normAria = normalize(aria);
        const combined = `${normTitle} ${normAria}`;

        if (config.filterMode === 'whitelist') {
            const allowed = config.whitelistedChannels.some(c => {
                const nc = normalize(c);
                return nc && (normChannel.includes(nc) || nc.includes(normChannel));
            });
            if (!allowed) {
                return { block: true, reason: 'Kênh không nằm trong Whitelist' };
            }
            return { block: false };
        }

        if (config.blacklistedKeywords) {
            for (const kw of config.blacklistedKeywords) {
                const nkw = normalize(kw);
                if (nkw && (combined.includes(nkw) || normTitle.includes(nkw))) {
                    return { block: true, reason: `Từ khóa cấm: ${kw}` };
                }
            }
        }

        return { block: false };
    }

    function openQuickBlock(title, channel) {
        const existing = document.getElementById('mob-quick-modal');
        if (existing) existing.remove();

        const words = title.split(/\s+/).filter(w => w.length > 2);
        const suggested = words.slice(0, 2).join(' ') || title.slice(0, 15);

        const modal = document.createElement('div');
        modal.id = 'mob-quick-modal';
        modal.className = 'mobile-quick-modal';
        modal.innerHTML = `
            <div class="mobile-quick-card">
                <h3 style="color:#38bdf8; margin-bottom: 8px;">🛡️ Chặn Nhanh Video Này</h3>
                <p style="font-size:12px; color:#cbd5e1; margin-bottom:12px;">${title.slice(0, 60)}...</p>
                ${channel ? `<button id="btn-m-block-ch" class="mobile-menu-btn danger">🚫 Chặn Toàn Bộ Kênh: ${channel}</button>` : ''}
                <button id="btn-m-block-kw" class="mobile-menu-btn">🎯 Chặn Từ Khóa: "${suggested}"</button>
                <button id="btn-m-close" class="mobile-menu-btn" style="background:transparent; color:#94a3b8; text-align:center;">Hủy</button>
            </div>
        `;
        document.body.appendChild(modal);

        document.getElementById('btn-m-block-kw')?.addEventListener('click', () => {
            if (!config.blacklistedKeywords.includes(suggested)) {
                config.blacklistedKeywords.push(suggested);
                modal.remove();
                scanAll();
            }
        });

        document.getElementById('btn-m-close')?.addEventListener('click', () => modal.remove());
    }

    function attachQuickButton(el, title, channel) {
        if (!title && !channel) return;
        if (el.querySelector('.mobile-quick-block-btn')) return;

        if (window.getComputedStyle(el).position === 'static') {
            el.style.position = 'relative';
        }

        const btn = document.createElement('button');
        btn.className = 'mobile-quick-block-btn';
        btn.innerHTML = '🛡️ Chặn';
        btn.onclick = (e) => {
            e.preventDefault();
            e.stopPropagation();
            openQuickBlock(title, channel);
        };
        el.appendChild(btn);
    }

    function processElement(el) {
        if (!el || el.classList.contains('ytk-filtered-hidden')) return;

        let title = '';
        const tEl = el.querySelector('#video-title, #title, h3, [id*="title"]');
        if (tEl) title = tEl.textContent || '';

        let channel = '';
        const cEl = el.querySelector('#channel-name, #byline, [id*="byline"]');
        if (cEl) channel = cEl.textContent || '';

        let aria = '';
        const aEl = el.querySelector('a[aria-label]');
        if (aEl) aria = aEl.getAttribute('aria-label') || '';

        if (!title && aria) title = aria;

        const res = checkVideo(title, channel, aria);
        if (res.block) {
            el.classList.add('ytk-filtered-hidden');
            el.style.display = 'none';
            if (window.AndroidBridge && window.AndroidBridge.onVideoBlocked) {
                window.AndroidBridge.onVideoBlocked(title, res.reason);
            }
        } else {
            attachQuickButton(el, title.trim(), channel.trim());
        }
    }

    function checkWatchPage() {
        if (!location.pathname.includes('/watch') && !location.search.includes('v=')) return;

        let title = document.title || '';
        let channel = '';
        const tEl = document.querySelector('ytkids-video-metadata #video-title, #meta #title, h1.title');
        if (tEl) title = tEl.textContent || title;

        const cEl = document.querySelector('ytkids-video-metadata #channel-name, [id*="byline"]');
        if (cEl) channel = cEl.textContent || channel;

        const res = checkVideo(title, channel, '');
        if (res.block) {
            const vids = document.querySelectorAll('video');
            vids.forEach(v => {
                try { v.pause(); v.currentTime = 0; } catch (e) {}
            });

            if (!document.getElementById('mobile-watch-guard')) {
                const overlay = document.createElement('div');
                overlay.id = 'mobile-watch-guard';
                overlay.style.cssText = `
                    position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
                    background: #0f172a; color: white; z-index: 99999999;
                    display: flex; flex-direction: column; align-items: center; justify-content: center;
                    text-align: center; padding: 20px; font-family: sans-serif;
                `;
                overlay.innerHTML = `
                    <div style="font-size: 54px; margin-bottom: 12px;">🛡️</div>
                    <h2 style="font-size: 20px; color: #38bdf8; margin-bottom: 8px;">Video Bị Phụ Huynh Chặn</h2>
                    <p style="font-size: 14px; color: #cbd5e1; margin-bottom: 20px;">${res.reason}</p>
                    <button id="mobile-home-btn" style="
                        background: #0284c7; color: white; border: none; padding: 12px 24px;
                        border-radius: 24px; font-weight: bold; font-size: 15px;
                    ">Quay về Trang Chủ</button>
                `;
                document.body.appendChild(overlay);
                document.getElementById('mobile-home-btn').onclick = () => {
                    location.href = 'https://www.youtubekids.com/';
                };
            }
        }
    }

    function scanAll() {
        loadConfig();
        const selectors = [
            'ytkids-compact-video-renderer',
            'ytkids-grid-video-renderer',
            'ytkids-video-renderer',
            'ytkids-carousel-item-renderer',
            'ytkids-item-section-renderer',
            'ytd-compact-video-renderer',
            'ytd-grid-video-renderer'
        ];
        document.querySelectorAll(selectors.join(',')).forEach(processElement);
        checkWatchPage();
    }

    window.__reScanSafeFilter = scanAll;

    const obs = new MutationObserver(() => {
        scanAll();
    });
    obs.observe(document.documentElement || document.body, { childList: true, subtree: true });

    scanAll();
})();

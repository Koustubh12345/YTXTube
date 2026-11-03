const PIPED_INSTANCES = [
  'https://api.piped.private.coffee',
  'https://pipedapi.ducks.party'
];

let currentInstance = PIPED_INSTANCES[0];
let currentVideoId = null;
let currentVideoData = null;
let isFeedLoading = false;
let feedPage = 1;
let currentCategory = 'All';
let ytPlayer = null;

const DEFAULT_VIDEOS = [
  {
    id: 'dQw4w9WgXcQ',
    title: 'Rick Astley - Never Gonna Give You Up (Official Music Video) [4K Remastered]',
    uploaderName: 'Rick Astley',
    uploaderAvatar: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg',
    duration: '3:33',
    views: '1.5B views',
    uploaded: '14 years ago',
    likes: 19500000,
    subs: '4.56M subscribers'
  },
  {
    id: 'k6jwb5k15Z4',
    title: 'Magna vs Dante 「Black Clover S2 AMV」 The Way It Ends (Full HD 1080p)',
    uploaderName: 'ABOKAI',
    uploaderAvatar: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/k6jwb5k15Z4/hqdefault.jpg',
    duration: '4:41',
    views: '785K views',
    uploaded: '3 days ago',
    likes: 42000,
    subs: '850K subscribers'
  },
  {
    id: '0e3GPea1Tyg',
    title: 'MrBeast - $456,000 Squid Game In Real Life!',
    uploaderName: 'MrBeast',
    uploaderAvatar: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/0e3GPea1Tyg/hqdefault.jpg',
    duration: '25:41',
    views: '654M views',
    uploaded: '2 years ago',
    likes: 17200000,
    subs: '320M subscribers'
  },
  {
    id: '9bZkp7q19f0',
    title: 'PSY - GANGNAM STYLE (강남스타일) M/V',
    uploaderName: 'officialpsy',
    uploaderAvatar: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/9bZkp7q19f0/hqdefault.jpg',
    duration: '4:13',
    views: '5.2B views',
    uploaded: '11 years ago',
    likes: 28000000,
    subs: '18.9M subscribers'
  },
  {
    id: 'kJQP7kiw5Fk',
    title: 'Luis Fonsi - Despacito ft. Daddy Yankee',
    uploaderName: 'Luis Fonsi',
    uploaderAvatar: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/kJQP7kiw5Fk/hqdefault.jpg',
    duration: '4:42',
    views: '8.5B views',
    uploaded: '7 years ago',
    likes: 54000000,
    subs: '31.2M subscribers'
  },
  {
    id: 'JGwWNGJdvx8',
    title: 'Ed Sheeran - Shape of You (Official Music Video)',
    uploaderName: 'Ed Sheeran',
    uploaderAvatar: 'https://images.unsplash.com/photo-1522075469751-3a6694fb2f61?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/JGwWNGJdvx8/hqdefault.jpg',
    duration: '4:23',
    views: '6.3B views',
    uploaded: '7 years ago',
    likes: 33000000,
    subs: '54.5M subscribers'
  },
  {
    id: 'YQHsXMglC9A',
    title: 'Adele - Hello (Official Music Video)',
    uploaderName: 'Adele',
    uploaderAvatar: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg',
    duration: '6:07',
    views: '3.2B views',
    uploaded: '8 years ago',
    likes: 18500000,
    subs: '30.1M subscribers'
  },
  {
    id: 'fJ9rUzIMcZQ',
    title: 'Queen – Bohemian Rhapsody (Official Video Remastered)',
    uploaderName: 'Queen Official',
    uploaderAvatar: 'https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=64&h=64&fit=crop',
    thumbnail: 'https://i.ytimg.com/vi/fJ9rUzIMcZQ/hqdefault.jpg',
    duration: '6:00',
    views: '1.7B views',
    uploaded: '15 years ago',
    likes: 12100000,
    subs: '17.8M subscribers'
  }
];

const FALLBACK_COMMENTS = [
  {
    author: '@AlexMusicVibes',
    thumbnail: 'https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=64&h=64&fit=crop',
    commentedTime: '2 days ago',
    commentText: 'This video never gets old! Pure masterpiece every single time.',
    likeCount: 4320
  },
  {
    author: '@StreamLover',
    thumbnail: 'https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=64&h=64&fit=crop',
    commentedTime: '4 days ago',
    commentText: 'Clean playback with zero ads, loving this interface!',
    likeCount: 1845
  },
  {
    author: '@SoundMaster_Pro',
    thumbnail: 'https://images.unsplash.com/photo-1580489944761-15a19d654956?w=64&h=64&fit=crop',
    commentedTime: '1 week ago',
    commentText: 'The sound mixing and production here is completely next level.',
    likeCount: 940
  },
  {
    author: '@CinemaBuff',
    thumbnail: 'https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=64&h=64&fit=crop',
    commentedTime: '2 weeks ago',
    commentText: 'Who else is watching this today? Legendary timeless classic.',
    likeCount: 654
  }
];

function initSystemTheme() {
  const sync = () => {
    const isDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    document.body.classList.remove('dark-theme', 'light-theme');
    document.body.classList.add(isDark ? 'dark-theme' : 'light-theme');
  };
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', sync);
  }
  sync();
}

const ALGO = {
  STOP_WORDS: new Set([
    'the', 'a', 'an', 'and', 'or', 'in', 'on', 'at', 'to', 'for', 'of', 'with', 
    'by', 'from', 'official', 'video', 'music', 'hd', '4k', 'remastered', 'ft', 
    'feat', 'mv', 'lyrics', 'full', 'live', 'episode', 'ep', 'season', 'part', 'new'
  ]),

  getHistory() {
    try {
      return JSON.parse(localStorage.getItem('ytxtube_history') || '[]');
    } catch {
      return [];
    }
  },

  getPreferences() {
    try {
      return JSON.parse(localStorage.getItem('ytxtube_prefs') || '{"keywords":{}, "channels":{}}');
    } catch {
      return { keywords: {}, channels: {} };
    }
  },

  recordWatch(video) {
    if (!video || !video.id) return;
    try {
      let history = this.getHistory();
      history = history.filter(item => item.id !== video.id);
      history.unshift({
        id: video.id,
        title: video.title,
        uploader: video.uploaderName,
        timestamp: Date.now()
      });
      if (history.length > 50) history = history.slice(0, 50);
      localStorage.setItem('ytxtube_history', JSON.stringify(history));

      const prefs = this.getPreferences();
      if (video.uploaderName) {
        prefs.channels[video.uploaderName] = (prefs.channels[video.uploaderName] || 0) + 3;
      }
      if (video.title) {
        const words = video.title.toLowerCase().replace(/[^a-z0-9\s]/g, '').split(/\s+/);
        words.forEach(w => {
          if (w.length > 2 && !this.STOP_WORDS.has(w)) {
            prefs.keywords[w] = (prefs.keywords[w] || 0) + 1;
          }
        });
      }
      localStorage.setItem('ytxtube_prefs', JSON.stringify(prefs));
    } catch (e) {
      console.warn('Algorithm record note:', e);
    }
  },

  getTopInterests() {
    const prefs = this.getPreferences();
    const sortedKeywords = Object.entries(prefs.keywords || {})
      .sort((a, b) => b[1] - a[1])
      .map(entry => entry[0]);
    const sortedChannels = Object.entries(prefs.channels || {})
      .sort((a, b) => b[1] - a[1])
      .map(entry => entry[0]);

    const interests = [];
    if (sortedChannels.length > 0) interests.push(sortedChannels[0]);
    if (sortedKeywords.length > 0) interests.push(sortedKeywords[0]);
    if (sortedChannels.length > 1) interests.push(sortedChannels[1]);
    if (sortedKeywords.length > 1) interests.push(sortedKeywords[1]);

    return interests;
  }
};

function formatViews(views) {
  if (views === undefined || views === null) return '1.2M views';
  if (typeof views === 'string' && views.includes('view')) return views;
  const n = parseInt(views, 10);
  if (isNaN(n)) return '1.2M views';
  if (n >= 1e9) return (n / 1e9).toFixed(1) + 'B views';
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M views';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K views';
  return n + ' views';
}

function formatCount(count, fallback = '158K') {
  if (count === undefined || count === null) return fallback;
  const n = parseInt(count, 10);
  if (isNaN(n)) return fallback;
  if (n >= 1e6) return (n / 1e6).toFixed(1) + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1) + 'K';
  return n.toString();
}

function formatDuration(sec) {
  if (sec === -1 || sec === null || sec === undefined) return 'LIVE';
  const s = parseInt(sec, 10);
  if (isNaN(s) || s < 0) return '0:00';
  const hrs = Math.floor(s / 3600);
  const mins = Math.floor((s % 3600) / 60);
  const remSec = s % 60;
  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${remSec.toString().padStart(2, '0')}`;
  }
  return `${mins}:${remSec.toString().padStart(2, '0')}`;
}

function formatUploadTime(dateStr, isLive = false) {
  if (isLive) return 'Streaming now';
  if (!dateStr) return 'Recently';
  return dateStr;
}

function escapeHtml(str) {
  if (!str) return '';
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function copyTextToClipboard(text) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    return navigator.clipboard.writeText(text);
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.focus();
  textarea.select();
  try {
    document.execCommand('copy');
  } catch (err) {}
  document.body.removeChild(textarea);
  return Promise.resolve();
}

async function fetchVideos(query = '', category = 'All') {
  let endpoint = `${currentInstance}/trending?region=US`;
  if (query) {
    endpoint = `${currentInstance}/search?q=${encodeURIComponent(query)}&filter=videos`;
  } else if (category && category !== 'All') {
    endpoint = `${currentInstance}/search?q=${encodeURIComponent(category)}&filter=videos`;
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);
    const res = await fetch(endpoint, { signal: controller.signal });
    clearTimeout(timeout);

    if (!res.ok) throw new Error('Network error');
    const data = await res.json();
    const rawItems = Array.isArray(data) ? data : (data.items || []);

    if (rawItems.length > 0) {
      let parsed = rawItems
        .filter(item => {
          if (!query && category === 'All') {
            const isLive = item.duration === -1 || item.duration < 0 || (item.title && item.title.toLowerCase().includes('live'));
            const isNews = item.title && /news|aaj tak|india today|cnn|fox|bbc|abp/i.test(item.title);
            if (isLive && isNews) return false;
          }
          return true;
        })
        .map(item => parseVideoItem(item));

      return parsed.length > 0 ? parsed : DEFAULT_VIDEOS;
    }
  } catch (e) {
    console.warn('API fetch fallback:', e.message);
    for (const inst of PIPED_INSTANCES) {
      if (inst !== currentInstance) {
        currentInstance = inst;
        break;
      }
    }
  }

  if (query) {
    const qLower = query.toLowerCase();
    const filtered = DEFAULT_VIDEOS.filter(v => 
      v.title.toLowerCase().includes(qLower) || 
      v.uploaderName.toLowerCase().includes(qLower)
    );
    return filtered.length > 0 ? filtered : DEFAULT_VIDEOS;
  }
  return DEFAULT_VIDEOS;
}

function parseVideoItem(item) {
  let vid = item.url ? item.url.replace('/watch?v=', '') : item.id;
  if (vid && vid.includes('&')) vid = vid.split('&')[0];
  
  const isLive = item.duration === -1 || item.duration < 0 || (item.title && item.title.toLowerCase().includes('live'));

  return {
    id: vid || 'dQw4w9WgXcQ',
    title: item.title || 'YouTube Video',
    uploaderName: item.uploaderName || item.channel || 'Official Channel',
    uploaderAvatar: item.uploaderAvatar || `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(item.uploaderName || 'YT')}`,
    thumbnail: item.thumbnail || `https://i.ytimg.com/vi/${vid}/hqdefault.jpg`,
    duration: isLive ? 'LIVE' : formatDuration(item.duration),
    isLive: isLive,
    views: formatViews(item.views),
    uploaded: formatUploadTime(item.uploadedDate || item.uploaded, isLive)
  };
}

async function getRecommendedFeed() {
  const interests = ALGO.getTopInterests();
  if (interests.length === 0) {
    return await fetchVideos();
  }

  try {
    const primaryQuery = interests[0];
    const secondaryQuery = interests.length > 1 ? interests[1] : null;

    const [primaryVideos, secondaryVideos, trendingVideos] = await Promise.all([
      fetchVideos(primaryQuery),
      secondaryQuery ? fetchVideos(secondaryQuery) : Promise.resolve([]),
      fetchVideos()
    ]);

    const combined = [];
    const seenIds = new Set();

    const addUnique = (list, count) => {
      let added = 0;
      for (const v of list) {
        if (!seenIds.has(v.id)) {
          seenIds.add(v.id);
          combined.push(v);
          added++;
          if (added >= count) break;
        }
      }
    };

    addUnique(primaryVideos, 4);
    if (secondaryVideos.length > 0) addUnique(secondaryVideos, 3);
    addUnique(trendingVideos, 6);
    addUnique(primaryVideos, 12);
    addUnique(trendingVideos, 12);

    return combined.length > 0 ? combined : DEFAULT_VIDEOS;
  } catch (e) {
    return await fetchVideos();
  }
}

function renderGrid(videos, append = false) {
  const grid = document.getElementById('video-grid');
  if (!grid) return;
  if (!append) grid.innerHTML = '';

  videos.forEach(v => {
    const card = document.createElement('div');
    card.className = 'video-card';
    card.onclick = () => openWatchView(v);

    const badgeHtml = v.isLive || v.duration === 'LIVE'
      ? `<span class="live-badge">● LIVE</span>`
      : `<span class="duration-badge">${v.duration}</span>`;

    card.innerHTML = `
      <div class="thumbnail-box">
        <img src="${v.thumbnail}" alt="${escapeHtml(v.title)}" loading="lazy" onerror="this.src='https://i.ytimg.com/vi/${v.id}/hqdefault.jpg'">
        ${badgeHtml}
      </div>
      <div class="card-details">
        <img class="card-avatar" src="${v.uploaderAvatar}" alt="${escapeHtml(v.uploaderName)}" onerror="this.src='https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(v.uploaderName)}'">
        <div class="card-meta">
          <h3 class="card-title" title="${escapeHtml(v.title)}">${escapeHtml(v.title)}</h3>
          <div class="card-channel">${escapeHtml(v.uploaderName)}</div>
          <div class="card-views">${v.views} • ${v.uploaded}</div>
        </div>
      </div>
    `;
    grid.appendChild(card);
  });
}

function loadNativePlayer(videoId, title) {
  const container = document.getElementById('player-container');
  if (!container) return;

  container.innerHTML = `
    <iframe 
      id="yt-embed" 
      src="https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&enablejsapi=1&rel=0&modestbranding=1&iv_load_policy=3&playsinline=1&vq=highres" 
      title="${escapeHtml(title)}" 
      frameborder="0" 
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; display-capture; screen-wake-lock" 
      referrerpolicy="strict-origin-when-cross-origin" 
      allowfullscreen>
    </iframe>
  `;

  if (typeof YT !== 'undefined' && YT.Player) {
    try {
      ytPlayer = new YT.Player('yt-embed', {
        events: {
          onReady: (event) => {
            try {
              event.target.playVideo();
              if (event.target.setPlaybackQuality) {
                event.target.setPlaybackQuality('highres');
              }
            } catch (e) {}
          }
        }
      });
    } catch (e) {
      console.warn('YT.Player init note:', e);
    }
  }
}

async function fetchComments(videoId) {
  const mobileList = document.getElementById('comments-list');
  const desktopList = document.getElementById('desktop-comments-list');
  const previewCountEl = document.getElementById('comments-preview-count');
  const desktopCountEl = document.getElementById('desktop-comments-count');
  const drawerCountEl = document.getElementById('comments-drawer-count');
  const previewTextEl = document.getElementById('comments-preview-text');
  
  if (mobileList) mobileList.innerHTML = '<div style="padding: 24px; color: var(--yt-text-muted); text-align: center;">Loading comments...</div>';
  if (desktopList) desktopList.innerHTML = '<div style="padding: 24px; color: var(--yt-text-muted); text-align: center;">Loading comments...</div>';

  try {
    const res = await fetch(`${currentInstance}/comments/${videoId}`);
    if (!res.ok) throw new Error('Comments failed');
    const data = await res.json();
    
    const count = data.commentCount;
    const countStr = count ? `${count.toLocaleString()}` : 'Comments';
    if (previewCountEl) previewCountEl.textContent = countStr;
    if (desktopCountEl) desktopCountEl.textContent = countStr;
    if (drawerCountEl) drawerCountEl.textContent = countStr;

    const comments = data.comments;
    if (comments && comments.length > 0) {
      if (previewTextEl) previewTextEl.textContent = comments[0].commentText ? `"${comments[0].commentText}"` : 'Tap to view comments';
      renderComments(comments);
      return;
    }
  } catch (e) {
    console.warn('Comments fetch note:', e);
  }

  if (previewCountEl) previewCountEl.textContent = '1,842';
  if (desktopCountEl) desktopCountEl.textContent = '1,842';
  if (drawerCountEl) drawerCountEl.textContent = '1,842';
  if (previewTextEl) previewTextEl.textContent = `"${FALLBACK_COMMENTS[0].commentText}"`;
  renderComments(FALLBACK_COMMENTS);
}

function renderComments(comments) {
  const containers = [
    document.getElementById('comments-list'),
    document.getElementById('desktop-comments-list')
  ].filter(Boolean);

  containers.forEach(container => {
    container.innerHTML = '';
    comments.forEach(c => {
      const item = document.createElement('div');
      item.className = 'comment-item';

      const author = c.author || '@user';
      const avatar = c.thumbnail || `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(author)}`;
      const time = c.commentedTime || 'Recently';
      const text = c.commentText || '';
      const likes = c.likeCount || 0;

      item.innerHTML = `
        <img class="comment-avatar" src="${avatar}" alt="${escapeHtml(author)}" onerror="this.src='https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(author)}'">
        <div class="comment-body">
          <div class="comment-header">
            <span class="comment-author">${escapeHtml(author)}</span>
            <span class="comment-time">${escapeHtml(time)}</span>
          </div>
          <div class="comment-text">${escapeHtml(text)}</div>
          <div class="comment-actions">
            <button class="comment-like-btn" title="Like">
              <svg viewBox="0 0 24 24" class="yt-icon" style="width: 16px; height: 16px;"><path d="M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73v-2z" fill="currentColor"/></svg>
              <span class="comment-like-num">${likes > 0 ? formatCount(likes) : ''}</span>
            </button>
            <button class="comment-dislike-btn" title="Dislike">
              <svg viewBox="0 0 24 24" class="yt-icon" style="width: 16px; height: 16px;"><path d="M15 3H6c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v2c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 23l6.59-6.59c.36-.36.58-.86.58-1.41V5c0-1.1-.9-2-2-2zm4 0v12h4V3h-4z" fill="currentColor"/></svg>
            </button>
            <span class="comment-reply-btn">Reply</span>
          </div>
        </div>
      `;

      const likeBtn = item.querySelector('.comment-like-btn');
      const likeNum = item.querySelector('.comment-like-num');
      let hasLiked = false;
      likeBtn.onclick = () => {
        hasLiked = !hasLiked;
        likeBtn.style.color = hasLiked ? '#3ea6ff' : 'var(--yt-text-muted)';
        const current = parseInt(likeNum.textContent) || (likes || 0);
        likeNum.textContent = hasLiked ? formatCount(current + 1) : (likes > 0 ? formatCount(likes) : '');
      };

      container.appendChild(item);
    });
  });
}

function openWatchView(video, pushHistory = true) {
  currentVideoId = video.id;
  currentVideoData = video;

  if (pushHistory) {
    window.history.pushState({ view: 'watch', videoId: video.id }, '', `?v=${video.id}`);
  }

  ALGO.recordWatch(video);

  document.getElementById('feed-view').style.display = 'none';
  document.getElementById('chips-bar').style.display = 'none';
  document.getElementById('watch-view').style.display = 'block';

  document.getElementById('watch-title').textContent = video.title;
  document.getElementById('watch-channel-name').textContent = video.uploaderName;
  document.getElementById('watch-channel-avatar').src = video.uploaderAvatar;
  document.getElementById('watch-channel-subs').textContent = video.subs || '1.2M subscribers';
  document.getElementById('watch-like-count').textContent = formatCount(video.likes, '158K');

  loadNativePlayer(video.id, video.title);
  renderRelatedList(video.id);
  fetchComments(video.id);
  fetchStreamDetails(video.id);

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function fetchStreamDetails(videoId) {
  try {
    const res = await fetch(`${currentInstance}/streams/${videoId}`);
    if (!res.ok) return;
    const data = await res.json();
    
    if (data.likes) {
      document.getElementById('watch-like-count').textContent = formatCount(data.likes);
    }
    if (data.uploaderSubscriberCount) {
      document.getElementById('watch-channel-subs').textContent = `${formatCount(data.uploaderSubscriberCount)} subscribers`;
    }
    if (data.uploaderAvatar) {
      document.getElementById('watch-channel-avatar').src = data.uploaderAvatar;
    }
    if (data.relatedStreams && data.relatedStreams.length > 0) {
      const related = data.relatedStreams.map(item => parseVideoItem(item));
      renderRelatedListCustom(related, false);
    }
  } catch (e) {
    console.log('Stream details fetch note:', e);
  }
}

function renderRelatedList(excludeId) {
  const list = document.getElementById('related-list');
  if (!list) return;
  list.innerHTML = '';
  const related = DEFAULT_VIDEOS.filter(v => v.id !== excludeId);
  renderRelatedListCustom(related, false);
}

function renderRelatedListCustom(videos, append = false) {
  const list = document.getElementById('related-list');
  if (!list) return;
  if (!append) list.innerHTML = '';

  videos.forEach(v => {
    const item = document.createElement('div');
    item.className = 'related-item';
    item.onclick = () => openWatchView(v);

    item.innerHTML = `
      <div class="related-thumb">
        <img src="${v.thumbnail}" alt="${escapeHtml(v.title)}" onerror="this.src='https://i.ytimg.com/vi/${v.id}/hqdefault.jpg'">
      </div>
      <div class="related-meta">
        <h4 class="related-title">${escapeHtml(v.title)}</h4>
        <div class="related-channel">${escapeHtml(v.uploaderName)}</div>
        <div class="related-views">${v.views}</div>
      </div>
    `;
    list.appendChild(item);
  });
}

function goHome(pushHistory = true) {
  if (pushHistory) {
    window.history.pushState({ view: 'home' }, '', window.location.pathname);
  }
  document.getElementById('watch-view').style.display = 'none';
  document.getElementById('feed-view').style.display = 'block';
  document.getElementById('chips-bar').style.display = 'block';
  
  const container = document.getElementById('player-container');
  if (container) container.innerHTML = '';

  window.scrollTo({ top: 0, behavior: 'smooth' });
}

document.addEventListener('DOMContentLoaded', async () => {
  initSystemTheme();

  window.history.replaceState({ view: 'home' }, '', window.location.pathname);

  const videos = await getRecommendedFeed();
  renderGrid(videos);

  const logoBtn = document.getElementById('logo-btn');
  if (logoBtn) {
    logoBtn.onclick = (e) => {
      e.preventDefault();
      goHome();
    };
  }

  window.addEventListener('popstate', (e) => {
    const modals = [
      'comments-drawer-backdrop',
      'options-modal-backdrop',
      'donate-modal-backdrop'
    ];
    let modalClosed = false;
    for (const m of modals) {
      const el = document.getElementById(m);
      if (el && el.style.display !== 'none') {
        el.style.display = 'none';
        modalClosed = true;
      }
    }
    if (modalClosed) return;

    const searchOverlay = document.getElementById('mobile-search-overlay');
    if (searchOverlay && searchOverlay.classList.contains('active')) {
      searchOverlay.classList.remove('active');
      return;
    }

    const watchView = document.getElementById('watch-view');
    if (watchView && watchView.style.display !== 'none') {
      goHome(false);
      return;
    }
  });

  window.addEventListener('wheel', (e) => {
    if (window.innerWidth > 800) {
      const watchView = document.getElementById('watch-view');
      if (watchView && watchView.style.display !== 'none') {
        if (e.target.closest('#desktop-comments-list')) {
          return;
        }
        const secondaryCol = document.querySelector('.secondary-column');
        if (secondaryCol) {
          secondaryCol.scrollTop += e.deltaY;
        }
      }
    }
  }, { passive: true });

  const searchForm = document.getElementById('search-form');
  const searchInput = document.getElementById('search-input');
  const clearBtn = document.getElementById('clear-search');

  if (searchInput && clearBtn) {
    searchInput.addEventListener('input', () => {
      clearBtn.style.display = searchInput.value ? 'flex' : 'none';
    });

    clearBtn.onclick = () => {
      searchInput.value = '';
      clearBtn.style.display = 'none';
      searchInput.focus();
    };
  }

  if (searchForm && searchInput) {
    searchForm.onsubmit = async (e) => {
      e.preventDefault();
      const q = searchInput.value.trim();
      if (!q) return;

      goHome();
      const grid = document.getElementById('video-grid');
      if (grid) grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; padding: 40px; color: var(--yt-text-muted);">Searching YouTube...</div>';
      
      const results = await fetchVideos(q);
      renderGrid(results);
    };
  }

  const mobileSearchBtn = document.getElementById('mobile-search-toggle');
  const mobileSearchOverlay = document.getElementById('mobile-search-overlay');
  const mobileSearchBack = document.getElementById('mobile-search-back');
  const mobileSearchForm = document.getElementById('mobile-search-form');
  const mobileSearchInput = document.getElementById('mobile-search-input');

  if (mobileSearchBtn && mobileSearchOverlay && mobileSearchInput) {
    mobileSearchBtn.onclick = () => {
      mobileSearchOverlay.classList.add('active');
      mobileSearchInput.focus();
    };
  }

  if (mobileSearchBack && mobileSearchOverlay && mobileSearchInput) {
    mobileSearchBack.onclick = () => {
      mobileSearchOverlay.classList.remove('active');
      mobileSearchInput.value = '';
    };
  }

  if (mobileSearchForm && mobileSearchInput) {
    mobileSearchForm.onsubmit = async (e) => {
      e.preventDefault();
      const q = mobileSearchInput.value.trim();
      if (!q) return;

      if (mobileSearchOverlay) mobileSearchOverlay.classList.remove('active');
      goHome();
      const grid = document.getElementById('video-grid');
      if (grid) grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; padding: 40px; color: var(--yt-text-muted);">Searching YouTube...</div>';
      
      const results = await fetchVideos(q);
      renderGrid(results);
    };
  }

  const chips = document.querySelectorAll('.chip');
  chips.forEach(chip => {
    chip.onclick = async () => {
      chips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');

      const cat = chip.dataset.category;
      currentCategory = cat;
      feedPage = 1;
      goHome();
      const grid = document.getElementById('video-grid');
      if (grid) grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; padding: 40px; color: var(--yt-text-muted);">Loading...</div>';

      const results = cat === 'All' ? await getRecommendedFeed() : await fetchVideos('', cat);
      renderGrid(results);
    };
  });

  window.addEventListener('scroll', async () => {
    if ((window.innerHeight + window.scrollY) >= document.body.offsetHeight - 500) {
      if (isFeedLoading) return;

      const feedView = document.getElementById('feed-view');
      const watchView = document.getElementById('watch-view');

      if (feedView && feedView.style.display !== 'none') {
        isFeedLoading = true;
        feedPage++;
        const loader = document.getElementById('feed-loader');
        if (loader) loader.style.display = 'flex';

        const interests = ALGO.getTopInterests();
        const nextTopic = interests[feedPage % (interests.length || 1)] || 'trending';
        const moreVideos = await fetchVideos(currentCategory === 'All' ? nextTopic : currentCategory);
        
        renderGrid(moreVideos, true);
        isFeedLoading = false;
      }
      
      if (watchView && watchView.style.display !== 'none' && currentVideoData) {
        isFeedLoading = true;
        const moreRelated = await fetchVideos(currentVideoData.uploaderName || 'popular');
        renderRelatedListCustom(moreRelated, true);
        isFeedLoading = false;
      }
    }
  });

  const commentsPreviewCard = document.getElementById('comments-preview-card');
  const commentsDrawerBackdrop = document.getElementById('comments-drawer-backdrop');
  const closeCommentsDrawerBtn = document.getElementById('close-comments-drawer-btn');

  if (commentsPreviewCard && commentsDrawerBackdrop) {
    commentsPreviewCard.onclick = () => {
      commentsDrawerBackdrop.style.display = 'flex';
    };
  }

  if (closeCommentsDrawerBtn && commentsDrawerBackdrop) {
    closeCommentsDrawerBtn.onclick = () => {
      commentsDrawerBackdrop.style.display = 'none';
    };
  }

  if (commentsDrawerBackdrop) {
    commentsDrawerBackdrop.onclick = (e) => {
      if (e.target === commentsDrawerBackdrop) {
        commentsDrawerBackdrop.style.display = 'none';
      }
    };
  }

  const menuDotsBtn = document.getElementById('menu-dots-btn');
  const optionsBackdrop = document.getElementById('options-modal-backdrop');
  const closeOptionsBtn = document.getElementById('close-options-btn');

  if (menuDotsBtn && optionsBackdrop) {
    menuDotsBtn.onclick = () => {
      optionsBackdrop.style.display = 'flex';
    };
  }

  if (closeOptionsBtn && optionsBackdrop) {
    closeOptionsBtn.onclick = () => {
      optionsBackdrop.style.display = 'none';
    };
  }

  if (optionsBackdrop) {
    optionsBackdrop.onclick = (e) => {
      if (e.target === optionsBackdrop) {
        optionsBackdrop.style.display = 'none';
      }
    };
  }

  const openDonateBtn = document.getElementById('open-donate-btn');
  const donateBackdrop = document.getElementById('donate-modal-backdrop');
  const closeDonateBtn = document.getElementById('close-donate-btn');
  const copyUpiBtn = document.getElementById('copy-upi-btn');
  const saveQrBtn = document.getElementById('save-qr-btn');
  const donateNowBtn = document.getElementById('donate-now-btn');

  if (openDonateBtn && donateBackdrop && optionsBackdrop) {
    openDonateBtn.onclick = () => {
      optionsBackdrop.style.display = 'none';
      donateBackdrop.style.display = 'flex';
    };
  }

  if (closeDonateBtn && donateBackdrop) {
    closeDonateBtn.onclick = () => {
      donateBackdrop.style.display = 'none';
    };
  }

  if (donateBackdrop) {
    donateBackdrop.onclick = (e) => {
      if (e.target === donateBackdrop) {
        donateBackdrop.style.display = 'none';
      }
    };
  }

  if (copyUpiBtn) {
    copyUpiBtn.onclick = () => {
      const upiEl = document.getElementById('upi-id-text');
      const upi = upiEl ? upiEl.textContent.trim() : 'koustubhgaikwad7-1@okaxis';
      copyTextToClipboard(upi);
      copyUpiBtn.textContent = 'Copied!';
      setTimeout(() => {
        copyUpiBtn.textContent = 'Copy';
      }, 2000);
    };
  }

  if (saveQrBtn) {
    saveQrBtn.onclick = () => {
      const a = document.createElement('a');
      a.href = 'upi_qr.png';
      a.download = 'ytxtube_upi_qr.png';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    };
  }

  if (donateNowBtn) {
    donateNowBtn.onclick = () => {
      window.location.href = 'upi://pay?pa=koustubhgaikwad7-1@okaxis&pn=YTXTube%20Support&cu=INR';
    };
  }

  const shareBtn = document.getElementById('share-btn');
  if (shareBtn) {
    shareBtn.onclick = () => {
      if (!currentVideoId) return;
      const titleEl = document.getElementById('watch-title');
      const title = titleEl ? titleEl.textContent : 'YTXTube Video';
      const url = `https://www.youtube.com/watch?v=${currentVideoId}`;
      
      if (navigator.share) {
        navigator.share({
          title: title,
          text: title,
          url: url
        }).catch(() => {});
      } else {
        copyTextToClipboard(url);
        const span = shareBtn.querySelector('span');
        if (span) {
          span.textContent = 'Copied!';
          setTimeout(() => { span.textContent = 'Share'; }, 2000);
        }
      }
    };
  }

  const likeBtn = document.getElementById('like-btn');
  let liked = false;
  if (likeBtn) {
    likeBtn.onclick = () => {
      liked = !liked;
      likeBtn.style.color = liked ? '#3ea6ff' : 'var(--yt-text)';
    };
  }

  const dislikeBtn = document.getElementById('dislike-btn');
  let disliked = false;
  if (dislikeBtn) {
    dislikeBtn.onclick = () => {
      disliked = !disliked;
      dislikeBtn.style.color = disliked ? '#3ea6ff' : 'var(--yt-text)';
      if (disliked && liked && likeBtn) {
        liked = false;
        likeBtn.style.color = 'var(--yt-text)';
      }
    };
  }

  const subBtn = document.getElementById('subscribe-btn');
  let subscribed = false;
  if (subBtn) {
    subBtn.onclick = () => {
      subscribed = !subscribed;
      subBtn.textContent = subscribed ? 'Subscribed ✓' : 'Subscribe';
      subBtn.style.backgroundColor = subscribed ? 'var(--yt-hover)' : 'var(--yt-text)';
      subBtn.style.color = subscribed ? 'var(--yt-text)' : 'var(--yt-bg)';
    };
  }
});

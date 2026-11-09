const home = document.getElementById('home-screen');
const preview = document.getElementById('preview-screen');
const locationScreen = document.getElementById('location-screen');
const audienceScreen = document.getElementById('audience-screen');
const reviewScreen = document.getElementById('review-screen');
const archiveScreen = document.getElementById('archive-screen');
const pending = document.getElementById('pending-screen');
const message = document.getElementById('message');
const timer = document.getElementById('record-timer');
const timerText = timer.querySelector('span');
const actions = document.getElementById('home-actions');
const recordingActions = document.getElementById('recording-actions');
const audio = document.getElementById('audio-preview');
const title = document.getElementById('story-title');
const seek = document.getElementById('preview-seek');
const seekPath = document.getElementById('seek-progress-path');
const seekHandle = document.getElementById('seek-handle');
const playbackTime = document.getElementById('playback-time');
const playToggle = document.getElementById('play-toggle');
const playIcon = document.getElementById('play-icon');
const reviewSeek = document.getElementById('review-seek');
const reviewSeekPath = document.getElementById('review-seek-progress-path');
const reviewSeekHandle = document.getElementById('review-seek-handle');
const reviewPlaybackTime = document.getElementById('review-playback-time');
const reviewPlayToggle = document.getElementById('review-play-toggle');
const reviewPlayIcon = document.getElementById('review-play-icon');
const reviewAddress = document.getElementById('review-address');
const reviewAudienceIcon = document.getElementById('review-audience-icon');
let recorder = null;
let stream = null;
let chunks = [];
let audioUrl = null;
let startedAt = 0;
let timerId = null;
let discardRecording = false;
let lastRecordingDurationMs = 0;
let pendingUploadKey = null;
let pendingUploadFingerprint = null;
let apiToken = '';
const serverCheck = fetch('./v1/health', { cache: 'no-store' })
  .then(async response => response.ok && (await response.json()).service === 'roadio')
  .catch(() => false);
async function apiRequest(path, options = {}) {
  if (!apiToken) apiToken = window.prompt('Roadio 시연 계정 연결 코드를 입력해 주세요.')?.trim() || '';
  if (!apiToken) throw new Error('서버 계정 연결이 필요해요.');
  const response = await fetch(path, {
    ...options, cache: 'no-store',
    headers: { ...options.headers, Authorization: `Bearer ${apiToken}` }
  });
  if (response.status === 401) {
    apiToken = '';
    throw new Error('연결 코드가 유효하지 않아요. 시연 계정을 다시 확인해 주세요.');
  }
  if (!response.ok) {
    const detail = await response.json().catch(() => ({}));
    throw new Error(detail.error || `서버 오류 (${response.status})`);
  }
  return response;
}
const locationQuery = document.getElementById('location-query');
const locationResults = document.getElementById('location-results');
const locationSearch = document.getElementById('location-search');
const locationSearchStatus = document.getElementById('location-search-status');
const locationConfirm = document.getElementById('location-confirm');
const locationToast = document.getElementById('location-toast');
const audienceOptions = [...document.querySelectorAll('.audience-option')];
const audienceSubmit = document.getElementById('audience-submit');
const audienceLabels = { private: '나에게만 들려주기', neighbors: '내 이웃에게 들려주기', public: '모두에게 들려주기' };
let selectedAudience = 'private';
let naverMap = null;
let radiusCircle = null;
let selectedLocation = null;
let searchTimer = null;
let searchSequence = 0;
let locationToastTimer = null;
let locationInteracted = false;

function show(screen) {
  home.hidden = screen !== home;
  preview.hidden = screen !== preview;
  locationScreen.hidden = screen !== locationScreen;
  audienceScreen.hidden = screen !== audienceScreen;
  reviewScreen.hidden = screen !== reviewScreen;
  archiveScreen.hidden = screen !== archiveScreen;
  pending.hidden = screen !== pending;
  document.querySelector('.quick-menu').hidden = screen !== home;
  if (screen !== locationScreen) setLocationToast('');
}
function setLocationToast(text, duration = 2000) {
  clearTimeout(locationToastTimer);
  locationToastTimer = null;
  locationToast.textContent = text;
  locationToast.hidden = !text;
  if (text) locationToastTimer = setTimeout(() => setLocationToast(''), duration);
}
function locationErrorMessage(error) {
  if (!window.isSecureContext) return '현재 위치는 HTTPS에서 사용할 수 있어요.';
  if (!navigator.geolocation) return '이 브라우저에서 현재 위치를 사용할 수 없어요.';
  if (error?.code === 1) return '위치 접근이 차단됐어요. iPhone 설정에서 Safari의 위치 권한을 허용해 주세요.';
  if (error?.code === 3) return '위치 확인 시간이 초과됐어요. 위치 서비스를 켜고 다시 눌러 주세요.';
  return '현재 위치를 확인할 수 없어요. 위치 서비스를 확인하거나 지도에서 직접 선택해 주세요.';
}
function setSearchStatus(text) {
  locationSearchStatus.textContent = text;
  locationSearchStatus.hidden = !text;
  locationSearch.classList.toggle('has-results', !!text || !locationResults.hidden);
}
function hideSearchResults() {
  locationResults.replaceChildren();
  locationResults.hidden = true;
  locationQuery.setAttribute('aria-expanded', 'false');
  setSearchStatus('');
}
function setSelectedLocation(position) {
  selectedLocation = { latitude: position.lat(), longitude: position.lng(), radiusMeters: 100, address: '' };
  locationConfirm.disabled = false;
  radiusCircle?.setCenter(position);
}
function loadNaverMaps(clientId) {
  return new Promise((resolve, reject) => {
    if (window.naver?.maps?.Map && window.naver?.maps?.Service) { resolve(); return; }
    const script = document.createElement('script');
    script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(clientId)}&submodules=geocoder`;
    script.onload = () => {
      // Geocoder submodule can become available shortly after the core script loads.
      const started = Date.now();
      const waitForService = () => {
        if (window.naver?.maps?.Map && window.naver?.maps?.Service) { resolve(); return; }
        if (Date.now() - started >= 5000) { reject(new Error('지도 검색 모듈을 불러오지 못했습니다.')); return; }
        setTimeout(waitForService, 100);
      };
      waitForService();
    };
    script.onerror = () => reject(new Error('지도 API를 불러오지 못했습니다.'));
    document.head.appendChild(script);
  });
}
async function openLocationScreen() {
  audio.pause();
  show(locationScreen);
  if (naverMap) { naverMap.autoResize(); return; }
  const clientId = window.ROADIO_CONFIG?.naverMapClientId?.trim();
  const fallback = document.getElementById('location-map-fallback');
  if (!clientId) {
    fallback.hidden = false;
    document.getElementById('location-map-fallback-text').textContent = 'NAVER Cloud Maps의 Client ID를 web/config.js에 입력해 주세요.';
    return;
  }
  try {
    await loadNaverMaps(clientId);
    if (locationScreen.hidden) return;
    const initial = new naver.maps.LatLng(37.5665, 126.9780);
    naverMap = new naver.maps.Map('location-map', { center: initial, zoom: 16, scaleControl: false, mapDataControl: false, logoControlOptions: { position: naver.maps.Position.BOTTOM_LEFT } });
    radiusCircle = new naver.maps.Circle({ map: naverMap, center: initial, radius: 100, strokeColor: '#2356c4', strokeOpacity: .7, strokeWeight: 1, fillColor: '#91b2ef', fillOpacity: .25, clickable: false });
    document.getElementById('location-radius-preview').hidden = true;
    naver.maps.Event.addListener(naverMap, 'center_changed', center => radiusCircle.setCenter(center));
    naver.maps.Event.addListener(naverMap, 'dragend', () => {
      locationInteracted = true;
      setSelectedLocation(naverMap.getCenter());
    });
    fallback.hidden = true;
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(position => {
        if (locationScreen.hidden || locationInteracted) return;
        const center = new naver.maps.LatLng(position.coords.latitude, position.coords.longitude);
        naverMap.setCenter(center);
        setSelectedLocation(center);
        setLocationToast('현재 위치로 이동했어요.');
      }, error => {
        if (!locationScreen.hidden && !locationInteracted) setLocationToast(locationErrorMessage(error), 6000);
      }, { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 });
    } else {
      setLocationToast(locationErrorMessage(), 6000);
    }
  } catch (error) {
    fallback.hidden = false;
    document.getElementById('location-map-fallback-text').textContent = '지도 인증 또는 등록된 웹 주소를 확인해 주세요.';
  }
}
function makeHighlightedTitle(text, query) {
  const fragment = document.createDocumentFragment();
  const index = text.toLocaleLowerCase().indexOf(query.toLocaleLowerCase());
  if (index < 0) { fragment.append(document.createTextNode(text)); return fragment; }
  fragment.append(document.createTextNode(text.slice(0, index)));
  const mark = document.createElement('mark');
  mark.textContent = text.slice(index, index + query.length);
  fragment.append(mark, document.createTextNode(text.slice(index + query.length)));
  return fragment;
}
function renderAddressResults(addresses, query) {
  locationResults.replaceChildren();
  addresses.forEach(address => {
    const label = address.roadAddress || address.jibunAddress || address.englishAddress;
    if (!label) return;
    const item = document.createElement('li');
    const button = document.createElement('button');
    const heading = document.createElement('span');
    const detail = document.createElement('small');
    button.type = 'button';
    button.setAttribute('role', 'option');
    heading.className = 'location-result-title';
    heading.append(makeHighlightedTitle(label, query));
    detail.textContent = address.jibunAddress && address.jibunAddress !== label ? address.jibunAddress : '주소';
    button.append(heading, detail);
    button.addEventListener('click', () => {
      const lat = Number(address.y);
      const lng = Number(address.x);
      if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;
      const position = new naver.maps.LatLng(lat, lng);
      locationInteracted = true;
      naverMap.setCenter(position);
      setSelectedLocation(position);
      selectedLocation.address = label;
      locationQuery.value = label;
      hideSearchResults();
      locationQuery.blur();
      setLocationToast('지도 중심의 100m 반경으로 선택됩니다.');
    });
    item.append(button);
    locationResults.append(item);
  });
  locationResults.hidden = !locationResults.childElementCount;
  locationQuery.setAttribute('aria-expanded', String(!locationResults.hidden));
  setSearchStatus(locationResults.hidden ? '주소를 찾지 못했어요. 시·군·구를 포함한 도로명 또는 지번 주소를 입력하거나 지도를 움직여 선택해 주세요. 장소명 검색은 지원되지 않아요.' : '');
  locationSearch.classList.toggle('has-results', !locationResults.hidden);
}
function searchAddress(query) {
  const sequence = ++searchSequence;
  if (!window.naver?.maps?.Service) { setSearchStatus('지도 연결 후 검색할 수 있어요.'); return; }
  setSearchStatus('검색 중…');
  naver.maps.Service.geocode({ query, count: 10 }, (status, response) => {
    if (sequence !== searchSequence || locationScreen.hidden || locationQuery.value.trim() !== query) return;
    if (status !== naver.maps.Service.Status.OK) { setSearchStatus('주소 검색에 실패했어요. 잠시 후 다시 시도해 주세요.'); return; }
    renderAddressResults(response?.v2?.addresses || [], query);
  });
}
locationQuery.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchSequence++;
  const query = locationQuery.value.trim();
  if (!query) { hideSearchResults(); return; }
  if (query.length < 2) { hideSearchResults(); return; }
  locationResults.hidden = true;
  setSearchStatus('검색 중…');
  searchTimer = setTimeout(() => searchAddress(query), 300);
});
locationQuery.addEventListener('keydown', event => {
  if (event.key === 'Escape') { hideSearchResults(); locationQuery.blur(); }
  if (event.key === 'Enter' && !locationResults.hidden) { event.preventDefault(); locationResults.querySelector('button')?.click(); }
});
document.getElementById('location-back').addEventListener('click', () => { clearTimeout(searchTimer); searchSequence++; hideSearchResults(); show(preview); });
document.getElementById('location-current').addEventListener('click', () => {
  if (!naverMap) { setLocationToast('네이버 지도를 먼저 연결해 주세요.'); return; }
  if (!navigator.geolocation) { setLocationToast('이 브라우저에서 현재 위치를 사용할 수 없어요.'); return; }
  navigator.geolocation.getCurrentPosition(position => {
    const center = new naver.maps.LatLng(position.coords.latitude, position.coords.longitude);
    locationInteracted = true;
    naverMap.setCenter(center);
    setSelectedLocation(center);
    setLocationToast('현재 위치로 이동했어요.');
  }, error => setLocationToast(locationErrorMessage(error), 6000), { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 });
});
locationConfirm.addEventListener('click', () => {
  if (!selectedLocation) return;
  show(audienceScreen);
});
function selectAudience(value) {
  if (!Object.hasOwn(audienceLabels, value)) return;
  selectedAudience = value;
  audienceOptions.forEach(option => {
    const selected = option.dataset.audience === value;
    option.classList.toggle('is-selected', selected);
    option.setAttribute('aria-checked', String(selected));
    option.tabIndex = selected ? 0 : -1;
  });
  audienceSubmit.textContent = audienceLabels[value];
}
audienceOptions.forEach((option, index) => {
  option.addEventListener('click', () => selectAudience(option.dataset.audience));
  option.addEventListener('keydown', event => {
    if (!['ArrowDown', 'ArrowRight', 'ArrowUp', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? audienceOptions.length - 1 : (index + (['ArrowDown', 'ArrowRight'].includes(event.key) ? 1 : -1) + audienceOptions.length) % audienceOptions.length;
    selectAudience(audienceOptions[nextIndex].dataset.audience);
    audienceOptions[nextIndex].focus();
  });
});
document.getElementById('audience-back').addEventListener('click', () => {
  show(locationScreen);
  naverMap?.autoResize();
});
function resolveReviewAddress(location) {
  if (location.address) { reviewAddress.textContent = location.address; return; }
  reviewAddress.textContent = '주소를 확인하고 있어요…';
  if (!window.naver?.maps?.Service?.reverseGeocode) {
    reviewAddress.textContent = `선택한 위치 · ${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;
    return;
  }
  const coords = new naver.maps.LatLng(location.latitude, location.longitude);
  const fallback = `선택한 위치 · ${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;
  const timeout = setTimeout(() => { if (selectedLocation === location && !location.address) reviewAddress.textContent = fallback; }, 5000);
  naver.maps.Service.reverseGeocode({ coords }, (status, response) => {
    clearTimeout(timeout);
    if (selectedLocation !== location) return;
    const address = status === naver.maps.Service.Status.OK ? response?.v2?.address : null;
    location.address = address?.roadAddress || address?.jibunAddress || '';
    reviewAddress.textContent = location.address || fallback;
  });
}
function openReviewScreen() {
  document.getElementById('review-error').textContent = '';
  document.getElementById('review-story-title').textContent = title.value.trim();
  reviewAudienceIcon.dataset.audience = selectedAudience;
  reviewAudienceIcon.setAttribute('aria-label', { private: '나만', neighbors: '내 이웃', public: '전체 공개' }[selectedAudience]);
  resolveReviewAddress(selectedLocation);
  updateSeek();
  show(reviewScreen);
}
audienceSubmit.addEventListener('click', () => {
  if (!selectedLocation) { openLocationScreen(); return; }
  openReviewScreen();
});
document.getElementById('review-back').addEventListener('click', () => { audio.pause(); show(audienceScreen); });
document.getElementById('review-submit').addEventListener('click', saveReview);
function setMessage(text) { message.textContent = text; }
function formatTime(seconds) {
  return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`;
}
function releaseStream() {
  stream?.getTracks().forEach(track => track.stop());
  stream = null;
  clearInterval(timerId);
  timerId = null;
}
function clearAudio() {
  audio.pause();
  audio.removeAttribute('src');
  audio.load();
  updateSeek();
  if (audioUrl) URL.revokeObjectURL(audioUrl);
  audioUrl = null;
  pendingUploadKey = null;
  pendingUploadFingerprint = null;
}
async function startRecording() {
  setMessage('');
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    setMessage('녹음하려면 HTTPS로 접속한 뒤 지원되는 브라우저에서 열어 주세요.');
    return;
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    recorder = new MediaRecorder(stream);
    chunks = [];
    discardRecording = false;
    recorder.addEventListener('dataavailable', event => { if (event.data.size) chunks.push(event.data); });
    recorder.addEventListener('stop', () => {
      if (!discardRecording) lastRecordingDurationMs = Math.max(1, Date.now() - startedAt);
      releaseStream();
      if (discardRecording) return;
      if (!chunks.length) { setMessage('녹음 파일을 만들지 못했어요. 다시 시도해 주세요.'); return; }
      clearAudio();
      audioUrl = URL.createObjectURL(new Blob(chunks, { type: recorder.mimeType || 'audio/mp4' }));
      audio.src = audioUrl;
      updateSeek();
      timer.hidden = true;
      show(preview);
    });
    recorder.addEventListener('error', () => {
      releaseStream();
      timer.hidden = true;
      setMessage('녹음 중 오류가 발생했어요. 다시 시도해 주세요.');
    });
    recorder.start();
    startedAt = Date.now();
    timerText.textContent = '00:00';
    timer.hidden = false;
    timerId = setInterval(() => {
      timerText.textContent = formatTime(Math.floor((Date.now() - startedAt) / 1000));
    }, 250);
    document.getElementById('home-title').innerHTML = '이야기를<br>말해주세요';
    actions.hidden = true;
    recordingActions.hidden = false;
  } catch (error) {
    releaseStream();
    setMessage(error?.name === 'NotAllowedError' ? '마이크 권한이 필요해요. 브라우저 설정에서 허용해 주세요.' : '마이크를 사용할 수 없어요. 연결 상태를 확인해 주세요.');
  }
}
function resetHome() {
  document.getElementById('home-title').innerHTML = '어떤 이야기를<br>들려주고 싶으신가요?';
  actions.hidden = false;
  recordingActions.hidden = true;
  timer.hidden = true;
  show(home);
}
function stopRecording() { if (recorder?.state === 'recording') recorder.stop(); }
function cancelRecording() {
  if (recorder?.state === 'recording') {
    discardRecording = true;
    recorder.stop();
  }
  releaseStream();
  chunks = [];
  resetHome();
}
function showPending(heading, description) {
  audio.pause();
  document.getElementById('pending-title').textContent = heading;
  document.getElementById('pending-description').textContent = description;
  show(pending);
}
function updateSeek() {
  const duration = !audio.hasAttribute('src') ? 0 :
    Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : lastRecordingDurationMs / 1000;
  const current = duration ? Math.min(audio.currentTime || 0, duration) : 0;
  const currentLabel = formatTime(Math.floor(current));
  const durationLabel = formatTime(Math.floor(duration));
  const fraction = duration ? current / duration : 0;
  const angle = Math.PI * (1 - fraction);
  [[seek, seekPath, seekHandle, playbackTime], [reviewSeek, reviewSeekPath, reviewSeekHandle, reviewPlaybackTime]].forEach(([slider, path, handle, time]) => {
    path.style.strokeDasharray = `${fraction * 100} 100`;
    handle.style.left = `${99.623 + 87.3824 * Math.cos(angle)}px`;
    handle.style.top = `${6 + 87.3824 * Math.sin(angle)}px`;
    time.textContent = `${currentLabel}/${durationLabel}`;
    slider.setAttribute('aria-valuemax', String(Math.floor(duration)));
    slider.setAttribute('aria-valuenow', String(Math.floor(current)));
    slider.setAttribute('aria-valuetext', `현재 ${currentLabel}, 전체 ${durationLabel}`);
  });
}
function updatePlayButton() {
  const playing = !audio.paused && !audio.ended;
  playIcon.src = playing ? './assets/pause.svg' : './assets/play.svg';
  reviewPlayIcon.src = playing ? './assets/pause.svg' : './assets/review-play.svg';
  playToggle.setAttribute('aria-label', playing ? '일시정지' : '재생');
  reviewPlayToggle.setAttribute('aria-label', playing ? '일시정지' : '재생');
}
function seekTo(slider, clientX, clientY) {
  if (!Number.isFinite(audio.duration) || audio.duration <= 0) return;
  const rect = slider.getBoundingClientRect();
  const x = clientX - rect.left - 99.623;
  const y = clientY - rect.top - 6;
  let angle = Math.atan2(Math.max(0, y), x);
  angle = Math.max(0, Math.min(Math.PI, angle));
  audio.currentTime = audio.duration * (1 - angle / Math.PI);
  updateSeek();
}
audio.addEventListener('loadedmetadata', updateSeek);
audio.addEventListener('durationchange', updateSeek);
audio.addEventListener('timeupdate', updateSeek);
audio.addEventListener('play', updatePlayButton);
audio.addEventListener('pause', updatePlayButton);
audio.addEventListener('ended', updatePlayButton);
async function togglePlayback() {
  if (!audio.src) return;
  if (audio.paused) {
    try { await audio.play(); } catch { setMessage('녹음 파일을 재생하지 못했어요.'); }
  } else audio.pause();
}
playToggle.addEventListener('click', togglePlayback);
reviewPlayToggle.addEventListener('click', togglePlayback);
function handleSeekKeydown(event) {
  if (!Number.isFinite(audio.duration) || audio.duration <= 0) return;
  if (event.key === 'ArrowRight' || event.key === 'ArrowUp') audio.currentTime = Math.min(audio.duration, audio.currentTime + 5);
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowDown') audio.currentTime = Math.max(0, audio.currentTime - 5);
  else if (event.key === 'Home') audio.currentTime = 0;
  else if (event.key === 'End') audio.currentTime = audio.duration;
  else return;
  event.preventDefault();
  updateSeek();
}
[seek, reviewSeek].forEach(slider => {
  slider.addEventListener('pointerdown', event => {
    slider.setPointerCapture(event.pointerId);
    seekTo(slider, event.clientX, event.clientY);
  });
  slider.addEventListener('pointermove', event => {
    if (slider.hasPointerCapture(event.pointerId)) seekTo(slider, event.clientX, event.clientY);
  });
  slider.addEventListener('pointerup', event => {
    if (slider.hasPointerCapture(event.pointerId)) slider.releasePointerCapture(event.pointerId);
  });
  slider.addEventListener('keydown', handleSeekKeydown);
});
function updateTitleState() {
  const editing = document.activeElement === title;
  title.classList.toggle('is-complete', !!title.value.trim() && !editing);
  title.placeholder = editing ? '' : '이야기에 이름을 붙여주세요';
  if (title.value) {
    const context = document.createElement('canvas').getContext('2d');
    context.font = `${editing ? 500 : 600} 16px "Asta Sans"`;
    title.style.width = `${Math.max(60, Math.min(280, Math.ceil(context.measureText(title.value).width + 16)))}px`;
  } else title.style.width = editing ? '60px' : '174px';
}
title.addEventListener('focus', updateTitleState);
title.addEventListener('blur', updateTitleState);
title.addEventListener('input', updateTitleState);
document.getElementById('record-button').addEventListener('click', startRecording);
document.getElementById('stop-button').addEventListener('click', stopRecording);
document.getElementById('cancel-button').addEventListener('click', cancelRecording);
document.getElementById('rerecord-button').addEventListener('click', () => { clearAudio(); resetHome(); startRecording(); });
document.getElementById('next-button').addEventListener('click', () => {
  if (!title.value.trim()) { title.focus(); title.setCustomValidity('제목을 입력해 주세요.'); title.reportValidity(); return; }
  title.setCustomValidity('');
  openLocationScreen();
});
title.addEventListener('input', () => title.setCustomValidity(''));
document.getElementById('my-capsules').addEventListener('click', () => { if (recorder?.state === 'recording') cancelRecording(); openArchiveScreen(); });
document.getElementById('capsule-map').addEventListener('click', () => { if (recorder?.state === 'recording') cancelRecording(); showPending('이야기 지도 준비 중', '이 지도는 이야기의 위치만 보여줄 예정입니다. 지도에서 음성은 재생하지 않습니다.'); });
document.getElementById('back-button').addEventListener('click', () => { clearAudio(); resetHome(); });

// 06a browser-only archive. Server publishing and cross-device visibility belong to DEV-09/10.
const archiveList = document.getElementById('archive-list');
const archiveEmpty = document.getElementById('archive-empty');
const archiveStatus = document.getElementById('archive-status');
const archiveOverlay = document.getElementById('archive-overlay');
const archiveAudienceDialog = document.getElementById('archive-audience-dialog');
const archiveDeleteDialog = document.getElementById('archive-delete-dialog');
const archiveAudio = new Audio();
let archiveAudioUrl = null;
let archivePlayingId = null;
let archiveItems = [];
let activeArchiveId = null;
let draftArchiveAudience = null;
let archiveReturnFocus = null;
let archiveStatusTimer = null;
let archiveDatabasePromise = null;
const archiveAudienceMeta = {
  private: { name: '나만', detail: '나만 들을 수 있어요.', icon: './assets/archive-lock.svg' },
  neighbors: { name: '내 이웃', detail: '초대한 이웃만 들을 수 있어요.', icon: './assets/archive-neighbors.svg' },
  public: { name: '전체 공개', detail: '누구나 들을 수 있어요.', icon: './assets/archive-globe.svg' }
};
function openArchiveDatabase() {
  if (!window.indexedDB) return Promise.reject(new Error('이 브라우저는 로컬 저장을 지원하지 않아요.'));
  if (!archiveDatabasePromise) archiveDatabasePromise = new Promise((resolve, reject) => {
    const request = indexedDB.open('roadio-local-archive', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('capsules', { keyPath: 'id' });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('로컬 저장소를 열지 못했어요.'));
    request.onblocked = () => reject(new Error('다른 탭에서 저장소를 사용 중이에요.'));
  }).catch(error => { archiveDatabasePromise = null; throw error; });
  return archiveDatabasePromise;
}
async function archiveRequest(mode, method, value) {
  const db = await openArchiveDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('capsules', mode);
    const request = transaction.objectStore('capsules')[method](value);
    let result;
    request.onsuccess = () => { result = request.result; };
    request.onerror = () => reject(request.error || new Error('저장소 작업에 실패했어요.'));
    transaction.onabort = () => reject(transaction.error || new Error('저장소 작업이 중단됐어요.'));
    transaction.oncomplete = () => resolve(result);
  });
}
function announceArchive(text) {
  clearTimeout(archiveStatusTimer);
  archiveStatus.textContent = text;
  archiveStatus.hidden = !text;
  if (text) archiveStatusTimer = setTimeout(() => { archiveStatus.hidden = true; }, 4500);
}
function archiveRemainingLabel(expiresAt) {
  const remaining = expiresAt - Date.now();
  if (remaining <= 0) return null;
  if (remaining < 86400000) return { text: remaining <= 12 * 3600000 ? `${Math.ceil(remaining / 3600000)}h` : 'D-1', urgent: true };
  return { text: `D-${Math.ceil(remaining / 86400000)}`, urgent: false };
}
function stopArchiveAudio() {
  archiveAudio.pause();
  archiveAudio.removeAttribute('src');
  archiveAudio.load();
  if (archiveAudioUrl) URL.revokeObjectURL(archiveAudioUrl);
  archiveAudioUrl = null;
  archivePlayingId = null;
}
async function toggleArchiveAudio(item) {
  if (archivePlayingId === item.id && !archiveAudio.paused) { archiveAudio.pause(); renderArchiveList(); return; }
  stopArchiveAudio();
  try {
    const blob = item.server ? await (await apiRequest(`/v1/capsules/${item.id}/audio`)).blob() : item.audio;
    archiveAudioUrl = URL.createObjectURL(blob);
  } catch (error) { announceArchive(error.message || '녹음 파일을 불러오지 못했어요.'); return; }
  archiveAudio.src = archiveAudioUrl;
  archivePlayingId = item.id;
  try { await archiveAudio.play(); } catch { stopArchiveAudio(); announceArchive('녹음 파일을 재생하지 못했어요.'); }
  renderArchiveList();
}
archiveAudio.addEventListener('ended', () => { stopArchiveAudio(); renderArchiveList(); });
archiveAudio.addEventListener('pause', () => { if (!archiveScreen.hidden) renderArchiveList(); });
function closeArchivePopover() { archiveList.querySelectorAll('.archive-popover').forEach(menu => menu.hidden = true); }
function makeArchiveRow(item) {
  const badge = archiveRemainingLabel(item.expiresAt);
  const row = document.createElement('li');
  row.className = 'archive-item';
  const info = document.createElement('div'); info.className = 'archive-info';
  const icon = document.createElement('span'); icon.className = 'archive-icon';
  const iconImage = document.createElement('img'); iconImage.src = archiveAudienceMeta[item.audience]?.icon || archiveAudienceMeta.private.icon; iconImage.alt = archiveAudienceMeta[item.audience]?.name || '나만'; icon.append(iconImage);
  const nameBadge = document.createElement('div'); nameBadge.className = 'archive-name-badge';
  const name = document.createElement('span'); name.className = 'archive-name'; name.textContent = item.title;
  const timeBadge = document.createElement('span'); timeBadge.className = `archive-badge${badge?.urgent ? ' is-urgent' : ''}`; timeBadge.textContent = badge?.text || '';
  nameBadge.append(name, timeBadge); info.append(icon, nameBadge);
  const actions = document.createElement('div'); actions.className = 'archive-item-actions';
  const play = document.createElement('button'); play.type = 'button'; play.className = 'archive-play';
  const isPlaying = archivePlayingId === item.id && !archiveAudio.paused;
  play.setAttribute('aria-label', `${item.title} ${isPlaying ? '일시정지' : '재생'}`);
  const playIcon = document.createElement('img'); playIcon.src = isPlaying ? './assets/pause.svg' : './assets/archive-play.svg'; playIcon.alt = ''; play.append(playIcon);
  play.addEventListener('click', () => toggleArchiveAudio(item));
  const more = document.createElement('button'); more.type = 'button'; more.className = 'archive-more'; more.setAttribute('aria-label', `${item.title} 더보기`); more.setAttribute('aria-expanded', 'false');
  const moreIcon = document.createElement('img'); moreIcon.src = './assets/archive-more.svg'; moreIcon.alt = ''; more.append(moreIcon);
  const popover = document.createElement('div'); popover.className = 'archive-popover'; popover.hidden = true;
  const change = document.createElement('button'); change.type = 'button'; change.textContent = '공개 범위 변경'; change.addEventListener('click', () => openArchiveDialog('audience', item, more));
  const remove = document.createElement('button'); remove.type = 'button'; remove.textContent = '삭제하기'; remove.addEventListener('click', () => openArchiveDialog('delete', item, more));
  popover.append(change, remove);
  more.addEventListener('click', event => { event.stopPropagation(); const wasHidden = popover.hidden; closeArchivePopover(); popover.hidden = !wasHidden; more.setAttribute('aria-expanded', String(wasHidden)); });
  actions.append(play, more); row.append(info, actions, popover);
  return row;
}
function renderArchiveList() {
  const visible = archiveItems.filter(item => archiveRemainingLabel(item.expiresAt));
  archiveList.replaceChildren(...visible.map(makeArchiveRow));
  archiveEmpty.hidden = visible.length > 0;
}
async function openArchiveScreen() {
  audio.pause();
  stopArchiveAudio();
  show(archiveScreen);
  try {
    if (await serverCheck) {
      const response = await apiRequest('/v1/capsules');
      archiveItems = (await response.json()).filter(item => item.state === 'ACTIVE').map(item => ({
        id: item.id, title: item.title,
        audience: { SELF: 'private', SELECTED: 'neighbors', PUBLIC: 'public' }[item.visibility],
        createdAt: Date.parse(item.createdAt), expiresAt: Date.parse(item.expiresAt), server: true
      }));
      renderArchiveList();
      return true;
    }
    archiveItems = (await archiveRequest('readonly', 'getAll')).sort((a, b) => b.createdAt - a.createdAt);
    const expired = archiveItems.filter(item => item.expiresAt <= Date.now());
    archiveItems = archiveItems.filter(item => item.expiresAt > Date.now());
    renderArchiveList();
    await Promise.all(expired.map(item => archiveRequest('readwrite', 'delete', item.id)));
    return true;
  } catch (error) { archiveItems = []; renderArchiveList(); announceArchive(error.message || '목록을 불러오지 못했어요.'); return false; }
}
async function serverRecipients() {
  const response = await apiRequest('/v1/me/recipients');
  return (await response.json()).map(item => item.id);
}
async function saveReview() {
  if (!(await serverCheck)) return saveReviewLocally();
  const submit = document.getElementById('review-submit');
  if (submit.disabled) return;
  if (!chunks.length || !selectedLocation || !title.value.trim()) {
    document.getElementById('review-error').textContent = '녹음과 위치, 제목을 다시 확인해 주세요.';
    return;
  }
  submit.disabled = true;
  try {
    const recipients = selectedAudience === 'neighbors' ? await serverRecipients() : [];
    if (selectedAudience === 'neighbors' && !recipients.length) throw new Error('등록된 이웃이 없어요. 테스트 이웃을 먼저 연결해 주세요.');
    const metadata = {
      title: title.value.trim(), position: { latitude: selectedLocation.latitude, longitude: selectedLocation.longitude },
      visibility: { private: 'SELF', neighbors: 'SELECTED', public: 'PUBLIC' }[selectedAudience],
      selectedRecipientUserIds: recipients, contentOrigin: 'MOBILE_RECORDING',
      durationMs: Math.max(1, Math.min(600000, Math.round(lastRecordingDurationMs || audio.duration * 1000 || 1)))
    };
    const fingerprint = JSON.stringify(metadata);
    if (pendingUploadFingerprint !== fingerprint) {
      pendingUploadKey = crypto.randomUUID();
      pendingUploadFingerprint = fingerprint;
    }
    const body = new FormData();
    body.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
    body.append('audio', new Blob(chunks, { type: recorder?.mimeType || 'audio/mp4' }), 'recording');
    await apiRequest('/v1/capsules', { method: 'POST', body, headers: { 'Idempotency-Key': pendingUploadKey } });
    clearAudio(); title.value = ''; updateTitleState(); selectedLocation = null; selectAudience('private');
    if (await openArchiveScreen()) announceArchive('서버에 이야기를 등록했어요.');
  } catch (error) {
    const detail = error instanceof TypeError ? '서버에 연결하지 못했어요.' : (error.message || '등록하지 못했어요.');
    document.getElementById('review-error').textContent = `${detail} 다시 눌러 재시도할 수 있어요.`;
  } finally { submit.disabled = false; }
}
async function saveReviewLocally() {
  const submit = document.getElementById('review-submit');
  if (submit.disabled) return;
  if (!chunks.length || !selectedLocation || !title.value.trim()) { showPending('저장할 이야기가 없어요', '녹음과 위치, 제목을 다시 확인해 주세요.'); return; }
  submit.disabled = true;
  const createdAt = Date.now();
  const item = {
    id: crypto.randomUUID?.() || `${createdAt}-${Math.random()}`,
    title: title.value.trim(), audience: selectedAudience,
    location: { ...selectedLocation }, createdAt, expiresAt: createdAt + 72 * 60 * 60 * 1000,
    audio: new Blob(chunks, { type: recorder?.mimeType || 'audio/mp4' })
  };
  try {
    await archiveRequest('readwrite', 'put', item);
    clearAudio(); title.value = ''; updateTitleState(); selectedLocation = null; selectAudience('private');
    await openArchiveScreen();
    announceArchive('이 기기에만 저장했어요. 다른 기기에는 게시되지 않았습니다.');
  } catch { document.getElementById('review-error').textContent = '이 기기에 저장하지 못했어요. 저장 공간을 확인해 주세요.'; }
  finally { submit.disabled = false; }
}
function selectArchiveAudience(value) {
  if (!archiveAudienceMeta[value]) return;
  draftArchiveAudience = value;
  document.querySelectorAll('.archive-audience-choice').forEach(choice => {
    const selected = choice.dataset.audience === value;
    choice.classList.toggle('is-selected', selected);
    choice.setAttribute('aria-checked', String(selected));
    choice.tabIndex = selected ? 0 : -1;
  });
}
function openArchiveDialog(type, item, returnFocus) {
  closeArchivePopover();
  activeArchiveId = item.id;
  archiveReturnFocus = returnFocus;
  archiveAudienceDialog.hidden = type !== 'audience';
  archiveDeleteDialog.hidden = type !== 'delete';
  if (type === 'audience') {
    archiveAudienceDialog.querySelector('h2').textContent = item.title;
    const options = document.getElementById('archive-audience-options');
    options.replaceChildren(...Object.entries(archiveAudienceMeta).map(([value, meta]) => {
      const choice = document.createElement('button'); choice.type = 'button'; choice.className = 'archive-audience-choice'; choice.dataset.audience = value; choice.setAttribute('role', 'radio');
      const iconWrap = document.createElement('span'); iconWrap.className = 'archive-dialog-icon';
      const icon = document.createElement('img'); icon.src = { private: './assets/archive-dialog-lock.svg', neighbors: './assets/archive-dialog-neighbors.svg', public: './assets/archive-dialog-globe.svg' }[value]; icon.alt = ''; iconWrap.append(icon);
      const copy = document.createElement('span'); copy.className = 'archive-audience-copy';
      const heading = document.createElement('strong'); heading.textContent = meta.name;
      const detail = document.createElement('small'); detail.textContent = meta.detail;
      const check = document.createElement('img'); check.className = 'archive-choice-check'; check.src = './assets/archive-check.svg'; check.alt = '';
      copy.append(heading, detail); choice.append(iconWrap, copy, check);
      choice.addEventListener('click', () => selectArchiveAudience(value));
      return choice;
    }));
    selectArchiveAudience(item.audience);
  } else document.getElementById('archive-delete-title').textContent = item.title;
  archiveOverlay.hidden = false;
  (type === 'audience' ? archiveAudienceDialog : archiveDeleteDialog).querySelector('.archive-cancel').focus();
}
function closeArchiveDialog() {
  archiveOverlay.hidden = true;
  archiveAudienceDialog.hidden = true;
  archiveDeleteDialog.hidden = true;
  activeArchiveId = null;
  archiveReturnFocus?.focus();
  archiveReturnFocus = null;
}
document.querySelectorAll('.archive-cancel').forEach(button => button.addEventListener('click', closeArchiveDialog));
archiveOverlay.addEventListener('click', event => { if (event.target === archiveOverlay) closeArchiveDialog(); });
document.addEventListener('keydown', event => {
  if (event.key === 'Escape') { if (!archiveOverlay.hidden) closeArchiveDialog(); else closeArchivePopover(); }
  if (event.key !== 'Tab' || archiveOverlay.hidden) return;
  const dialog = archiveAudienceDialog.hidden ? archiveDeleteDialog : archiveAudienceDialog;
  const focusable = [...dialog.querySelectorAll('button:not([hidden])')];
  const first = focusable[0]; const last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
});
document.addEventListener('click', event => { if (!event.target.closest('.archive-more,.archive-popover')) closeArchivePopover(); });
document.getElementById('archive-audience-confirm').addEventListener('click', async () => {
  const item = archiveItems.find(entry => entry.id === activeArchiveId);
  if (!item) return;
  const updated = { ...item, audience: draftArchiveAudience };
  if (item.server) {
    try {
      const recipients = draftArchiveAudience === 'neighbors' ? await serverRecipients() : [];
      if (draftArchiveAudience === 'neighbors' && !recipients.length) throw new Error('등록된 이웃이 없어요.');
      await apiRequest(`/v1/capsules/${item.id}`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ visibility: { private: 'SELF', neighbors: 'SELECTED', public: 'PUBLIC' }[draftArchiveAudience],
                               selectedRecipientUserIds: recipients })
      });
      archiveItems = archiveItems.map(entry => entry.id === updated.id ? updated : entry);
      closeArchiveDialog(); renderArchiveList(); announceArchive('서버의 공개 범위를 변경했어요.');
    } catch (error) { announceArchive(error.message || '공개 범위를 변경하지 못했어요.'); }
    return;
  }
  try { await archiveRequest('readwrite', 'put', updated); archiveItems = archiveItems.map(entry => entry.id === updated.id ? updated : entry); closeArchiveDialog(); renderArchiveList(); announceArchive('이 기기의 공개 범위 표시를 변경했어요.'); }
  catch { announceArchive('공개 범위를 저장하지 못했어요.'); }
});
document.getElementById('archive-delete-confirm').addEventListener('click', async () => {
  const id = activeArchiveId;
  if (!id) return;
  if (archiveItems.find(item => item.id === id)?.server) {
    try {
      await apiRequest(`/v1/capsules/${id}`, { method: 'DELETE' });
      if (archivePlayingId === id) stopArchiveAudio();
      archiveItems = archiveItems.filter(item => item.id !== id);
      closeArchiveDialog(); renderArchiveList(); announceArchive('서버에서 이야기를 삭제했어요.');
    } catch (error) { announceArchive(error.message || '삭제하지 못했어요.'); }
    return;
  }
  try { await archiveRequest('readwrite', 'delete', id); if (archivePlayingId === id) stopArchiveAudio(); archiveItems = archiveItems.filter(item => item.id !== id); closeArchiveDialog(); renderArchiveList(); announceArchive('이 기기에서 이야기를 삭제했어요.'); }
  catch { announceArchive('이야기를 삭제하지 못했어요.'); }
});
document.getElementById('archive-back').addEventListener('click', () => { stopArchiveAudio(); resetHome(); });
window.addEventListener('pagehide', releaseStream);

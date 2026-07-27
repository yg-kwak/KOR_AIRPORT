/* 정규인원 얼굴 등록 — 웹캠(사진 촬영). 클라이언트 PC 카메라를 getUserMedia 로 열어 실시간 미리보기하고,
   촬영 시 현재 프레임을 캡처해 onCapture(원본 base64) 로 넘긴다. BiostarX 연동은 호출측(useFaceImage)이 수행. */
(function () {
  const $ = (id) => document.getElementById(id);
  let stream = null;
  let onCaptureCb = null;

  async function open(onCapture) {
    onCaptureCb = onCapture;
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      const insecure = !window.isSecureContext; // http+IP 접속이면 카메라 API 자체가 없음
      toast.error(insecure
        ? '카메라는 보안 접속에서만 됩니다. localhost 또는 https 로 접속하세요. (장치에서 촬영은 사용 가능)'
        : '이 브라우저에서는 카메라를 사용할 수 없습니다.');
      return;
    }
    $('faceCamModal').classList.add('open');
    $('faceCamShot').disabled = true;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        video: { width: { ideal: 640 }, height: { ideal: 480 } }, audio: false });
      const video = $('faceCamVideo');
      video.srcObject = stream;
      await video.play();
      $('faceCamShot').disabled = false;
    } catch (e) {
      toast.error('카메라를 열 수 없습니다. 브라우저 카메라 권한을 확인하세요.');
      close();
    }
  }

  function stop() {
    if (stream) { stream.getTracks().forEach((t) => t.stop()); stream = null; }
    const video = $('faceCamVideo');
    if (video) video.srcObject = null;
  }
  function close() { stop(); $('faceCamModal').classList.remove('open'); }

  async function shot() {
    const video = $('faceCamVideo');
    if (!video || !video.videoWidth) return;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d').drawImage(video, 0, 0);
    const b64 = canvas.toDataURL('image/jpeg', 0.92).split(',')[1]; // 촬영 원본
    close();
    if (onCaptureCb) await onCaptureCb(b64); // BiostarX 연동 성공 시 얼굴 표시(호출측)
  }

  document.addEventListener('DOMContentLoaded', () => {
    if (!$('faceCamModal')) return;
    $('faceCamShot').addEventListener('click', shot);
    $('faceCamCancel').addEventListener('click', close);
    $('faceCamClose').addEventListener('click', close);
  });

  window.faceCam = { open };
})();

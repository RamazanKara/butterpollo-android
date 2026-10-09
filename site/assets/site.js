const video = document.querySelector('.demo video');

if (video) {
  const portrait = matchMedia('(max-width: 600px)');
  const landscapePoster = video.poster;
  const updatePoster = () => {
    video.poster = portrait.matches ? video.dataset.portraitPoster : landscapePoster;
  };
  updatePoster();
  portrait.addEventListener('change', updatePoster);

  async function prepareVideo() {
    const sources = [...video.querySelectorAll('source')];
    const preferred = sources.filter(source => !source.media || matchMedia(source.media).matches);
    // Missing footage leaves the finished poster in place.
    for (const source of preferred) {
      try {
        const response = await fetch(source.dataset.src, { method: 'HEAD' });
        if (!response.ok || !response.headers.get('content-type')?.startsWith('video/')) continue;
        source.src = source.dataset.src;
        video.controls = true;
        const restorePoster = () => {
          video.controls = false;
          source.removeAttribute('src');
          video.load();
        };
        source.addEventListener('error', restorePoster, { once: true });
        video.addEventListener('error', restorePoster, { once: true });
        video.load();
        return;
      } catch {
        // Local-file and offline previews retain the poster.
      }
    }
  }

  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        observer.disconnect();
        prepareVideo();
      }
    }, { rootMargin: '200px' });
    observer.observe(video);
  }
}

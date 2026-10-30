// The phone in the hero dictates a sentence into a chat, over and over.
(function () {
  const phone = document.querySelector(".phone");
  if (!phone) return;
  const field = phone.querySelector(".field .text");
  const placeholder = phone.querySelector(".field .placeholder");
  const bars = [...phone.querySelectorAll(".pill .wave i")];
  const words = phone.dataset.phrase.split(" ");
  const still = matchMedia("(prefers-reduced-motion: reduce)").matches;

  if (still) {
    phone.classList.add("recording");
    placeholder.hidden = true;
    field.textContent = words.join(" ");
    return;
  }

  const wait = (ms) => new Promise((r) => setTimeout(r, ms));
  let waving = null;

  function wave(on) {
    clearInterval(waving);
    if (!on) return bars.forEach((b) => (b.style.height = "3px"));
    waving = setInterval(() => {
      bars.forEach((b) => (b.style.height = 3 + Math.round(Math.random() * 11) + "px"));
    }, 110);
  }

  async function loop() {
    for (;;) {
      field.textContent = "";
      placeholder.hidden = false;
      await wait(1600);
      phone.classList.add("recording");
      wave(true);
      await wait(900);
      placeholder.hidden = true;
      // Words arrive in bursts, like the live preview does.
      for (let i = 0; i < words.length; ) {
        const burst = 1 + Math.floor(Math.random() * 3);
        i = Math.min(words.length, i + burst);
        field.textContent = words.slice(0, i).join(" ");
        await wait(260 + Math.random() * 420);
      }
      await wait(700);
      phone.classList.remove("recording");
      wave(false);
      await wait(3200);
    }
  }
  loop();
})();

// Six months of a made-up dictation habit for the statistics heatmap.
(function () {
  const map = document.querySelector(".heatmap");
  if (!map) return;
  let seed = 7;
  const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  const cells = [];
  for (let d = 0; d < 26 * 7; d++) {
    const weekend = d % 7 >= 5;
    const warm = d / 182;
    const r = rand() + warm * 0.35 - (weekend ? 0.3 : 0);
    const level = r > 0.95 ? 3 : r > 0.72 ? 2 : r > 0.45 ? 1 : 0;
    cells.push(`<i data-l="${level}"></i>`);
  }
  map.innerHTML = cells.join("");
})();

// Point the download buttons at the APK of the latest release.
(function () {
  const links = document.querySelectorAll("[data-apk]");
  if (!links.length) return;
  fetch("https://api.github.com/repos/jakub-m-arch/voicemator/releases/latest")
    .then((r) => (r.ok ? r.json() : Promise.reject()))
    .then((release) => {
      const apk = release.assets.find((a) => a.name.endsWith(".apk"));
      links.forEach((a) => {
        if (apk) a.href = apk.browser_download_url;
        const v = a.querySelector("[data-version]");
        if (v) v.textContent = release.tag_name;
      });
    })
    .catch(() => {});
})();

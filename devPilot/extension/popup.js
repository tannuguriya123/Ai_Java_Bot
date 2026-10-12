const scanButton = document.querySelector("#scan");
const downloadButton = document.querySelector("#download");
const results = document.querySelector("#results");
const status = document.querySelector("#status");
let detectedFiles = [];

function setStatus(message) {
  status.textContent = message;
}

function scanGuPage() {
  const found = new Map();

  for (const anchor of document.querySelectorAll("a[href]")) {
    if (anchor.getClientRects().length === 0) continue;

    let url;
    try {
      url = new URL(anchor.href, location.href);
    } catch {
      continue;
    }
    if (url.origin !== location.origin) continue;

    const label = (
      anchor.innerText ||
      anchor.getAttribute("aria-label") ||
      anchor.getAttribute("title") ||
      ""
    )
      .replace(/\s+/g, " ")
      .trim();
    let pathName = url.pathname.split("/").pop() || "";
    try {
      pathName = decodeURIComponent(pathName);
    } catch {
      // Keep the encoded name if a URL contains malformed escapes.
    }

    const downloadName = anchor.getAttribute("download") || pathName;
    const candidate = `${downloadName} ${label} ${anchor.getAttribute("title") || ""}`;
    const extensionMatch = candidate.match(/[^/\\\s]+\.(?:pdf|pptx?)(?=$|[\s?#])/i);
    const moodleResource = /\/mod\/resource\/view\.php$/i.test(url.pathname);
    if (!extensionMatch && !moodleResource) continue;
    if (found.has(url.href)) continue;

    found.set(url.href, {
      url: url.href,
      name: extensionMatch?.[0] || "",
      label: label || downloadName || "Moodle course resource",
    });
  }

  return {
    files: [...found.values()].slice(0, 200),
    pageTitle: document.title,
    pageUrl: location.href,
  };
}

function renderFiles(files) {
  detectedFiles = files;
  results.replaceChildren();
  for (const [index, file] of files.entries()) {
    const label = document.createElement("label");
    label.className = "file";
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.value = String(index);
    checkbox.checked = true;
    const text = document.createElement("span");
    text.textContent = file.label;
    label.append(checkbox, text);
    results.append(label);
  }
  downloadButton.disabled = files.length === 0;
}

scanButton.addEventListener("click", async () => {
  results.replaceChildren();
  downloadButton.disabled = true;
  scanButton.disabled = true;
  try {
    if (typeof chrome.tabs?.query !== "function") {
      throw new Error("The tabs API is unavailable. Reload the extension after granting its tabs permission.");
    }
    if (typeof chrome.scripting?.executeScript !== "function") {
      throw new Error("The scripting API is unavailable. Reload the extension after granting its scripting permission.");
    }
    const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
    if (!tab?.id || !tab.url?.startsWith("https://gulms.galgotiasuniversity.org/")) {
      setStatus("Open a GU LMS page in the active tab first.");
      return;
    }
    const [execution] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: scanGuPage,
    });
    const response = execution?.result;
    if (!response) {
      throw new Error("Chrome did not return a scan result.");
    }
    renderFiles(response?.files ?? []);
    setStatus(
      detectedFiles.length
        ? `Found ${detectedFiles.length} possible material link(s) on “${response.pageTitle}”.`
        : "No visible PDF, PowerPoint, or Moodle resource links found. Open a course page or a resource/folder containing materials."
    );
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    setStatus(
      `Chrome could not scan this tab: ${detail}. Check the extension's site access for gulms.galgotiasuniversity.org, then reload the LMS tab and retry.`
    );
  } finally {
    scanButton.disabled = false;
  }
});

downloadButton.addEventListener("click", async () => {
  const selected = [...results.querySelectorAll("input:checked")]
    .map((checkbox) => detectedFiles[Number(checkbox.value)])
    .filter(Boolean);
  if (!selected.length) {
    setStatus("Select at least one file.");
    return;
  }

  downloadButton.disabled = true;
  let started = 0;
  const failures = [];
  for (const file of selected) {
    try {
      const options = {
        url: file.url,
        conflictAction: "uniquify",
        saveAs: false,
      };
      if (file.name) {
        const name = file.name.replace(/[<>:"/\\|?*\u0000-\u001f]/g, "_").slice(-160);
        options.filename = `DevPilot course files/${name || "course-material"}`;
      }
      await chrome.downloads.download({
        ...options,
      });
      started++;
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error);
      failures.push(`${file.label}: ${detail}`);
    }
  }
  if (failures.length === 0) {
    setStatus(`Started ${started} download(s). Check Chrome's downloads, then upload them in DevPilot → My courses.`);
  } else {
    setStatus(
      `Started ${started} of ${selected.length} downloads. Failed: ${failures.join("; ")}. Check Chrome's downloads and extension site access.`
    );
  }
  downloadButton.disabled = false;
});

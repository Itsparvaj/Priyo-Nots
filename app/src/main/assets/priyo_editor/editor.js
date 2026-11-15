(function () {
  "use strict";

  const surface = document.getElementById("editor-surface");
  let isEditable = true;
  let suppressNotify = false;
  let notifyTimer = null;

  const ALLOWED_TAGS = new Set([
    "P", "BR", "DIV", "SPAN", "B", "STRONG", "I", "EM", "U", "S", "STRIKE", "DEL",
    "H1", "H2", "H3", "UL", "OL", "LI", "BLOCKQUOTE", "PRE", "CODE", "MARK", "HR",
    "INPUT", "LABEL", "A"
  ]);

  function sanitizeHtml(rawHtml) {
    if (!rawHtml) return "";
    const parser = new DOMParser();
    const doc = parser.parseFromString("<div>" + rawHtml + "</div>", "text/html");
    const root = doc.body.firstElementChild;
    if (!root) return "";

    function cleanNode(node) {
      const children = Array.from(node.childNodes);
      for (const child of children) {
        if (child.nodeType === Node.ELEMENT_NODE) {
          const tag = child.tagName.toUpperCase();
          if (!ALLOWED_TAGS.has(tag)) {
            if (tag === "SCRIPT" || tag === "STYLE" || tag === "IFRAME" || tag === "OBJECT" || tag === "EMBED") {
              child.remove();
              continue;
            }
            const textFallback = doc.createTextNode(child.textContent || "");
            node.replaceChild(textFallback, child);
            continue;
          }
          // Strip unsafe attributes
          for (const attr of Array.from(child.attributes)) {
            const name = attr.name.toLowerCase();
            if (name.startsWith("on")) {
              child.removeAttribute(attr.name);
              continue;
            }
            if (name === "href") {
              const val = (attr.value || "").trim().toLowerCase();
              if (!val.startsWith("https://") && !val.startsWith("http://") && !val.startsWith("mailto:")) {
                child.removeAttribute(attr.name);
              }
            } else if (!["class", "type", "checked", "data-checked", "contenteditable", "style"].includes(name)) {
              child.removeAttribute(attr.name);
            }
          }
          if (tag === "INPUT" && (child.getAttribute("type") || "").toLowerCase() !== "checkbox") {
            child.remove();
            continue;
          }
          cleanNode(child);
        }
      }
    }

    cleanNode(root);
    return root.innerHTML;
  }

  function extractPlainText() {
    const clone = surface.cloneNode(true);
    const items = clone.querySelectorAll(".priyo-check-item");
    items.forEach(function (item) {
      const checked = item.getAttribute("data-checked") === "true";
      const textEl = item.querySelector(".priyo-check-text");
      const text = textEl ? textEl.textContent : "";
      const replacement = document.createTextNode((checked ? "[x] " : "[ ] ") + text + "\n");
      item.parentNode.replaceChild(replacement, item);
    });
    return (clone.innerText || clone.textContent || "").trim();
  }

  function countWords(text) {
    if (!text) return 0;
    const matches = text.trim().split(/\s+/).filter(Boolean);
    return matches.length;
  }

  function syncCheckboxAttributes() {
    const items = surface.querySelectorAll(".priyo-check-item");
    items.forEach(function (item) {
      const box = item.querySelector(".priyo-check-box");
      if (box) {
        if (item.getAttribute("data-checked") === "true") {
          box.checked = true;
          box.setAttribute("checked", "checked");
        } else {
          box.checked = false;
          box.removeAttribute("checked");
        }
      }
    });
  }

  function notifyContentChanged(immediate) {
    if (suppressNotify) return;
    const emit = function () {
      syncCheckboxAttributes();
      const html = sanitizeHtml(surface.innerHTML);
      const plain = extractPlainText();
      const words = countWords(plain);
      if (window.PriyoBridge && typeof window.PriyoBridge.onContentChanged === "function") {
        window.PriyoBridge.onContentChanged(html, plain, words, plain.length);
      }
    };
    if (immediate) {
      if (notifyTimer) clearTimeout(notifyTimer);
      emit();
    } else {
      if (notifyTimer) clearTimeout(notifyTimer);
      notifyTimer = setTimeout(emit, 180);
    }
  }

  function notifyFormatState() {
    if (!isEditable) return;
    if (!window.PriyoBridge || typeof window.PriyoBridge.onFormatStateChanged !== "function") return;
    try {
      const state = {
        bold: document.queryCommandState("bold"),
        italic: document.queryCommandState("italic"),
        underline: document.queryCommandState("underline"),
        strike: document.queryCommandState("strikeThrough"),
        ul: document.queryCommandState("insertUnorderedList"),
        ol: document.queryCommandState("insertOrderedList"),
        block: (document.queryCommandValue("formatBlock") || "").toLowerCase()
      };
      window.PriyoBridge.onFormatStateChanged(JSON.stringify(state));
    } catch (e) {
      // Ignore queryCommand errors on unsupported selections
    }
  }

  surface.addEventListener("input", function () {
    notifyContentChanged(false);
    notifyFormatState();
  });

  surface.addEventListener("keyup", notifyFormatState);
  surface.addEventListener("mouseup", notifyFormatState);
  document.addEventListener("selectionchange", notifyFormatState);

  surface.addEventListener("change", function (event) {
    const target = event.target;
    if (target && target.classList && target.classList.contains("priyo-check-box")) {
      const item = target.closest(".priyo-check-item");
      if (item) {
        item.setAttribute("data-checked", target.checked ? "true" : "false");
        if (target.checked) {
          target.setAttribute("checked", "checked");
        } else {
          target.removeAttribute("checked");
        }
        notifyContentChanged(true);
      }
    }
  });

  window.PriyoEditor = {
    setMode: function (mode) {
      isEditable = mode !== "view";
      document.body.classList.toggle("mode-edit", isEditable);
      document.body.classList.toggle("mode-view", !isEditable);
      surface.setAttribute("contenteditable", isEditable ? "true" : "false");
      const checkTexts = surface.querySelectorAll(".priyo-check-text");
      checkTexts.forEach(function (el) {
        el.setAttribute("contenteditable", isEditable ? "true" : "false");
      });
    },

    setTheme: function (theme, customBgHex) {
      const isDark = theme === "dark";
      document.body.classList.toggle("theme-dark", isDark);
      document.body.classList.toggle("theme-light", !isDark);
      if (customBgHex && /^#[0-9a-fA-F]{6}$/.test(customBgHex)) {
        document.documentElement.style.setProperty("--bg-color", customBgHex);
      } else {
        document.documentElement.style.removeProperty("--bg-color");
      }
    },

    setHtml: function (html) {
      suppressNotify = true;
      surface.innerHTML = sanitizeHtml(html || "");
      syncCheckboxAttributes();
      const checkTexts = surface.querySelectorAll(".priyo-check-text");
      checkTexts.forEach(function (el) {
        el.setAttribute("contenteditable", isEditable ? "true" : "false");
      });
      suppressNotify = false;
      notifyContentChanged(true);
    },

    execFormat: function (command, value) {
      if (!isEditable) return;
      surface.focus();
      switch (command) {
        case "bold":
        case "italic":
        case "underline":
        case "strikeThrough":
        case "insertUnorderedList":
        case "insertOrderedList":
        case "undo":
        case "redo":
        case "removeFormat":
          document.execCommand(command, false, null);
          break;
        case "heading":
          const current = (document.queryCommandValue("formatBlock") || "").toLowerCase();
          const targetTag = value || "h2";
          if (current === targetTag.toLowerCase()) {
            document.execCommand("formatBlock", false, "p");
          } else {
            document.execCommand("formatBlock", false, targetTag);
          }
          break;
        case "quote":
          const curBlock = (document.queryCommandValue("formatBlock") || "").toLowerCase();
          document.execCommand("formatBlock", false, curBlock === "blockquote" ? "p" : "blockquote");
          break;
        case "code":
          const sel = window.getSelection();
          const selectedText = sel ? sel.toString() : "";
          const escaped = selectedText
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");
          if (selectedText.indexOf("\n") >= 0 || selectedText.length === 0) {
            document.execCommand("insertHTML", false, "<pre><code>" + (escaped || "code") + "</code></pre><p><br></p>");
          } else {
            document.execCommand("insertHTML", false, "<code>" + escaped + "</code>");
          }
          break;
        case "highlight":
          const hlSel = window.getSelection();
          const hlText = hlSel ? hlSel.toString() : "";
          if (hlText) {
            const hlEscaped = hlText.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
            document.execCommand("insertHTML", false, "<mark>" + hlEscaped + "</mark>");
          }
          break;
        case "hr":
          document.execCommand("insertHTML", false, "<hr><p><br></p>");
          break;
        case "checklist":
          const itemHtml =
            '<div class="priyo-check-item" data-checked="false">' +
            '<input type="checkbox" class="priyo-check-box" />' +
            '<span class="priyo-check-text" contenteditable="true">To-do item</span>' +
            '</div><p><br></p>';
          document.execCommand("insertHTML", false, itemHtml);
          break;
        default:
          break;
      }
      notifyContentChanged(true);
      notifyFormatState();
    },

    focusEditor: function () {
      if (isEditable) surface.focus();
    },

    requestSnapshot: function () {
      notifyContentChanged(true);
    }
  };

  if (window.PriyoBridge && typeof window.PriyoBridge.onEditorReady === "function") {
    window.PriyoBridge.onEditorReady();
  }
})();

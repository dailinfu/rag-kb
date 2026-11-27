(function () {
    "use strict";

    const API = "/api";
    const MAX_FILE_SIZE = 20 * 1024 * 1024;
    const QUESTION_LIMIT = 1000;
    const documentList = document.getElementById("documentList");
    const documentCount = document.getElementById("documentCount");
    const fileInput = document.getElementById("fileInput");
    const uploadZone = document.getElementById("uploadZone");
    const refreshButton = document.getElementById("refreshButton");
    const chatForm = document.getElementById("chatForm");
    const questionInput = document.getElementById("questionInput");
    const characterCount = document.getElementById("characterCount");
    const sendButton = document.getElementById("sendButton");
    const conversation = document.getElementById("conversation");
    const messages = document.getElementById("messages");
    const welcomeCard = document.getElementById("welcomeCard");
    const toastRegion = document.getElementById("toastRegion");
    let sending = false;
    let uploading = false;

    async function request(url, options) {
        const response = await fetch(url, options);
        const text = await response.text();
        let payload = text;
        if (text) {
            try {
                payload = JSON.parse(text);
            } catch (_) {
                payload = text;
            }
        }
        if (!response.ok) {
            const message = payload && typeof payload === "object" && payload.message
                ? payload.message
                : (typeof payload === "string" && payload ? payload : "请求失败，请稍后重试");
            throw new Error(message);
        }
        return payload;
    }

    function showToast(message, type) {
        const toast = document.createElement("div");
        toast.className = "toast" + (type === "error" ? " error" : "");
        toast.textContent = message;
        toastRegion.appendChild(toast);
        window.setTimeout(function () {
            toast.remove();
        }, 4200);
    }

    function formatDate(value) {
        if (!value) return "";
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return "";
        return new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
    }

    async function loadDocuments() {
        refreshButton.classList.add("loading");
        try {
            const docs = await request(API + "/documents");
            renderDocuments(Array.isArray(docs) ? docs : []);
        } catch (error) {
            documentList.innerHTML = "";
            const placeholder = document.createElement("div");
            placeholder.className = "list-placeholder";
            placeholder.textContent = "加载失败，点击刷新重试";
            documentList.appendChild(placeholder);
            showToast(error.message, "error");
        } finally {
            refreshButton.classList.remove("loading");
        }
    }

    function renderDocuments(docs) {
        documentCount.textContent = String(docs.length);
        documentList.innerHTML = "";
        if (docs.length === 0) {
            const placeholder = document.createElement("div");
            placeholder.className = "list-placeholder";
            placeholder.textContent = "还没有文档，添加资料开始使用";
            documentList.appendChild(placeholder);
            return;
        }

        docs.forEach(function (doc) {
            const item = document.createElement("div");
            item.className = "document-item";

            const icon = document.createElement("span");
            icon.className = "file-icon";
            icon.textContent = (doc.filename || "").toLowerCase().endsWith(".md") ? "MD" : "TXT";

            const copy = document.createElement("div");
            copy.className = "document-copy";
            const name = document.createElement("div");
            name.className = "document-name";
            name.textContent = doc.filename || "未命名文档";
            name.title = doc.filename || "";
            const meta = document.createElement("div");
            meta.className = "document-meta";
            const chunkCount = Number(doc.chunkCount) || 0;
            meta.textContent = chunkCount + " 个片段" + (doc.createdAt ? " · " + formatDate(doc.createdAt) : "");
            copy.append(name, meta);

            const remove = document.createElement("button");
            remove.className = "delete-button";
            remove.type = "button";
            remove.setAttribute("aria-label", "删除 " + (doc.filename || "文档"));
            remove.title = "删除文档";
            remove.textContent = "×";
            remove.addEventListener("click", function () {
                deleteDocument(doc.id, doc.filename);
            });
            item.append(icon, copy, remove);
            documentList.appendChild(item);
        });
    }

    async function deleteDocument(id, filename) {
        if (!id || !window.confirm("确定删除“" + (filename || "这份文档") + "”吗？对应的知识片段也会一并删除。")) return;
        try {
            await request(API + "/documents/" + encodeURIComponent(id), { method: "DELETE" });
            showToast("文档已删除");
            await loadDocuments();
        } catch (error) {
            showToast(error.message, "error");
        }
    }

    function handleFiles(files) {
        if (!files || files.length === 0) return;
        if (files.length > 1) {
            showToast("一次只能上传一个文件", "error");
            return;
        }
        const file = files[0];
        if (!/\.(txt|md)$/.test(file.name)) {
            showToast("仅支持 .txt 或 .md 文档", "error");
            return;
        }
        if (file.size > MAX_FILE_SIZE) {
            showToast("文件不能超过 20 MB", "error");
            return;
        }
        uploadFile(file);
    }

    async function uploadFile(file) {
        if (uploading) return;
        uploading = true;
        uploadZone.disabled = true;
        uploadZone.querySelector(".upload-copy strong").textContent = "正在上传并处理…";
        const formData = new FormData();
        formData.append("file", file);
        try {
            const result = await request(API + "/documents/upload", { method: "POST", body: formData });
            showToast("已添加 " + result.filename + "，生成 " + result.chunkCount + " 个知识片段");
            await loadDocuments();
        } catch (error) {
            showToast(error.message, "error");
        } finally {
            uploading = false;
            uploadZone.disabled = false;
            uploadZone.querySelector(".upload-copy strong").textContent = "添加知识文档";
            fileInput.value = "";
        }
    }

    function appendUserMessage(question) {
        const message = document.createElement("div");
        message.className = "message user";
        const body = document.createElement("div");
        body.className = "message-body";
        body.textContent = question;
        message.appendChild(body);
        messages.appendChild(message);
        scrollConversation();
    }

    function appendTyping() {
        const message = document.createElement("div");
        message.className = "message assistant";
        message.id = "typingMessage";
        const avatar = document.createElement("div");
        avatar.className = "assistant-avatar";
        avatar.textContent = "知";
        const body = document.createElement("div");
        body.className = "message-body";
        const label = document.createElement("div");
        label.className = "assistant-label";
        label.textContent = "知问正在查阅知识库";
        const typing = document.createElement("div");
        typing.className = "typing";
        for (let i = 0; i < 3; i++) typing.appendChild(document.createElement("span"));
        body.append(label, typing);
        message.append(avatar, body);
        messages.appendChild(message);
        scrollConversation();
        return message;
    }

    function appendAssistantMessage(answer, sources, isError) {
        const message = document.createElement("div");
        message.className = "message assistant";
        const avatar = document.createElement("div");
        avatar.className = "assistant-avatar";
        avatar.textContent = "知";
        const body = document.createElement("div");
        body.className = "message-body";
        if (isError) body.classList.add("error-message");
        const label = document.createElement("div");
        label.className = "assistant-label";
        label.textContent = isError ? "请求未完成" : "知问";
        const text = document.createElement("div");
        text.textContent = answer;
        body.append(label, text);

        if (!isError) {
            const sourceSection = document.createElement("div");
            sourceSection.className = "sources";
            const sourceHeading = document.createElement("div");
            sourceHeading.className = "sources-heading";
            const sourceIcon = document.createElement("span");
            sourceIcon.textContent = "↗";
            sourceHeading.append(sourceIcon, document.createTextNode("参考来源"));
            sourceSection.appendChild(sourceHeading);

            if (Array.isArray(sources) && sources.length > 0) {
                const list = document.createElement("div");
                list.className = "source-list";
                sources.forEach(function (source) {
                    const card = document.createElement("div");
                    card.className = "source-card";
                    const meta = document.createElement("div");
                    meta.className = "source-meta";
                    const filename = document.createElement("span");
                    filename.className = "source-filename";
                    filename.textContent = source.filename || "知识库文档";
                    filename.title = source.filename || "";
                    const score = document.createElement("span");
                    score.className = "source-score";
                    const numericScore = Number(source.score);
                    score.textContent = "相关度 " + (Number.isFinite(numericScore) ? Math.round(numericScore * 100) : 0) + "%";
                    meta.append(filename, score);
                    const content = document.createElement("div");
                    content.className = "source-content";
                    content.textContent = source.content || "";
                    card.append(meta, content);
                    list.appendChild(card);
                });
                sourceSection.appendChild(list);
            } else {
                const noSources = document.createElement("div");
                noSources.className = "no-sources";
                noSources.textContent = "本次没有匹配到可展示的参考片段。";
                sourceSection.appendChild(noSources);
            }
            body.appendChild(sourceSection);
        }
        message.append(avatar, body);
        messages.appendChild(message);
        scrollConversation();
    }

    function scrollConversation() {
        window.requestAnimationFrame(function () {
            conversation.scrollTop = conversation.scrollHeight;
        });
    }

    function setSending(value) {
        sending = value;
        sendButton.disabled = value;
        sendButton.innerHTML = value ? '<span aria-hidden="true">…</span>' : '<span aria-hidden="true">↑</span>';
    }

    async function sendQuestion(question) {
        const normalized = question.trim();
        if (!normalized || sending) return;
        if (normalized.length > QUESTION_LIMIT) {
            showToast("问题不能超过 " + QUESTION_LIMIT + " 个字符", "error");
            return;
        }
        welcomeCard.hidden = true;
        appendUserMessage(normalized);
        questionInput.value = "";
        updateComposer();
        setSending(true);
        const typing = appendTyping();
        try {
            const result = await request(API + "/chat", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ question: normalized })
            });
            typing.remove();
            appendAssistantMessage(result.answer || "没有收到模型回答。", result.sources || [], false);
        } catch (error) {
            typing.remove();
            appendAssistantMessage(error.message || "问答请求失败，请稍后重试。", [], true);
        } finally {
            setSending(false);
            questionInput.focus();
        }
    }

    function updateComposer() {
        characterCount.textContent = questionInput.value.length + " / " + QUESTION_LIMIT;
        questionInput.style.height = "auto";
        questionInput.style.height = Math.min(questionInput.scrollHeight, 140) + "px";
    }

    uploadZone.addEventListener("click", function () {
        if (!uploading) fileInput.click();
    });
    fileInput.addEventListener("change", function () {
        handleFiles(fileInput.files);
    });
    uploadZone.addEventListener("dragover", function (event) {
        event.preventDefault();
        uploadZone.classList.add("dragging");
    });
    uploadZone.addEventListener("dragleave", function () {
        uploadZone.classList.remove("dragging");
    });
    uploadZone.addEventListener("drop", function (event) {
        event.preventDefault();
        uploadZone.classList.remove("dragging");
        handleFiles(event.dataTransfer.files);
    });
    refreshButton.addEventListener("click", loadDocuments);
    chatForm.addEventListener("submit", function (event) {
        event.preventDefault();
        sendQuestion(questionInput.value);
    });
    questionInput.addEventListener("input", updateComposer);
    questionInput.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            sendQuestion(questionInput.value);
        }
    });
    document.getElementById("newChatButton").addEventListener("click", function () {
        messages.innerHTML = "";
        welcomeCard.hidden = false;
        questionInput.value = "";
        updateComposer();
        questionInput.focus();
    });
    document.querySelectorAll(".suggestion").forEach(function (button) {
        button.addEventListener("click", function () {
            sendQuestion(button.dataset.question || "");
        });
    });

    updateComposer();
    loadDocuments();
}());

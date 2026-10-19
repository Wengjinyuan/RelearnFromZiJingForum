(() => {
    "use strict";

    // 这个页面只依赖新功能自己的接口，不调用原论坛的 API。
    const CHAT_ENDPOINT = "/ai/ask";

    const form = document.querySelector("#chatForm");
    const input = document.querySelector("#questionInput");
    const sendButton = document.querySelector("#sendButton");
    const clearButton = document.querySelector("#clearButton");
    const messages = document.querySelector("#messages");
    const template = document.querySelector("#messageTemplate");
    const characterCount = document.querySelector("#characterCount");
    const status = document.querySelector("#connectionStatus");
    const suggestions = document.querySelectorAll("[data-question]");
    const welcomeMessage = messages.firstElementChild.cloneNode(true);

    let requestInProgress = false;

    function setStatus(type, text) {
        status.className = `connection-status${type ? ` is-${type}` : ""}`;
        status.querySelector("span:last-child").textContent = text;
    }

    function resizeInput() {
        input.style.height = "auto";
        input.style.height = `${Math.min(input.scrollHeight, 150)}px`;
        characterCount.textContent = String(input.value.length);
    }

    function scrollToLatest() {
        messages.scrollTop = messages.scrollHeight;
    }

    function createMessage(role, text, options = {}) {
        const fragment = template.content.cloneNode(true);
        const article = fragment.querySelector(".message");
        const avatar = fragment.querySelector(".avatar");
        const roleLabel = fragment.querySelector(".message-role");
        const body = fragment.querySelector("p");

        const isUser = role === "user";
        article.classList.add(isUser ? "user-message" : "assistant-message");
        if (options.error) {
            article.classList.add("error-message");
        }

        avatar.textContent = isUser ? "你" : "AI";
        roleLabel.textContent = isUser ? "你" : options.error ? "请求失败" : "LangChain4j 助手";

        // 使用 textContent 写入模型回答，避免把回答中的 HTML 当成页面代码执行。
        body.textContent = text;
        messages.appendChild(fragment);
        scrollToLatest();
        return messages.lastElementChild;
    }

    function createLoadingMessage() {
        const message = createMessage("assistant", "");
        const body = message.querySelector("p");
        body.setAttribute("aria-label", "模型正在生成回答");
        body.innerHTML = '<span class="typing-dots" aria-hidden="true"><i></i><i></i><i></i></span>';
        return message;
    }

    async function askModel(question) {
        requestInProgress = true;
        sendButton.disabled = true;
        input.disabled = true;
        setStatus("loading", "模型思考中");

        createMessage("user", question);
        const loadingMessage = createLoadingMessage();

        try {
            const response = await fetch(CHAT_ENDPOINT, {
                method: "POST",
                headers: {
                    "Accept": "text/plain",
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({ question })
            });

            const answer = await response.text();
            loadingMessage.remove();

            if (!response.ok) {
                throw new Error(answer || `服务器返回了 ${response.status}`);
            }

            createMessage("assistant", answer || "模型返回了空内容，请换一个问题试试。");
            setStatus("success", "回答完成");
        } catch (error) {
            loadingMessage.remove();
            const reason = error instanceof Error ? error.message : "未知错误";
            createMessage(
                "assistant",
                `没有拿到模型回答。请确认后端已经启动，并检查 API Key 与模型配置。\n\n错误信息：${reason}`,
                { error: true }
            );
            setStatus("error", "请求失败");
        } finally {
            requestInProgress = false;
            sendButton.disabled = false;
            input.disabled = false;
            input.focus();
        }
    }

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const question = input.value.trim();
        if (!question || requestInProgress) {
            return;
        }

        input.value = "";
        resizeInput();
        askModel(question);
    });

    input.addEventListener("input", resizeInput);
    input.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            form.requestSubmit();
        }
    });

    clearButton.addEventListener("click", () => {
        if (requestInProgress) {
            return;
        }
        messages.replaceChildren(welcomeMessage.cloneNode(true));
        setStatus("", "等待提问");
        input.focus();
    });

    suggestions.forEach((button) => {
        button.addEventListener("click", () => {
            if (requestInProgress) {
                return;
            }
            input.value = button.dataset.question || "";
            resizeInput();
            input.focus();
        });
    });

    resizeInput();
})();

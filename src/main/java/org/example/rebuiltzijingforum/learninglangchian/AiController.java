package org.example.rebuiltzijingforum.learninglangchian;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 独立的 LangChain4j 学习入口，不参与 F08 论坛 AI 搜索。 */
@Controller
@RequestMapping("/ai")
@ConditionalOnProperty(prefix = "forum.learning-ai", name = "enabled", havingValue = "true")
public class AiController {
    private final ChatModel model;

    public AiController(ChatModel model) {
        this.model = model;
    }

    @GetMapping("/chat")
    public String chatPage() {
        return "redirect:/langchian/index.html";
    }

    @ResponseBody
    @PostMapping(value = "/ask", consumes = "application/json", produces = "text/plain;charset=UTF-8")
    public String ask(@Valid @RequestBody QuestionRequest request) {
        return model.chat(request.question().trim());
    }

    public record QuestionRequest(
            @NotBlank(message = "问题不能为空")
            @Size(max = 1000, message = "问题不能超过1000字")
            String question) {}
}

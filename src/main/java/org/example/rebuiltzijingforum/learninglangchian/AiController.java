
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.*;

// 本文件要完成：接收用户问题，调用 AI，返回结果。
@RestController
@RequestMapping("/ai")
public class AiController {

    private final ChatModel model;

    public AiController() {
        model = OpenAiChatModel.builder()
                .baseUrl("https://api.deepseek.com/v1")
                .apiKey(System.getenv("DEEPSEEK_API_KEY"))
                .modelName("deepseek-flash")
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String question) {
        return model.chat(question);
    }
}
